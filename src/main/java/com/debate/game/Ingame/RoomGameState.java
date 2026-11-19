package com.debate.game.Ingame;

import com.debate.game.Ingame.enums.GamePhase;
import com.debate.game.dto.websocket.CardPayload;
import com.debate.game.dto.websocket.ChatMessagePayload;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class RoomGameState {

    private final String roomCode;
    private GamePhase phase;
    private RoomStatePayload.ItemPayload currentItem;
    private Long itemEndsAt;

    /** 이 방에 속한 참가자별 진행 상태 */
    private final Map<Long, PlayerState> players = new LinkedHashMap<>();

    private RoomStatePayload.SpeechPayload speech;
    private Long lastSpeakerId;
    private RoomStatePayload.PendingRebuttalPayload pendingRebuttal;
    private RoomStatePayload.EffectPayload effect;

    /** 방 전체 상태에 포함할 현재 게임의 결과 목록 */
    private final List<RoomStatePayload.ItemResultPayload> results = new ArrayList<>();

    /** 플레이어 ID별 손패. 각 연결에는 본인 손패만 보내야 합니다. */
    private final Map<Long, List<CardPayload>> hands = new LinkedHashMap<>();

    /** 현재 물품 동안의 채팅 기록. 다음 물품이 시작되면 초기화합니다. */
    private final List<ChatMessagePayload> chatHistory = new ArrayList<>();

    private long nextEffectId = 1;

    public RoomGameState(
            String roomCode,
            RoomStatePayload.ItemPayload firstItem,
            List<PlayerState> initialPlayers
    ) {
        this.roomCode = roomCode;
        this.phase = GamePhase.VOTING;
        this.currentItem = firstItem;

        for (PlayerState player : initialPlayers) {
            players.put(player.getId(), player);
            hands.put(player.getId(), new ArrayList<>());
        }
    }

    public long nextEffectId() {
        return nextEffectId++;
    }

    @Getter
    @Setter
    public static class PlayerState {
        private Long id;
        private String nickname;
        private LobbyRole role;
        private boolean ready;
        private Stance stance;
        private boolean hasVoted;
        private boolean isHost;

        public PlayerState(
                Long id,
                String nickname,
                LobbyRole role,
                boolean ready,
                boolean isHost
        ) {
            this.id = id;
            this.nickname = nickname;
            this.role = role;
            this.ready = ready;
            this.isHost = isHost;
        }
    }
}