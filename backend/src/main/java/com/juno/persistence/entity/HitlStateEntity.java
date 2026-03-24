package com.juno.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persistent HITL (Human-in-the-Loop) state.
 * Survives server restarts — a user can approve a profile update 24h+ later.
 */
@Entity
@Table(name = "hitl_states")
public class HitlStateEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, unique = true, length = 64)
    private String threadId;

    @Column(length = 64)
    private String agentName;

    @Column(columnDefinition = "TEXT")
    private String pendingToolCall;

    @Column(columnDefinition = "TEXT")
    private String chatOptions;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant expiresAt;

    public HitlStateEntity() {}

    public HitlStateEntity(String id, String threadId, String agentName,
                           String pendingToolCall, String chatOptions) {
        this.id = id;
        this.threadId = threadId;
        this.agentName = agentName;
        this.pendingToolCall = pendingToolCall;
        this.chatOptions = chatOptions;
        this.createdAt = Instant.now();
        this.expiresAt = Instant.now().plusSeconds(7 * 24 * 3600); // 7 days default
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getPendingToolCall() { return pendingToolCall; }
    public void setPendingToolCall(String pendingToolCall) { this.pendingToolCall = pendingToolCall; }
    public String getChatOptions() { return chatOptions; }
    public void setChatOptions(String chatOptions) { this.chatOptions = chatOptions; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
