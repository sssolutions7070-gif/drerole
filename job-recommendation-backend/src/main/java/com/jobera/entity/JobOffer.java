package com.jobera.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** A job offer for one application, sent by the recruiter and signed (or declined) by the
 *  candidate. "Signing" is a typed full legal name plus a recorded IP and timestamp - a
 *  click-to-sign audit trail, not a cryptographic signature - deliberately not integrating a real
 *  e-signature provider (DocuSign etc.) for this. */
@Entity
@Table(name = "job_offers")
public class JobOffer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false, unique = true)
    private Long applicationId;

    @Column(nullable = false)
    private String title;

    @Column(name = "salary_offered", nullable = false)
    private String salaryOffered;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "additional_terms", columnDefinition = "TEXT")
    private String additionalTerms;

    @Column(nullable = false, length = 20)
    private String status = "SENT";

    @Column(name = "candidate_signature_name")
    private String candidateSignatureName;

    @Column(name = "candidate_signature_ip")
    private String candidateSignatureIp;

    @Column(name = "signature_data_url", columnDefinition = "TEXT")
    private String signatureDataUrl;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "decline_reason", columnDefinition = "TEXT")
    private String declineReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public JobOffer() {}

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) {
            status = "SENT";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSalaryOffered() { return salaryOffered; }
    public void setSalaryOffered(String salaryOffered) { this.salaryOffered = salaryOffered; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public String getAdditionalTerms() { return additionalTerms; }
    public void setAdditionalTerms(String additionalTerms) { this.additionalTerms = additionalTerms; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCandidateSignatureName() { return candidateSignatureName; }
    public void setCandidateSignatureName(String candidateSignatureName) { this.candidateSignatureName = candidateSignatureName; }
    public String getCandidateSignatureIp() { return candidateSignatureIp; }
    public void setCandidateSignatureIp(String candidateSignatureIp) { this.candidateSignatureIp = candidateSignatureIp; }
    public String getSignatureDataUrl() { return signatureDataUrl; }
    public void setSignatureDataUrl(String signatureDataUrl) { this.signatureDataUrl = signatureDataUrl; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String declineReason) { this.declineReason = declineReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
