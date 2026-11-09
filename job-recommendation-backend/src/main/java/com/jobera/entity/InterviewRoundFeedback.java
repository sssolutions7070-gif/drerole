package com.jobera.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** The candidate's rating of one specific interview round - how that round itself went
 *  (organization, professionalism), not the company overall. Distinct from InterviewExperience
 *  (a free-standing, company-level, post-process review): this is tied to a concrete
 *  InterviewRound and feeds the recruiter's accountability numbers alongside response-time
 *  (see ApplicationStatusService/CompanyService), rather than being purely a public review. One
 *  row per (round, candidate) - a candidate can update their own rating after submitting it. */
@Entity
@Table(name = "interview_round_feedback",
       uniqueConstraints = @UniqueConstraint(columnNames = {"interview_round_id", "candidate_user_id"}))
public class InterviewRoundFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interview_round_id", nullable = false)
    private Long interviewRoundId;

    @Column(name = "candidate_user_id", nullable = false)
    private Long candidateUserId;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInterviewRoundId() { return interviewRoundId; }
    public void setInterviewRoundId(Long interviewRoundId) { this.interviewRoundId = interviewRoundId; }
    public Long getCandidateUserId() { return candidateUserId; }
    public void setCandidateUserId(Long candidateUserId) { this.candidateUserId = candidateUserId; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
