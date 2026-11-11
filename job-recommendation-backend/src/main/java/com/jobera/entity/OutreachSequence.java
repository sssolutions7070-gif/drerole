package com.jobera.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A multi-step automated outreach campaign to one sourced candidate - step 0 sends immediately
 *  on creation (via DirectMessageService), later steps fire once their delay has elapsed, and the
 *  whole sequence stops the moment the candidate replies (see OutreachSequenceService). */
@Entity
@Table(name = "outreach_sequences")
public class OutreachSequence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;

    @Column(name = "candidate_user_id", nullable = false)
    private Long candidateUserId;

    // JSON array of {"delayDays": int, "message": string}, ordered - step 0 is sent immediately
    @Column(columnDefinition = "TEXT", nullable = false)
    private String steps;

    @Column(name = "current_step", nullable = false)
    private Integer currentStep = 0;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_STOPPED = "STOPPED";
    public static final String STATUS_REPLIED = "REPLIED";

    @Column(nullable = false, length = 20)
    private String status = STATUS_ACTIVE;

    @Column(name = "last_step_sent_at")
    private LocalDateTime lastStepSentAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public OutreachSequence() {}

    public OutreachSequence(Long recruiterId, Long candidateUserId, String steps) {
        this.recruiterId = recruiterId;
        this.candidateUserId = candidateUserId;
        this.steps = steps;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecruiterId() { return recruiterId; }
    public void setRecruiterId(Long recruiterId) { this.recruiterId = recruiterId; }
    public Long getCandidateUserId() { return candidateUserId; }
    public void setCandidateUserId(Long candidateUserId) { this.candidateUserId = candidateUserId; }
    public String getSteps() { return steps; }
    public void setSteps(String steps) { this.steps = steps; }
    public Integer getCurrentStep() { return currentStep; }
    public void setCurrentStep(Integer currentStep) { this.currentStep = currentStep; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getLastStepSentAt() { return lastStepSentAt; }
    public void setLastStepSentAt(LocalDateTime lastStepSentAt) { this.lastStepSentAt = lastStepSentAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
