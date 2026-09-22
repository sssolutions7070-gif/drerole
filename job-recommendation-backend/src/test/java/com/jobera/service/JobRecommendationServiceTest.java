package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.JobPosting;
import com.jobera.entity.Resume;

@ExtendWith(MockitoExtension.class)
class JobRecommendationServiceTest {

    @Mock JobPostingService jobPostingService;
    @Mock ResumeService resumeService;

    JobRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new JobRecommendationService(jobPostingService, resumeService);
    }

    private JobPosting job(long id, String title, String requiredSkillsJson, String jobType) {
        JobPosting job = new JobPosting();
        job.setId(id);
        job.setTitle(title);
        job.setRequiredSkills(requiredSkillsJson);
        job.setJobType(jobType);
        job.setIsActive(true);
        return job;
    }

    private Resume resumeWithSkills(String skillsJson) {
        Resume resume = new Resume();
        resume.setSkills(skillsJson);
        return resume;
    }

    @Test
    void usersWithoutAResumeGetNoRecommendations() {
        when(resumeService.getLatestResumeByUserId(1L)).thenReturn(Optional.empty());

        assertTrue(service.recommendJobsForUser(1L).isEmpty());
    }

    @Test
    void onlyJobsAtOrAboveTheMatchThresholdAreRecommendedBestFirst() {
        when(resumeService.getLatestResumeByUserId(1L)).thenReturn(
            Optional.of(resumeWithSkills("[\"Java\", \"Spring Boot\", \"React\"]")));
        JobPosting perfect = job(1, "Backend", "[\"Java\", \"Spring Boot\"]", "Full-time");
        JobPosting half = job(2, "Fullstack", "[\"Java\", \"Docker\"]", "Full-time");
        JobPosting weak = job(3, "Platform", "[\"Java\", \"Docker\", \"AWS\", \"Kubernetes\"]", "Full-time");
        JobPosting unrelated = job(4, "Data", "[\"Python\", \"Django\"]", "Full-time");
        when(jobPostingService.getActiveJobPostings()).thenReturn(List.of(half, unrelated, weak, perfect));

        List<JobPosting> recommended = service.recommendJobsForUser(1L);

        assertEquals(List.of(perfect, half), recommended);
    }

    @Test
    void internshipsAreNotRecommendedAsJobs() {
        when(resumeService.getLatestResumeByUserId(1L)).thenReturn(
            Optional.of(resumeWithSkills("[\"Java\"]")));
        when(jobPostingService.getActiveJobPostings()).thenReturn(
            List.of(job(1, "Java Intern", "[\"Java\"]", "Internship")));

        assertTrue(service.recommendJobsForUser(1L).isEmpty());
    }

    @Test
    void skillVariationsAreTreatedAsTheSameSkill() {
        when(resumeService.getLatestResumeByUserId(1L)).thenReturn(
            Optional.of(resumeWithSkills("[\"JS\", \"ReactJS\"]")));
        JobPosting frontend = job(1, "Frontend", "[\"JavaScript\", \"React\"]", "Full-time");
        when(jobPostingService.getActiveJobPostings()).thenReturn(List.of(frontend));

        assertEquals(List.of(frontend), service.recommendJobsForUser(1L));
    }
}
