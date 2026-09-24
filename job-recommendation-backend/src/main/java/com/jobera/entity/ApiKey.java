package com.jobera.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A machine credential for a recruiter's own integrations (their ATS, a script, Zapier) to call
 *  the same API a logged-in recruiter uses, without a browser session. Only the SHA-256 hash is
 *  stored - the plaintext key is shown to the recruiter exactly once, at creation. */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    // First few characters of the plaintext key, kept only so the recruiter can tell their keys
    // apart in a list ("dr_live_a1b2...") - not enough to reconstruct or guess the key.
    @Column(name = "key_prefix", nullable = false, length = 32)
    private String keyPrefix;

    @Column(name = "label")
    private String label;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(nullable = false)
    private boolean revoked = false;

    public ApiKey() {}

    public ApiKey(Long recruiterId, String keyHash, String keyPrefix, String label) {
        this.recruiterId = recruiterId;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.label = label;
        this.createdAt = LocalDateTime.now();
        this.revoked = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRecruiterId() { return recruiterId; }
    public void setRecruiterId(Long recruiterId) { this.recruiterId = recruiterId; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
}
