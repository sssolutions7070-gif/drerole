package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.repository.ResumeRepository;
import com.jobera.repository.UserCertificationRepository;
import com.jobera.repository.UserEducationRepository;
import com.jobera.repository.UserExperienceRepository;
import com.jobera.repository.UserRepository;
import com.jobera.repository.UserSkillRepository;
import com.jobera.service.ProfileStrengthService;

/** Only the new profile-strength endpoint - same scoping rationale as the other *Test files that
 *  extend an existing controller with no prior dedicated test coverage. */
@ExtendWith(MockitoExtension.class)
class UserProfileControllerProfileStrengthTest {

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
    void returnsTheScoreFromTheService() {
        when(profileStrengthService.calculate(5L)).thenReturn(Map.of("score", 75, "checks", java.util.List.of()));

        ResponseEntity<Map<String, Object>> response = controller.getProfileStrength(5L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(75, response.getBody().get("score"));
    }

    @Test
    void anUnknownUserReturnsNotFound() {
        when(profileStrengthService.calculate(404L)).thenThrow(new ProfileStrengthService.UserNotFoundException(404L));

        ResponseEntity<Map<String, Object>> response = controller.getProfileStrength(404L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
