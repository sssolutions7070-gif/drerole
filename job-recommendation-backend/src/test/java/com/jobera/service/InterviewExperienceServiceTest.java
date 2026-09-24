package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.InterviewExperience;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.InterviewExperienceRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class InterviewExperienceServiceTest {

    @Mock InterviewExperienceRepository interviewExperienceRepository;
    @Mock RecruiterRepository recruiterRepository;
    @Mock UserRepository userRepository;

    InterviewExperienceService service;
    User user;
    Recruiter recruiter;

    @BeforeEach
    void setUp() {
        service = new InterviewExperienceService(interviewExperienceRepository, recruiterRepository, userRepository);
        user = new User("candidate@example.com", "hash", "Candidate");
        user.setId(1L);
        recruiter = new Recruiter();
        recruiter.setId(10L);
        recruiter.setCompanyName("Acme");
    }

    @Test
    void submittingANewExperienceStartsPending() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(recruiterRepository.findById(10L)).thenReturn(Optional.of(recruiter));
        when(interviewExperienceRepository.findByUserIdAndRecruiterId(1L, 10L)).thenReturn(Optional.empty());
        when(interviewExperienceRepository.save(any(InterviewExperience.class))).thenAnswer(inv -> inv.getArgument(0));

        InterviewExperience experience = service.submitExperience(1L, 10L, 4, "OFFER", "Three rounds, all technical.");

        assertEquals(InterviewExperience.MODERATION_PENDING, experience.getModerationStatus());
        assertEquals(4, experience.getDifficultyRating());
        assertEquals("OFFER", experience.getOutcome());
    }

    @Test
    void editingAnExistingExperienceResetsItToPendingRegardlessOfPriorStatus() {
        InterviewExperience existing = new InterviewExperience(user, recruiter, 3, "REJECTED", "It was fine");
        existing.setModerationStatus(InterviewExperience.MODERATION_APPROVED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(recruiterRepository.findById(10L)).thenReturn(Optional.of(recruiter));
        when(interviewExperienceRepository.findByUserIdAndRecruiterId(1L, 10L)).thenReturn(Optional.of(existing));
        when(interviewExperienceRepository.save(any(InterviewExperience.class))).thenAnswer(inv -> inv.getArgument(0));

        InterviewExperience updated = service.submitExperience(1L, 10L, 5, "OFFER", "Actually I got the offer");

        assertEquals(InterviewExperience.MODERATION_PENDING, updated.getModerationStatus());
        assertEquals("OFFER", updated.getOutcome());
    }

    @Test
    void rejectsDifficultyRatingsOutsideOneToFive() {
        assertThrows(InterviewExperienceService.ExperienceException.class, () -> service.submitExperience(1L, 10L, 0, "OFFER", "x"));
        assertThrows(InterviewExperienceService.ExperienceException.class, () -> service.submitExperience(1L, 10L, 6, "OFFER", "x"));
        assertThrows(InterviewExperienceService.ExperienceException.class, () -> service.submitExperience(1L, 10L, null, "OFFER", "x"));
    }

    @Test
    void rejectsAnUnknownOutcome() {
        assertThrows(InterviewExperienceService.ExperienceException.class, () -> service.submitExperience(1L, 10L, 3, "MAYBE", "x"));
        assertThrows(InterviewExperienceService.ExperienceException.class, () -> service.submitExperience(1L, 10L, 3, null, "x"));
    }

    @Test
    void publicExperiencesOnlyIncludeApprovedOnesAndComputeTheAverageDifficulty() {
        InterviewExperience a = new InterviewExperience(user, recruiter, 4, "OFFER", "Hard rounds");
        InterviewExperience b = new InterviewExperience(user, recruiter, 2, "REJECTED", "Easy but slow");
        when(interviewExperienceRepository.findByRecruiterIdAndModerationStatusOrderByCreatedAtDesc(10L, "APPROVED"))
            .thenReturn(List.of(a, b));

        Map<String, Object> result = service.getPublicExperiences(10L);

        assertEquals(3.0, result.get("averageDifficulty"));
        assertEquals(2, result.get("totalExperiences"));
    }

    @Test
    void publicExperienceViewDoesNotExposeTheReporterIdentity() {
        InterviewExperience experience = new InterviewExperience(user, recruiter, 4, "OFFER", "Good process");
        when(interviewExperienceRepository.findByRecruiterIdAndModerationStatusOrderByCreatedAtDesc(10L, "APPROVED"))
            .thenReturn(List.of(experience));

        Map<String, Object> result = service.getPublicExperiences(10L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> experiences = (List<Map<String, Object>>) result.get("experiences");
        assertFalse(experiences.get(0).containsKey("userId"));
        assertFalse(experiences.get(0).containsKey("user"));
    }

    @Test
    void handlesACompanyWithNoExperiencesYet() {
        when(interviewExperienceRepository.findByRecruiterIdAndModerationStatusOrderByCreatedAtDesc(10L, "APPROVED"))
            .thenReturn(List.of());

        Map<String, Object> result = service.getPublicExperiences(10L);

        assertEquals(0.0, result.get("averageDifficulty"));
        assertEquals(0, result.get("totalExperiences"));
    }

    @Test
    void approvingAnExperienceClearsAnyPriorRejectionReason() {
        InterviewExperience experience = new InterviewExperience(user, recruiter, 3, "OFFER", "x");
        experience.setModerationStatus(InterviewExperience.MODERATION_REJECTED);
        experience.setRejectionReason("Too vague");
        when(interviewExperienceRepository.findById(5L)).thenReturn(Optional.of(experience));
        when(interviewExperienceRepository.save(any(InterviewExperience.class))).thenAnswer(inv -> inv.getArgument(0));

        InterviewExperience approved = service.approveExperience(5L);

        assertEquals(InterviewExperience.MODERATION_APPROVED, approved.getModerationStatus());
        assertNull(approved.getRejectionReason());
    }

    @Test
    void rejectingAnExperienceRecordsTheReason() {
        InterviewExperience experience = new InterviewExperience(user, recruiter, 1, "REJECTED", "spam spam spam");
        when(interviewExperienceRepository.findById(5L)).thenReturn(Optional.of(experience));
        when(interviewExperienceRepository.save(any(InterviewExperience.class))).thenAnswer(inv -> inv.getArgument(0));

        InterviewExperience rejected = service.rejectExperience(5L, "Looks like spam");

        assertEquals(InterviewExperience.MODERATION_REJECTED, rejected.getModerationStatus());
        assertEquals("Looks like spam", rejected.getRejectionReason());
    }
}
