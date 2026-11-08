package com.jobera.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.User;
import com.jobera.security.OAuthStateStore;
import com.jobera.service.GoogleCalendarService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Lets a signed-in user (candidate or recruiter) connect their Google Calendar so scheduled
 * interview rounds appear there automatically (see GoogleCalendarService.syncRound, wired into
 * InterviewRoundService's scheduling methods) - an alternative to the existing manual .ics
 * download/"Add to Google Calendar" link for anyone who'd rather not do that every time.
 *
 * /connect and /status/DELETE are ordinary authenticated JSON endpoints, called with fetch/axios
 * like any other route here. /callback is different: Google redirects the bare browser back to
 * it with no Authorization header, so it has to be public (AuthInterceptor.PUBLIC_PATHS) and
 * relies entirely on the state parameter - issued bound to this user by /connect - to know whose
 * account to attach the tokens to.
 */
@RestController
@RequestMapping("/api/calendar/google")
public class CalendarIntegrationController {

    private static final String ERROR_KEY = "error";

    private final GoogleCalendarService googleCalendarService;
    private final OAuthStateStore stateStore;
    private final String frontendUrl;

    public CalendarIntegrationController(GoogleCalendarService googleCalendarService,
                                          OAuthStateStore stateStore,
                                          @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.googleCalendarService = googleCalendarService;
        this.stateStore = stateStore;
        this.frontendUrl = frontendUrl;
    }

    private User callingUser(HttpServletRequest request) {
        return (User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
    }

    @GetMapping("/connect")
    public ResponseEntity<?> connect(HttpServletRequest request) {
        if (!googleCalendarService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Google Calendar sync is not configured on this server"));
        }
        String state = stateStore.issueForUser(callingUser(request).getId());
        return ResponseEntity.ok(Map.of("url", googleCalendarService.buildAuthorizeUrl(state)));
    }

    @GetMapping("/callback")
    public ResponseEntity<?> callback(@RequestParam(required = false) String code,
                                       @RequestParam(required = false) String state,
                                       @RequestParam(required = false) String error) {
        if (error != null) {
            return redirect(resultUrl(false, "Google Calendar connection was cancelled"));
        }
        Optional<Long> userId = stateStore.consumeUserId(state);
        if (userId.isEmpty()) {
            return redirect(resultUrl(false, "This connection link has expired - please try again"));
        }
        if (code == null || code.isBlank()) {
            return redirect(resultUrl(false, "Missing authorization code from Google"));
        }
        try {
            googleCalendarService.connect(code, userId.get());
            return redirect(resultUrl(true, null));
        } catch (GoogleCalendarService.CalendarSyncException e) {
            return redirect(resultUrl(false, e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(HttpServletRequest request) {
        boolean connected = googleCalendarService.isConnected(callingUser(request).getId());
        return ResponseEntity.ok(Map.of("connected", connected));
    }

    @DeleteMapping
    public ResponseEntity<?> disconnect(HttpServletRequest request) {
        googleCalendarService.disconnect(callingUser(request).getId());
        return ResponseEntity.ok(Map.of("disconnected", true));
    }

    private String resultUrl(boolean connected, String message) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontendUrl + "/profile")
            .queryParam("googleCalendar", connected ? "connected" : "error");
        if (message != null) {
            builder.queryParam("message", message);
        }
        return builder.build().encode().toUriString();
    }

    private ResponseEntity<?> redirect(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, url);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
