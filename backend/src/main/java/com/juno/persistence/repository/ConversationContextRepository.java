package com.juno.persistence.repository;

import com.juno.persistence.entity.ConversationContextEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationContextRepository extends JpaRepository<ConversationContextEntity, String> {
    Optional<ConversationContextEntity> findByThreadIdAndAgentName(String threadId, String agentName);
    void deleteByThreadId(String threadId);
}
