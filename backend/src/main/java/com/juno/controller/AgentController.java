package com.juno.controller;

import com.juno.agent.OrchestratorService;
import com.juno.protocol.ProtocolAdapter;
import com.juno.protocol.RunAgentInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * SSE endpoint for the agent system.
 * Accepts AG-UI RunAgentInput, delegates to OrchestratorService,
 * and converts the AgentResponse to SSE events via ProtocolAdapter.
 *
 * This controller is thin — all logic is in OrchestratorService (protocol-agnostic)
 * and ProtocolAdapter (protocol-specific serialization).
 */
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*") // Phase 4: replace with proper CORS config
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final OrchestratorService orchestrator;
    private final ProtocolAdapter protocolAdapter;

    public AgentController(OrchestratorService orchestrator, ProtocolAdapter protocolAdapter) {
        this.orchestrator = orchestrator;
        this.protocolAdapter = protocolAdapter;
    }

    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> run(@RequestBody RunAgentInput input) {
        String threadId = input.threadId() != null ? input.threadId() : "default";
        String runId = input.runId() != null ? input.runId() : "run-" + System.currentTimeMillis();

        log.info("Agent request: threadId={}, runId={}, message='{}'",
                threadId, runId, truncate(input.userMessage(), 100));

        return Flux.defer(() -> {
            try {
                var response = orchestrator.process(threadId, input);
                return protocolAdapter.toSSE(response, threadId, runId);
            } catch (Exception e) {
                log.error("Agent error for thread {}: {}", threadId, e.getMessage(), e);
                return protocolAdapter.toErrorSSE(e.getMessage(), threadId, runId);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
