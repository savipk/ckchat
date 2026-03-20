package com.juno.service;

import com.juno.protocol.AgentResponse;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory conversation state store for HITL persistence.
 * Phase 1: ConcurrentHashMap (single-server).
 * Phase 2: Replace with Cosmos DB / PostgreSQL for distributed persistence.
 */
@Service
public class ConversationStateStore {

    private final ConcurrentHashMap<String, AgentResponse.ConversationState> store = new ConcurrentHashMap<>();

    public void save(String threadId, AgentResponse.ConversationState state) {
        store.put(threadId, state);
    }

    public AgentResponse.ConversationState load(String threadId) {
        return store.get(threadId);
    }

    public void delete(String threadId) {
        store.remove(threadId);
    }

    public boolean hasPending(String threadId) {
        return store.containsKey(threadId);
    }
}
