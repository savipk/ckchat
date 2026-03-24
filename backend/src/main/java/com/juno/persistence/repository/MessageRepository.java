package com.juno.persistence.repository;

import com.juno.persistence.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, String> {
    List<MessageEntity> findByThreadIdOrderByCreatedAtAsc(String threadId);
    void deleteByThreadId(String threadId);
}
