package com.jobera.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A recruiter-registered URL to be POSTed to when a chosen event happens on one of their jobs -
 *  lets an external system (their own ATS, Zapier) react without polling the API. */
@Entity
@Table(name = "webhooks")
public class Webhook {
    public static final String EVENT_APPLICATION_SUBMITTED = "APPLICATION_SUBMITTED";
    public static final String EVENT_OFFER_ACCEPTED = "OFFER_ACCEPTED";
    public static final String EVENT_INTERVIEW_SCHEDULED = "INTERVIEW_SCHEDULED";
    public static final String EVENT_TEST_COMPLETED = "TEST_COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;

    @Column(nullable = false)
    private String url;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    // HMAC-SHA256 signing secret for this one webhook - generated once, shown once, never
    // re-displayed, the same handling as ApiKey's plaintext key.
    @Column(nullable = false)
    private String secret;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean active = true;

    public Webhook() {}

    public Webhook(Long recruiterId, String url, String eventType, String secret) {
        this.recruiterId = recruiterId;
        this.url = url;
        this.eventType = eventType;
        this.secret = secret;
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecruiterId() { return recruiterId; }
    public void setRecruiterId(Long recruiterId) { this.recruiterId = recruiterId; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
