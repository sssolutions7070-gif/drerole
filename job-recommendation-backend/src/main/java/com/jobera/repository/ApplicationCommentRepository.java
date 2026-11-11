package com.jobera.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jobera.entity.ApplicationComment;

public interface ApplicationCommentRepository extends JpaRepository<ApplicationComment, Long> {
    List<ApplicationComment> findByApplicationIdOrderByCreatedAtAsc(Long applicationId);
}
