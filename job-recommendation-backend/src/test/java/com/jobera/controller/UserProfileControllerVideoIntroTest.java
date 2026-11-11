package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import com.jobera.entity.User;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.UserCertificationRepository;
import com.jobera.repository.UserEducationRepository;
import com.jobera.repository.UserExperienceRepository;
import com.jobera.repository.UserRepository;
import com.jobera.repository.UserSkillRepository;
import com.jobera.service.ProfileStrengthService;

/** Only the new video-intro upload/delete endpoints - same scoping rationale as the other
 *  *Test files that extend an existing controller with no prior dedicated test coverage. */
@ExtendWith(MockitoExtension.class)
class UserProfileControllerVideoIntroTest {

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

    @AfterEach
    void cleanUp() throws IOException {
        Path dir = Paths.get("uploads/video-intros");
        if (Files.exists(dir)) {
            try (var files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().startsWith("7_")).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    @Test
    void uploadsAVideoAndStoresItsUrlOnTheUser() throws IOException {
        User user = new User();
        user.setId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile("file", "intro.mp4", "video/mp4", "fake-video-bytes".getBytes());

        ResponseEntity<Map<String, String>> response = controller.uploadVideoIntro(7L, file);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().get("videoIntroUrl").startsWith("/uploads/video-intros/7_"));
        verify(userRepository).save(user);
        assertTrue(user.getVideoIntroUrl().endsWith(".mp4"));
    }

    @Test
    void rejectsAnUnsupportedFileFormat() throws IOException {
        User user = new User();
        user.setId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        MockMultipartFile file = new MockMultipartFile("file", "intro.exe", "application/octet-stream", "bytes".getBytes());

        ResponseEntity<Map<String, String>> response = controller.uploadVideoIntro(7L, file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").contains("Unsupported"));
    }

    @Test
    void rejectsAFileOverTheSizeLimit() throws IOException {
        User user = new User();
        user.setId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        MockMultipartFile oversized = new MockMultipartFile("file", "intro.mp4", "video/mp4", new byte[]{1}) {
            @Override
            public long getSize() {
                return 51L * 1024 * 1024;
            }
        };

        ResponseEntity<Map<String, String>> response = controller.uploadVideoIntro(7L, oversized);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().get("error").contains("50MB"));
    }

    @Test
    void anUnknownUserReturnsNotFoundOnUpload() throws IOException {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());
        MockMultipartFile file = new MockMultipartFile("file", "intro.mp4", "video/mp4", "bytes".getBytes());

        ResponseEntity<Map<String, String>> response = controller.uploadVideoIntro(404L, file);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void deletesTheVideoIntro() {
        User user = new User();
        user.setId(7L);
        user.setVideoIntroUrl("/uploads/video-intros/7_123_abc.mp4");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        ResponseEntity<Map<String, String>> response = controller.deleteVideoIntro(7L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(null, user.getVideoIntroUrl());
        verify(userRepository).save(user);
    }

    @Test
    void anUnknownUserReturnsNotFoundOnDelete() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, String>> response = controller.deleteVideoIntro(404L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
