package com.debate.game.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Plain JSON WebSocket to match frontend/src/api/socket.ts (NOT STOMP).
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class LobbyWebSocketConfig implements WebSocketConfigurer {

    private final LobbyWebSocketHandler handler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/rooms/{roomCode}")
                .setAllowedOriginPatterns("*");

        // R17 is a development integration stage.
        // Lock down origins and replace unsigned playerId before deployment.
    }
}