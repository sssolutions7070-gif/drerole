package com.jobera.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.jobera.ws.NotificationWebSocketHandler;
import com.jobera.ws.TokenHandshakeInterceptor;
import com.jobera.ws.VideoSignalHandshakeInterceptor;
import com.jobera.ws.VideoSignalWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationHandler;
    private final TokenHandshakeInterceptor tokenHandshakeInterceptor;
    private final VideoSignalWebSocketHandler videoSignalHandler;
    private final VideoSignalHandshakeInterceptor videoSignalHandshakeInterceptor;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(NotificationWebSocketHandler notificationHandler,
                           TokenHandshakeInterceptor tokenHandshakeInterceptor,
                           VideoSignalWebSocketHandler videoSignalHandler,
                           VideoSignalHandshakeInterceptor videoSignalHandshakeInterceptor,
                           @Value("${app.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*}") String[] allowedOriginPatterns) {
        this.notificationHandler = notificationHandler;
        this.tokenHandshakeInterceptor = tokenHandshakeInterceptor;
        this.videoSignalHandler = videoSignalHandler;
        this.videoSignalHandshakeInterceptor = videoSignalHandshakeInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationHandler, "/ws/notifications")
            .addInterceptors(tokenHandshakeInterceptor)
            .setAllowedOriginPatterns(allowedOriginPatterns);
        registry.addHandler(videoSignalHandler, "/ws/video-interview")
            .addInterceptors(videoSignalHandshakeInterceptor)
            .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
