package com.jobera.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A teammate invited onto a company's recruiting account, distinct from the account that owns
 *  the {@link Recruiter} row itself (Recruiter.user). Access control treats a team member as
 *  equivalent to the owner for that one company - see AccessPolicy.ownsRecruiterId, the single
 *  chokepoint every recruiter-owned-resource check in the app already funnels through. */
@Entity
@Table(name = "recruiter_team_members", uniqueConstraints = @UniqueConstraint(columnNames = {"recruiter_id", "user_id"}))
public class RecruiterTeamMember {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_HIRING_MANAGER = "HIRING_MANAGER";
    public static final String ROLE_INTERVIEWER = "INTERVIEWER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "invited_at", nullable = false)
    private LocalDateTime invitedAt;

    public RecruiterTeamMember() {}

    public RecruiterTeamMember(Long recruiterId, Long userId, String role) {
        this.recruiterId = recruiterId;
        this.userId = userId;
        this.role = role;
        this.invitedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecruiterId() { return recruiterId; }
    public void setRecruiterId(Long recruiterId) { this.recruiterId = recruiterId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public LocalDateTime getInvitedAt() { return invitedAt; }
    public void setInvitedAt(LocalDateTime invitedAt) { this.invitedAt = invitedAt; }
}
