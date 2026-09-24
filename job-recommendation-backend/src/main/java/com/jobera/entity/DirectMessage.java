package com.jobera.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A chat message between a recruiter and a candidate that isn't tied to any application - the
 *  outreach case {@link Message} doesn't cover: a recruiter found this candidate through
 *  CandidateDiscoveryService (the "open to work" pool) and there's no application yet to scope a
 *  {@link Message} thread to. A thread is identified by the (recruiterId, candidateUserId) pair. */
@Entity
@Table(name = "direct_messages")
public class DirectMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;

    @Column(name = "candidate_user_id", nullable = false)
    private Long candidateUserId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;

    public DirectMessage() {}

    public DirectMessage(Long recruiterId, Long candidateUserId, Long senderId, Long recipientId, String body) {
        this.recruiterId = recruiterId;
        this.candidateUserId = candidateUserId;
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.body = body;
    }

    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecruiterId() { return recruiterId; }
    public void setRecruiterId(Long recruiterId) { this.recruiterId = recruiterId; }
    public Long getCandidateUserId() { return candidateUserId; }
    public void setCandidateUserId(Long candidateUserId) { this.candidateUserId = candidateUserId; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
}
