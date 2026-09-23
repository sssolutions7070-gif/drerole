package com.jobera.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class VideoSignalSessionRegistryTest {

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }

    @Test
    void othersInRoomIsEmptyBeforeAnyoneJoins() {
        VideoSignalSessionRegistry registry = new VideoSignalSessionRegistry();

        assertTrue(registry.othersInRoom("room-1", session("a")).isEmpty());
    }

    @Test
    void othersInRoomExcludesTheSenderItself() {
        VideoSignalSessionRegistry registry = new VideoSignalSessionRegistry();
        WebSocketSession candidateSession = session("a");
        registry.join("room-1", candidateSession);

        assertTrue(registry.othersInRoom("room-1", candidateSession).isEmpty());
    }

    @Test
    void othersInRoomIncludesTheOtherParticipant() {
        VideoSignalSessionRegistry registry = new VideoSignalSessionRegistry();
        WebSocketSession candidateSession = session("a");
        WebSocketSession recruiterSession = session("b");
        registry.join("room-1", candidateSession);
        registry.join("room-1", recruiterSession);

        var others = registry.othersInRoom("room-1", candidateSession);

        assertEquals(1, others.size());
        assertTrue(others.contains(recruiterSession));
    }

    @Test
    void differentRoomsDoNotSeeEachOthersSessions() {
        VideoSignalSessionRegistry registry = new VideoSignalSessionRegistry();
        WebSocketSession roomOneSession = session("a");
        WebSocketSession roomTwoSession = session("b");
        registry.join("room-1", roomOneSession);
        registry.join("room-2", roomTwoSession);

        assertTrue(registry.othersInRoom("room-1", session("c")).size() == 1);
        assertTrue(registry.othersInRoom("room-1", session("c")).contains(roomOneSession));
    }

    @Test
    void leavingRemovesTheSessionFromTheRoom() {
        VideoSignalSessionRegistry registry = new VideoSignalSessionRegistry();
        WebSocketSession candidateSession = session("a");
        WebSocketSession recruiterSession = session("b");
        registry.join("room-1", candidateSession);
        registry.join("room-1", recruiterSession);

        registry.leave("room-1", recruiterSession);

        assertTrue(registry.othersInRoom("room-1", candidateSession).isEmpty());
    }

    @Test
    void leavingARoomThatWasNeverJoinedIsANoOp() {
        VideoSignalSessionRegistry registry = new VideoSignalSessionRegistry();

        registry.leave("room-1", session("a"));

        assertTrue(registry.othersInRoom("room-1", session("b")).isEmpty());
    }
}
