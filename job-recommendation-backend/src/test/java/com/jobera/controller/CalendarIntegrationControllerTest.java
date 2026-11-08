package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.User;
import com.jobera.security.OAuthStateStore;
import com.jobera.service.GoogleCalendarService;
import com.jobera.service.GoogleCalendarService.CalendarSyncException;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class CalendarIntegrationControllerTest {

    @Mock GoogleCalendarService googleCalendarService;
    @Mock OAuthStateStore stateStore;
    @Mock HttpServletRequest request;

    CalendarIntegrationController controller;
    User user;

    @BeforeEach
    void setUp() {
        controller = new CalendarIntegrationController(googleCalendarService, stateStore, "http://localhost:3000");
        user = new User("person@example.com", "hash", "Person One");
        user.setId(7L);
        lenient().when(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).thenReturn(user);
    }

    private String locationOf(ResponseEntity<?> response) {
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        return response.getHeaders().getFirst(HttpHeaders.LOCATION);
    }

    @Test
    void connectReturnsTheAuthorizeUrlBoundToTheCallingUser() {
        when(googleCalendarService.isConfigured()).thenReturn(true);
        when(stateStore.issueForUser(7L)).thenReturn("state-1");
        when(googleCalendarService.buildAuthorizeUrl("state-1")).thenReturn("https://accounts.google.com/authorize?state=state-1");

        ResponseEntity<?> response = controller.connect(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(java.util.Map.of("url", "https://accounts.google.com/authorize?state=state-1"), response.getBody());
    }

    @Test
    void connectReportsNotConfiguredWithoutIssuingAState() {
        when(googleCalendarService.isConfigured()).thenReturn(false);

        ResponseEntity<?> response = controller.connect(request);

        assertEquals(400, response.getStatusCode().value());
        verify(stateStore, never()).issueForUser(any());
    }

    @Test
    void aSuccessfulCallbackConnectsTheBoundUserAndRedirectsToTheProfilePage() {
        when(stateStore.consumeUserId("state-1")).thenReturn(Optional.of(7L));

        String location = locationOf(controller.callback("auth-code", "state-1", null));

        verify(googleCalendarService).connect("auth-code", 7L);
        assertTrue(location.startsWith("http://localhost:3000/profile"));
        assertTrue(location.contains("googleCalendar=connected"));
    }

    @Test
    void aCallbackWithAProviderErrorNeverConnects() {
        String location = locationOf(controller.callback(null, "state-1", "access_denied"));

        assertTrue(location.contains("googleCalendar=error"));
        verify(googleCalendarService, never()).connect(any(), any());
    }

    @Test
    void aCallbackWithAnExpiredOrUnknownStateIsRejected() {
        when(stateStore.consumeUserId("bad-state")).thenReturn(Optional.empty());

        String location = locationOf(controller.callback("auth-code", "bad-state", null));

        assertTrue(location.contains("googleCalendar=error"));
        verify(googleCalendarService, never()).connect(any(), any());
    }

    @Test
    void aCallbackWithoutACodeIsRejected() {
        when(stateStore.consumeUserId("state-1")).thenReturn(Optional.of(7L));

        String location = locationOf(controller.callback(null, "state-1", null));

        assertTrue(location.contains("googleCalendar=error"));
        verify(googleCalendarService, never()).connect(any(), any());
    }

    @Test
    void aFailedConnectDuringTheCallbackRedirectsWithTheErrorMessage() {
        when(stateStore.consumeUserId("state-1")).thenReturn(Optional.of(7L));
        org.mockito.Mockito.doThrow(new CalendarSyncException("Google did not return an access token"))
            .when(googleCalendarService).connect("auth-code", 7L);

        String location = locationOf(controller.callback("auth-code", "state-1", null));

        assertTrue(location.contains("googleCalendar=error"));
        assertTrue(location.contains("message"));
    }

    @Test
    void statusReportsWhetherTheCallingUserIsConnected() {
        when(googleCalendarService.isConnected(7L)).thenReturn(true);

        ResponseEntity<?> response = controller.status(request);

        assertEquals(java.util.Map.of("connected", true), response.getBody());
    }

    @Test
    void disconnectDisconnectsTheCallingUser() {
        ResponseEntity<?> response = controller.disconnect(request);

        verify(googleCalendarService).disconnect(7L);
        assertEquals(java.util.Map.of("disconnected", true), response.getBody());
    }
}
