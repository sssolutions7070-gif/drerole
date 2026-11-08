package com.jobera.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Remembers the Google Calendar event id a given interview round was pushed to for a given user,
 *  so a later reschedule updates that same event (PATCH) instead of creating a duplicate. One row
 *  per (round, user) pair - the candidate and the recruiter each get their own event, since they
 *  each authorize their own calendar independently. */
@Entity
@Table(name = "calendar_sync_records",
       uniqueConstraints = @UniqueConstraint(columnNames = {"interview_round_id", "user_id"}))
public class CalendarSyncRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interview_round_id", nullable = false)
    private Long interviewRoundId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "google_event_id", nullable = false, length = 512)
    private String googleEventId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getInterviewRoundId() { return interviewRoundId; }
    public void setInterviewRoundId(Long interviewRoundId) { this.interviewRoundId = interviewRoundId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getGoogleEventId() { return googleEventId; }
    public void setGoogleEventId(String googleEventId) { this.googleEventId = googleEventId; }
}
