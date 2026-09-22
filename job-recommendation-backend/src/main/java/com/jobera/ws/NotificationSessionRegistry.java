package com.jobera.ws;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/** Tracks which open WebSocket sessions belong to which logged-in user, so a status change can be pushed to them. */
@Component
public class NotificationSessionRegistry {

    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessionsByUserId.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUserId.remove(userId, sessions);
        }
    }

    public Set<WebSocketSession> sessionsFor(Long userId) {
        return sessionsByUserId.getOrDefault(userId, Set.of());
    }

    public int connectedUserCount() {
        return sessionsByUserId.size();
    }
}
