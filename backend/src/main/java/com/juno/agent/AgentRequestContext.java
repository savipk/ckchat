package com.juno.agent;

/**
 * ThreadLocal-based holder for per-request context (threadId, userId).
 * Set by OrchestratorService before calling agents, cleared in finally block.
 * Tools read via AgentRequestContext.get().
 *
 * Safe with WebFlux + Schedulers.boundedElastic() because OrchestratorService.process()
 * runs synchronously within the deferred block on the same elastic thread.
 */
public final class AgentRequestContext {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    public record Context(String threadId, String userId) {}

    public static void set(String threadId, String userId) {
        CURRENT.set(new Context(threadId, userId));
    }

    public static Context get() {
        var ctx = CURRENT.get();
        return ctx != null ? ctx : new Context("default", "default");
    }

    public static void clear() {
        CURRENT.remove();
    }

    private AgentRequestContext() {}
}
