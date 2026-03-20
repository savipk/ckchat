package com.juno.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * AG-UI RunAgentInput — the POST body sent by the frontend.
 * This DTO is protocol-specific but kept minimal so it can
 * be adapted to other formats (Vercel AI SDK, etc.).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RunAgentInput(
        @JsonProperty("threadId") String threadId,
        @JsonProperty("runId") String runId,
        @JsonProperty("messages") List<Message> messages,
        @JsonProperty("state") Map<String, Object> state,
        @JsonProperty("tools") List<Map<String, Object>> tools,
        @JsonProperty("context") List<Map<String, Object>> context,
        @JsonProperty("forwardedProps") Map<String, Object> forwardedProps
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            @JsonProperty("id") String id,
            @JsonProperty("role") String role,
            @JsonProperty("content") String content,
            @JsonProperty("toolCallId") String toolCallId
    ) {}

    /**
     * Extract a tool result message if this is a HITL resume request.
     * Returns null if this is a normal user message.
     */
    public Message extractToolResult() {
        if (messages == null) return null;
        return messages.stream()
                .filter(m -> "tool".equals(m.role()) && m.toolCallId() != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get the latest user message content.
     */
    public String userMessage() {
        if (messages == null) return "";
        return messages.stream()
                .filter(m -> "user".equals(m.role()))
                .reduce((first, second) -> second) // last user message
                .map(Message::content)
                .orElse("");
    }
}
