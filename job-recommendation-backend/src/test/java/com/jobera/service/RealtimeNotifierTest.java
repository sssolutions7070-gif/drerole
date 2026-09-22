package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.jobera.ws.NotificationSessionRegistry;

@ExtendWith(MockitoExtension.class)
class RealtimeNotifierTest {

    @Mock NotificationSessionRegistry registry;

    @Test
    void withNoOpenSessionsNothingIsSent() {
        RealtimeNotifier notifier = new RealtimeNotifier(registry);
        when(registry.sessionsFor(1L)).thenReturn(Set.of());

        notifier.notifyUser(1L, "APPLICATION_STATUS_CHANGED", Map.of("status", "SHORTLISTED"));
        // Nothing to verify beyond "no exception" - there is no session to assert a send on.
    }

    @Test
    void nullUserIdIsANoOp() {
        RealtimeNotifier notifier = new RealtimeNotifier(registry);

        notifier.notifyUser(null, "APPLICATION_STATUS_CHANGED", Map.of());

        verify(registry, never()).sessionsFor(any());
    }

    @Test
    void sendsTheEventAsJsonToEveryOpenSession() throws IOException {
        RealtimeNotifier notifier = new RealtimeNotifier(registry);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(registry.sessionsFor(1L)).thenReturn(Set.of(session));

        notifier.notifyUser(1L, "APPLICATION_STATUS_CHANGED", Map.of("status", "SHORTLISTED"));

        verify(session).sendMessage(argThat((TextMessage m) ->
            m.getPayload().contains("\"type\":\"APPLICATION_STATUS_CHANGED\"") && m.getPayload().contains("SHORTLISTED")));
    }

    @Test
    void skipsAClosedSession() throws IOException {
        RealtimeNotifier notifier = new RealtimeNotifier(registry);
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);
        when(registry.sessionsFor(1L)).thenReturn(Set.of(session));

        notifier.notifyUser(1L, "APPLICATION_STATUS_CHANGED", Map.of());

        verify(session, never()).sendMessage(any());
    }

    @Test
    void aFailedSendToOneSessionDoesNotThrowOrStopOthers() throws IOException {
        RealtimeNotifier notifier = new RealtimeNotifier(registry);
        WebSocketSession failing = mock(WebSocketSession.class);
        WebSocketSession working = mock(WebSocketSession.class);
        when(failing.isOpen()).thenReturn(true);
        when(working.isOpen()).thenReturn(true);
        org.mockito.Mockito.doThrow(new IOException("boom")).when(failing).sendMessage(any());
        when(registry.sessionsFor(1L)).thenReturn(Set.of(failing, working));

        assertDoesNotThrow(() -> notifier.notifyUser(1L, "APPLICATION_STATUS_CHANGED", Map.of()));

        verify(working).sendMessage(any());
    }
}
