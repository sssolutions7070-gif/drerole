package com.jobera.ws;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Relays WebRTC signaling messages (SDP offers/answers, ICE candidates) between the two
 * participants of one video-interview room. The server never looks at the message contents - it
 * just forwards whatever JSON one peer sends to whichever other peer(s) are currently in the same
 * room, exactly like a two-person chat relay. The actual media (audio/video) never touches this
 * server - it flows peer-to-peer once WebRTC negotiation (driven by these relayed messages)
 * completes.
 */
@Component
public class VideoSignalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VideoSignalWebSocketHandler.class);

    private final VideoSignalSessionRegistry registry;

    public VideoSignalWebSocketHandler(VideoSignalSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String roomId = roomIdOf(session);
        if (roomId != null) {
            registry.join(roomId, session);
            broadcast(roomId, session, "{\"type\":\"peer-joined\"}");
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        String roomId = roomIdOf(session);
        if (roomId != null) {
            broadcast(roomId, session, message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String roomId = roomIdOf(session);
        if (roomId != null) {
            registry.leave(roomId, session);
            broadcast(roomId, session, "{\"type\":\"peer-left\"}");
        }
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        log.debug("Video signaling transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    private void broadcast(String roomId, WebSocketSession sender, String payload) {
        Set<WebSocketSession> others = registry.othersInRoom(roomId, sender);
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession peer : others) {
            try {
                if (peer.isOpen()) {
                    peer.sendMessage(message);
                }
            } catch (IOException e) {
                log.debug("Failed to relay to session {}: {}", peer.getId(), e.getMessage());
            }
        }
    }

    private String roomIdOf(WebSocketSession session) {
        return (String) session.getAttributes().get(VideoSignalHandshakeInterceptor.ROOM_ID_ATTRIBUTE);
    }
}
