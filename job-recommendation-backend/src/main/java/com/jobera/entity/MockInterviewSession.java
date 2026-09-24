package com.jobera.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A candidate's practice-interview session for a specific job posting - AI-generated questions,
 *  answered and scored one at a time, so they can rehearse before the real interview. */
@Entity
@Table(name = "mock_interview_sessions")
public class MockInterviewSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_user_id", nullable = false)
    private Long candidateUserId;

    @Column(name = "job_posting_id", nullable = false)
    private Long jobPostingId;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    @Column(nullable = false, length = 20)
    private String status = "IN_PROGRESS";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public MockInterviewSession() {}

    public MockInterviewSession(Long candidateUserId, Long jobPostingId, String jobTitle) {
        this.candidateUserId = candidateUserId;
        this.jobPostingId = jobPostingId;
        this.jobTitle = jobTitle;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "IN_PROGRESS";
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCandidateUserId() { return candidateUserId; }
    public void setCandidateUserId(Long candidateUserId) { this.candidateUserId = candidateUserId; }
    public Long getJobPostingId() { return jobPostingId; }
    public void setJobPostingId(Long jobPostingId) { this.jobPostingId = jobPostingId; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
