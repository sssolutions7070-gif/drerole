package com.jobera.ws;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class NotificationWebSocketHandlerTest {

    @Test
    void connectingWithAResolvedUserIdRegistersTheSession() {
        NotificationSessionRegistry registry = mock(NotificationSessionRegistry.class);
        NotificationWebSocketHandler handler = new NotificationWebSocketHandler(registry);
        WebSocketSession session = sessionWithUserId(7L);

        handler.afterConnectionEstablished(session);

        verify(registry).register(7L, session);
    }

    @Test
    void connectingWithNoUserIdDoesNotRegister() {
        NotificationSessionRegistry registry = mock(NotificationSessionRegistry.class);
        NotificationWebSocketHandler handler = new NotificationWebSocketHandler(registry);
        WebSocketSession session = sessionWithUserId(null);

        handler.afterConnectionEstablished(session);

        verify(registry, never()).register(org.mockito.ArgumentMatchers.any(), eq(session));
    }

    @Test
    void closingUnregistersTheSession() {
        NotificationSessionRegistry registry = mock(NotificationSessionRegistry.class);
        NotificationWebSocketHandler handler = new NotificationWebSocketHandler(registry);
        WebSocketSession session = sessionWithUserId(7L);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(registry).unregister(7L, session);
    }

    @Test
    void transportErrorsDoNotThrow() {
        NotificationSessionRegistry registry = mock(NotificationSessionRegistry.class);
        NotificationWebSocketHandler handler = new NotificationWebSocketHandler(registry);
        WebSocketSession session = sessionWithUserId(7L);

        assertDoesNotThrow(() -> handler.handleTransportError(session, new RuntimeException("boom")));
    }

    private WebSocketSession sessionWithUserId(Long userId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        if (userId != null) {
            attributes.put(NotificationWebSocketHandler.USER_ID_ATTRIBUTE, userId);
        }
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }
}
