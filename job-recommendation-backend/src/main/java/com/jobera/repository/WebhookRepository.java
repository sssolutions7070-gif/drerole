package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.Webhook;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, Long> {
    List<Webhook> findByRecruiterIdOrderByCreatedAtDesc(Long recruiterId);
    List<Webhook> findByRecruiterIdAndEventTypeAndActiveTrue(Long recruiterId, String eventType);
    Optional<Webhook> findByIdAndRecruiterId(Long id, Long recruiterId);
}
