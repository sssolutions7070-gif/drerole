package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.User;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.UserCertificationRepository;
import com.jobera.repository.UserEducationRepository;
import com.jobera.repository.UserExperienceRepository;
import com.jobera.repository.UserRepository;
import com.jobera.repository.UserSkillRepository;
import com.jobera.service.ProfileStrengthService;

/** Only the new diversity-info endpoints - same scoping rationale as the other *Test files that
 *  extend this controller with no prior dedicated coverage of the new path. */
@ExtendWith(MockitoExtension.class)
class UserProfileControllerDiversityInfoTest {

    @Mock UserRepository userRepository;
    @Mock UserSkillRepository userSkillRepository;
    @Mock UserEducationRepository userEducationRepository;
    @Mock UserExperienceRepository userExperienceRepository;
    @Mock UserCertificationRepository userCertificationRepository;
    @Mock ResumeRepository resumeRepository;
    @Mock ProfileStrengthService profileStrengthService;

    UserProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new UserProfileController(userRepository, userSkillRepository, userEducationRepository,
            userExperienceRepository, userCertificationRepository, resumeRepository, profileStrengthService);
    }

    @Test
    void returnsTheCandidatesOwnAnswers() {
        User user = new User();
        user.setId(1L);
        user.setGenderIdentity("Woman");
        user.setEthnicity("Prefer not to say");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ResponseEntity<Map<String, Object>> response = controller.getDiversityInfo(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Woman", response.getBody().get("genderIdentity"));
        assertEquals("Prefer not to say", response.getBody().get("ethnicity"));
    }

    @Test
    void returnsNotFoundForAnUnknownUserOnGet() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.getDiversityInfo(404L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void updatesAllFourFields() {
        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ResponseEntity<Map<String, Object>> response = controller.updateDiversityInfo(1L, Map.of(
            "genderIdentity", "Non-binary",
            "ethnicity", "Prefer not to say",
            "veteranStatus", "Not a veteran",
            "disabilityStatus", "Prefer not to say"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Non-binary", user.getGenderIdentity());
        verify(userRepository).save(user);
    }

    @Test
    void returnsNotFoundForAnUnknownUserOnUpdate() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.updateDiversityInfo(404L, Map.of());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
