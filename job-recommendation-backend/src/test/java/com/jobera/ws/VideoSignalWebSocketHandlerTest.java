package com.jobera.ws;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class VideoSignalWebSocketHandlerTest {

    private WebSocketSession sessionInRoom(String sessionId, String roomId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        if (roomId != null) {
            attributes.put(VideoSignalHandshakeInterceptor.ROOM_ID_ATTRIBUTE, roomId);
        }
        when(session.getId()).thenReturn(sessionId);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }

    @Test
    void connectingJoinsTheRoomAndNotifiesAnyoneAlreadyThere() throws IOException {
        VideoSignalSessionRegistry registry = new VideoSignalSessionRegistry();
        WebSocketSession first = sessionInRoom("a", "room-1");
        when(first.isOpen()).thenReturn(true);
        registry.join("room-1", first);
        VideoSignalWebSocketHandler handler = new VideoSignalWebSocketHandler(registry);

        WebSocketSession second = sessionInRoom("b", "room-1");
        handler.afterConnectionEstablished(second);

        verify(first).sendMessage(new TextMessage("{\"type\":\"peer-joined\"}"));
    }

    @Test
    void connectingWithoutARoomDoesNotRegisterOrBroadcast() {
        VideoSignalSessionRegistry registry = mock(VideoSignalSessionRegistry.class);
        VideoSignalWebSocketHandler handler = new VideoSignalWebSocketHandler(registry);
        WebSocketSession session = sessionInRoom("a", null);

        handler.afterConnectionEstablished(session);

        verify(registry, never()).join(any(), any());
    }

    @Test
    void aTextMessageIsRelayedToTheOtherParticipantOnly() throws IOException {
        VideoSignalSessionRegistry registry = new VideoSignalSessionRegistry();
        WebSocketSession sender = sessionInRoom("a", "room-1");
        WebSocketSession other = sessionInRoom("b", "room-1");
        when(other.isOpen()).thenReturn(true);
        registry.join("room-1", sender);
        registry.join("room-1", other);
        VideoSignalWebSocketHandler handler = new VideoSignalWebSocketHandler(registry);

        handler.handleTextMessage(sender, new TextMessage("{\"type\":\"offer\",\"sdp\":\"...\"}"));

        verify(other).sendMessage(new TextMessage("{\"type\":\"offer\",\"sdp\":\"...\"}"));
        verify(sender, never()).sendMessage(any());
    }

    @Test
    void closingLeavesTheRoomAndNotifiesTheRemainingPeer() throws IOException {
        VideoSignalSessionRegistry registry = new VideoSignalSessionRegistry();
        WebSocketSession leaving = sessionInRoom("a", "room-1");
        WebSocketSession remaining = sessionInRoom("b", "room-1");
        when(remaining.isOpen()).thenReturn(true);
        registry.join("room-1", leaving);
        registry.join("room-1", remaining);
        VideoSignalWebSocketHandler handler = new VideoSignalWebSocketHandler(registry);

        handler.afterConnectionClosed(leaving, CloseStatus.NORMAL);

        verify(remaining).sendMessage(new TextMessage("{\"type\":\"peer-left\"}"));
        assItRemoved(registry, remaining);
    }

    private void assItRemoved(VideoSignalSessionRegistry registry, WebSocketSession remaining) {
        // The leaving session should no longer count as "other" to the remaining one.
        org.junit.jupiter.api.Assertions.assertTrue(registry.othersInRoom("room-1", remaining).isEmpty());
    }

    @Test
    void transportErrorsDoNotThrow() {
        VideoSignalSessionRegistry registry = new VideoSignalSessionRegistry();
        VideoSignalWebSocketHandler handler = new VideoSignalWebSocketHandler(registry);
        WebSocketSession session = sessionInRoom("a", "room-1");

        assertDoesNotThrow(() -> handler.handleTransportError(session, new RuntimeException("boom")));
    }
}
