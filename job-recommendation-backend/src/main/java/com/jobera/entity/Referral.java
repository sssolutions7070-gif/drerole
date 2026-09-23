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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One successful signup attributed to another user's referral code. */
@Entity
@Table(name = "referrals", uniqueConstraints = @UniqueConstraint(columnNames = {"referred_user_id"}))
public class Referral {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "referrer_id", nullable = false)
    private User referrer;

    // A user can only ever be referred once - the unique constraint above enforces that at the
    // DB level, not just in application logic.
    @ManyToOne
    @JoinColumn(name = "referred_user_id", nullable = false)
    private User referredUser;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    public Referral() {}

    public Referral(User referrer, User referredUser) {
        this.referrer = referrer;
        this.referredUser = referredUser;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getReferrer() { return referrer; }
    public void setReferrer(User referrer) { this.referrer = referrer; }
    public User getReferredUser() { return referredUser; }
    public void setReferredUser(User referredUser) { this.referredUser = referredUser; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
