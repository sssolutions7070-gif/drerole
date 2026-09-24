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
import com.jobera.service.InterviewRoundService;

/**
 * Authenticates an interview-round signaling connection (video + the shared code editor) the
 * same way TokenHandshakeInterceptor does (bearer token as a query param, since browsers can't
 * set a header on a WebSocket upgrade), then also checks the caller is actually one of the two
 * people on this round's application - a WebSocket handshake never passes through
 * AuthInterceptor/AccessPolicy, so that check has to happen here instead.
 */
@Component
public class VideoSignalHandshakeInterceptor implements HandshakeInterceptor {

    static final String ROOM_ID_ATTRIBUTE = "roomId";

    private final AuthenticationService authenticationService;
    private final InterviewRoundService interviewRoundService;

    public VideoSignalHandshakeInterceptor(AuthenticationService authenticationService,
                                            InterviewRoundService interviewRoundService) {
        this.authenticationService = authenticationService;
        this.interviewRoundService = interviewRoundService;
    }

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler, @NonNull Map<String, Object> attributes) {
        var query = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
        String token = query.getFirst("token");
        String roomId = query.getFirst("roomId");
        if (token == null || token.isBlank() || roomId == null || roomId.isBlank()) {
            return false;
        }

        Optional<User> user = authenticationService.validateToken(token);
        if (user.isEmpty() || !interviewRoundService.isParticipant(roomId, user.get().getId())) {
            return false;
        }

        attributes.put(NotificationWebSocketHandler.USER_ID_ATTRIBUTE, user.get().getId());
        attributes.put(ROOM_ID_ATTRIBUTE, roomId);
        return true;
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request, @NonNull ServerHttpResponse response,
                               @NonNull WebSocketHandler wsHandler, @Nullable Exception exception) {
        // Nothing to do.
    }
}
