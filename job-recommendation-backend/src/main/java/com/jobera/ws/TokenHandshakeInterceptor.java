package com.jobera.ws;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.jobera.entity.User;
import com.jobera.service.AuthenticationService;

/**
 * Authenticates the WebSocket handshake using the same bearer tokens as the REST API. Browsers'
 * native WebSocket API cannot set an Authorization header, so the token travels as a query
 * parameter here instead; the connection is rejected outright if it doesn't resolve to a user.
 */
@Component
public class TokenHandshakeInterceptor implements HandshakeInterceptor {

    private final AuthenticationService authenticationService;

    public TokenHandshakeInterceptor(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
            .getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            return false;
        }

        Optional<User> user = authenticationService.validateToken(token);
        if (user.isEmpty()) {
            return false;
        }

        attributes.put(NotificationWebSocketHandler.USER_ID_ATTRIBUTE, user.get().getId());
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, @Nullable Exception exception) {
        // Nothing to do.
    }
}
