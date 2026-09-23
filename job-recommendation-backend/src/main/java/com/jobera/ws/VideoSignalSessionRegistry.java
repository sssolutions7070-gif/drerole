package com.jobera.ws;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/** Tracks which open WebSocket sessions are in which video-interview "room", so an SDP/ICE
 *  message from one participant can be relayed to the other. Unlike NotificationSessionRegistry
 *  (keyed by user, server-push only), a room here is a two-way relay between whoever is connected
 *  to the same roomId. */
@Component
public class VideoSignalSessionRegistry {

    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();

    public void join(String roomId, WebSocketSession session) {
        sessionsByRoom.computeIfAbsent(roomId, id -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void leave(String roomId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByRoom.get(roomId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByRoom.remove(roomId, sessions);
        }
    }

    /** Every other session currently in the room - i.e. who a message from `sender` should be relayed to. */
    public Set<WebSocketSession> othersInRoom(String roomId, WebSocketSession sender) {
        Set<WebSocketSession> sessions = sessionsByRoom.getOrDefault(roomId, Set.of());
        return sessions.stream().filter(s -> !s.getId().equals(sender.getId()))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
