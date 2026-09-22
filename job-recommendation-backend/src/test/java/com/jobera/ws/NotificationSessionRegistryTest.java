package com.jobera.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class NotificationSessionRegistryTest {

    @Test
    void sessionsForAnUnknownUserIsEmpty() {
        NotificationSessionRegistry registry = new NotificationSessionRegistry();

        assertTrue(registry.sessionsFor(1L).isEmpty());
    }

    @Test
    void registeringAddsTheSessionAndUnregisteringRemovesIt() {
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        WebSocketSession session = mock(WebSocketSession.class);

        registry.register(1L, session);
        assertEquals(1, registry.sessionsFor(1L).size());
        assertTrue(registry.sessionsFor(1L).contains(session));

        registry.unregister(1L, session);
        assertTrue(registry.sessionsFor(1L).isEmpty());
        assertEquals(0, registry.connectedUserCount());
    }

    @Test
    void aUserCanHaveMultipleOpenTabs() {
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        WebSocketSession tab1 = mock(WebSocketSession.class);
        WebSocketSession tab2 = mock(WebSocketSession.class);

        registry.register(1L, tab1);
        registry.register(1L, tab2);

        assertEquals(2, registry.sessionsFor(1L).size());

        registry.unregister(1L, tab1);
        assertEquals(1, registry.sessionsFor(1L).size());
        assertTrue(registry.sessionsFor(1L).contains(tab2));
    }

    @Test
    void differentUsersDoNotSeeEachOthersSessions() {
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        WebSocketSession sessionForUser1 = mock(WebSocketSession.class);
        WebSocketSession sessionForUser2 = mock(WebSocketSession.class);

        registry.register(1L, sessionForUser1);
        registry.register(2L, sessionForUser2);

        assertEquals(1, registry.sessionsFor(1L).size());
        assertTrue(registry.sessionsFor(1L).contains(sessionForUser1));
        assertTrue(registry.sessionsFor(2L).contains(sessionForUser2));
        assertEquals(2, registry.connectedUserCount());
    }

    @Test
    void unregisteringASessionThatWasNeverRegisteredIsANoOp() {
        NotificationSessionRegistry registry = new NotificationSessionRegistry();
        WebSocketSession session = mock(WebSocketSession.class);

        registry.unregister(1L, session);

        assertTrue(registry.sessionsFor(1L).isEmpty());
    }
}
