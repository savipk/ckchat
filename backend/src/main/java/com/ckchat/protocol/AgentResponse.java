package com.ckchat.protocol;

import java.util.List;
import java.util.Map;

/**
 * Protocol-agnostic domain object returned by OrchestratorService.
 * The ProtocolAdapter converts this into framework-specific SSE events
 * (AG-UI for CopilotKit, Vercel AI SDK for assistant-ui, etc.).
 */
public record AgentResponse(
        String text,
        List<ToolCallResult> toolCallResults,
        boolean hitlPending,
        HitlState hitlState,
        ConversationState conversationState
) {

    public static AgentResponse text(String text) {
        return new AgentResponse(text, List.of(), false, null, null);
    }

    public static AgentResponse withToolResults(String text, List<ToolCallResult> toolCallResults) {
        return new AgentResponse(text, toolCallResults, false, null, null);
    }

    public static AgentResponse hitlPending(HitlState hitlState, ConversationState conversationState) {
        return new AgentResponse(null, List.of(), true, hitlState, conversationState);
    }

    /**
     * Represents the result of a backend tool execution.
     */
    public record ToolCallResult(
            String toolCallId,
            String toolName,
            String arguments,
            String result
    ) {}

    /**
     * State to persist when a HITL tool call is detected.
     * Saved to shared DB so any server can resume.
     */
    public record ConversationState(
            String threadId,
            String agentName,
            List<Map<String, Object>> history,
            Map<String, Object> pendingToolCall,
            Map<String, Object> chatOptions
    ) {}

    /**
     * HITL-specific state sent to the frontend for rendering
     * the approval UI (e.g., ProfileUpdateConfirmation).
     */
    public record HitlState(
            String toolCallId,
            String toolCallName,
            Map<String, Object> toolCallArgs,
            String status,
            String agentName
    ) {}
}
