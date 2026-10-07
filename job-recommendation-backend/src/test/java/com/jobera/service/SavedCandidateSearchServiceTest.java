package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Recruiter;
import com.jobera.entity.SavedCandidateSearch;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.SavedCandidateSearchRepository;

@ExtendWith(MockitoExtension.class)
class SavedCandidateSearchServiceTest {

    @Mock
    private SavedCandidateSearchRepository searchRepository;

    @Mock
    private RecruiterRepository recruiterRepository;

    @Mock
    private CandidateDiscoveryService candidateDiscoveryService;

    @Mock
    private NotificationFeedService notificationFeedService;

    @Mock
    private RealtimeNotifier realtimeNotifier;

    private SavedCandidateSearchService service;

    @BeforeEach
    void setUp() {
        service = new SavedCandidateSearchService(
            searchRepository,
            recruiterRepository,
            candidateDiscoveryService,
            notificationFeedService,
            realtimeNotifier
        );
    }

    @Test
    void savesValidSearch() {
        when(recruiterRepository.existsById(10L)).thenReturn(true);
        when(searchRepository.save(any(SavedCandidateSearch.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedCandidateSearch result = service.saveSearch(10L, "React Devs", "React", "Pune", null, true);

        assertNotNull(result);
        assertEquals(10L, result.getRecruiterId());
        assertEquals("React Devs", result.getName());
        assertEquals("React", result.getSkillFilter());
        assertEquals("Pune", result.getLocationFilter());
        assertTrue(result.isAlertEnabled());
    }

    @Test
    void rejectsEmptySearchName() {
        assertThrows(IllegalArgumentException.class, () ->
            service.saveSearch(10L, "   ", "React", null, null, true)
        );
    }

    @Test
    void rejectsMissingRecruiter() {
        when(recruiterRepository.existsById(99L)).thenReturn(false);

        assertThrows(NoSuchElementException.class, () ->
            service.saveSearch(99L, "Java", "Java", null, null, true)
        );
    }

    @Test
    void listsSearchesForRecruiter() {
        SavedCandidateSearch s1 = new SavedCandidateSearch(10L, "S1", "Go", null, null, true);
        when(searchRepository.findByRecruiterIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(s1));

        List<SavedCandidateSearch> list = service.getSearchesByRecruiter(10L);
        assertEquals(1, list.size());
        assertEquals("S1", list.get(0).getName());
    }

    @Test
    void deletesSearch() {
        SavedCandidateSearch s1 = new SavedCandidateSearch(10L, "S1", "Go", null, null, true);
        when(searchRepository.findByIdAndRecruiterId(1L, 10L)).thenReturn(Optional.of(s1));

        service.deleteSearch(1L, 10L);
        verify(searchRepository).delete(s1);
    }

    @Test
    void togglesAlert() {
        SavedCandidateSearch s1 = new SavedCandidateSearch(10L, "S1", "Go", null, null, true);
        when(searchRepository.findByIdAndRecruiterId(1L, 10L)).thenReturn(Optional.of(s1));
        when(searchRepository.save(s1)).thenReturn(s1);

        SavedCandidateSearch updated = service.toggleAlert(1L, 10L, false);
        assertFalse(updated.isAlertEnabled());
    }

    @Test
    void checksAndNotifiesMatches() {
        SavedCandidateSearch search = new SavedCandidateSearch(10L, "React Alert", "React", "Pune", null, true);
        search.setId(50L);
        when(searchRepository.findByIdAndRecruiterId(50L, 10L)).thenReturn(Optional.of(search));
        when(candidateDiscoveryService.discover("React", "Pune"))
            .thenReturn(List.of(Map.of("fullName", "Alice Candidate")));

        Recruiter recruiter = new Recruiter();
        recruiter.setId(10L);
        com.jobera.entity.User user = new com.jobera.entity.User();
        user.setId(200L);
        recruiter.setUser(user);
        when(recruiterRepository.findById(10L)).thenReturn(Optional.of(recruiter));

        int matches = service.checkAndNotifyMatches(50L, 10L);

        assertEquals(1, matches);
        verify(notificationFeedService).record(
            eq(200L),
            eq("SAVED_SEARCH_ALERT"),
            eq("Candidate Alert: React Alert"),
            eq("1 new candidate(s) match your saved search (1 in total)."),
            eq("/recruiter/dashboard")
        );
        verify(realtimeNotifier).notifyUser(eq(200L), eq("SAVED_SEARCH_ALERT"), any());
    }

    @Test
    void doesNotRenotifyWhenMatchCountIsUnchanged() {
        SavedCandidateSearch search = new SavedCandidateSearch(10L, "React Alert", "React", null, null, true);
        search.setId(50L);
        search.setLastMatchCount(1);
        when(searchRepository.findByIdAndRecruiterId(50L, 10L)).thenReturn(Optional.of(search));
        when(candidateDiscoveryService.discover("React", null))
            .thenReturn(List.of(Map.of("fullName", "Alice Candidate")));

        int matches = service.checkAndNotifyMatches(50L, 10L);

        assertEquals(1, matches);
        verifyNoInteractions(notificationFeedService, realtimeNotifier);
    }

    @Test
    void runAllAlertsEvaluatesEnabledSearchesAndSkipsFailures() {
        SavedCandidateSearch broken = new SavedCandidateSearch(10L, "Broken", "Java", null, null, true);
        broken.setId(1L);
        SavedCandidateSearch healthy = new SavedCandidateSearch(10L, "Healthy", "React", null, null, true);
        healthy.setId(2L);
        when(searchRepository.findByAlertEnabledTrue()).thenReturn(List.of(broken, healthy));
        when(candidateDiscoveryService.discover("Java", null)).thenThrow(new RuntimeException("boom"));
        when(candidateDiscoveryService.discover("React", null)).thenReturn(List.of(Map.of("fullName", "Bob")));

        service.runAllAlerts();

        verify(searchRepository).save(healthy);
        verify(searchRepository, never()).save(broken);
    }
}
