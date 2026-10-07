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

/**
 * A saved candidate discovery search query with optional alert notifications.
 */
@Entity
@Table(name = "saved_candidate_searches")
public class SavedCandidateSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "skill_filter", length = 150)
    private String skillFilter;

    @Column(name = "location_filter", length = 150)
    private String locationFilter;

    @Column(name = "ai_query", length = 500)
    private String aiQuery;

    @Column(name = "alert_enabled", nullable = false)
    private boolean alertEnabled = true;

    /** Match count seen at the last evaluation; alerts fire only when the count grows past it. */
    @Column(name = "last_match_count", nullable = false)
    private int lastMatchCount = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SavedCandidateSearch() {}

    public SavedCandidateSearch(Long recruiterId, String name, String skillFilter, String locationFilter, String aiQuery, Boolean alertEnabled) {
        this.recruiterId = recruiterId;
        this.name = name;
        this.skillFilter = skillFilter;
        this.locationFilter = locationFilter;
        this.aiQuery = aiQuery;
        this.alertEnabled = alertEnabled != null ? alertEnabled : true;
    }

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
    public Long getRecruiterId() { return recruiterId; }
    public void setRecruiterId(Long recruiterId) { this.recruiterId = recruiterId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSkillFilter() { return skillFilter; }
    public void setSkillFilter(String skillFilter) { this.skillFilter = skillFilter; }
    public String getLocationFilter() { return locationFilter; }
    public void setLocationFilter(String locationFilter) { this.locationFilter = locationFilter; }
    public String getAiQuery() { return aiQuery; }
    public void setAiQuery(String aiQuery) { this.aiQuery = aiQuery; }
    public boolean isAlertEnabled() { return alertEnabled; }
    public void setAlertEnabled(boolean alertEnabled) { this.alertEnabled = alertEnabled; }
    public int getLastMatchCount() { return lastMatchCount; }
    public void setLastMatchCount(int lastMatchCount) { this.lastMatchCount = lastMatchCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
