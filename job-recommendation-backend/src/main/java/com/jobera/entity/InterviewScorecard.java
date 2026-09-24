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

/** A recruiter's structured feedback on one interview round - a fixed rubric rather than free-form
 *  notes alone, so scores are comparable across rounds and candidates. One per round: submitting
 *  again overwrites the previous scorecard for that round rather than creating a second one. */
@Entity
@Table(name = "interview_scorecards")
public class InterviewScorecard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interview_round_id", nullable = false, unique = true)
    private Long interviewRoundId;

    @Column(name = "submitted_by_user_id", nullable = false)
    private Long submittedByUserId;

    @Column(name = "communication_rating", nullable = false)
    private Integer communicationRating;

    @Column(name = "technical_rating", nullable = false)
    private Integer technicalRating;

    @Column(name = "culture_fit_rating", nullable = false)
    private Integer cultureFitRating;

    @Column(nullable = false, length = 20)
    private String recommendation;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public InterviewScorecard() {}

    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
        updatedAt = submittedAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInterviewRoundId() { return interviewRoundId; }
    public void setInterviewRoundId(Long interviewRoundId) { this.interviewRoundId = interviewRoundId; }
    public Long getSubmittedByUserId() { return submittedByUserId; }
    public void setSubmittedByUserId(Long submittedByUserId) { this.submittedByUserId = submittedByUserId; }
    public Integer getCommunicationRating() { return communicationRating; }
    public void setCommunicationRating(Integer communicationRating) { this.communicationRating = communicationRating; }
    public Integer getTechnicalRating() { return technicalRating; }
    public void setTechnicalRating(Integer technicalRating) { this.technicalRating = technicalRating; }
    public Integer getCultureFitRating() { return cultureFitRating; }
    public void setCultureFitRating(Integer cultureFitRating) { this.cultureFitRating = cultureFitRating; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
