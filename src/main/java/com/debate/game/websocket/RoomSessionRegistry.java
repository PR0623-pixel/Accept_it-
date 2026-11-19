package com.debate.game.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RoomSessionRegistry {

    private final ConcurrentMap<String, ConcurrentMap<String, WebSocketSession>> rooms =
            new ConcurrentHashMap<>();

    public void register(
            String roomCode,
            String playerId,
            WebSocketSession session
    ) {
        ConcurrentMap<String, WebSocketSession> roomSessions =
                rooms.computeIfAbsent(roomCode, ignored -> new ConcurrentHashMap<>());

        WebSocketSession previous = roomSessions.put(playerId, session);

        if (previous != null && previous != session && previous.isOpen()) {
            try {
                previous.close();
            } catch (IOException ignored) {
                // 기존 연결 종료 실패는 새 연결 등록에 영향을 주지 않는다.
            }
        }
    }

    public void unregister(
            String roomCode,
            String playerId,
            WebSocketSession session
    ) {
        ConcurrentMap<String, WebSocketSession> roomSessions = rooms.get(roomCode);
        if (roomSessions == null) {
            return;
        }

        roomSessions.remove(playerId, session);

        if (roomSessions.isEmpty()) {
            rooms.remove(roomCode, roomSessions);
        }
    }

    public void broadcast(String roomCode, String jsonMessage) {
        ConcurrentMap<String, WebSocketSession> roomSessions = rooms.get(roomCode);
        if (roomSessions == null) {
            return;
        }

        roomSessions.forEach((playerId, session) -> {
            try {
                // 같은 세션에 여러 스레드가 동시에 쓰지 않도록 직렬화한다.
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(jsonMessage));
                    }
                }
            } catch (IOException e) {
                unregister(roomCode, playerId, session);
            }
        });
    }

    public Map<String, WebSocketSession> sessionsIn(String roomCode) {
        ConcurrentMap<String, WebSocketSession> roomSessions = rooms.get(roomCode);
        return roomSessions == null ? Map.of() : Map.copyOf(roomSessions);
    }
}