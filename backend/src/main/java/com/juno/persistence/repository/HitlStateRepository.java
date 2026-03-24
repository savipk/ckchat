package com.juno.persistence.repository;

import com.juno.persistence.entity.HitlStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HitlStateRepository extends JpaRepository<HitlStateEntity, String> {
    Optional<HitlStateEntity> findByThreadId(String threadId);
    boolean existsByThreadId(String threadId);
    void deleteByThreadId(String threadId);
}
