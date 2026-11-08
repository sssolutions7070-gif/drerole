package com.jobera.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.jobera.entity.CalendarCredential;
import com.jobera.entity.CalendarSyncRecord;
import com.jobera.entity.InterviewRound;
import com.jobera.repository.CalendarCredentialRepository;
import com.jobera.repository.CalendarSyncRecordRepository;

/**
 * Pushes scheduled interview rounds straight into a user's Google Calendar, instead of them
 * having to download the .ics file (InterviewRoundService.generateIcs) and import it by hand.
 * Called directly with RestTemplate against the Calendar REST API, same hand-rolled style as
 * OAuthService's login flow and for the same reason - this app doesn't use Spring Security.
 *
 * This is a *second*, separate OAuth grant from "Sign in with Google" (OAuthService): login only
 * ever asks for "openid email profile" and never stores a token, while this asks for the
 * calendar.events scope and keeps an access/refresh token pair per user so it can push events
 * later without the user being present. A user can have signed in with Google and never connected
 * their calendar, or vice versa (signed up with a password, connected their calendar separately).
 *
 * Needs its own real credentials to do anything beyond building the authorize URL - see
 * secrets.properties.example for GOOGLE_CALENDAR_CLIENT_ID etc.
 */
@Service
public class GoogleCalendarService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarService.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public static class CalendarSyncException extends RuntimeException {
        public CalendarSyncException(String message) {
            super(message);
        }
    }

    private final CalendarCredentialRepository credentialRepository;
    private final CalendarSyncRecordRepository syncRecordRepository;
    private final RestTemplate restTemplate;
    private final String clientId;
    private final String clientSecret;
    private final String backendBaseUrl;
    private final String frontendUrl;

    public GoogleCalendarService(CalendarCredentialRepository credentialRepository,
                                  CalendarSyncRecordRepository syncRecordRepository,
                                  RestTemplate restTemplate,
                                  @Value("${app.calendar.google-client-id:}") String clientId,
                                  @Value("${app.calendar.google-client-secret:}") String clientSecret,
                                  @Value("${app.backend-base-url:http://localhost:8081}") String backendBaseUrl,
                                  @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.credentialRepository = credentialRepository;
        this.syncRecordRepository = syncRecordRepository;
        this.restTemplate = restTemplate;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.backendBaseUrl = backendBaseUrl;
        this.frontendUrl = frontendUrl;
    }

    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    private String redirectUri() {
        return backendBaseUrl + "/api/calendar/google/callback";
    }

    public String buildAuthorizeUrl(String state) {
        if (!isConfigured()) {
            throw new CalendarSyncException("Google Calendar sync is not configured on this server");
        }
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
            .queryParam("client_id", clientId)
            .queryParam("redirect_uri", redirectUri())
            .queryParam("response_type", "code")
            .queryParam("scope", SCOPE)
            // offline + consent: without both, Google only issues a refresh token the very first
            // time a given account ever consents to this app, which would silently break
            // reconnecting after a disconnect.
            .queryParam("access_type", "offline")
            .queryParam("prompt", "consent")
            .queryParam("state", state)
            .build().encode().toUriString();
    }

    public void connect(String code, Long userId) {
        if (!isConfigured()) {
            throw new CalendarSyncException("Google Calendar sync is not configured on this server");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri());
        form.add("grant_type", "authorization_code");

        Map<String, Object> response = postForm("https://oauth2.googleapis.com/token", form);
        String accessToken = asString(response.get("access_token"));
        String refreshToken = asString(response.get("refresh_token"));
        int expiresIn = response.get("expires_in") instanceof Number n ? n.intValue() : 3600;
        if (accessToken == null) {
            throw new CalendarSyncException("Google did not return an access token");
        }

        CalendarCredential credential = credentialRepository.findByUserId(userId).orElseGet(CalendarCredential::new);
        credential.setUserId(userId);
        credential.setProvider("GOOGLE");
        credential.setAccessToken(accessToken);
        // A reconnect that Google doesn't re-grant a refresh token for (prompt=consent should
        // always cover that, but defensively) keeps the previous one rather than losing it.
        if (refreshToken != null) {
            credential.setRefreshToken(refreshToken);
        }
        credential.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
        credentialRepository.save(credential);
    }

    public boolean isConnected(Long userId) {
        return credentialRepository.findByUserId(userId).isPresent();
    }

    public void disconnect(Long userId) {
        credentialRepository.deleteByUserId(userId);
    }

    /** Pushes one interview round to whichever of its two participants (candidate, recruiter)
     *  have connected their Google Calendar - a no-op for anyone who hasn't. Best-effort: a
     *  participant's calendar push failing (expired/revoked grant, Google being down, ...) must
     *  never block scheduling the interview itself, so every failure is caught and logged here. */
    public void syncRound(InterviewRound round, Long candidateUserId, Long recruiterUserId) {
        for (Long userId : Arrays.asList(candidateUserId, recruiterUserId)) {
            if (userId == null) {
                continue;
            }
            try {
                syncRoundForUser(round, userId);
            } catch (Exception e) {
                log.warn("Google Calendar sync failed for round {} / user {}: {}", round.getId(), userId, e.getMessage());
            }
        }
    }

    private void syncRoundForUser(InterviewRound round, Long userId) {
        Optional<CalendarCredential> credential = credentialRepository.findByUserId(userId);
        if (credential.isEmpty() || round.getScheduledAt() == null) {
            return;
        }
        String accessToken = ensureFreshAccessToken(credential.get());

        LocalDateTime start = round.getScheduledAt();
        LocalDateTime end = start.plusHours(1);
        String joinUrl = frontendUrl + "/interview-rounds/" + round.getId();
        // Same "floating" local time simplification InterviewRoundService.generateIcs makes -
        // this app stores every schedule as a plain LocalDateTime with no real timezone, so UTC
        // here is a formality Google's API requires, not a claim that the wall-clock value is
        // actually UTC.
        Map<String, Object> event = Map.of(
            "summary", round.getTitle(),
            "description", "Join the interview: " + joinUrl,
            "start", Map.of("dateTime", start.format(EVENT_TIME), "timeZone", "UTC"),
            "end", Map.of("dateTime", end.format(EVENT_TIME), "timeZone", "UTC")
        );

        Optional<CalendarSyncRecord> existing =
            syncRecordRepository.findByInterviewRoundIdAndUserId(round.getId(), userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> result;
        try {
            if (existing.isPresent()) {
                result = patchJson("https://www.googleapis.com/calendar/v3/calendars/primary/events/"
                    + existing.get().getGoogleEventId(), headers, event);
            } else {
                result = postJson("https://www.googleapis.com/calendar/v3/calendars/primary/events",
                    headers, event);
            }
        } catch (RestClientException e) {
            throw new CalendarSyncException("Google Calendar API call failed: " + e.getMessage());
        }

        String eventId = asString(result.get("id"));
        if (eventId == null) {
            return;
        }
        CalendarSyncRecord record = existing.orElseGet(CalendarSyncRecord::new);
        record.setInterviewRoundId(round.getId());
        record.setUserId(userId);
        record.setGoogleEventId(eventId);
        syncRecordRepository.save(record);
    }

    private String ensureFreshAccessToken(CalendarCredential credential) {
        if (credential.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(1))) {
            return credential.getAccessToken();
        }
        if (credential.getRefreshToken() == null) {
            throw new CalendarSyncException("Google Calendar access expired and there's no refresh token to renew it");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("refresh_token", credential.getRefreshToken());
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");

        Map<String, Object> response = postForm("https://oauth2.googleapis.com/token", form);
        String accessToken = asString(response.get("access_token"));
        if (accessToken == null) {
            throw new CalendarSyncException("Failed to refresh the Google Calendar access token");
        }
        int expiresIn = response.get("expires_in") instanceof Number n ? n.intValue() : 3600;
        credential.setAccessToken(accessToken);
        credential.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
        credentialRepository.save(credential);
        return accessToken;
    }

    private Map<String, Object> postForm(String url, MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(url, new HttpEntity<>(form, headers), Map.class);
            if (body == null) {
                throw new CalendarSyncException("Google returned an empty token response");
            }
            return body;
        } catch (RestClientException e) {
            throw new CalendarSyncException("Failed to reach Google's token endpoint");
        }
    }

    private Map<String, Object> postJson(String url, HttpHeaders headers, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        return responseBody(response);
    }

    private Map<String, Object> patchJson(String url, HttpHeaders headers, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PATCH, new HttpEntity<>(body, headers), Map.class);
        return responseBody(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responseBody(ResponseEntity<Map> response) {
        Map<String, Object> body = response.getBody();
        return body != null ? body : Map.of();
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
