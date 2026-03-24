package com.juno.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String threadId;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 128)
    private String toolCallId;

    @Column(nullable = false)
    private Instant createdAt;

    public MessageEntity() {}

    public MessageEntity(String id, String threadId, String role, String content) {
        this.id = id;
        this.threadId = threadId;
        this.role = role;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
