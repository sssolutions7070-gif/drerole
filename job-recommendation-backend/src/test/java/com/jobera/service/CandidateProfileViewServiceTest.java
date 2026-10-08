package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.ProfileView;
import com.jobera.entity.Recruiter;
import com.jobera.repository.ProfileViewRepository;
import com.jobera.repository.RecruiterRepository;

@ExtendWith(MockitoExtension.class)
class CandidateProfileViewServiceTest {

    @Mock ProfileViewRepository profileViewRepository;
    @Mock RecruiterRepository recruiterRepository;

    CandidateProfileViewService service;

    @BeforeEach
    void setUp() {
        service = new CandidateProfileViewService(profileViewRepository, recruiterRepository);
    }

    @Test
    void firstViewCreatesANewRow() {
        when(profileViewRepository.findByRecruiterIdAndViewedUserId(10L, 50L)).thenReturn(Optional.empty());
        when(profileViewRepository.save(any(ProfileView.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordView(10L, 50L);

        ArgumentCaptor<ProfileView> captor = ArgumentCaptor.forClass(ProfileView.class);
        verify(profileViewRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getRecruiterId());
        assertEquals(50L, captor.getValue().getViewedUserId());
    }

    @Test
    void reviewingTheSameCandidateUpdatesTheExistingRowInstedOfDuplicating() {
        ProfileView existing = new ProfileView(10L, 50L);
        existing.setId(1L);
        existing.setViewedAt(LocalDateTime.now().minusDays(3));
        when(profileViewRepository.findByRecruiterIdAndViewedUserId(10L, 50L)).thenReturn(Optional.of(existing));
        when(profileViewRepository.save(any(ProfileView.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordView(10L, 50L);

        verify(profileViewRepository, never()).save(org.mockito.ArgumentMatchers.argThat(v -> !v.getId().equals(1L)));
        ArgumentCaptor<ProfileView> captor = ArgumentCaptor.forClass(ProfileView.class);
        verify(profileViewRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getId());
    }

    @Test
    void viewsForCandidateResolveTheCompanyNameAtReadTime() {
        ProfileView view = new ProfileView(10L, 50L);
        view.setViewedAt(LocalDateTime.now());
        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        recruiter.setCompanyName("Acme");
        when(profileViewRepository.findByViewedUserIdOrderByViewedAtDesc(50L)).thenReturn(List.of(view));
        when(recruiterRepository.findById(10L)).thenReturn(Optional.of(recruiter));

        List<Map<String, Object>> views = service.getViewsForCandidate(50L);

        assertEquals(1, views.size());
        assertEquals("Acme", views.get(0).get("companyName"));
    }

    @Test
    void getViewCountDelegatesToTheRepository() {
        when(profileViewRepository.countByViewedUserId(50L)).thenReturn(4L);

        assertEquals(4L, service.getViewCount(50L));
    }
}
