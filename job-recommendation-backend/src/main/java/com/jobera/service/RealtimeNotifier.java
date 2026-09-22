package com.jobera.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobera.ws.NotificationSessionRegistry;

/** Pushes a JSON event to a user's open browser tabs, if any are connected; a no-op otherwise. */
@Service
public class RealtimeNotifier {

    private static final Logger log = LoggerFactory.getLogger(RealtimeNotifier.class);

    private final NotificationSessionRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RealtimeNotifier(NotificationSessionRegistry registry) {
        this.registry = registry;
    }

    public void notifyUser(Long userId, String type, Map<String, Object> data) {
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = registry.sessionsFor(userId);
        if (sessions.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            log.warn("Failed to serialize realtime event {}: {}", type, e.getMessage());
            return;
        }

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                log.debug("Failed to push to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }
}
