package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

import com.jobera.entity.Resume;
import com.jobera.entity.SkillBadge;
import com.jobera.entity.User;
import com.jobera.entity.UserCertification;
import com.jobera.entity.UserEducation;
import com.jobera.entity.UserExperience;
import com.jobera.entity.UserSkill;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.SkillBadgeRepository;
import com.jobera.repository.UserCertificationRepository;
import com.jobera.repository.UserEducationRepository;
import com.jobera.repository.UserExperienceRepository;
import com.jobera.repository.UserRepository;
import com.jobera.repository.UserSkillRepository;

@ExtendWith(MockitoExtension.class)
class ProfileStrengthServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserSkillRepository userSkillRepository;
    @Mock UserEducationRepository userEducationRepository;
    @Mock UserExperienceRepository userExperienceRepository;
    @Mock UserCertificationRepository userCertificationRepository;
    @Mock SkillBadgeRepository skillBadgeRepository;
    @Mock ResumeRepository resumeRepository;

    ProfileStrengthService service;

    @BeforeEach
    void setUp() {
        // ResumeQualityService has no dependencies of its own - a real instance is simpler and
        // just as fast as mocking it, and exercises the real resume-quality-score integration.
        service = new ProfileStrengthService(userRepository, userSkillRepository, userEducationRepository,
            userExperienceRepository, userCertificationRepository, skillBadgeRepository, resumeRepository,
            new ResumeQualityService());
    }

    private User barebonesUser(long id) {
        User user = new User();
        user.setId(id);
        lenient().when(userRepository.findById(id)).thenReturn(Optional.of(user));
        lenient().when(userSkillRepository.findByUserId(id)).thenReturn(List.of());
        lenient().when(userEducationRepository.findByUserId(id)).thenReturn(List.of());
        lenient().when(userExperienceRepository.findByUserId(id)).thenReturn(List.of());
        lenient().when(userCertificationRepository.findByUserId(id)).thenReturn(List.of());
        lenient().when(skillBadgeRepository.findByUserIdOrderByEarnedAtDesc(id)).thenReturn(List.of());
        lenient().when(resumeRepository.findFirstByUserIdOrderByUploadDateDesc(id)).thenReturn(Optional.empty());
        return user;
    }

    @Test
    void aBarebonesProfileScoresLow() {
        barebonesUser(1L);

        Map<String, Object> result = service.calculate(1L);

        assertTrue((int) result.get("score") < 30, "Expected a low score for an empty profile, got " + result.get("score"));
    }

    @Test
    void aFullyFilledOutProfileScoresHigh() {
        User user = barebonesUser(2L);
        user.setHeadline("Backend Engineer");
        user.setBio("I build scalable backend systems and love mentoring junior engineers.");
        user.setLinkedin("https://linkedin.com/in/example");

        when(userSkillRepository.findByUserId(2L)).thenReturn(
            List.of(new UserSkill(), new UserSkill(), new UserSkill()));
        when(userEducationRepository.findByUserId(2L)).thenReturn(List.of(new UserEducation()));
        when(userExperienceRepository.findByUserId(2L)).thenReturn(List.of(new UserExperience()));
        when(userCertificationRepository.findByUserId(2L)).thenReturn(List.of(new UserCertification()));
        when(skillBadgeRepository.findByUserIdOrderByEarnedAtDesc(2L)).thenReturn(List.of(new SkillBadge()));

        Resume resume = new Resume();
        resume.setParsedText("Led a team of 5 engineers. Built and deployed a microservices platform that "
            + "improved throughput by 40%. Designed CI/CD pipelines, implemented automated testing, "
            + "and mentored two junior engineers. Optimized database queries reducing latency by 25%. "
            + "Contact: jane@example.com, 555-123-4567. " + "word ".repeat(150));
        resume.setSkills("[\"Java\", \"Spring\", \"AWS\", \"Kubernetes\"]");
        resume.setEducationLevel("Bachelor's Degree");
        when(resumeRepository.findFirstByUserIdOrderByUploadDateDesc(2L)).thenReturn(Optional.of(resume));

        Map<String, Object> result = service.calculate(2L);

        assertTrue((int) result.get("score") >= 80, "Expected a high score for a filled-out profile, got " + result.get("score"));
    }

    @Test
    void missingAResumeFailsThatCheckWithoutCrashing() {
        barebonesUser(3L);

        Map<String, Object> result = service.calculate(3L);

        assertEquals(null, result.get("resumeQualityScore"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) result.get("checks");
        Map<String, Object> resumeCheck = checks.stream()
            .filter(c -> "resume_quality".equals(c.get("id"))).findFirst().orElseThrow();
        assertFalse((boolean) resumeCheck.get("passed"));
    }

    @Test
    void anUnknownUserFailsCleanly() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ProfileStrengthService.UserNotFoundException.class, () -> service.calculate(404L));
    }
}
