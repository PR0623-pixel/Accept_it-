package com.debate.game.realtime;

import com.debate.game.Ingame.RoomGameState;
import com.debate.game.Ingame.RoomGameStateStore;
import com.debate.game.dto.LobbySnapshotResponse;
import com.debate.game.entity.enums.Stance;
import com.debate.game.exception.*;
import com.debate.game.service.RoomService;
import com.debate.game.websocket.GameActionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Minimal room-scoped JSON transport for lobby and in-game events; no STOMP. */
@Component
@RequiredArgsConstructor
public class LobbyWebSocketHandler extends TextWebSocketHandler {
    private final RoomService roomService;
    private final LobbyStateReader stateReader;
    private final LobbyRoomStateMapper stateMapper;
    private final IngameRoomStateMapper ingameStateMapper;
    private final RoomGameStateStore gameStateStore;
    private final ObjectMapper mapper;
    private final ConcurrentMap<String, ParticipantSocket> connections = new ConcurrentHashMap<>();

    private record ParticipantSocket(String roomCode, Long playerId, WebSocketSession socket) {}
    private record Envelope(String type, Object payload) {}

    @Override
    public void afterConnectionEstablished(WebSocketSession socket) throws Exception {
        URI uri = socket.getUri();
        String path = uri == null ? "" : uri.getPath();
        String prefix = "/ws/rooms/";
        String code = path.startsWith(prefix)
                ? path.substring(prefix.length()).strip().toUpperCase(Locale.ROOT) : "";
        if (!code.matches("[A-Z0-9]{6}")) {
            failAndClose(socket, "ROOM_NOT_FOUND", "방을 찾을 수 없습니다.");
            return;
        }
        String rawPlayerId = uri == null ? null : UriComponentsBuilder.fromUri(uri)
                .build().getQueryParams().getFirst("playerId");
        Long playerId;
        try {
            playerId = Long.parseLong(rawPlayerId);
            if (playerId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException | NullPointerException e) {
            failAndClose(socket, "PLAYER_NOT_IN_ROOM", "방의 참가자만 연결할 수 있습니다.");
            return;
        }

        LobbySnapshotResponse lobby;
        try {
            lobby = stateReader.read(code);
        } catch (InvalidRoomCodeException e) {
            failAndClose(socket, "ROOM_NOT_FOUND", "방을 찾을 수 없습니다.");
            return;
        }
        if (lobby.players().stream().noneMatch(p -> Objects.equals(p.id(), playerId.toString()))) {
            failAndClose(socket, "PLAYER_NOT_IN_ROOM", "방의 참가자만 연결할 수 있습니다.");
            return;
        }

        connections.put(socket.getId(), new ParticipantSocket(code, playerId, socket));
        RoomGameState gameState = gameStateStore.find(code).orElse(null);
        if (gameState == null) {
            send(socket, "ROOM_STATE", stateMapper.map(lobby));
            send(socket, "HAND", List.of());
            send(socket, "CHAT_HISTORY", List.of());
        } else {
            send(socket, "ROOM_STATE", ingameStateMapper.map(gameState, playerId));
            send(socket, "HAND", gameState.handFor(playerId));
            send(socket, "CHAT_HISTORY", gameState.chatHistorySnapshot());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) throws Exception {
        ParticipantSocket source = connections.get(socket.getId());
        if (source == null) {
            failAndClose(socket, "PLAYER_NOT_IN_ROOM", "등록되지 않은 연결입니다.");
            return;
        }
        try {
            JsonNode root = mapper.readTree(message.getPayload());
            if (!root.isObject() || !root.hasNonNull("type")
                    || !root.has("payload") || !root.path("payload").isObject()) {
                throw new IllegalArgumentException("메시지 형식이 올바르지 않습니다.");
            }
            JsonNode payload = root.get("payload");
            switch (root.get("type").asText()) {
                case "SET_ROLE" -> roomService.selectRole(source.playerId(), requiredText(payload, "role"));
                case "SET_READY" -> roomService.setReady(source.roomCode(), source.playerId(),
                        requiredBoolean(payload, "ready"));
                case "UPDATE_SETTINGS" -> roomService.updateSocketSettings(source.roomCode(), source.playerId(),
                        requiredInteger(payload, "maxItems"), requiredInteger(payload, "itemSeconds"),
                        requiredInteger(payload, "cardsPerItem"));
                case "START_GAME" -> roomService.startGame(source.roomCode(), source.playerId());
                case "VOTE" -> vote(source, requiredText(payload, "stance"));
                // case "USE_CARD" -> useCard(source, requiredLong(payload, "cardId"));
                case "START_SPEECH" -> startSpeech(source, requiredLong(payload, "cardId"));
                case "END_SPEECH" -> endSpeech(source);
                case "SEND_CHAT" -> sendChat(source, requiredText(payload, "text"));
                case "REQUEST_REBUTTAL" -> requestRebuttal(source, requiredLong(payload, "cardId"));
                case "DECIDE_REBUTTAL" -> decideRebuttal(source, requiredBoolean(payload, "approve"));
                case "DECIDE_ITEM" -> decideItem(source, requiredBoolean(payload, "accept"));
                default -> throw new IllegalArgumentException("현재 지원하지 않는 이벤트입니다.");
            }
        } catch (Exception e) {
            sendError(socket, codeOf(e), userMessage(e));
        }
    }

    private void vote(ParticipantSocket source, String rawStance) {
        final Stance stance;
        try {
            stance = Stance.valueOf(rawStance.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("stance는 PRO 또는 CON이어야 합니다.");
        }
        RoomGameState state = gameStateStore.find(source.roomCode())
                .orElseThrow(() -> new GameActionException("INVALID", "진행 중인 게임 상태를 찾을 수 없습니다."));
        state.vote(source.playerId(), stance, System.currentTimeMillis());
        broadcastRoomState(source.roomCode());
    }

    private RoomGameState gameState(ParticipantSocket source) {
    return gameStateStore.find(source.roomCode())
            .orElseThrow(() -> new GameActionException(
                    "INVALID",
                    "진행 중인 게임 상태를 찾을 수 없습니다."
            ));
    }

    private void startSpeech(ParticipantSocket source, Long cardId) {
        gameState(source).startSpeech(source.playerId(), cardId, System.currentTimeMillis());
        broadcastRoomState(source.roomCode());
    }

    private void endSpeech(ParticipantSocket source) {
        gameState(source).endSpeech(source.playerId());
        broadcastRoomState(source.roomCode());
    }

    private void sendChat(ParticipantSocket source, String text) {
        var chat = gameState(source).sendChat(source.playerId(), text, System.currentTimeMillis());
        for (ParticipantSocket connection : connections.values()) {
            if (source.roomCode().equals(connection.roomCode())) {
                sendQuietly(connection.socket(), "CHAT", chat);
            }
        }
    }
    private void requestRebuttal(ParticipantSocket source, Long cardId) {
        RoomGameState state = gameState(source);
        state.requestRebuttal(source.playerId(), cardId, System.currentTimeMillis());
        sendQuietly(source.socket(), "HAND", state.handFor(source.playerId()));
        broadcastRoomState(source.roomCode());
    }

    private void decideRebuttal(ParticipantSocket source, boolean approve) {
        RoomGameState state = gameState(source);
        state.decideRebuttal(source.playerId(), approve, System.currentTimeMillis());
        broadcastRoomState(source.roomCode());
    }

    private void useCard(ParticipantSocket source, Long cardId) {
        RoomGameState state = gameStateStore.find(source.roomCode())
                .orElseThrow(() -> new GameActionException(
                        "INVALID",
                        "진행 중인 게임 상태를 찾을 수 없습니다."
                ));

        state.useCard(source.playerId(), cardId);

        // 갱신된 손패는 카드 소유자에게만 보냅니다.
        sendQuietly(source.socket(), "HAND", state.handFor(source.playerId()));
    }

    private void decideItem(
        ParticipantSocket source,
        boolean accept
    ) {
        RoomGameState state = gameStateStore.find(source.roomCode())
                .orElseThrow(() -> new GameActionException(
                        "INVALID",
                        "진행 중인 게임 상태를 찾을 수 없습니다."
                ));

        state.decideItem(
                source.playerId(),
                accept,
                System.currentTimeMillis()
        );

        broadcastRoomState(source.roomCode());
    }

    private static String requiredText(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException(name + " 값이 필요합니다.");
        }
        return value.asText();
    }

    private static boolean requiredBoolean(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(name + "은 true/false여야 합니다.");
        }
        return value.asBoolean();
    }

    private static Integer requiredInteger(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(name + "은 정수여야 합니다.");
        }
        return value.intValue();
    }
    private static Long requiredLong(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() <= 0) {
            throw new IllegalArgumentException(name + "은 양의 정수여야 합니다.");
        }
        return value.longValue();
    }

    private static String codeOf(Exception ex) {
        if (ex instanceof GameActionException gameError) return gameError.getCode();
        if (ex instanceof NotHostException) return "NOT_HOST";
        if (ex instanceof RoleFullException) return "ROLE_FULL";
        if (ex instanceof RoleRequiredException) return "ROLE_REQUIRED";
        if (ex instanceof NotEnoughPlayersException) return "NOT_ENOUGH_PLAYERS";
        if (ex instanceof InvalidStartConditionsException startError) return startError.getCode();
        if (ex instanceof PlayerNotInRoomException) return "PLAYER_NOT_IN_ROOM";
        if (ex instanceof InvalidRoomCodeException) return "ROOM_NOT_FOUND";
        if (ex instanceof AlreadyStartedException) return "ALREADY_STARTED";
        return "INVALID";
    }

    private static String userMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "요청을 처리할 수 없습니다." : message;
    }

    public boolean hasRoomConnections(String roomCode) {
        return connections.values().stream().anyMatch(c -> c.roomCode().equals(roomCode));
    }

    /** Sends recipient-specific room snapshots, masking other votes while voting. */
    public void broadcastRoomState(String roomCode) {
        RoomGameState gameState = gameStateStore.find(roomCode).orElse(null);
        if (gameState != null) {
            for (ParticipantSocket connection : connections.values()) {
                if (roomCode.equals(connection.roomCode())) {
                    sendQuietly(connection.socket(), "ROOM_STATE",
                            ingameStateMapper.map(gameState, connection.playerId()));
                }
            }
            return;
        }
        if (!hasRoomConnections(roomCode)) return;
        LobbySnapshotResponse snapshot;
        try {
            snapshot = stateReader.read(roomCode);
        } catch (InvalidRoomCodeException e) {
            closeRoom(roomCode);
            return;
        }
        Map<String, Object> state = stateMapper.map(snapshot);
        for (ParticipantSocket connection : connections.values()) {
            if (roomCode.equals(connection.roomCode())) {
                sendQuietly(connection.socket(), "ROOM_STATE", state);
            }
        }
    }

    public void closePlayer(String roomCode, Long playerId) {
        for (Map.Entry<String, ParticipantSocket> entry : connections.entrySet()) {
            ParticipantSocket connection = entry.getValue();
            if (roomCode.equals(connection.roomCode()) && Objects.equals(playerId, connection.playerId())
                    && connections.remove(entry.getKey(), connection)) {
                sendError(connection.socket(), "PLAYER_NOT_IN_ROOM", "방에서 퇴장했습니다.");
                closeQuietly(connection.socket());
            }
        }
    }

    public void closeRoom(String roomCode) {
        gameStateStore.remove(roomCode);
        for (Map.Entry<String, ParticipantSocket> entry : connections.entrySet()) {
            ParticipantSocket connection = entry.getValue();
            if (roomCode.equals(connection.roomCode()) && connections.remove(entry.getKey(), connection)) {
                sendError(connection.socket(), "ROOM_NOT_FOUND", "방이 종료되었습니다.");
                closeQuietly(connection.socket());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
        connections.remove(socket.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession socket, Throwable error) {
        connections.remove(socket.getId());
        closeQuietly(socket);
    }

    private void failAndClose(WebSocketSession socket, String code, String message) {
        sendError(socket, code, message);
        closeQuietly(socket);
    }

    private void sendError(WebSocketSession socket, String code, String message) {
        sendQuietly(socket, "ERROR", Map.of("code", code, "message", message));
    }

    private void sendQuietly(WebSocketSession socket, String type, Object payload) {
        try {
            send(socket, type, payload);
        } catch (IOException e) {
            connections.remove(socket.getId());
            closeQuietly(socket);
        }
    }

    private void send(WebSocketSession socket, String type, Object payload) throws IOException {
        synchronized (socket) {
            if (socket.isOpen()) {
                socket.sendMessage(new TextMessage(mapper.writeValueAsString(new Envelope(type, payload))));
            }
        }
    }

    private void closeQuietly(WebSocketSession socket) {
        try {
            if (socket.isOpen()) socket.close();
        } catch (IOException ignored) {
        }
    }
}
