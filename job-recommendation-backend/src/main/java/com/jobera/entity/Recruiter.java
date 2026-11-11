package com.jobera.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "recruiters")
public class Recruiter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private String companyName;
    
    @Column(columnDefinition = "TEXT")
    private String companyDescription;
    
    private String companyWebsite;
    private String companySize;
    private String industry;
    private String contactEmail;
    private String contactPhone;
    
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // Subscription (Stripe). FREE is the default for every recruiter and needs no Stripe
    // involvement at all - stripeCustomerId/stripeSubscriptionId only get set once they actually
    // start a paid checkout.
    @Column(name = "subscription_tier", nullable = false, length = 20)
    private String subscriptionTier = "FREE";

    @Column(name = "subscription_status", length = 20)
    private String subscriptionStatus;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    public static final String VERIFICATION_NONE = "NONE";
    public static final String VERIFICATION_PENDING = "PENDING";
    public static final String VERIFICATION_VERIFIED = "VERIFIED";
    public static final String VERIFICATION_REJECTED = "REJECTED";

    @Column(name = "verification_status", nullable = false, length = 20)
    private String verificationStatus = VERIFICATION_NONE;

    @Column(name = "verification_document_path")
    private String verificationDocumentPath;

    @Column(name = "verification_rejection_reason")
    private String verificationRejectionReason;

    // Richer employer branding, shown on the public company profile page (CompanyService)
    @Column(name = "company_logo")
    private String companyLogo;

    @Column(name = "company_banner")
    private String companyBanner;

    @Column(name = "mission_statement", columnDefinition = "TEXT")
    private String missionStatement;

    // JSON array of short perk/benefit strings, same JSON-string-column convention used
    // elsewhere in this codebase (e.g. JobPosting.requiredSkills)
    @Column(columnDefinition = "TEXT")
    private String benefits;

    @Column(name = "company_linkedin")
    private String companyLinkedin;

    @Column(name = "company_twitter")
    private String companyTwitter;

    @Column(name = "founded_year")
    private Integer foundedYear;

    private String headquarters;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (subscriptionTier == null) {
            subscriptionTier = "FREE";
        }
        if (verificationStatus == null) {
            verificationStatus = VERIFICATION_NONE;
        }
    }

    // A recruiter row created before this column existed gets backfilled by MySQL with '' (the
    // implicit default for a NOT NULL varchar), not NULL and not the Java field default above -
    // normalize it here so every existing recruiter is still treated as FREE.
    @PostLoad
    protected void onLoad() {
        if (subscriptionTier == null || subscriptionTier.isBlank()) {
            subscriptionTier = "FREE";
        }
        if (verificationStatus == null || verificationStatus.isBlank()) {
            verificationStatus = VERIFICATION_NONE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Constructors
    public Recruiter() {}
    
    public Recruiter(User user, String companyName) {
        this.user = user;
        this.companyName = companyName;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getCompanyDescription() { return companyDescription; }
    public void setCompanyDescription(String companyDescription) { this.companyDescription = companyDescription; }
    public String getCompanyWebsite() { return companyWebsite; }
    public void setCompanyWebsite(String companyWebsite) { this.companyWebsite = companyWebsite; }
    public String getCompanySize() { return companySize; }
    public void setCompanySize(String companySize) { this.companySize = companySize; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public String getCompanyLogo() { return companyLogo; }
    public void setCompanyLogo(String companyLogo) { this.companyLogo = companyLogo; }
    public String getCompanyBanner() { return companyBanner; }
    public void setCompanyBanner(String companyBanner) { this.companyBanner = companyBanner; }
    public String getMissionStatement() { return missionStatement; }
    public void setMissionStatement(String missionStatement) { this.missionStatement = missionStatement; }
    public String getBenefits() { return benefits; }
    public void setBenefits(String benefits) { this.benefits = benefits; }
    public String getCompanyLinkedin() { return companyLinkedin; }
    public void setCompanyLinkedin(String companyLinkedin) { this.companyLinkedin = companyLinkedin; }
    public String getCompanyTwitter() { return companyTwitter; }
    public void setCompanyTwitter(String companyTwitter) { this.companyTwitter = companyTwitter; }
    public Integer getFoundedYear() { return foundedYear; }
    public void setFoundedYear(Integer foundedYear) { this.foundedYear = foundedYear; }
    public String getHeadquarters() { return headquarters; }
    public void setHeadquarters(String headquarters) { this.headquarters = headquarters; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getSubscriptionTier() { return subscriptionTier; }
    public void setSubscriptionTier(String subscriptionTier) { this.subscriptionTier = subscriptionTier; }
    public String getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(String subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public void setStripeSubscriptionId(String stripeSubscriptionId) { this.stripeSubscriptionId = stripeSubscriptionId; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getVerificationDocumentPath() { return verificationDocumentPath; }
    public void setVerificationDocumentPath(String verificationDocumentPath) { this.verificationDocumentPath = verificationDocumentPath; }
    public String getVerificationRejectionReason() { return verificationRejectionReason; }
    public void setVerificationRejectionReason(String verificationRejectionReason) { this.verificationRejectionReason = verificationRejectionReason; }
}