package com.jobera.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.jobera.dto.JobPostingDTO;
import com.jobera.entity.JobPosting;
import com.jobera.entity.Recruiter;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.RecruiterRepository;

/**
 * "Company" here means a recruiter's own organization, not a separate entity: Recruiter already
 * carries companyName/description/website/size/industry, and a job posting's free-text company
 * field is independently editable per posting (recruiters can type anything), so it's not a
 * reliable key to group by. Building a real shared-Company model that multiple recruiter
 * accounts can belong to - with all the de-duplication and account-linking that implies - is a
 * bigger, separate decision than this covers; this delivers the concrete value (browse a
 * company's open jobs, see its profile) without guessing at which recruiter accounts represent
 * the same real-world company.
 */
@Service
public class CompanyService {

    public static class CompanyNotFoundException extends RuntimeException {
        public CompanyNotFoundException(Long recruiterId) {
            super("Company not found: " + recruiterId);
        }
    }

    private final RecruiterRepository recruiterRepository;
    private final JobPostingRepository jobPostingRepository;
    private final CompanyReviewService companyReviewService;

    public CompanyService(RecruiterRepository recruiterRepository,
                           JobPostingRepository jobPostingRepository,
                           CompanyReviewService companyReviewService) {
        this.recruiterRepository = recruiterRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.companyReviewService = companyReviewService;
    }

    private List<JobPosting> openJobsFor(Long recruiterId) {
        return jobPostingRepository.findByRecruiterIdAndIsActiveTrueAndModerationStatusOrderByPostedDateDesc(
            recruiterId, JobPosting.MODERATION_APPROVED);
    }

    /** Only companies with at least one publicly-visible job - nothing to browse into otherwise. */
    public List<Map<String, Object>> listCompanies() {
        return recruiterRepository.findAll().stream()
            .map(recruiter -> {
                int openJobCount = openJobsFor(recruiter.getId()).size();
                return openJobCount == 0 ? null : companySummary(recruiter, openJobCount);
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private Map<String, Object> companySummary(Recruiter recruiter, int openJobCount) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("recruiterId", recruiter.getId());
        summary.put("companyName", recruiter.getCompanyName());
        summary.put("industry", recruiter.getIndustry());
        summary.put("companySize", recruiter.getCompanySize());
        summary.put("openJobCount", openJobCount);
        return summary;
    }

    /** Shown even with zero open jobs right now (e.g. a bookmarked link) - just an empty list. */
    public Map<String, Object> getCompanyProfile(Long recruiterId) {
        Recruiter recruiter = recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new CompanyNotFoundException(recruiterId));

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("recruiterId", recruiter.getId());
        profile.put("companyName", recruiter.getCompanyName());
        profile.put("companyDescription", recruiter.getCompanyDescription());
        profile.put("companyWebsite", recruiter.getCompanyWebsite());
        profile.put("companySize", recruiter.getCompanySize());
        profile.put("industry", recruiter.getIndustry());
        // Contact email/phone are deliberately left out of the public profile - a candidate
        // reaches the company by applying or messaging through the platform, not a cold contact.
        profile.put("jobs", openJobsFor(recruiterId).stream().map(JobPostingDTO::new).toList());

        Map<String, Object> reviewSummary = companyReviewService.getPublicReviews(recruiterId);
        profile.put("averageRating", reviewSummary.get("averageRating"));
        profile.put("totalReviews", reviewSummary.get("totalReviews"));
        return profile;
    }
}
