package com.debate.game.websocket;

import com.debate.game.websocket.InitialSyncProvider;
import com.debate.game.dto.websocket.WebSocketMessage;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private static final String ROOM_CODE_ATTRIBUTE = "roomCode";
    private static final String PLAYER_ID_ATTRIBUTE = "playerId";

    private final RoomSessionRegistry sessionRegistry;
    private final InitialSyncProvider initialSyncProvider;
    private final ObjectMapper objectMapper;

    public RoomWebSocketHandler(RoomSessionRegistry sessionRegistry, InitialSyncProvider initialSyncProvider, ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.initialSyncProvider = initialSyncProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        String roomCode = extractRoomCode(session.getUri());
        String playerId = extractQueryParameter(session.getUri(), "playerId");

        if (isBlank(roomCode) || isBlank(playerId)) {
            sendError(session, "INVALID_CONNECTION",
                    "방 코드와 플레이어 ID가 필요합니다.");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        
        var sync = initialSyncProvider.load(roomCode, playerId);

        if (sync.isEmpty()){
            sendError(session, "ROOM_NOT_FOUND",
                    "방 또는 참가자를 찾을 수 없습니다.");
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        //검증 및 조회 성공 시에만 세션 등록
        session.getAttributes().put(ROOM_CODE_ATTRIBUTE, roomCode);
        session.getAttributes().put(PLAYER_ID_ATTRIBUTE, playerId);
        sessionRegistry.register(roomCode, playerId, session);

        //프론트 계약에 정해진 순서로 초기 상태 전달.
        send(session, new WebSocketMessage<>("ROOM_STATE", sync.get().roomState()));
        send(session, new WebSocketMessage<>("HAND", sync.get().hand()));
        send(session, new WebSocketMessage<>("CHAT_HISTORY", sync.get().chatHistory()));
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws IOException {
        // 다음 단계에서 메시지 봉투 파싱과 액션별 처리를 연결한다.
        sendError(session, "NOT_IMPLEMENTED",
                "WebSocket 액션 처리는 아직 구현되지 않았습니다.");
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            CloseStatus status
    ) {
        Object roomCode = session.getAttributes().get(ROOM_CODE_ATTRIBUTE);
        Object playerId = session.getAttributes().get(PLAYER_ID_ATTRIBUTE);

        if (roomCode instanceof String code && playerId instanceof String id) {
            sessionRegistry.unregister(code, id, session);
        }
    }

    private void sendError(
            WebSocketSession session,
            String code,
            String message
    ) throws IOException {
        String json = """
                {"type":"ERROR","payload":{"code":"%s","message":"%s"}}
                """.formatted(escapeJson(code), escapeJson(message)).trim();

        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        }
    }

    private String extractRoomCode(URI uri) {
        if (uri == null) {
            return null;
        }

        String path = uri.getPath();
        String prefix = "/ws/rooms/";

        if (path == null || !path.startsWith(prefix)) {
            return null;
        }

        String value = path.substring(prefix.length());
        int slash = value.indexOf('/');

        if (slash >= 0) {
            value = value.substring(0, slash);
        }

        return decode(value);
    }

    private String extractQueryParameter(URI uri, String name) {
        if (uri == null || uri.getRawQuery() == null) {
            return null;
        }

        for (String part : uri.getRawQuery().split("&")) {
            int equals = part.indexOf('=');
            if (equals < 0) {
                continue;
            }

            String key = decode(part.substring(0, equals));
            if (name.equals(key)) {
                return decode(part.substring(equals + 1));
            }
        }

        return null;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private void send(WebSocketSession session, Object message) throws IOException {
    String json = objectMapper.writeValueAsString(message);

    synchronized (session) {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(json));
            }
        }
    }
}