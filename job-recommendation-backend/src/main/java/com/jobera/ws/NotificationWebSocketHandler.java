package com.jobera.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Server-push only: the client never sends anything meaningful over this socket, it just
 * stays open so {@link NotificationSessionRegistry} can look it up when an event happens.
 * The connected user's id is placed in the session attributes by {@link TokenHandshakeInterceptor}.
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationWebSocketHandler.class);
    static final String USER_ID_ATTRIBUTE = "userId";

    private final NotificationSessionRegistry registry;

    public NotificationWebSocketHandler(NotificationSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        Long userId = userIdOf(session);
        if (userId != null) {
            registry.register(userId, session);
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId != null) {
            registry.unregister(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        // Clients don't send commands over this channel; a stray message is just ignored.
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        log.debug("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    private Long userIdOf(WebSocketSession session) {
        return (Long) session.getAttributes().get(USER_ID_ATTRIBUTE);
    }
}
