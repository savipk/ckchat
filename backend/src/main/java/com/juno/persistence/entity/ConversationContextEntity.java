package com.juno.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Agent conversation state per thread.
 * Stores JD drafts, search context, and other agent-specific state
 * that needs to survive across requests in a distributed environment.
 */
@Entity
@Table(name = "conversation_contexts",
       uniqueConstraints = @UniqueConstraint(columnNames = {"threadId", "agentName"}))
public class ConversationContextEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String threadId;

    @Column(nullable = false, length = 64)
    private String agentName;

    @Column(columnDefinition = "TEXT")
    private String contextData;

    @Column(nullable = false)
    private Instant updatedAt;

    public ConversationContextEntity() {}

    public ConversationContextEntity(String id, String threadId, String agentName, String contextData) {
        this.id = id;
        this.threadId = threadId;
        this.agentName = agentName;
        this.contextData = contextData;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getContextData() { return contextData; }
    public void setContextData(String contextData) { this.contextData = contextData; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
