package com.juno.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "threads")
public class ThreadEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String userId;

    @Column(length = 256)
    private String title;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastMessageAt;

    @Column(length = 512)
    private String previewText;

    public ThreadEntity() {}

    public ThreadEntity(String id, String userId) {
        this.id = id;
        this.userId = userId;
        this.createdAt = Instant.now();
        this.lastMessageAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
    public String getPreviewText() { return previewText; }
    public void setPreviewText(String previewText) { this.previewText = previewText; }
}
