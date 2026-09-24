package com.jobera.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A candidate's account of what actually interviewing at a company was like - process,
 *  difficulty, outcome - distinct from CompanyReview's overall star rating. Goes through the same
 *  moderation queue before it's publicly visible, for the same reason: user-submitted text about
 *  a real company carries real spam/defamation risk. */
@Entity
@Table(name = "interview_experiences", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "recruiter_id"}))
public class InterviewExperience {
    public static final String MODERATION_PENDING = "PENDING";
    public static final String MODERATION_APPROVED = "APPROVED";
    public static final String MODERATION_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "recruiter_id", nullable = false)
    private Recruiter recruiter;

    @Column(name = "difficulty_rating", nullable = false)
    private Integer difficultyRating; // 1 (easy) to 5 (very hard)

    @Column(nullable = false, length = 20)
    private String outcome; // OFFER, REJECTED, NO_RESPONSE, WITHDREW

    @Column(name = "process_description", columnDefinition = "TEXT")
    private String processDescription;

    @Column(name = "moderation_status")
    private String moderationStatus = MODERATION_PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public InterviewExperience() {}

    public InterviewExperience(User user, Recruiter recruiter, Integer difficultyRating, String outcome,
                                String processDescription) {
        this.user = user;
        this.recruiter = recruiter;
        this.difficultyRating = difficultyRating;
        this.outcome = outcome;
        this.processDescription = processDescription;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Recruiter getRecruiter() { return recruiter; }
    public void setRecruiter(Recruiter recruiter) { this.recruiter = recruiter; }
    public Integer getDifficultyRating() { return difficultyRating; }
    public void setDifficultyRating(Integer difficultyRating) { this.difficultyRating = difficultyRating; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getProcessDescription() { return processDescription; }
    public void setProcessDescription(String processDescription) { this.processDescription = processDescription; }
    public String getModerationStatus() { return moderationStatus; }
    public void setModerationStatus(String moderationStatus) { this.moderationStatus = moderationStatus; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
