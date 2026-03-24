package com.juno.persistence.repository;

import com.juno.persistence.entity.ThreadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ThreadRepository extends JpaRepository<ThreadEntity, String> {
    List<ThreadEntity> findByUserIdOrderByLastMessageAtDesc(String userId);
}
