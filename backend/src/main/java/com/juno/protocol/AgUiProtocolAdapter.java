package com.juno.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AG-UI protocol adapter for CopilotKit.
 * Converts AgentResponse domain objects into AG-UI SSE events.
 *
 * AG-UI event types used:
 * - RUN_STARTED / RUN_FINISHED / RUN_ERROR (lifecycle)
 * - TEXT_MESSAGE_START / TEXT_MESSAGE_CONTENT / TEXT_MESSAGE_END (text)
 * - TOOL_CALL_START / TOOL_CALL_ARGS / TOOL_CALL_END (tool calls)
 * - STATE_SNAPSHOT (state sync)
 */
@Component
public class AgUiProtocolAdapter implements ProtocolAdapter {

    private final ObjectMapper objectMapper;

    public AgUiProtocolAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<ServerSentEvent<String>> toSSE(AgentResponse response, String threadId, String runId) {
        return Flux.create(sink -> {
            try {
                // RUN_STARTED
                sink.next(sse(event("RUN_STARTED", Map.of(
                        "threadId", threadId,
                        "runId", runId
                ))));

                if (response.hitlPending()) {
                    // HITL path: emit tool call events + state snapshot
                    var hitl = response.hitlState();

                    sink.next(sse(event("TOOL_CALL_START", Map.of(
                            "toolCallId", hitl.toolCallId(),
                            "toolCallName", hitl.toolCallName()
                    ))));

                    sink.next(sse(event("TOOL_CALL_ARGS", Map.of(
                            "toolCallId", hitl.toolCallId(),
                            "delta", serialize(hitl.toolCallArgs())
                    ))));

                    sink.next(sse(event("TOOL_CALL_END", Map.of(
                            "toolCallId", hitl.toolCallId()
                    ))));

                    sink.next(sse(event("STATE_SNAPSHOT", Map.of(
                            "snapshot", Map.of(
                                    "status", hitl.status(),
                                    "agent", hitl.agentName()
                            )
                    ))));
                } else {
                    // Normal path: emit text message + tool call results

                    // Emit tool call results first (for frontend card rendering)
                    for (var toolResult : response.toolCallResults()) {
                        sink.next(sse(event("TOOL_CALL_START", Map.of(
                                "toolCallId", toolResult.toolCallId(),
                                "toolCallName", toolResult.toolName()
                        ))));

                        sink.next(sse(event("TOOL_CALL_ARGS", Map.of(
                                "toolCallId", toolResult.toolCallId(),
                                "delta", toolResult.arguments()
                        ))));

                        sink.next(sse(event("TOOL_CALL_END", Map.of(
                                "toolCallId", toolResult.toolCallId()
                        ))));

                        sink.next(sse(event("TOOL_CALL_RESULT", Map.of(
                                "messageId", "msg-" + UUID.randomUUID().toString().substring(0, 8),
                                "toolCallId", toolResult.toolCallId(),
                                "content", toolResult.result(),
                                "role", "tool"
                        ))));
                    }

                    // Emit text message
                    if (response.text() != null && !response.text().isEmpty()) {
                        String messageId = "msg-" + UUID.randomUUID().toString().substring(0, 8);

                        sink.next(sse(event("TEXT_MESSAGE_START", Map.of(
                                "messageId", messageId,
                                "role", "assistant"
                        ))));

                        sink.next(sse(event("TEXT_MESSAGE_CONTENT", Map.of(
                                "messageId", messageId,
                                "delta", response.text()
                        ))));

                        sink.next(sse(event("TEXT_MESSAGE_END", Map.of(
                                "messageId", messageId
                        ))));
                    }
                }

                // RUN_FINISHED
                sink.next(sse(event("RUN_FINISHED", Map.of(
                        "threadId", threadId,
                        "runId", runId
                ))));

                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        });
    }

    @Override
    public Flux<ServerSentEvent<String>> toErrorSSE(String errorMessage, String threadId, String runId) {
        return Flux.just(
                sse(event("RUN_STARTED", Map.of("threadId", threadId, "runId", runId))),
                sse(event("RUN_ERROR", Map.of("message", errorMessage))),
                sse(event("RUN_FINISHED", Map.of("threadId", threadId, "runId", runId)))
        );
    }

    private Map<String, Object> event(String type, Map<String, Object> fields) {
        var event = new LinkedHashMap<String, Object>();
        event.put("type", type);
        event.put("timestamp", System.currentTimeMillis());
        event.putAll(fields);
        return event;
    }

    private ServerSentEvent<String> sse(Map<String, Object> event) {
        return ServerSentEvent.builder(serialize(event)).build();
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AG-UI event", e);
        }
    }
}
