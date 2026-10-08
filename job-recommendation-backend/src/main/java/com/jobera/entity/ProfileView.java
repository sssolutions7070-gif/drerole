package com.jobera.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A recruiter viewing an open-to-work candidate's expanded profile in Discover Candidates - the
 *  symmetric counterpart to a recruiter already being able to see who viewed an application.
 *  One row per (recruiter, candidate) pair; re-viewing just bumps viewedAt rather than piling up
 *  duplicate rows in the candidate's "who viewed me" list. */
@Entity
@Table(name = "profile_views", uniqueConstraints = @UniqueConstraint(columnNames = {"recruiter_id", "viewed_user_id"}))
public class ProfileView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;

    @Column(name = "viewed_user_id", nullable = false)
    private Long viewedUserId;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    public ProfileView() {}

    public ProfileView(Long recruiterId, Long viewedUserId) {
        this.recruiterId = recruiterId;
        this.viewedUserId = viewedUserId;
    }

    @PrePersist
    protected void onCreate() {
        if (viewedAt == null) {
            viewedAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecruiterId() { return recruiterId; }
    public void setRecruiterId(Long recruiterId) { this.recruiterId = recruiterId; }
    public Long getViewedUserId() { return viewedUserId; }
    public void setViewedUserId(Long viewedUserId) { this.viewedUserId = viewedUserId; }
    public LocalDateTime getViewedAt() { return viewedAt; }
    public void setViewedAt(LocalDateTime viewedAt) { this.viewedAt = viewedAt; }
}
