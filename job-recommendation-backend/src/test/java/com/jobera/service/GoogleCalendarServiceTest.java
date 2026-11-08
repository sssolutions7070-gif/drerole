package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.jobera.entity.CalendarCredential;
import com.jobera.entity.CalendarSyncRecord;
import com.jobera.entity.InterviewRound;
import com.jobera.repository.CalendarCredentialRepository;
import com.jobera.repository.CalendarSyncRecordRepository;
import com.jobera.service.GoogleCalendarService.CalendarSyncException;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarServiceTest {

    @Mock CalendarCredentialRepository credentialRepository;
    @Mock CalendarSyncRecordRepository syncRecordRepository;
    @Mock RestTemplate restTemplate;

    GoogleCalendarService configured;
    GoogleCalendarService unconfigured;

    @BeforeEach
    void setUp() {
        configured = new GoogleCalendarService(credentialRepository, syncRecordRepository, restTemplate,
            "cal-client-id", "cal-client-secret", "http://localhost:8081", "http://localhost:3000");
        unconfigured = new GoogleCalendarService(credentialRepository, syncRecordRepository, restTemplate,
            "", "", "http://localhost:8081", "http://localhost:3000");
    }

    private InterviewRound roundScheduledAt(LocalDateTime scheduledAt) {
        InterviewRound round = new InterviewRound(1L, 1, "Technical round", "TECHNICAL", "room-1", scheduledAt);
        round.setId(99L);
        return round;
    }

    @Test
    void reportsConfiguredOnlyWhenBothIdAndSecretAreSet() {
        assertTrue(configured.isConfigured());
        assertFalse(unconfigured.isConfigured());
    }

    @Test
    void authorizeUrlRequestsTheCalendarEventsScopeAndOfflineAccess() {
        String url = configured.buildAuthorizeUrl("the-state");

        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth"));
        assertTrue(url.contains("client_id=cal-client-id"));
        assertTrue(url.contains("state=the-state"));
        assertTrue(url.contains("scope=https://www.googleapis.com/auth/calendar.events"));
        assertTrue(url.contains("access_type=offline"));
        assertTrue(url.contains("prompt=consent"));
        assertTrue(url.contains("redirect_uri=http://localhost:8081/api/calendar/google/callback"));
    }

    @Test
    void buildingAnAuthorizeUrlWithoutCredentialsFails() {
        assertThrows(CalendarSyncException.class, () -> unconfigured.buildAuthorizeUrl("s"));
    }

    @Test
    void connectingWithoutCredentialsFails() {
        assertThrows(CalendarSyncException.class, () -> unconfigured.connect("code", 1L));
    }

    @Test
    void connectSavesTheAccessAndRefreshTokenForTheUser() {
        when(credentialRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"), any(), eq(Map.class)))
            .thenReturn(Map.of("access_token", "access-1", "refresh_token", "refresh-1", "expires_in", 3600));

        configured.connect("auth-code", 1L);

        ArgumentCaptor<CalendarCredential> captor = ArgumentCaptor.forClass(CalendarCredential.class);
        verify(credentialRepository).save(captor.capture());
        CalendarCredential saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals("access-1", saved.getAccessToken());
        assertEquals("refresh-1", saved.getRefreshToken());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void reconnectingWithoutANewRefreshTokenKeepsTheOldOne() {
        CalendarCredential existing = new CalendarCredential();
        existing.setUserId(1L);
        existing.setRefreshToken("original-refresh");
        when(credentialRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"), any(), eq(Map.class)))
            .thenReturn(Map.of("access_token", "access-2", "expires_in", 3600));

        configured.connect("auth-code", 1L);

        ArgumentCaptor<CalendarCredential> captor = ArgumentCaptor.forClass(CalendarCredential.class);
        verify(credentialRepository).save(captor.capture());
        assertEquals("original-refresh", captor.getValue().getRefreshToken());
    }

    @Test
    void aMissingAccessTokenInTheResponseFails() {
        when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(Map.of("error", "invalid_grant"));

        assertThrows(CalendarSyncException.class, () -> configured.connect("bad-code", 1L));
    }

    @Test
    void isConnectedAndDisconnectDelegateToTheRepository() {
        when(credentialRepository.findByUserId(1L)).thenReturn(Optional.of(new CalendarCredential()));
        when(credentialRepository.findByUserId(2L)).thenReturn(Optional.empty());

        assertTrue(configured.isConnected(1L));
        assertFalse(configured.isConnected(2L));

        configured.disconnect(1L);
        verify(credentialRepository).deleteByUserId(1L);
    }

    @Test
    void syncRoundSkipsAUserWhoHasNotConnectedTheirCalendar() {
        when(credentialRepository.findByUserId(5L)).thenReturn(Optional.empty());

        configured.syncRound(roundScheduledAt(LocalDateTime.now().plusDays(1)), 5L, null);

        verify(restTemplate, never()).exchange(any(String.class), any(HttpMethod.class), any(), eq(Map.class));
    }

    @Test
    void syncRoundSkipsARoundWithNoScheduledTimeYet() {
        lenient().when(credentialRepository.findByUserId(5L))
            .thenReturn(Optional.of(freshCredential(5L)));

        configured.syncRound(roundScheduledAt(null), 5L, null);

        verify(restTemplate, never()).exchange(any(String.class), any(HttpMethod.class), any(), eq(Map.class));
    }

    @Test
    void syncRoundCreatesAGoogleEventForEachConnectedParticipant() {
        when(credentialRepository.findByUserId(5L)).thenReturn(Optional.of(freshCredential(5L)));
        when(credentialRepository.findByUserId(6L)).thenReturn(Optional.of(freshCredential(6L)));
        when(syncRecordRepository.findByInterviewRoundIdAndUserId(99L, 5L)).thenReturn(Optional.empty());
        when(syncRecordRepository.findByInterviewRoundIdAndUserId(99L, 6L)).thenReturn(Optional.empty());
        when(restTemplate.exchange(eq("https://www.googleapis.com/calendar/v3/calendars/primary/events"),
                eq(HttpMethod.POST), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("id", "g-event-1")));

        configured.syncRound(roundScheduledAt(LocalDateTime.now().plusDays(1)), 5L, 6L);

        verify(restTemplate, org.mockito.Mockito.times(2)).exchange(
            eq("https://www.googleapis.com/calendar/v3/calendars/primary/events"),
            eq(HttpMethod.POST), any(), eq(Map.class));
        verify(syncRecordRepository, org.mockito.Mockito.times(2)).save(any(CalendarSyncRecord.class));
    }

    @Test
    void reschedulingAnAlreadySyncedRoundPatchesTheExistingEventInstead() {
        CalendarSyncRecord existing = new CalendarSyncRecord();
        existing.setInterviewRoundId(99L);
        existing.setUserId(5L);
        existing.setGoogleEventId("g-event-existing");

        when(credentialRepository.findByUserId(5L)).thenReturn(Optional.of(freshCredential(5L)));
        when(syncRecordRepository.findByInterviewRoundIdAndUserId(99L, 5L)).thenReturn(Optional.of(existing));
        when(restTemplate.exchange(eq("https://www.googleapis.com/calendar/v3/calendars/primary/events/g-event-existing"),
                eq(HttpMethod.PATCH), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("id", "g-event-existing")));

        configured.syncRound(roundScheduledAt(LocalDateTime.now().plusDays(2)), 5L, null);

        verify(restTemplate).exchange(eq("https://www.googleapis.com/calendar/v3/calendars/primary/events/g-event-existing"),
            eq(HttpMethod.PATCH), any(), eq(Map.class));
        verify(restTemplate, never()).exchange(eq("https://www.googleapis.com/calendar/v3/calendars/primary/events"),
            eq(HttpMethod.POST), any(), eq(Map.class));
    }

    @Test
    void anExpiredAccessTokenIsRefreshedBeforePushingTheEvent() {
        CalendarCredential expired = freshCredential(5L);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        expired.setRefreshToken("refresh-5");
        when(credentialRepository.findByUserId(5L)).thenReturn(Optional.of(expired));
        when(syncRecordRepository.findByInterviewRoundIdAndUserId(99L, 5L)).thenReturn(Optional.empty());
        when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"), any(), eq(Map.class)))
            .thenReturn(Map.of("access_token", "refreshed-access", "expires_in", 3600));
        when(restTemplate.exchange(eq("https://www.googleapis.com/calendar/v3/calendars/primary/events"),
                eq(HttpMethod.POST), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("id", "g-event-2")));

        configured.syncRound(roundScheduledAt(LocalDateTime.now().plusDays(1)), 5L, null);

        verify(restTemplate).postForObject(eq("https://oauth2.googleapis.com/token"), any(), eq(Map.class));
        verify(credentialRepository).save(expired);
    }

    @Test
    void anExpiredTokenWithNoRefreshTokenCannotBeRenewedButSyncRoundStillDoesNotThrow() {
        CalendarCredential expired = freshCredential(5L);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(5));
        expired.setRefreshToken(null);
        when(credentialRepository.findByUserId(5L)).thenReturn(Optional.of(expired));

        // Best-effort: a sync failure for one participant must never propagate out of syncRound.
        configured.syncRound(roundScheduledAt(LocalDateTime.now().plusDays(1)), 5L, null);

        verify(restTemplate, never()).exchange(any(String.class), any(HttpMethod.class), any(), eq(Map.class));
    }

    @Test
    void aGoogleApiFailureDuringSyncIsSwallowedRatherThanPropagated() {
        when(credentialRepository.findByUserId(5L)).thenReturn(Optional.of(freshCredential(5L)));
        when(syncRecordRepository.findByInterviewRoundIdAndUserId(99L, 5L)).thenReturn(Optional.empty());
        when(restTemplate.exchange(eq("https://www.googleapis.com/calendar/v3/calendars/primary/events"),
                eq(HttpMethod.POST), any(), eq(Map.class)))
            .thenThrow(new RestClientException("Google is down"));

        configured.syncRound(roundScheduledAt(LocalDateTime.now().plusDays(1)), 5L, null);

        verify(syncRecordRepository, never()).save(any(CalendarSyncRecord.class));
    }

    private CalendarCredential freshCredential(Long userId) {
        CalendarCredential credential = new CalendarCredential();
        credential.setUserId(userId);
        credential.setAccessToken("access-" + userId);
        credential.setExpiresAt(LocalDateTime.now().plusHours(1));
        return credential;
    }
}
