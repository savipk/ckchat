package com.ckchat.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ckchat.protocol.AgentResponse;
import com.ckchat.protocol.RunAgentInput;
import com.ckchat.service.ConversationStateStore;
import com.ckchat.service.ProfileScoreService;
import com.ckchat.tools.ProfileTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Protocol-agnostic orchestrator that routes user messages to specialist agents.
 *
 * Responsibilities:
 * - Classify user intent and route to the correct agent ChatClient
 * - Detect HITL tool calls (approveProfileUpdate) and return them for frontend rendering
 * - Handle HITL resume: load persisted state, execute the approved tool, return result
 * - For non-HITL flows: call specialist agent and return text response
 *
 * This service returns AgentResponse (domain objects).
 * The ProtocolAdapter handles serialization to SSE events.
 *
 * HITL pattern follows autochat/docs/springai/10-hitl-distributed-non-blocking.md:
 * - Stream 1: detect HITL tool → simulate score → emit events → persist state → close
 * - Stream 2: load state → execute backend tool → return final text
 */
@Service
public class OrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);
    private static final Set<String> HITL_TOOLS = Set.of("approveProfileUpdate");

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

    /**
     * Process a user request. Entry point for the AG-UI controller.
     */
    public AgentResponse process(String threadId, RunAgentInput input) {
        var toolResult = input.extractToolResult();

        if (toolResult != null) {
            return resumeFromHitl(threadId, toolResult);
        }

        return routeAndExecute(threadId, input);
    }

    private AgentResponse routeAndExecute(String threadId, RunAgentInput input) {
        String userMessage = input.userMessage();

        if (userMessage.isBlank()) {
            return AgentResponse.text("Hi! I can help with your profile, job search, outreach, "
                    + "candidate search, and job descriptions. What would you like to do?");
        }

        // Classify intent → agent name (null = direct orchestrator response)
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

        // Call the specialist agent
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

        // Parse the user's decision
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

        // Execute the approved update
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

    /**
     * Simple keyword-based intent classification.
     * Returns agent name or null for direct orchestrator response.
     *
     * Phase 2: Replace with LLM-based classification via orchestrator ChatClient.
     */
    private String classifyIntent(String message) {
        String lower = message.toLowerCase();

        if (containsAny(lower, "profile", "skills", "skill", "experience",
                "analyze my", "update my", "rollback", "infer skill")) {
            return "profile";
        }

        if (containsAny(lower, "find job", "find me job", "job match", "find role", "show me role",
                "job search", "matching job", "open position", "internal job", "find me a")) {
            return "job-discovery";
        }

        if (containsAny(lower, "draft message", "send message", "reach out", "contact hiring",
                "message the hiring", "apply for")) {
            return "outreach";
        }

        if (containsAny(lower, "find candidate", "search candidate", "search employee",
                "find employee", "find people", "search people")) {
            return "candidate-search";
        }

        if (containsAny(lower, "job description", "create jd", "write jd", "generate jd",
                "requisition", "draft jd")) {
            return "jd-generator";
        }

        if (containsAny(lower, "hello", "hi", "hey", "good morning", "good afternoon",
                "thanks", "thank you", "bye", "goodbye", "help", "what can you do")) {
            return null;
        }

        // Default: return null for orchestrator direct response
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

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
