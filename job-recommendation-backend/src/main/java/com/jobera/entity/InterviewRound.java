package com.jobera.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** One stage of a (possibly multi-round) interview pipeline for an application - a phone screen,
 *  a technical round, a culture-fit round, and so on. Each round gets its own video/code-editor
 *  room, scheduled and run independently of the others. */
@Entity
@Table(name = "interview_rounds")
public class InterviewRound {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(nullable = false)
    private String title;

    @Column(name = "round_type", nullable = false, length = 20)
    private String roundType;

    @Column(name = "room_id", nullable = false, unique = true, length = 36)
    private String roomId;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    // Set instead of scheduledAt when a recruiter offers the candidate a choice of times rather
    // than dictating one - a JSON array of ISO-8601 date-times. Cleared once the candidate picks
    // one (InterviewRoundService.confirmSlot moves that choice into scheduledAt).
    @Column(name = "proposed_slots", columnDefinition = "TEXT")
    private String proposedSlots;

    @Column(nullable = false, length = 20)
    private String status = "SCHEDULED";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public InterviewRound() {}

    public InterviewRound(Long applicationId, Integer roundNumber, String title, String roundType,
                           String roomId, LocalDateTime scheduledAt) {
        this.applicationId = applicationId;
        this.roundNumber = roundNumber;
        this.title = title;
        this.roundType = roundType;
        this.roomId = roomId;
        this.scheduledAt = scheduledAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) {
            status = "SCHEDULED";
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Integer getRoundNumber() { return roundNumber; }
    public void setRoundNumber(Integer roundNumber) { this.roundNumber = roundNumber; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRoundType() { return roundType; }
    public void setRoundType(String roundType) { this.roundType = roundType; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getProposedSlots() { return proposedSlots; }
    public void setProposedSlots(String proposedSlots) { this.proposedSlots = proposedSlots; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
