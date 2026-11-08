package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.jobera.dto.JobPostingDTO;
import com.jobera.entity.JobPosting;
import com.jobera.repository.ApplicationRepository;
import com.jobera.service.JobPostingService;
import com.jobera.service.JobRecommendationService;

/** Only GET /api/jobs/search: the DTO mapping, the internship filter, and the blank-keyword
 *  guard - the rest of this controller has no prior dedicated test file to extend. */
@ExtendWith(MockitoExtension.class)
class JobControllerSearchTest {

    @Mock JobPostingService jobPostingService;
    @Mock JobRecommendationService recommendationService;
    @Mock ApplicationRepository applicationRepository;

    JobController controller;

    @BeforeEach
    void setUp() {
        controller = new JobController(jobPostingService, recommendationService, applicationRepository);
    }

    private JobPosting job(long id, String title, String jobType) {
        JobPosting job = new JobPosting(title, "Acme", "desc");
        job.setId(id);
        job.setJobType(jobType);
        return job;
    }

    @Test
    void returnsRankedResultsAsDtosAndDropsInternships() {
        when(jobPostingService.searchJobs("java")).thenReturn(List.of(
            job(1L, "Java Developer", "Full-time"),
            job(2L, "Java Intern", "Internship")
        ));

        ResponseEntity<List<JobPostingDTO>> response = controller.searchJobs("java");

        assertEquals(1, response.getBody().size());
        assertEquals("Java Developer", response.getBody().get(0).getTitle());
    }

    @Test
    void blankKeywordReturnsNoResultsWithoutCallingTheService() {
        ResponseEntity<List<JobPostingDTO>> response = controller.searchJobs("   ");

        assertTrue(response.getBody().isEmpty());
        org.mockito.Mockito.verifyNoInteractions(jobPostingService);
    }
}
