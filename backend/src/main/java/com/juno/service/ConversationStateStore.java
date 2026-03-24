package com.juno.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.juno.persistence.entity.HitlStateEntity;
import com.juno.persistence.repository.HitlStateRepository;
import com.juno.protocol.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent conversation state store for HITL.
 * Uses JPA + SQLite (swappable to Cosmos DB).
 * Survives server restarts — users can approve HITL actions 24h+ later.
 */
@Service
public class ConversationStateStore {

    private static final Logger log = LoggerFactory.getLogger(ConversationStateStore.class);

    private final HitlStateRepository repository;
    private final ObjectMapper objectMapper;

    public ConversationStateStore(HitlStateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void save(String threadId, AgentResponse.ConversationState state) {
        // Remove any existing state for this thread first
        repository.deleteByThreadId(threadId);

        try {
            var entity = new HitlStateEntity(
                    UUID.randomUUID().toString().substring(0, 8),
                    threadId,
                    state.agentName(),
                    objectMapper.writeValueAsString(state.pendingToolCall()),
                    state.chatOptions() != null ? objectMapper.writeValueAsString(state.chatOptions()) : null
            );
            repository.save(entity);
            log.info("Saved HITL state for thread: {}", threadId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize HITL state for thread {}: {}", threadId, e.getMessage());
            throw new RuntimeException("Failed to save HITL state", e);
        }
    }

    public AgentResponse.ConversationState load(String threadId) {
        return repository.findByThreadId(threadId)
                .map(entity -> {
                    try {
                        Map<String, Object> pendingToolCall = objectMapper.readValue(
                                entity.getPendingToolCall(),
                                new TypeReference<Map<String, Object>>() {});
                        Map<String, Object> chatOptions = entity.getChatOptions() != null
                                ? objectMapper.readValue(entity.getChatOptions(),
                                        new TypeReference<Map<String, Object>>() {})
                                : null;
                        return new AgentResponse.ConversationState(
                                threadId,
                                entity.getAgentName(),
                                List.of(), // history not persisted (rebuilt from message store)
                                pendingToolCall,
                                chatOptions
                        );
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize HITL state for thread {}: {}", threadId, e.getMessage());
                        return null;
                    }
                })
                .orElse(null);
    }

    @Transactional
    public void delete(String threadId) {
        repository.deleteByThreadId(threadId);
        log.info("Deleted HITL state for thread: {}", threadId);
    }

    public boolean hasPending(String threadId) {
        return repository.existsByThreadId(threadId);
    }
}
