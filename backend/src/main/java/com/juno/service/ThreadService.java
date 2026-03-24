package com.juno.service;

import com.juno.persistence.entity.MessageEntity;
import com.juno.persistence.entity.ThreadEntity;
import com.juno.persistence.repository.MessageRepository;
import com.juno.persistence.repository.ThreadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ThreadService {

    private final ThreadRepository threadRepository;
    private final MessageRepository messageRepository;

    public ThreadService(ThreadRepository threadRepository, MessageRepository messageRepository) {
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
    }

    public ThreadEntity createThread(String userId) {
        var thread = new ThreadEntity(UUID.randomUUID().toString(), userId);
        return threadRepository.save(thread);
    }

    public List<ThreadEntity> getThreadsForUser(String userId) {
        return threadRepository.findByUserIdOrderByLastMessageAtDesc(userId);
    }

    public ThreadEntity updateTitle(String threadId, String title) {
        return threadRepository.findById(threadId)
                .map(t -> {
                    t.setTitle(title);
                    return threadRepository.save(t);
                })
                .orElse(null);
    }

    public ThreadEntity updateLastMessage(String threadId, String previewText) {
        return threadRepository.findById(threadId)
                .map(t -> {
                    t.setLastMessageAt(Instant.now());
                    t.setPreviewText(previewText != null && previewText.length() > 500
                            ? previewText.substring(0, 500) : previewText);
                    return threadRepository.save(t);
                })
                .orElse(null);
    }

    @Transactional
    public void deleteThread(String threadId) {
        messageRepository.deleteByThreadId(threadId);
        threadRepository.deleteById(threadId);
    }

    public MessageEntity addMessage(String threadId, String role, String content) {
        var msg = new MessageEntity(UUID.randomUUID().toString(), threadId, role, content);
        return messageRepository.save(msg);
    }

    public List<MessageEntity> getMessages(String threadId) {
        return messageRepository.findByThreadIdOrderByCreatedAtAsc(threadId);
    }
}
