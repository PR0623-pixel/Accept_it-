package com.debate.game.Ingame;

import com.debate.game.Ingame.enums.GamePhase;
import com.debate.game.dto.websocket.CardPayload;
import com.debate.game.dto.websocket.ChatMessagePayload;
import com.debate.game.dto.websocket.RoomStatePayload;
import com.debate.game.dto.websocket.RoomStatePayload.PendingRebuttalPayload;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
import com.debate.game.websocket.GameActionException;
import com.debate.game.validation.RoomSettingsPolicy;
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
    private final Map<Long, PlayerState> players = new LinkedHashMap<>();

    private RoomStatePayload.SpeechPayload speech;
    private Long lastSpeakerId;
    private RoomStatePayload.PendingRebuttalPayload pendingRebuttal;
    private final Map<Long, List<CardPayload>> hands = new LinkedHashMap<>();
    private final List<ChatMessagePayload> chatHistory = new ArrayList<>();
    private Long pendingRebuttalCardId;
    private RoomStatePayload.EffectPayload effect;
    private final RoomStatePayload.RoomSettingsPayload settings;
    private final List<RoomStatePayload.ItemResultPayload> results = new ArrayList<>();
    private final List<RoomStatePayload.ItemPayload> items;
    private final List<Long> procurementItemIds;
    private int currentItemIndex;
    private final int speechSeconds;
    private final int rebuttalSeconds;
 
    private long nextEffectId = 1;

    public RoomGameState(
            String roomCode,
            List<RoomStatePayload.ItemPayload> items,
            List<Long> procurementItemIds,
            RoomStatePayload.RoomSettingsPayload settings,
            List<PlayerState> initialPlayers
    ) {
        this(roomCode, items, procurementItemIds, settings, initialPlayers,
                RoomSettingsPolicy.DEFAULT_SPEECH_SECONDS,
                RoomSettingsPolicy.DEFAULT_REBUTTAL_SECONDS);
    }

    /** The speech/rebuttal limits are server-owned room settings, not WebSocket DTO fields. */
    public RoomGameState(
            String roomCode,
            List<RoomStatePayload.ItemPayload> items,
            List<Long> procurementItemIds,
            RoomStatePayload.RoomSettingsPayload settings,
            List<PlayerState> initialPlayers,
            int speechSeconds,
            int rebuttalSeconds
    ) {
        if (items == null || items.isEmpty()
        || procurementItemIds == null
        || procurementItemIds.size() != items.size()) {
            throw new IllegalArgumentException("게임을 시작할 물품이 없습니다.");
        }

        this.roomCode = roomCode;
        this.phase = GamePhase.VOTING;
        this.items = List.copyOf(items);
        this.procurementItemIds = List.copyOf(procurementItemIds);
        this.currentItemIndex = 0;
        this.currentItem = this.items.get(currentItemIndex);
        this.settings = settings;
        if (speechSeconds < 1 || rebuttalSeconds < 1) {
            throw new IllegalArgumentException("발언 및 반론 시간은 1초 이상이어야 합니다.");
        }
        this.speechSeconds = speechSeconds;
        this.rebuttalSeconds = rebuttalSeconds;

        for (PlayerState player : initialPlayers) {
            players.put(player.getId(), player);
            hands.put(player.getId(), new ArrayList<>());
        }

    }

    public long nextEffectId() {
        return nextEffectId++;
    }

    /** Applies one vote and advances the phase once both guests have voted. */
    public synchronized GamePhase vote(Long playerId, Stance stance, long now) {
        if (phase != GamePhase.VOTING) {
            throw new GameActionException("INVALID", "현재 투표할 수 있는 단계가 아닙니다.");
        }
        if (stance == null) {
            throw new GameActionException("INVALID", "찬성 또는 반대를 선택해야 합니다.");
        }

        PlayerState player = players.get(playerId);
        if (player == null) {
            throw new GameActionException("PLAYER_NOT_IN_ROOM", "방의 참가자가 아닙니다.");
        }
        if (player.getRole() != LobbyRole.GUEST) {
            throw new GameActionException("INVALID", "객원만 투표할 수 있습니다.");
        }
        if (player.isHasVoted()) {
            throw new GameActionException("ALREADY_VOTED", "이미 투표했습니다.");
        }

        List<PlayerState> guests = players.values().stream()
                .filter(member -> member.getRole() == LobbyRole.GUEST)
                .toList();
        if (guests.size() != 2) {
            throw new GameActionException("INVALID", "투표 가능한 객원 구성이 올바르지 않습니다.");
        }

        player.setStance(stance);
        player.setHasVoted(true);
        if (guests.stream().anyMatch(member -> !member.isHasVoted())) {
            return phase;
        }

        Stance firstStance = guests.get(0).getStance();
        Stance secondStance = guests.get(1).getStance();
        if (firstStance == secondStance) {
            phase = GamePhase.MODERATOR_DECISION;
            itemEndsAt = null;
        } else {
            phase = GamePhase.DEBATE;
            itemEndsAt = now + settings.itemSeconds() * 1_000L;
        }
        return phase;
    }

    public synchronized List<CardPayload> handFor(Long playerId) {
        return List.copyOf(hands.getOrDefault(playerId, List.of()));
    }

    public synchronized List<ChatMessagePayload> chatHistorySnapshot() {
        return List.copyOf(chatHistory);
    }

    public synchronized void giveCards(Long playerId, List<CardPayload> cards) {
        PlayerState player = players.get(playerId);
        if (player == null) {
            throw new GameActionException("PLAYER_NOT_IN_ROOM", "방의 참가자가 아닙니다.");
        }

        hands.put(playerId, new ArrayList<>(cards));
    }

    public synchronized Long procurementItemIdAt(int index) {
        if (index < 0 || index >= procurementItemIds.size()) {
            throw new IllegalArgumentException("물품 인덱스가 올바르지 않습니다.");
        }
        return procurementItemIds.get(index);
    }

    public synchronized CardPayload useCard(Long playerId, Long cardId) {
        List<CardPayload> hand = hands.get(playerId);
        if (hand == null) {
            throw new GameActionException("INVALID", "손패가 없습니다.");
        }

        CardPayload card = hand.stream()
                .filter(item -> item.cardId() == cardId)
                .findFirst()
                .orElseThrow(() -> new GameActionException(
                        "CARD_NOT_IN_HAND",
                        "본인 손패에 없는 카드입니다."
                ));

        if (card.used()) {
            throw new GameActionException("CARD_ALREADY_USED", "이미 사용한 카드입니다.");
        }

        int index = hand.indexOf(card);
        hand.set(index, new CardPayload(
                card.cardId(),
                card.keyword(),
                card.title(),
                card.description(),
                true
        ));
        return hand.get(index);
    }

    /** Validate the room deadline before any persistent card mutation. */
    public synchronized void requireDebateOpenAt(long now) {
        if (phase != GamePhase.DEBATE) {
            throw new GameActionException("INVALID", "토론 중에만 수행할 수 있습니다.");
        }
        if (itemEndsAt != null && now >= itemEndsAt) {
            throw new GameActionException("TIME_EXPIRED", "토론 시간이 종료되었습니다.");
        }
    }

    public synchronized RoomStatePayload.SpeechPayload startSpeech(
        Long playerId,
        Long cardId,
        long now
) {
    requireDebateOpenAt(now);

    PlayerState player = players.get(playerId);
    if (player == null) {
        throw new GameActionException("PLAYER_NOT_IN_ROOM", "방의 참가자가 아닙니다.");
    }
    if (player.getRole() != LobbyRole.GUEST || player.getStance() == null) {
        throw new GameActionException("INVALID", "객원만 발언할 수 있습니다.");
    }
    if (speech != null || pendingRebuttal != null) {
        throw new GameActionException("FLOOR_BUSY", "다른 사람이 발언 중입니다.");
    }
    if (playerId.equals(lastSpeakerId)) {
        throw new GameActionException("CONSECUTIVE_SPEECH", "직전 발언자는 연속으로 발언할 수 없습니다.");
    }

    CardPayload card = useCard(playerId, cardId);
    speech = new RoomStatePayload.SpeechPayload(
            playerId.toString(),
            "SPEECH",
            now + speechSeconds * 1_000L,
            card.keyword()
    );
    lastSpeakerId = playerId;
    return speech;
}

    public synchronized void endSpeech(Long playerId) {
        if (speech == null || !playerId.toString().equals(speech.speakerId())) {
            throw new GameActionException("NOT_YOUR_TURN", "현재 발언권을 가진 사람만 종료할 수 있습니다.");
        }
        speech = null;
    }

    /**
     * Server clock is authoritative. The item deadline takes precedence over an
     * active speech (including an approved rebuttal). A speech deadline only
     * releases the floor; a pending moderator rebuttal decision is preserved.
     */
    public enum TimerOutcome {
        NONE, SPEECH_EXPIRED, DEBATE_FINISHED
    }

    public synchronized TimerOutcome expireTimers(long now) {
        if (phase != GamePhase.DEBATE) {
            return TimerOutcome.NONE;
        }
        if (itemEndsAt != null && now >= itemEndsAt) {
            return finishDebateIfActive() ? TimerOutcome.DEBATE_FINISHED : TimerOutcome.NONE;
        }
        if (speech != null && now >= speech.endsAt()) {
            speech = null;
            return TimerOutcome.SPEECH_EXPIRED;
        }
        return TimerOutcome.NONE;
    }

    /**
     * Manual moderator action. Returns false on an already-completed debate so
     * duplicate END_DEBATE frames do not emit duplicate state changes.
     */
    public synchronized boolean endDebate(Long moderatorId) {
        PlayerState moderator = players.get(moderatorId);
        if (moderator == null) {
            throw new GameActionException("PLAYER_NOT_IN_ROOM", "방의 참가자가 아닙니다.");
        }
        if (moderator.getRole() != LobbyRole.MODERATOR) {
            throw new GameActionException("INVALID", "사회자만 토론을 종료할 수 있습니다.");
        }
        if (phase != GamePhase.DEBATE && phase != GamePhase.MODERATOR_DECISION) {
            throw new GameActionException("INVALID", "토론 중에만 종료할 수 있습니다.");
        }

        return finishDebateIfActive();
    }

    /**
     * Single DEBATE -> MODERATOR_DECISION transition for every termination source.
     * S02 (deadline) and S03 (card exhaustion) must validate their trigger
     * before calling this method. Does not implement a timer/card detector.
     *
     * @return true only for the first successful transition
     */
    public synchronized boolean finishDebateIfActive() {
        if (phase != GamePhase.DEBATE) {
            return false;
        }

        // A pending rebuttal has not been approved. Cancel it and return its
        // reserved card before discarding the pending request.
        if (pendingRebuttal != null && pendingRebuttalCardId != null) {
            restoreCard(Long.valueOf(pendingRebuttal.requesterId()), pendingRebuttalCardId);
        }

        phase = GamePhase.MODERATOR_DECISION;
        itemEndsAt = null;
        speech = null;
        lastSpeakerId = null;
        pendingRebuttal = null;
        pendingRebuttalCardId = null;
        return true;
    }

    public synchronized ChatMessagePayload sendChat(Long playerId, String rawText, long now) {
        if (phase == GamePhase.DEBATE) {
            requireDebateOpenAt(now);
        }
        PlayerState player = players.get(playerId);
        if (player == null) {
            throw new GameActionException("PLAYER_NOT_IN_ROOM", "방의 참가자가 아닙니다.");
        }

        String text = rawText == null ? "" : rawText.strip();
        if (text.isEmpty() || text.length() > 200) {
            throw new GameActionException("INVALID", "채팅은 1자 이상 200자 이하여야 합니다.");
        }

        String kind;
        if (player.getRole() == LobbyRole.MODERATOR) {
            kind = "MODERATOR";
        } else {
            if (speech == null || !playerId.toString().equals(speech.speakerId())
                    || now >= speech.endsAt()) {
                throw new GameActionException("NOT_YOUR_TURN", "발언권이 있을 때만 채팅할 수 있습니다.");
            }
            kind = speech.kind();
        }

        ChatMessagePayload chat = new ChatMessagePayload(
                Long.toString(System.nanoTime()),
                kind,
                playerId.toString(),
                player.getNickname(),
                player.getStance() == null ? null : player.getStance().name(),
                text,
                speech != null && kind.equals(speech.kind()) ? speech.keyword() : null,
                now
        );
        chatHistory.add(chat);
        return chat;
    }

    public synchronized PendingRebuttalPayload requestRebuttal(
            Long requesterId,
            Long cardId,
            long now
    ) {
        requireDebateOpenAt(now);

        PlayerState requester = players.get(requesterId);
        if (requester == null) {
            throw new GameActionException("PLAYER_NOT_IN_ROOM", "방의 참가자가 아닙니다.");
        }
        if (requester.getRole() != LobbyRole.GUEST || requester.getStance() == null) {
            throw new GameActionException("INVALID", "진영을 선택한 객원만 반론을 요청할 수 있습니다.");
        }
        if (speech == null || !"SPEECH".equals(speech.kind())
                || requesterId.toString().equals(speech.speakerId())
                || now >= speech.endsAt()) {
            throw new GameActionException("NO_TARGET", "반론할 상대의 발언이 없습니다.");
        }
        if (pendingRebuttal != null) {
            throw new GameActionException("PENDING", "이미 처리 대기 중인 반론 요청이 있습니다.");
        }

        CardPayload card = useCard(requesterId, cardId);

        pendingRebuttal = new PendingRebuttalPayload(
                requesterId.toString(),
                speech.speakerId(),
                card.keyword()
        );
        pendingRebuttalCardId = cardId;

        return pendingRebuttal;
    }

    public synchronized void decideRebuttal(
            Long moderatorId,
            boolean approve,
            long now
    ) {
        PlayerState moderator = players.get(moderatorId);
        if (moderator == null) {
            throw new GameActionException("PLAYER_NOT_IN_ROOM", "방의 참가자가 아닙니다.");
        }
        if (moderator.getRole() != LobbyRole.MODERATOR) {
            throw new GameActionException("INVALID", "사회자만 반론을 결정할 수 있습니다.");
        }
        if (phase != GamePhase.DEBATE || pendingRebuttal == null) {
            throw new GameActionException("INVALID", "결정할 반론 요청이 없습니다.");
        }
        requireDebateOpenAt(now);

        Long requesterId = Long.valueOf(pendingRebuttal.requesterId());
        String keyword = pendingRebuttal.keyword();

        if (approve) {
            speech = new RoomStatePayload.SpeechPayload(
                    requesterId.toString(),
                    "REBUTTAL",
                    now + rebuttalSeconds * 1_000L,
                    keyword
            );
            lastSpeakerId = requesterId;
            effect = new RoomStatePayload.EffectPayload(
                    nextEffectId(),
                    "REBUTTAL_APPROVED",
                    requesterId.toString()
            );
        } else {
            restoreCard(requesterId, pendingRebuttalCardId);
            effect = new RoomStatePayload.EffectPayload(
                    nextEffectId(),
                    "REBUTTAL_DENIED",
                    requesterId.toString()
            );
            // 묵살 시 기존 발언자의 speech는 유지합니다.
        }

        pendingRebuttal = null;
        pendingRebuttalCardId = null;
    }
    
    public synchronized void restoreCard(Long playerId, Long cardId) {
        if (playerId == null || cardId == null) {
            return;
        }

        List<CardPayload> hand = hands.get(playerId);
        if (hand == null) {
            return;
        }

        for (int i = 0; i < hand.size(); i++) {
            CardPayload card = hand.get(i);
            if (card.cardId() == cardId) {
                hand.set(i, new CardPayload(
                        card.cardId(),
                        card.keyword(),
                        card.title(),
                        card.description(),
                        false
                ));
                return;
            }
        }
    }

    public synchronized int validateItemDecisionAndGetNextIndex(Long moderatorId) {
        if (phase != GamePhase.MODERATOR_DECISION) {
            throw new GameActionException("INVALID", "사회자 결정 단계가 아닙니다.");
        }

        PlayerState moderator = players.get(moderatorId);
        if (moderator == null) {
            throw new GameActionException(
                    "PLAYER_NOT_IN_ROOM",
                    "방의 참가자가 아닙니다."
            );
        }

        if (moderator.getRole() != LobbyRole.MODERATOR) {
            throw new GameActionException(
                    "INVALID",
                    "사회자만 물품을 결정할 수 있습니다."
            );
        }

        if (currentItem == null) {
            throw new GameActionException("INVALID", "현재 결정할 물품이 없습니다.");
        }

        return currentItemIndex + 1 < items.size()
                ? currentItemIndex + 1
                : -1;
    }

    public synchronized void decideItem(
        Long moderatorId,
        boolean accept,
        long now
    ) {
        validateItemDecisionAndGetNextIndex(moderatorId);

        results.add(new RoomStatePayload.ItemResultPayload(
                currentItem.name(),
                accept
        ));

        effect = new RoomStatePayload.EffectPayload(
                nextEffectId(),
                accept ? "ITEM_ACCEPTED" : "ITEM_REJECTED",
                null
        );

        if (currentItemIndex + 1 < items.size()) {
            // 다음 물품이 남아 있음
            currentItemIndex++;
            currentItem = items.get(currentItemIndex);

            phase = GamePhase.VOTING;
            itemEndsAt = null;
            speech = null;
            lastSpeakerId = null;
            pendingRebuttal = null;
            pendingRebuttalCardId = null;

            chatHistory.clear();
            for (List<CardPayload> hand : hands.values()) {
                hand.clear();
            }

            for (PlayerState player : players.values()) {
                player.setStance(null);
                player.setHasVoted(false);
            }
        } else {
            // 모든 물품을 결정했음
            phase = GamePhase.RESULT;
            currentItem = null;
            itemEndsAt = null;
            speech = null;
            lastSpeakerId = null;
            pendingRebuttal = null;
            pendingRebuttalCardId = null;
            chatHistory.clear();
            for (List<CardPayload> hand : hands.values()) {
                hand.clear();
            }
        }
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
