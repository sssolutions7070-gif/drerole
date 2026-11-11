package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Resume;
import com.jobera.entity.User;
import com.jobera.entity.UserSkill;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.UserRepository;
import com.jobera.repository.UserSkillRepository;

@ExtendWith(MockitoExtension.class)
class CandidateDiscoveryServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserSkillRepository userSkillRepository;
    @Mock ResumeRepository resumeRepository;

    CandidateDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new CandidateDiscoveryService(userRepository, userSkillRepository, resumeRepository);
    }

    private User user(long id, String fullName, String location) {
        User u = new User();
        u.setId(id);
        u.setFullName(fullName);
        u.setLocation(location);
        u.setOpenToWork(true);
        return u;
    }

    private UserSkill skill(User owner, String name) {
        UserSkill s = new UserSkill();
        s.setUser(owner);
        s.setSkillName(name);
        return s;
    }

    @Test
    void returnsOnlyOpenToWorkCandidates() {
        User alice = user(1, "Alice", "Remote");
        alice.setVideoIntroUrl("/uploads/video-intros/1_abc.mp4");
        when(userRepository.findByOpenToWorkTrueAndIsActiveTrue()).thenReturn(List.of(alice));
        when(userSkillRepository.findByUserId(1L)).thenReturn(List.of(skill(alice, "Java")));
        when(resumeRepository.findByUserId(1L)).thenReturn(List.of(new Resume()));

        List<Map<String, Object>> results = service.discover(null, null);

        assertEquals(1, results.size());
        assertEquals("Alice", results.get(0).get("fullName"));
        assertEquals(true, results.get(0).get("hasResume"));
        assertEquals("/uploads/video-intros/1_abc.mp4", results.get(0).get("videoIntroUrl"));
    }

    @Test
    void filtersBySkillCaseInsensitively() {
        User alice = user(1, "Alice", "Remote");
        User bob = user(2, "Bob", "Remote");
        when(userRepository.findByOpenToWorkTrueAndIsActiveTrue()).thenReturn(List.of(alice, bob));
        when(userSkillRepository.findByUserId(1L)).thenReturn(List.of(skill(alice, "Java")));
        when(userSkillRepository.findByUserId(2L)).thenReturn(List.of(skill(bob, "Python")));
        when(resumeRepository.findByUserId(1L)).thenReturn(List.of());
        when(resumeRepository.findByUserId(2L)).thenReturn(List.of());

        List<Map<String, Object>> results = service.discover("java", null);

        assertEquals(1, results.size());
        assertEquals("Alice", results.get(0).get("fullName"));
    }

    @Test
    void filtersByLocation() {
        User alice = user(1, "Alice", "Pune");
        User bob = user(2, "Bob", "Remote");
        when(userRepository.findByOpenToWorkTrueAndIsActiveTrue()).thenReturn(List.of(alice, bob));
        when(userSkillRepository.findByUserId(1L)).thenReturn(List.of());
        when(userSkillRepository.findByUserId(2L)).thenReturn(List.of());
        when(resumeRepository.findByUserId(1L)).thenReturn(List.of());
        when(resumeRepository.findByUserId(2L)).thenReturn(List.of());

        List<Map<String, Object>> results = service.discover(null, "remote");

        assertEquals(1, results.size());
        assertEquals("Bob", results.get(0).get("fullName"));
    }

    @Test
    void returnsEmptyWhenNoOneIsOpenToWork() {
        when(userRepository.findByOpenToWorkTrueAndIsActiveTrue()).thenReturn(List.of());

        assertTrue(service.discover(null, null).isEmpty());
    }
}
