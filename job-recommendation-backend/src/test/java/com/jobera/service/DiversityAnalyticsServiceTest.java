package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobPostingRepository;

@ExtendWith(MockitoExtension.class)
class DiversityAnalyticsServiceTest {

    @Mock JobPostingRepository jobPostingRepository;
    @Mock ApplicationRepository applicationRepository;

    DiversityAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new DiversityAnalyticsService(jobPostingRepository, applicationRepository);
    }

    private User candidate(long id, String gender) {
        User u = new User();
        u.setId(id);
        u.setGenderIdentity(gender);
        return u;
    }

    private Application applicationFor(User user) {
        Application app = new Application();
        app.setUser(user);
        return app;
    }

    private JobPosting job(long id) {
        JobPosting job = new JobPosting();
        job.setId(id);
        return job;
    }

    @SuppressWarnings("unchecked")
    @Test
    void withholdsACategoryBelowTheMinimumResponseThreshold() {
        JobPosting job1 = job(1L);
        when(jobPostingRepository.findByRecruiterId(10L)).thenReturn(List.of(job1));
        when(applicationRepository.findByJobPostingId(1L)).thenReturn(List.of(
            applicationFor(candidate(1, "Woman")),
            applicationFor(candidate(2, "Man")),
            applicationFor(candidate(3, null))
        ));

        Map<String, Object> result = service.getAggregateForRecruiter(10L);

        Map<String, Object> genderBreakdown = (Map<String, Object>) result.get("genderIdentity");
        assertEquals(false, genderBreakdown.get("reportable"));
        assertEquals(2L, genderBreakdown.get("responseCount"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void reportsPercentagesOnceTheMinimumThresholdIsMet() {
        JobPosting job1 = job(1L);
        when(jobPostingRepository.findByRecruiterId(10L)).thenReturn(List.of(job1));
        when(applicationRepository.findByJobPostingId(1L)).thenReturn(List.of(
            applicationFor(candidate(1, "Woman")),
            applicationFor(candidate(2, "Woman")),
            applicationFor(candidate(3, "Woman")),
            applicationFor(candidate(4, "Man")),
            applicationFor(candidate(5, "Man"))
        ));

        Map<String, Object> result = service.getAggregateForRecruiter(10L);

        Map<String, Object> genderBreakdown = (Map<String, Object>) result.get("genderIdentity");
        assertEquals(true, genderBreakdown.get("reportable"));
        Map<String, Double> percentages = (Map<String, Double>) genderBreakdown.get("percentages");
        assertEquals(60.0, percentages.get("Woman"));
        assertEquals(40.0, percentages.get("Man"));
    }

    @Test
    void countsTotalApplicantsAcrossAllJobs() {
        JobPosting job1 = job(1L);
        JobPosting job2 = job(2L);
        when(jobPostingRepository.findByRecruiterId(10L)).thenReturn(List.of(job1, job2));
        when(applicationRepository.findByJobPostingId(1L)).thenReturn(List.of(applicationFor(candidate(1, "Woman"))));
        when(applicationRepository.findByJobPostingId(2L)).thenReturn(List.of(applicationFor(candidate(2, "Man"))));

        Map<String, Object> result = service.getAggregateForRecruiter(10L);

        assertEquals(2, result.get("totalApplicants"));
    }

    @Test
    void ignoresApplicationsWithNoAssociatedUser() {
        JobPosting job1 = job(1L);
        when(jobPostingRepository.findByRecruiterId(10L)).thenReturn(List.of(job1));
        Application orphan = new Application();
        when(applicationRepository.findByJobPostingId(1L)).thenReturn(List.of(orphan));

        Map<String, Object> result = service.getAggregateForRecruiter(10L);

        assertEquals(0, result.get("totalApplicants"));
    }
}
