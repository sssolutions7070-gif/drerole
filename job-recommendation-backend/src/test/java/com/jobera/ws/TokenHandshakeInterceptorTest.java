package com.jobera.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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

@ExtendWith(MockitoExtension.class)
class TokenHandshakeInterceptorTest {

    @Mock AuthenticationService authenticationService;
    @Mock ServerHttpRequest request;
    @Mock ServerHttpResponse response;
    @Mock WebSocketHandler wsHandler;

    TokenHandshakeInterceptor interceptor;

    private TokenHandshakeInterceptor newInterceptor() {
        return new TokenHandshakeInterceptor(authenticationService);
    }

    private void requestUri(String uri) {
        when(request.getURI()).thenReturn(URI.create(uri));
    }

    @Test
    void acceptsAValidTokenAndStoresTheUserId() {
        interceptor = newInterceptor();
        requestUri("ws://localhost:8081/ws/notifications?token=good-token");
        User user = new User();
        user.setId(9L);
        when(authenticationService.validateToken("good-token")).thenReturn(Optional.of(user));

        Map<String, Object> attributes = new HashMap<>();
        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertTrue(allowed);
        assertEquals(9L, attributes.get(NotificationWebSocketHandler.USER_ID_ATTRIBUTE));
    }

    @Test
    void rejectsAnInvalidToken() {
        interceptor = newInterceptor();
        requestUri("ws://localhost:8081/ws/notifications?token=bad-token");
        when(authenticationService.validateToken("bad-token")).thenReturn(Optional.empty());

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        assertFalse(allowed);
    }

    @Test
    void rejectsAMissingToken() {
        interceptor = newInterceptor();
        requestUri("ws://localhost:8081/ws/notifications");

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        assertFalse(allowed);
    }

    @Test
    void rejectsAnEmptyToken() {
        interceptor = newInterceptor();
        requestUri("ws://localhost:8081/ws/notifications?token=");

        boolean allowed = interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        assertFalse(allowed);
    }
}
