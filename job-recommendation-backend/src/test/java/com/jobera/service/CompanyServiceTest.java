package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.JobPosting;
import com.jobera.entity.Recruiter;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.RecruiterRepository;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock RecruiterRepository recruiterRepository;
    @Mock JobPostingRepository jobPostingRepository;
    @Mock CompanyReviewService companyReviewService;

    CompanyService service;

    @BeforeEach
    void setUp() {
        service = new CompanyService(recruiterRepository, jobPostingRepository, companyReviewService);
        lenient().when(companyReviewService.getPublicReviews(anyLong()))
            .thenReturn(Map.of("averageRating", 0.0, "totalReviews", 0));
    }

    private Recruiter recruiter(long id, String name) {
        Recruiter recruiter = new Recruiter();
        recruiter.setId(id);
        recruiter.setCompanyName(name);
        recruiter.setIndustry("Software");
        recruiter.setCompanySize("50-200");
        return recruiter;
    }

    @Test
    void listCompaniesOnlyIncludesThoseWithAtLeastOneOpenJob() {
        Recruiter withJobs = recruiter(1L, "Acme");
        Recruiter withoutJobs = recruiter(2L, "Empty Co");
        when(recruiterRepository.findAll()).thenReturn(List.of(withJobs, withoutJobs));
        when(jobPostingRepository.findByRecruiterIdAndIsActiveTrueAndModerationStatusOrderByPostedDateDesc(1L, "APPROVED"))
            .thenReturn(List.of(new JobPosting()));
        when(jobPostingRepository.findByRecruiterIdAndIsActiveTrueAndModerationStatusOrderByPostedDateDesc(2L, "APPROVED"))
            .thenReturn(List.of());

        List<Map<String, Object>> companies = service.listCompanies();

        assertEquals(1, companies.size());
        assertEquals("Acme", companies.get(0).get("companyName"));
        assertEquals(1, companies.get(0).get("openJobCount"));
        assertEquals(false, companies.get(0).get("verified"));
    }

    @Test
    void listCompaniesMarksAVerifiedRecruiterAsVerified() {
        Recruiter verified = recruiter(1L, "Acme");
        verified.setVerificationStatus(Recruiter.VERIFICATION_VERIFIED);
        when(recruiterRepository.findAll()).thenReturn(List.of(verified));
        when(jobPostingRepository.findByRecruiterIdAndIsActiveTrueAndModerationStatusOrderByPostedDateDesc(1L, "APPROVED"))
            .thenReturn(List.of(new JobPosting()));

        List<Map<String, Object>> companies = service.listCompanies();

        assertEquals(true, companies.get(0).get("verified"));
    }

    @Test
    void companyProfileIncludesItsOpenJobsAndProfileFields() {
        Recruiter recruiter = recruiter(1L, "Acme");
        when(recruiterRepository.findById(1L)).thenReturn(Optional.of(recruiter));
        JobPosting job = new JobPosting();
        job.setTitle("Backend Engineer");
        when(jobPostingRepository.findByRecruiterIdAndIsActiveTrueAndModerationStatusOrderByPostedDateDesc(1L, "APPROVED"))
            .thenReturn(List.of(job));

        Map<String, Object> profile = service.getCompanyProfile(1L);

        assertEquals("Acme", profile.get("companyName"));
        assertEquals(false, profile.get("verified"));
        assertEquals("Software", profile.get("industry"));
        assertTrue(profile.containsKey("jobs"));
        @SuppressWarnings("unchecked")
        List<?> jobs = (List<?>) profile.get("jobs");
        assertEquals(1, jobs.size());
    }

    @Test
    void companyProfileStillWorksWithZeroOpenJobsRightNow() {
        Recruiter recruiter = recruiter(1L, "Acme");
        when(recruiterRepository.findById(1L)).thenReturn(Optional.of(recruiter));
        when(jobPostingRepository.findByRecruiterIdAndIsActiveTrueAndModerationStatusOrderByPostedDateDesc(1L, "APPROVED"))
            .thenReturn(List.of());

        Map<String, Object> profile = service.getCompanyProfile(1L);

        @SuppressWarnings("unchecked")
        List<?> jobs = (List<?>) profile.get("jobs");
        assertTrue(jobs.isEmpty());
    }

    @Test
    void companyProfileDoesNotExposeContactDetails() {
        Recruiter recruiter = recruiter(1L, "Acme");
        recruiter.setId(1L);
        when(recruiterRepository.findById(1L)).thenReturn(Optional.of(recruiter));
        when(jobPostingRepository.findByRecruiterIdAndIsActiveTrueAndModerationStatusOrderByPostedDateDesc(1L, "APPROVED"))
            .thenReturn(List.of());

        Map<String, Object> profile = service.getCompanyProfile(1L);

        assertTrue(!profile.containsKey("contactEmail") && !profile.containsKey("contactPhone"));
    }

    @Test
    void companyProfileIncludesTheReviewSummary() {
        Recruiter recruiter = recruiter(1L, "Acme");
        when(recruiterRepository.findById(1L)).thenReturn(Optional.of(recruiter));
        when(jobPostingRepository.findByRecruiterIdAndIsActiveTrueAndModerationStatusOrderByPostedDateDesc(1L, "APPROVED"))
            .thenReturn(List.of());
        when(companyReviewService.getPublicReviews(1L))
            .thenReturn(Map.of("averageRating", 4.5, "totalReviews", 2, "reviews", List.of()));

        Map<String, Object> profile = service.getCompanyProfile(1L);

        assertEquals(4.5, profile.get("averageRating"));
        assertEquals(2, profile.get("totalReviews"));
    }

    @Test
    void throwsForAnUnknownRecruiter() {
        when(recruiterRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(CompanyService.CompanyNotFoundException.class, () -> service.getCompanyProfile(404L));
    }
}
