package com.jobera.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.JobPosting;
import com.jobera.entity.User;
import com.jobera.repository.UserRepository;
import com.jobera.service.JobRecommendationService;
import com.jobera.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class JobAlertServiceTest {

    @Mock UserRepository userRepository;
    @Mock JobRecommendationService recommendationService;
    @Mock NotificationService notificationService;

    JobAlertService service;

    @BeforeEach
    void setUp() {
        service = new JobAlertService(userRepository, recommendationService, notificationService);
    }

    private JobPosting jobPostedAt(String title, LocalDateTime postedDate) {
        JobPosting job = new JobPosting();
        job.setTitle(title);
        job.setCompany("Acme");
        job.setPostedDate(postedDate);
        return job;
    }

    @Test
    void emailsASubscriberAboutJobsPostedSinceTheirLastAlert() {
        User user = new User("candidate@example.com", "hash", "Candidate");
        user.setId(1L);
        LocalDateTime lastAlert = LocalDateTime.now().minusDays(2);
        user.setLastJobAlertSentAt(lastAlert);

        JobPosting newJob = jobPostedAt("Backend Engineer", LocalDateTime.now().minusHours(1));
        JobPosting oldJob = jobPostedAt("Old Posting", lastAlert.minusDays(5));

        when(userRepository.findByJobAlertsEnabledTrueAndIsActiveTrue()).thenReturn(List.of(user));
        when(recommendationService.recommendJobsForUser(1L)).thenReturn(List.of(newJob, oldJob));

        service.sendDailyDigests();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JobPosting>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).sendJobAlertDigest(eq("candidate@example.com"), eq("Candidate"), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("Backend Engineer", captor.getValue().get(0).getTitle());
    }

    @Test
    void advancesTheWatermarkEvenWithNoNewMatches() {
        User user = new User("candidate@example.com", "hash", "Candidate");
        user.setId(1L);
        LocalDateTime originalWatermark = LocalDateTime.now().minusDays(2);
        user.setLastJobAlertSentAt(originalWatermark);

        when(userRepository.findByJobAlertsEnabledTrueAndIsActiveTrue()).thenReturn(List.of(user));
        when(recommendationService.recommendJobsForUser(1L)).thenReturn(List.of());

        service.sendDailyDigests();

        verify(notificationService, never()).sendJobAlertDigest(any(), any(), anyList());
        assertNotNull(user.getLastJobAlertSentAt());
        assertEquals(true, user.getLastJobAlertSentAt().isAfter(originalWatermark));
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void firstEverAlertUsesAccountCreationAsTheBaseline() {
        User user = new User("new@example.com", "hash", "New User");
        user.setId(2L);
        LocalDateTime joined = LocalDateTime.now().minusDays(10);
        user.setCreatedAt(joined);
        // lastJobAlertSentAt is null: never alerted before

        JobPosting postedAfterJoining = jobPostedAt("Frontend Role", joined.plusDays(1));
        JobPosting postedBeforeJoining = jobPostedAt("Ancient Posting", joined.minusDays(1));

        when(userRepository.findByJobAlertsEnabledTrueAndIsActiveTrue()).thenReturn(List.of(user));
        when(recommendationService.recommendJobsForUser(2L)).thenReturn(List.of(postedAfterJoining, postedBeforeJoining));

        service.sendDailyDigests();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JobPosting>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).sendJobAlertDigest(eq("new@example.com"), eq("New User"), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("Frontend Role", captor.getValue().get(0).getTitle());
    }

    @Test
    void oneSubscribersFailureDoesNotStopTheOthers() {
        User brokenUser = new User("broken@example.com", "hash", "Broken");
        brokenUser.setId(1L);
        User healthyUser = new User("healthy@example.com", "hash", "Healthy");
        healthyUser.setId(2L);
        healthyUser.setCreatedAt(LocalDateTime.now().minusDays(1));

        JobPosting match = jobPostedAt("Some Role", LocalDateTime.now());

        when(userRepository.findByJobAlertsEnabledTrueAndIsActiveTrue()).thenReturn(List.of(brokenUser, healthyUser));
        when(recommendationService.recommendJobsForUser(1L)).thenThrow(new RuntimeException("boom"));
        when(recommendationService.recommendJobsForUser(2L)).thenReturn(List.of(match));

        service.sendDailyDigests();

        verify(notificationService).sendJobAlertDigest(eq("healthy@example.com"), any(), anyList());
    }

    @Test
    void skipsUsersWhoHaveNotOptedIn() {
        when(userRepository.findByJobAlertsEnabledTrueAndIsActiveTrue()).thenReturn(List.of());

        service.sendDailyDigests();

        verify(recommendationService, never()).recommendJobsForUser(anyLong());
        verify(notificationService, never()).sendJobAlertDigest(any(), any(), anyList());
    }
}
