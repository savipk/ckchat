package com.juno.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juno.protocol.AgentResponse;
import com.juno.protocol.RunAgentInput;
import com.juno.service.ConversationStateStore;
import com.juno.service.ProfileScoreService;
import com.juno.tools.ProfileTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);
    private static final Set<String> HITL_TOOLS = Set.of("approveProfileUpdate");
    private static final Set<String> APPROVAL_PHRASES = Set.of(
            "yes", "y", "sure", "go ahead", "approve", "ok", "okay", "confirm", "yep", "yeah");
    private static final Set<String> REJECTION_PHRASES = Set.of(
            "no", "n", "cancel", "reject", "decline", "nope", "nah", "never mind");

    private final Map<String, ChatClient> agentClients;
    private final ConversationStateStore stateStore;
    private final ProfileScoreService scoreService;
    private final ProfileTools profileTools;
    private final ObjectMapper objectMapper;

    public OrchestratorService(Map<String, ChatClient> agentClients,
                                ConversationStateStore stateStore,
                                ProfileScoreService scoreService,
                                ProfileTools profileTools,
                                ObjectMapper objectMapper) {
        this.agentClients = agentClients;
        this.stateStore = stateStore;
        this.scoreService = scoreService;
        this.profileTools = profileTools;
        this.objectMapper = objectMapper;
    }

    public AgentResponse process(String threadId, RunAgentInput input) {
        // Set request context for tools to access threadId/userId
        String userId = extractUserId(input);
        AgentRequestContext.set(threadId, userId);
        try {
            var toolResult = input.extractToolResult();
            if (toolResult != null) {
                return resumeFromHitl(threadId, toolResult);
            }
            return routeAndExecute(threadId, input);
        } finally {
            AgentRequestContext.clear();
        }
    }

    private AgentResponse routeAndExecute(String threadId, RunAgentInput input) {
        String userMessage = input.userMessage();

        // HITL: check for pending approval via chat text
        if (stateStore.hasPending(threadId)) {
            String trimmed = userMessage.toLowerCase().trim();
            if (APPROVAL_PHRASES.contains(trimmed)) {
                log.info("Chat-based HITL approval for thread: {}", threadId);
                return executeApprovedHitl(threadId);
            } else if (REJECTION_PHRASES.contains(trimmed)) {
                stateStore.delete(threadId);
                return AgentResponse.text("No worries — the profile update has been cancelled.");
            } else {
                // Unrelated message while HITL pending → auto-reject and proceed
                log.info("Auto-rejecting pending HITL for thread {} due to new message", threadId);
                stateStore.delete(threadId);
            }
        }

        if (userMessage.isBlank()) {
            return AgentResponse.text("Hi! I can help with your profile, job search, outreach, "
                    + "candidate search, and job descriptions. What would you like to do?");
        }

        String agentName = classifyIntent(userMessage);

        if (agentName == null) {
            return handleDirectResponse(userMessage);
        }

        ChatClient agent = agentClients.get(agentName);
        if (agent == null) {
            log.warn("No agent found for name: {}", agentName);
            return AgentResponse.text("I can help with profile management, job discovery, outreach, "
                    + "candidate search, and job descriptions. Could you tell me more?");
        }

        log.info("Routing to agent: {} for thread: {}", agentName, threadId);

        String response = agent.prompt()
                .user(userMessage)
                .call()
                .content();

        return AgentResponse.text(response);
    }

    @SuppressWarnings("unchecked")
    private AgentResponse resumeFromHitl(String threadId, RunAgentInput.Message toolResult) {
        log.info("Resuming HITL for thread: {}, toolCallId: {}", threadId, toolResult.toolCallId());

        var savedState = stateStore.load(threadId);
        if (savedState == null) {
            return AgentResponse.text("I couldn't find the pending action. Could you try again?");
        }

        boolean approved = false;
        try {
            var decision = objectMapper.readValue(toolResult.content(), Map.class);
            approved = Boolean.TRUE.equals(decision.get("approved"));
        } catch (JsonProcessingException e) {
            approved = toolResult.content() != null && toolResult.content().contains("true");
        }

        stateStore.delete(threadId);

        if (!approved) {
            return AgentResponse.text("No worries — the profile update has been cancelled.");
        }

        return executeApprovedUpdate(savedState);
    }

    @SuppressWarnings("unchecked")
    private AgentResponse executeApprovedHitl(String threadId) {
        var savedState = stateStore.load(threadId);
        if (savedState == null) {
            return AgentResponse.text("I couldn't find the pending action. Could you try again?");
        }
        stateStore.delete(threadId);
        return executeApprovedUpdate(savedState);
    }

    @SuppressWarnings("unchecked")
    private AgentResponse executeApprovedUpdate(AgentResponse.ConversationState savedState) {
        var pendingTool = savedState.pendingToolCall();
        String section = (String) pendingTool.getOrDefault("section", "skills");
        var updates = (Map<String, Object>) pendingTool.getOrDefault("updates", Map.of());
        String operation = (String) pendingTool.getOrDefault("operation", "merge");
        String entryId = (String) pendingTool.get("entryId");

        var result = profileTools.updateProfile(section, updates, operation, entryId);

        int prevScore = (int) result.getOrDefault("previous_completion_score", 0);
        int newScore = (int) result.getOrDefault("estimated_new_score", 0);

        return AgentResponse.text("Profile updated successfully! Your completion score went from "
                + prevScore + "% to " + newScore + "%.");
    }

    private String classifyIntent(String message) {
        String lower = message.toLowerCase();

        if (containsAny(lower, "profile", "skills", "skill", "experience",
                "analyze my", "update my", "rollback", "infer skill")) {
            return "profile";
        }

        if (containsAny(lower, "find job", "find me job", "job match", "find role", "show me role",
                "job search", "matching job", "open position", "internal job", "find me a",
                "find matching role", "find roles")) {
            return "job-discovery";
        }

        if (containsAny(lower, "draft message", "send message", "reach out", "contact hiring",
                "message the hiring", "apply for")) {
            return "outreach";
        }

        if (containsAny(lower, "find candidate", "search candidate", "search employee",
                "find employee", "find people", "search people", "search for candidate",
                "find candidates")) {
            return "candidate-search";
        }

        if (containsAny(lower, "job description", "create jd", "write jd", "generate jd",
                "requisition", "draft jd", "create a job description")) {
            return "jd-generator";
        }

        if (containsAny(lower, "hello", "hi", "hey", "good morning", "good afternoon",
                "thanks", "thank you", "bye", "goodbye", "help", "what can you do")) {
            return null;
        }

        return null;
    }

    private AgentResponse handleDirectResponse(String message) {
        String lower = message.toLowerCase();

        if (containsAny(lower, "hello", "hi", "hey", "good morning", "good afternoon")) {
            return AgentResponse.text("Hi! I'm the HR Assistant. I can help you with:\n\n"
                    + "- **Profile management** — analyze, update skills, manage experience\n"
                    + "- **Job discovery** — find matching internal roles\n"
                    + "- **Outreach** — draft messages to hiring managers\n"
                    + "- **Candidate search** — find internal employees by skills\n"
                    + "- **JD creation** — create job descriptions\n\n"
                    + "What would you like to do?");
        }

        if (containsAny(lower, "thanks", "thank you")) {
            return AgentResponse.text("You're welcome! Let me know if there's anything else I can help with.");
        }

        if (containsAny(lower, "bye", "goodbye")) {
            return AgentResponse.text("Goodbye! Feel free to come back anytime.");
        }

        return AgentResponse.text("I can help with profile management, job discovery, outreach, "
                + "candidate search, and job descriptions. What would you like to do?");
    }

    private String extractUserId(RunAgentInput input) {
        if (input.forwardedProps() != null) {
            Object userId = input.forwardedProps().get("userId");
            if (userId != null) return String.valueOf(userId);
        }
        return "default";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
