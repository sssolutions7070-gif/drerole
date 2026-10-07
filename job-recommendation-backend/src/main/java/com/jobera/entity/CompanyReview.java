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

/** A candidate's review of a company (= a recruiter's organization - see CompanyService). Goes
 *  through the same moderation queue as job postings before it's publicly visible, since
 *  user-submitted text about a real company carries real spam/defamation risk. */
@Entity
@Table(name = "company_reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "recruiter_id"}))
public class CompanyReview {
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

    @Column(nullable = false)
    private Integer rating; // 1 to 5

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "moderation_status")
    private String moderationStatus = MODERATION_PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    /** The reviewed company's own public reply, visible alongside the review - unlike the review
     *  itself, this is the recruiter's own words about their own company, so it doesn't go through
     *  moderation. Null until they reply. */
    @Column(name = "recruiter_reply", columnDefinition = "TEXT")
    private String recruiterReply;

    @Column(name = "recruiter_reply_at")
    private LocalDateTime recruiterReplyAt;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public CompanyReview() {}

    public CompanyReview(User user, Recruiter recruiter, Integer rating, String comment) {
        this.user = user;
        this.recruiter = recruiter;
        this.rating = rating;
        this.comment = comment;
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
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getModerationStatus() { return moderationStatus; }
    public void setModerationStatus(String moderationStatus) { this.moderationStatus = moderationStatus; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getRecruiterReply() { return recruiterReply; }
    public void setRecruiterReply(String recruiterReply) { this.recruiterReply = recruiterReply; }
    public LocalDateTime getRecruiterReplyAt() { return recruiterReplyAt; }
    public void setRecruiterReplyAt(LocalDateTime recruiterReplyAt) { this.recruiterReplyAt = recruiterReplyAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
