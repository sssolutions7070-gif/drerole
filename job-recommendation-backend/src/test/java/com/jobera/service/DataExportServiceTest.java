package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.InternshipRatingRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.JobRatingRepository;
import com.jobera.repository.MessageRepository;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.SavedJobRepository;
import com.jobera.repository.UserCertificationRepository;
import com.jobera.repository.UserEducationRepository;
import com.jobera.repository.UserExperienceRepository;
import com.jobera.repository.UserRepository;
import com.jobera.repository.UserSkillRepository;

@ExtendWith(MockitoExtension.class)
class DataExportServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserSkillRepository userSkillRepository;
    @Mock UserEducationRepository userEducationRepository;
    @Mock UserExperienceRepository userExperienceRepository;
    @Mock UserCertificationRepository userCertificationRepository;
    @Mock ResumeRepository resumeRepository;
    @Mock ApplicationRepository applicationRepository;
    @Mock SavedJobRepository savedJobRepository;
    @Mock JobRatingRepository jobRatingRepository;
    @Mock InternshipRatingRepository internshipRatingRepository;
    @Mock MessageRepository messageRepository;
    @Mock JobPostingRepository jobPostingRepository;

    DataExportService service;

    @BeforeEach
    void setUp() {
        service = new DataExportService(userRepository, userSkillRepository, userEducationRepository,
            userExperienceRepository, userCertificationRepository, resumeRepository, applicationRepository,
            savedJobRepository, jobRatingRepository, internshipRatingRepository, messageRepository,
            jobPostingRepository);
    }

    @Test
    void exportsTheProfileAndEveryDataCategory() {
        User user = new User("candidate@example.com", "hash", "Candidate");
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSkillRepository.findByUserId(1L)).thenReturn(List.of());
        when(userEducationRepository.findByUserId(1L)).thenReturn(List.of());
        when(userExperienceRepository.findByUserId(1L)).thenReturn(List.of());
        when(userCertificationRepository.findByUserId(1L)).thenReturn(List.of());
        when(resumeRepository.findByUserId(1L)).thenReturn(List.of());
        when(applicationRepository.findByUserId(1L)).thenReturn(List.of());
        when(savedJobRepository.findByUserIdOrderBySavedAtDesc(1L)).thenReturn(List.of());
        when(jobRatingRepository.findByUserId(1L)).thenReturn(List.of());
        when(internshipRatingRepository.findByUserId(1L)).thenReturn(List.of());
        when(messageRepository.findBySenderIdOrRecipientIdOrderBySentAtAsc(1L, 1L)).thenReturn(List.of());

        Map<String, Object> export = service.exportMyData(1L);

        assertEquals(Set.of("profile", "skills", "education", "experience", "certifications",
            "resumes", "applications", "savedJobs", "jobRatingsGiven", "internshipRatingsGiven", "messages"),
            export.keySet());

        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) export.get("profile");
        assertEquals("candidate@example.com", profile.get("email"));
    }

    @Test
    void includesJobTitleAndCompanyOnEachApplication() {
        User user = new User("candidate@example.com", "hash", "Candidate");
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        stubEmptyExcept();

        JobPosting job = new JobPosting();
        job.setTitle("Backend Engineer");
        job.setCompany("Acme");
        Application application = new Application();
        application.setId(10L);
        application.setJobPosting(job);
        application.setStatus("SHORTLISTED");
        when(applicationRepository.findByUserId(1L)).thenReturn(List.of(application));

        Map<String, Object> export = service.exportMyData(1L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> applications = (List<Map<String, Object>>) export.get("applications");
        assertEquals(1, applications.size());
        assertEquals("Backend Engineer", applications.get(0).get("jobTitle"));
        assertEquals("Acme", applications.get(0).get("company"));
    }

    @Test
    void throwsForAnUnknownUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(DataExportService.UserNotFoundException.class, () -> service.exportMyData(404L));
    }

    private void stubEmptyExcept() {
        when(userSkillRepository.findByUserId(1L)).thenReturn(List.of());
        when(userEducationRepository.findByUserId(1L)).thenReturn(List.of());
        when(userExperienceRepository.findByUserId(1L)).thenReturn(List.of());
        when(userCertificationRepository.findByUserId(1L)).thenReturn(List.of());
        when(resumeRepository.findByUserId(1L)).thenReturn(List.of());
        when(savedJobRepository.findByUserIdOrderBySavedAtDesc(1L)).thenReturn(List.of());
        when(jobRatingRepository.findByUserId(1L)).thenReturn(List.of());
        when(internshipRatingRepository.findByUserId(1L)).thenReturn(List.of());
        when(messageRepository.findBySenderIdOrRecipientIdOrderBySentAtAsc(1L, 1L)).thenReturn(List.of());
    }
}
