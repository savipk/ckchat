package com.juno.agent;

/**
 * Per-request context carrying user and session information.
 * Injected into agents and tools via method parameters or request-scoped beans.
 */
public record AgentContext(
        String threadId,
        String userId,
        String displayName,
        String profilePath
) {

    public String subAgentThreadId(String agentName) {
        return threadId + ":" + agentName;
    }
}
