package com.juno.protocol;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * Converts protocol-agnostic AgentResponse into framework-specific SSE events.
 *
 * This is the SWAPPABLE layer. Implementations:
 * - AgUiProtocolAdapter: AG-UI events for CopilotKit
 * - (future) VercelAiProtocolAdapter: Vercel AI SDK format for assistant-ui
 *
 * Switching frontends = swap which @Bean is active.
 */
public interface ProtocolAdapter {

    /**
     * Convert an AgentResponse into a stream of SSE events,
     * bracketed by run lifecycle events (start/finish/error).
     */
    Flux<ServerSentEvent<String>> toSSE(AgentResponse response, String threadId, String runId);

    /**
     * Emit a run error as SSE events.
     */
    Flux<ServerSentEvent<String>> toErrorSSE(String errorMessage, String threadId, String runId);
}
