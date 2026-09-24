package com.jobera.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import com.jobera.entity.User;
import com.jobera.service.AuthenticationService;
import com.jobera.service.InterviewRoundService;

@ExtendWith(MockitoExtension.class)
class VideoSignalHandshakeInterceptorTest {

    @Mock AuthenticationService authenticationService;
    @Mock InterviewRoundService interviewRoundService;
    @Mock ServerHttpRequest request;
    @Mock ServerHttpResponse response;
    @Mock WebSocketHandler wsHandler;

    VideoSignalHandshakeInterceptor interceptor;

    private void requestUri(String uri) {
        when(request.getURI()).thenReturn(URI.create(uri));
    }

    @Test
    void aParticipantWithAValidTokenIsAdmitted() {
        interceptor = new VideoSignalHandshakeInterceptor(authenticationService, interviewRoundService);
        requestUri("ws://localhost:8081/ws/interview-round?token=good-token&roomId=room-1");
        User user = new User();
        user.setId(9L);
        when(authenticationService.validateToken("good-token")).thenReturn(Optional.of(user));
        when(interviewRoundService.isParticipant("room-1", 9L)).thenReturn(true);

        Map<String, Object> attributes = new HashMap<>();
        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertTrue(allowed);
        assertEquals(9L, attributes.get(NotificationWebSocketHandler.USER_ID_ATTRIBUTE));
        assertEquals("room-1", attributes.get(VideoSignalHandshakeInterceptor.ROOM_ID_ATTRIBUTE));
    }

    @Test
    void someoneNotOnTheApplicationIsRejectedEvenWithAValidToken() {
        interceptor = new VideoSignalHandshakeInterceptor(authenticationService, interviewRoundService);
        requestUri("ws://localhost:8081/ws/interview-round?token=good-token&roomId=room-1");
        User user = new User();
        user.setId(9L);
        when(authenticationService.validateToken("good-token")).thenReturn(Optional.of(user));
        when(interviewRoundService.isParticipant("room-1", 9L)).thenReturn(false);

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        assertFalse(allowed);
    }

    @Test
    void anInvalidTokenIsRejected() {
        interceptor = new VideoSignalHandshakeInterceptor(authenticationService, interviewRoundService);
        requestUri("ws://localhost:8081/ws/interview-round?token=bad-token&roomId=room-1");
        when(authenticationService.validateToken("bad-token")).thenReturn(Optional.empty());

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        assertFalse(allowed);
    }

    @Test
    void aMissingRoomIdIsRejectedWithoutEvenCheckingTheToken() {
        interceptor = new VideoSignalHandshakeInterceptor(authenticationService, interviewRoundService);
        requestUri("ws://localhost:8081/ws/interview-round?token=good-token");

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        assertFalse(allowed);
    }

    @Test
    void aMissingTokenIsRejected() {
        interceptor = new VideoSignalHandshakeInterceptor(authenticationService, interviewRoundService);
        requestUri("ws://localhost:8081/ws/interview-round?roomId=room-1");

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        assertFalse(allowed);
    }
}
