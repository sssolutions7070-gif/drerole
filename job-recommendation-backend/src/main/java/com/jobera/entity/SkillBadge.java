package com.jobera.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A verified credential earned by passing an aptitude or Dream Role test - shareable proof of a
 *  skill that's reusable across applications instead of re-testing every time. One per
 *  (candidate, test): a later attempt overwrites the score/date rather than stacking duplicates. */
@Entity
@Table(name = "skill_badges", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "source_type", "source_test_id"}))
public class SkillBadge {
    public static final String SOURCE_APTITUDE_TEST = "APTITUDE_TEST";
    public static final String SOURCE_DREAM_ROLE_TEST = "DREAM_ROLE_TEST";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "source_test_id", nullable = false)
    private Long sourceTestId;

    @Column(name = "badge_name", nullable = false)
    private String badgeName;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "earned_at", nullable = false)
    private LocalDateTime earnedAt;

    public SkillBadge() {}

    public SkillBadge(Long userId, String sourceType, Long sourceTestId, String badgeName, Integer score) {
        this.userId = userId;
        this.sourceType = sourceType;
        this.sourceTestId = sourceTestId;
        this.badgeName = badgeName;
        this.score = score;
        this.earnedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceTestId() { return sourceTestId; }
    public void setSourceTestId(Long sourceTestId) { this.sourceTestId = sourceTestId; }
    public String getBadgeName() { return badgeName; }
    public void setBadgeName(String badgeName) { this.badgeName = badgeName; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public LocalDateTime getEarnedAt() { return earnedAt; }
    public void setEarnedAt(LocalDateTime earnedAt) { this.earnedAt = earnedAt; }
}
