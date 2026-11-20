package com.debate.game.service;

import com.debate.game.exception.InvalidRoomCodeException;
import com.debate.game.exception.InvalidStartConditionsException;
import com.debate.game.exception.NotEnoughPlayersException;
import com.debate.game.exception.RoomFullException;
import com.debate.game.exception.RoleFullException;
import com.debate.game.exception.RoleRequiredException;
import com.debate.game.exception.AlreadyStartedException;
import com.debate.game.exception.PlayerNotInRoomException;
import com.debate.game.exception.RoomCodeGenerationException;
import com.debate.game.validation.NicknameValidator;
import com.debate.game.validation.RoomSettingsPolicy;
import com.debate.game.dto.RoomEnterResponse;
import com.debate.game.dto.RoomSettingsUpdateRequest;
import com.debate.game.dto.websocket.CardPayload;
import com.debate.game.exception.NotHostException;
import com.debate.game.entity.Player;
import com.debate.game.entity.ProcurementItem;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.entity.Room;
import com.debate.game.entity.SituationCard;
import com.debate.game.entity.enums.DebateRole;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.ProcurementItemRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.Ingame.GameStartedEvent;
import com.debate.game.Ingame.RoomGameState;
import com.debate.game.entity.enums.Stance;
import com.debate.game.repository.SituationCardRepository;

import com.debate.game.dto.websocket.RoomStatePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import com.debate.game.realtime.LobbyChangedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final NicknameValidator nicknameValidator;
    private final RoomCodeGenerator roomCodeGenerator;

    private static final int MAX_CODE_ATTEMPTS = 5;

    // Setter injection preserves the existing four-argument constructor used by
    // R01-R16 tests. Spring injects this in production; direct unit instances
    // have no transport side effects unless explicitly configured.
    private ApplicationEventPublisher lobbyEventPublisher;
    private ProcurementItemRepository procurementItemRepository;
    private SituationCardRepository situationCardRepository;

    @Autowired
    public void setLobbyEventPublisher(ApplicationEventPublisher publisher) {
        this.lobbyEventPublisher = publisher;
    }
    @Autowired 
    public void setProcurementItemRepository(ProcurementItemRepository repository){
        this.procurementItemRepository = repository;
    }
    @Autowired
    public void setSituationCardRepository(SituationCardRepository repository) {
        this.situationCardRepository = repository;
    }

    private void lobbyChanged(String roomCode, Long leftPlayerId, boolean roomClosed) {
        if (lobbyEventPublisher != null) {
            lobbyEventPublisher.publishEvent(new LobbyChangedEvent(roomCode, leftPlayerId, roomClosed));
        }
    }

    // R02: create a room and its Host player atomically.
    // R03: pre-check and 5 retries; database UNIQUE handles rare concurrent collisions.
    // R13 persists five settings; R14 validates them through RoomSettingsPolicy.
    @Transactional
    public RoomEnterResponse createRoom(String nickname, Integer maxCardCount, Integer turnTimeLimit) {
        // Validate before any database write, reusing the tested R01 rules.
        String normalizedName = nicknameValidator.normalizeAndValidate(nickname);
        int cards = RoomSettingsPolicy.initialCardsPerItem(maxCardCount);
        int minutes = RoomSettingsPolicy.initialItemMinutes(turnTimeLimit);

        // Try another code if it already exists. The DB UNIQUE constraint is the
        // final safeguard against concurrent requests choosing the same code.
        String roomCode = null;
        for (int i = 0; i < MAX_CODE_ATTEMPTS; i++) {
            String candidate = roomCodeGenerator.generate();
            if (!roomRepository.existsByRoomCode(candidate)) {
                roomCode = candidate;
                break;
            }
        }
        if (roomCode == null) {
            throw new RoomCodeGenerationException();
        }

        Room room = new Room();
        room.setRoomCode(roomCode);
        room.setMaxCardCount(cards);
        room.setTurnTimeLimit(minutes);
        // Defaults follow the existing frontend mock: 3 items, 300 seconds/item,
        // 3 cards/item; speech 120s and rebuttal 60s.
        room.setMaxItems(RoomSettingsPolicy.DEFAULT_MAX_ITEMS);
        room.setSpeechSeconds(RoomSettingsPolicy.DEFAULT_SPEECH_SECONDS);
        room.setRebuttalSeconds(RoomSettingsPolicy.DEFAULT_REBUTTAL_SECONDS);
        roomRepository.save(room);

        Player host = new Player();
        host.setRoom(room);
        host.setPlayerName(normalizedName);
        // Host permission is independent of lobby role and per-item stance.
        host.setRole(LobbyRole.NONE);
        host.setReady(false);
        playerRepository.save(host);

        // The same @Transactional boundary commits room, player and host reference together.
        room.setHostPlayer(host);
        roomRepository.save(room); // update host_player_id (also explicit for detached repository mocks)
        lobbyChanged(roomCode, null, false);
        return new RoomEnterResponse(roomCode, host.getId().toString());
    }

    // R05: serialize joins to the same room using a database row lock.
    // The lock stays held until this transaction commits, including player insert.
    @Transactional
    public RoomEnterResponse joinRoom(String roomCode, String playerName) {
        // Reuse R01 nickname rules, and accept lowercase room codes from manual input.
        String normalizedName = nicknameValidator.normalizeAndValidate(playerName);
        String normalizedCode = roomCode == null ? "" : roomCode.strip().toUpperCase(Locale.ROOT);
        if (!normalizedCode.matches("[A-Z0-9]{6}")) {
            throw new InvalidRoomCodeException("방을 찾을 수 없습니다.");
        }

        Room room = roomRepository.findByRoomCodeForUpdate(normalizedCode)
                .orElseThrow(() -> new InvalidRoomCodeException("방을 찾을 수 없습니다."));

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new AlreadyStartedException();
        }
        if (playerRepository.countByRoom(room) >= 3) {
            throw new RoomFullException("방이 가득 찼습니다.");
        }

        Player player = new Player();
        player.setRoom(room);
        player.setPlayerName(normalizedName);
        player.setRole(LobbyRole.NONE);
        player.setReady(false);
        playerRepository.save(player);

        // RoomEnterResponse uses String playerId (same as frontend/types.ts Player.id).
        lobbyChanged(room.getRoomCode(), null, false);
        return new RoomEnterResponse(room.getRoomCode(), player.getId().toString());
    }

    // R07/R08: leave a waiting room while holding the same lock as joinRoom.
    // Guest: remove only that player. Host: close the entire room with its members.
    @Transactional
    public void leaveRoom(String roomCode, Long playerId) {
        String normalizedCode = roomCode == null ? "" : roomCode.strip().toUpperCase(Locale.ROOT);
        if (!normalizedCode.matches("[A-Z0-9]{6}")) {
            throw new InvalidRoomCodeException("방을 찾을 수 없습니다.");
        }
        if (playerId == null || playerId <= 0) {
            throw new PlayerNotInRoomException();
        }

        Room room = roomRepository.findByRoomCodeForUpdate(normalizedCode)
                .orElseThrow(() -> new InvalidRoomCodeException("방을 찾을 수 없습니다."));
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new AlreadyStartedException();
        }
        Player player = playerRepository.findById(playerId)
                .orElseThrow(PlayerNotInRoomException::new);
        if (player.getRoom() == null || !Objects.equals(player.getRoom().getId(), room.getId())) {
            throw new PlayerNotInRoomException();
        }

        if (room.getHostPlayer() != null &&
                Objects.equals(room.getHostPlayer().getId(), player.getId())) {
            // R08: room.host_player_id -> player.id and player.room_id -> room.id
            // form a FK cycle. Clear and flush the Host reference BEFORE deleting players.
            room.setHostPlayer(null);
            roomRepository.saveAndFlush(room);

            // Max three players in a WAITING room. Delete them before deleting the room.
            List<Player> members = playerRepository.findByRoom(room);
            playerRepository.deleteAll(members);
            playerRepository.flush();
            roomRepository.delete(room);
            lobbyChanged(room.getRoomCode(), null, true);
            return;
        }

        // R07: a normal guest only frees their own slot.
        playerRepository.delete(player);
        lobbyChanged(room.getRoomCode(), playerId, false);
    }
    

    // R06: read-only lobby snapshot. Reused later by the WebSocket ROOM_STATE mapper.
    @Transactional(readOnly = true)
    public com.debate.game.dto.LobbySnapshotResponse getLobby(String roomCode) {
        String normalizedCode = roomCode == null ? "" : roomCode.strip().toUpperCase(Locale.ROOT);
        if (!normalizedCode.matches("[A-Z0-9]{6}")) {
            throw new InvalidRoomCodeException("방을 찾을 수 없습니다.");
        }
        Room room = roomRepository.findByRoomCode(normalizedCode)
                .orElseThrow(() -> new InvalidRoomCodeException("방을 찾을 수 없습니다."));
        java.util.List<com.debate.game.dto.LobbySnapshotResponse.LobbyPlayer> players =
                playerRepository.findByRoom(room).stream()
                        .sorted(java.util.Comparator.comparing(Player::getId))
                        .map(p -> new com.debate.game.dto.LobbySnapshotResponse.LobbyPlayer(
                                p.getId().toString(), p.getPlayerName(),
                                p.getRole() == null ? LobbyRole.NONE.name() : p.getRole().name(),
                                p.isReady(),
                                room.getHostPlayer() != null &&
                                        java.util.Objects.equals(room.getHostPlayer().getId(), p.getId())))
                        .toList();
        // Frontend itemSeconds is seconds; the existing DB column is minutes.
        return new com.debate.game.dto.LobbySnapshotResponse(
                room.getRoomCode(),
                room.getHostPlayer() == null ? null : room.getHostPlayer().getId().toString(),
                room.getStatus() == RoomStatus.WAITING ? "LOBBY" :
                        room.getStatus() == RoomStatus.FINISHED ? "RESULT" : "VOTING",
                players,
                new com.debate.game.dto.LobbySnapshotResponse.LobbySettings(
                        room.getMaxItems(), room.getTurnTimeLimit() * 60,
                        room.getMaxCardCount(), room.getSpeechSeconds(),
                        room.getRebuttalSeconds()),
                    currentItemSnapshot(room)
                );
    }
    //통신 간, 참가자 검증 조회
    @Transactional(readOnly = true)
    public Optional<com.debate.game.dto.LobbySnapshotResponse> getLobbyForPlayer(
            String roomCode,
            String playerId
    ) {
        String normalizedCode = roomCode == null
                ? ""
                : roomCode.strip().toUpperCase(Locale.ROOT);

        if (!normalizedCode.matches("[A-Z0-9]{6}") || playerId == null) {
            return Optional.empty();
        }

        final Long parsedPlayerId;
        try {
            parsedPlayerId = Long.valueOf(playerId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        Room room = roomRepository.findByRoomCode(normalizedCode).orElse(null);
        if (room == null) {
            return Optional.empty();
        }

        Player player = playerRepository.findById(parsedPlayerId).orElse(null);
        if (player == null
                || player.getRoom() == null
                || !Objects.equals(player.getRoom().getId(), room.getId())) {
            return Optional.empty();
        }

        return Optional.of(toLobbySnapshot(room));
    }

    private com.debate.game.dto.LobbySnapshotResponse toLobbySnapshot(Room room) {
        java.util.List<com.debate.game.dto.LobbySnapshotResponse.LobbyPlayer> players =
                playerRepository.findByRoom(room).stream()
                        .sorted(java.util.Comparator.comparing(Player::getId))
                        .map(p -> new com.debate.game.dto.LobbySnapshotResponse.LobbyPlayer(
                                p.getId().toString(),
                                p.getPlayerName(),
                                p.getRole() == null ? LobbyRole.NONE.name() : p.getRole().name(),
                                p.isReady(),
                                room.getHostPlayer() != null
                                        && Objects.equals(room.getHostPlayer().getId(), p.getId())
                        ))
                        .toList();

        return new com.debate.game.dto.LobbySnapshotResponse(
                room.getRoomCode(),
                room.getHostPlayer() == null
                        ? null : room.getHostPlayer().getId().toString(),
                room.getStatus() == RoomStatus.WAITING ? "LOBBY"
                        : room.getStatus() == RoomStatus.FINISHED ? "RESULT" : "VOTING",
                players,
                new com.debate.game.dto.LobbySnapshotResponse.LobbySettings(
                        room.getMaxItems(),
                        room.getTurnTimeLimit() * 60,
                        room.getMaxCardCount(),
                        room.getSpeechSeconds(),
                        room.getRebuttalSeconds()
                ),
                currentItemSnapshot(room)
        );
    }

    private com.debate.game.dto.LobbySnapshotResponse.ItemSnapshot currentItemSnapshot(Room room) {
    var item = room.getCurrentProcurementItem();
    if (item == null || room.getStatus() != RoomStatus.IN_PROGRESS) {
        return null;
        }

    return new com.debate.game.dto.LobbySnapshotResponse.ItemSnapshot(
            1,
            item.getItemName(),
            item.getDescription()
        );
    }

    // R13/R14: validate and persist five settings in a WAITING lobby.
    // R15: check actual room membership and the room's Host reference under the room lock.
    // The REST body playerId is NOT authenticated; W-stage must supply a trusted caller ID.
    @Transactional
    public void updateSettings(String roomCode, Long playerId, RoomSettingsUpdateRequest settings) {
        if (settings == null) {
            throw new IllegalArgumentException("설정 요청이 필요합니다.");
        }
        // Validate ALL inputs before locking or writing anything to the database.
        int cards = RoomSettingsPolicy.cardsPerItem(settings.cardsPerItem());
        int speech = RoomSettingsPolicy.speechSeconds(settings.speechSeconds());
        int rebuttal = RoomSettingsPolicy.rebuttalSeconds(settings.rebuttalSeconds());
        int item = RoomSettingsPolicy.itemSeconds(settings.itemSeconds());
        int items = RoomSettingsPolicy.maxItems(settings.maxItems());
        String normalizedCode = roomCode == null ? "" : roomCode.strip().toUpperCase(Locale.ROOT);
        if (!normalizedCode.matches("[A-Z0-9]{6}")) {
            throw new InvalidRoomCodeException("방을 찾을 수 없습니다.");
        }
        if (playerId == null || playerId <= 0) {
            throw new PlayerNotInRoomException();
        }

        // Same room -> players locking order used by R05/R10/R11.
        Room room = roomRepository.findByRoomCodeForUpdate(normalizedCode)
                .orElseThrow(() -> new InvalidRoomCodeException("방을 찾을 수 없습니다."));
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new AlreadyStartedException();
        }
        // R15: distinguish a nonexistent/cross-room player (404) from a
        // member who does not own Host permission (403). Read after locking Room
        // to retain the same lock order used by join/leave/role/ready changes.
        Long memberRoomId = playerRepository.findRoomIdByPlayerId(playerId)
                .orElseThrow(PlayerNotInRoomException::new);
        if (!Objects.equals(room.getId(), memberRoomId)) {
            throw new PlayerNotInRoomException();
        }
        // Host permission is independent of NONE/GUEST/MODERATOR lobby roles.
        if (room.getHostPlayer() == null ||
                !Objects.equals(room.getHostPlayer().getId(), playerId)) {
            throw new NotHostException();
        }

        room.setMaxCardCount(cards);
        room.setSpeechSeconds(speech);
        room.setRebuttalSeconds(rebuttal);
        room.setTurnTimeLimit(item / 60); // Legacy column stores minutes.
        room.setMaxItems(items);

        // Frontend mock clears everyone's Ready after UPDATE_SETTINGS.
        for (Player member : playerRepository.findByRoomForUpdate(room)) {
            member.setReady(false);
        }
        lobbyChanged(room.getRoomCode(), null, false);
    }

    // R17: frontend UPDATE_SETTINGS sends only the three editable fields.
    // Preserve speechSeconds/rebuttalSeconds without a read-then-write race.
    @Transactional
    public void updateSocketSettings(String roomCode, Long playerId, Integer maxItems,
                                     Integer itemSeconds, Integer cardsPerItem) {
        int validatedItems = RoomSettingsPolicy.maxItems(maxItems);
        int validatedSeconds = RoomSettingsPolicy.itemSeconds(itemSeconds);
        int validatedCards = RoomSettingsPolicy.cardsPerItem(cardsPerItem);
        String code = roomCode == null ? "" : roomCode.strip().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9]{6}")) throw new InvalidRoomCodeException("방을 찾을 수 없습니다.");
        if (playerId == null || playerId <= 0) throw new PlayerNotInRoomException();

        Room room = roomRepository.findByRoomCodeForUpdate(code)
                .orElseThrow(() -> new InvalidRoomCodeException("방을 찾을 수 없습니다."));
        if (room.getStatus() != RoomStatus.WAITING) throw new AlreadyStartedException();
        Long memberRoomId = playerRepository.findRoomIdByPlayerId(playerId)
                .orElseThrow(PlayerNotInRoomException::new);
        if (!Objects.equals(room.getId(), memberRoomId)) throw new PlayerNotInRoomException();
        if (room.getHostPlayer() == null ||
                !Objects.equals(room.getHostPlayer().getId(), playerId)) throw new NotHostException();

        room.setMaxItems(validatedItems);
        room.setTurnTimeLimit(validatedSeconds / 60);
        room.setMaxCardCount(validatedCards);
        for (Player member : playerRepository.findByRoomForUpdate(room)) member.setReady(false);
        lobbyChanged(room.getRoomCode(), null, false);
    }

    // R16: validate a Host-initiated start request WITHOUT starting the game.
    // G03 must call this inside its own start transaction (same room lock) before
    // creating the session, so conditions cannot change between check and start.
    @Transactional
    public void validateStartConditions(String roomCode, Long playerId) {
        // 저장소 조회 전에 요청자 ID부터 검증해야 합니다.
        if (playerId == null || playerId <= 0) {
            throw new PlayerNotInRoomException();
        }

        String normalizedCode = roomCode == null
                ? ""
                : roomCode.strip().toUpperCase(Locale.ROOT);
        if (!normalizedCode.matches("[A-Z0-9]{6}")) {
            throw new InvalidRoomCodeException("방을 찾을 수 없습니다.");
        }

        Room room = roomRepository.findByRoomCodeForUpdate(normalizedCode)
                .orElseThrow(() -> new InvalidRoomCodeException("방을 찾을 수 없습니다."));
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new AlreadyStartedException();
        }

        List<Player> members = playerRepository.findByRoomForUpdate(room);

                boolean callerIsMember = members.stream()
                .anyMatch(p -> Objects.equals(p.getId(), playerId));
        if (!callerIsMember) {
            throw new PlayerNotInRoomException();
        }

        if (room.getHostPlayer() == null
                || !Objects.equals(room.getHostPlayer().getId(), playerId)) {
            throw new NotHostException("방장만 게임 시작을 요청할 수 있습니다.");
        }

        if (members.size() < 3) {
            throw new NotEnoughPlayersException();
        }
        if (members.size() != 3) {
            throw new InvalidStartConditionsException(
                    "INVALID_PLAYER_COUNT",
                    "시작하려면 참가자가 정확히 3명이어야 합니다.");
        }

        long moderators = members.stream()
                .filter(p -> p.getRole() == LobbyRole.MODERATOR)
                .count();
        long guests = members.stream()
                .filter(p -> p.getRole() == LobbyRole.GUEST)
                .count();

        if (moderators != 1 || guests != 2) {
            throw new InvalidStartConditionsException(
                    "INVALID_ROLE_COMPOSITION",
                    "사회자 1명과 객원 2명이 역할을 선택해야 합니다.");
        }

        if (members.stream().anyMatch(p -> !p.isReady())) {
            throw new InvalidStartConditionsException(
                    "NOT_ALL_READY",
                    "모든 참가자가 Ready 상태여야 합니다.");
        }
    }

    @Transactional
    public void startGame(String roomCode, Long playerId) {
        String normalizedCode = roomCode == null
                ? ""
                : roomCode.strip().toUpperCase(Locale.ROOT);

        // 동일 트랜잭션 안에서 Host·인원·역할·Ready를 검증합니다.
        validateStartConditions(normalizedCode, playerId);

        Room room = roomRepository.findByRoomCodeForUpdate(normalizedCode)
                .orElseThrow(() -> new InvalidRoomCodeException("방을 찾을 수 없습니다."));

        

        List<ProcurementItem> catalog = procurementItemRepository.findAll();
        if (catalog.isEmpty()) {
            throw new IllegalStateException("시작할 수 있는 물품이 없습니다.");
        }

        // 카탈로그에서 설정된 개수만큼 중복 없이 무작위 선택
        List<ProcurementItem> shuffledItems = new java.util.ArrayList<>(catalog);
        java.util.Collections.shuffle(shuffledItems);

        int itemCount = Math.min(room.getMaxItems(), shuffledItems.size());
        List<ProcurementItem> selectedItems =
                new java.util.ArrayList<>(shuffledItems.subList(0, itemCount));

        ProcurementItem firstItem = selectedItems.get(0);

        // RoomGameState가 사용할 전체 물품 목록을 만들고,
        // 각 물품에 속한 상황 카드도 함께 생성합니다.
        List<RoomStatePayload.ItemPayload> gameItems = new java.util.ArrayList<>();

        for (int i = 0; i < selectedItems.size(); i++) {
            ProcurementItem item = selectedItems.get(i);

            createInitialSituationCards(room, item);

            gameItems.add(new RoomStatePayload.ItemPayload(
                    i + 1,
                    item.getItemName(),
                    item.getDescription()
            ));
        }

        room.setCurrentProcurementItem(firstItem);
        room.setCurrentTopic(firstItem.getTopicCategory());
        room.setCurrentTurnRole(null);

        List<Player> members = playerRepository.findByRoomForUpdate(room);
        for (Player player : members) {
            player.setStance(null);
        }

        room.setStatus(RoomStatus.IN_PROGRESS);

        List<RoomGameState.PlayerState> gamePlayers = members.stream()
                .map(player -> new RoomGameState.PlayerState(
                        player.getId(),
                        player.getPlayerName(),
                        player.getRole(),
                        player.isReady(),
                        room.getHostPlayer() != null
                                && Objects.equals(room.getHostPlayer().getId(), player.getId())
                ))
                .toList();
        List<Long> procurementItemIds = selectedItems.stream()
                .map(ProcurementItem::getId)
                .toList();

        RoomGameState gameState = new RoomGameState(
                room.getRoomCode(),
                gameItems,
                procurementItemIds,
                new RoomStatePayload.RoomSettingsPayload(
                        room.getMaxItems(),
                        room.getTurnTimeLimit() * 60,
                        room.getMaxCardCount()
                ),
                gamePlayers,
                room.getSpeechSeconds(),
                room.getRebuttalSeconds()
        );

        // DB 트랜잭션이 커밋된 경우에만 AFTER_COMMIT listener가 Store에 넣습니다.
        if (lobbyEventPublisher != null) {
            lobbyEventPublisher.publishEvent(new GameStartedEvent(gameState));
        }
    }

    @Transactional
    public void updateCurrentProcurementItem(String roomCode, Long procurementItemId) {
        String code = roomCode == null
                ? ""
                : roomCode.strip().toUpperCase(Locale.ROOT);

        if (!code.matches("[A-Z0-9]{6}") || procurementItemId == null) {
            throw new IllegalArgumentException("방 또는 물품 정보가 올바르지 않습니다.");
        }

        Room room = roomRepository.findByRoomCodeForUpdate(code)
                .orElseThrow(() -> new InvalidRoomCodeException("방을 찾을 수 없습니다."));

        ProcurementItem item = procurementItemRepository.findById(procurementItemId)
                .orElseThrow(() -> new IllegalArgumentException("물품을 찾을 수 없습니다."));

        room.setCurrentProcurementItem(item);
        room.setCurrentTopic(item.getTopicCategory());
        room.setCurrentTurnRole(null);
    }

    @Transactional
    public void finishGame(String roomCode) {
        String code = roomCode == null
                ? ""
                : roomCode.strip().toUpperCase(Locale.ROOT);

        if (!code.matches("[A-Z0-9]{6}")) {
            throw new InvalidRoomCodeException("방을 찾을 수 없습니다.");
        }

        Room room = roomRepository.findByRoomCodeForUpdate(code)
                .orElseThrow(() -> new InvalidRoomCodeException("방을 찾을 수 없습니다."));

        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            throw new IllegalStateException("현재 진행 중인 방이 아닙니다.");
        }

        room.setStatus(RoomStatus.FINISHED);
        room.setCurrentTurnRole(null);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<CardPayload>> dealCardsForGuests(
            String roomCode,
            Map<Long, Stance> guestStances
    ) {
        String code = roomCode == null
                ? ""
                : roomCode.strip().toUpperCase(Locale.ROOT);

        if (!code.matches("[A-Z0-9]{6}")) {
            throw new InvalidRoomCodeException("방을 찾을 수 없습니다.");
        }
        if (situationCardRepository == null) {
            throw new IllegalStateException("상황 카드 저장소가 설정되지 않았습니다.");
        }

        Room room = roomRepository.findByRoomCode(code)
                .orElseThrow(() -> new InvalidRoomCodeException("방을 찾을 수 없습니다."));
        ProcurementItem currentItem = room.getCurrentProcurementItem();
        if (currentItem == null) {
            throw new IllegalStateException("현재 진행 중인 물품이 설정되지 않았습니다.");
        }

        int cardsPerGuest = room.getMaxCardCount();
        Map<Long, List<CardPayload>> handsByPlayer = new LinkedHashMap<>();

        for (Map.Entry<Long, Stance> entry : guestStances.entrySet()) {
            Long playerId = entry.getKey();
            Stance stance = entry.getValue();

            if (playerId == null || stance == null) {
                throw new IllegalArgumentException("객원의 진영 정보가 올바르지 않습니다.");
            }

            DebateRole side = DebateRole.valueOf(stance.name());
            List<SituationCard> available =
                    situationCardRepository
                            .findByRoom_IdAndProcurementItem_IdAndOwnerRoleAndIsUsedFalseOrderByIdAsc(
                                    room.getId(),
                                    currentItem.getId(),
                                    side
                            );

            if (available.size() < cardsPerGuest) {
                throw new IllegalStateException(
                        stance + " 진영에 지급할 카드가 부족합니다."
                );
            }

            List<CardPayload> hand = new ArrayList<>();
            for (SituationCard card : available.subList(0, cardsPerGuest)) {
                hand.add(new CardPayload(
                        card.getId(),
                        card.getKeyword(),
                        card.getKeyword() + " 카드",
                        card.getContent(),
                        false
                ));
            }

            handsByPlayer.put(playerId, hand);
        }

        return handsByPlayer;
    }

    private Room lockWaitingRoom(String roomCode) {
        String normalizedCode = roomCode == null
                ? ""
                : roomCode.strip().toUpperCase(Locale.ROOT);

        if (!normalizedCode.matches("[A-Z0-9]{6}")) {
            throw new InvalidRoomCodeException("방을 찾을 수 없습니다.");
        }

        Room room = roomRepository.findByRoomCodeForUpdate(normalizedCode)
                .orElseThrow(() -> new InvalidRoomCodeException("방을 찾을 수 없습니다."));

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new AlreadyStartedException();
        }

        return room;
    }

    private void requireValidStart(Room room, List<Player> members, Long playerId) {
        if (playerId == null || playerId <= 0) {
            throw new PlayerNotInRoomException();
        }

        boolean callerIsMember = members.stream()
                .anyMatch(player -> Objects.equals(player.getId(), playerId));
        if (!callerIsMember) {
            throw new PlayerNotInRoomException();
        }

        if (room.getHostPlayer() == null
                || !Objects.equals(room.getHostPlayer().getId(), playerId)) {
            throw new NotHostException("방장만 게임 시작을 요청할 수 있습니다.");
        }

        if (members.size() < 3) {
            throw new NotEnoughPlayersException();
        }
        if (members.size() != 3) {
            throw new InvalidStartConditionsException(
                    "INVALID_PLAYER_COUNT",
                    "시작하려면 참가자가 정확히 3명이어야 합니다.");
        }

        long moderators = members.stream()
                .filter(player -> player.getRole() == LobbyRole.MODERATOR)
                .count();
        long guests = members.stream()
                .filter(player -> player.getRole() == LobbyRole.GUEST)
                .count();

        if (moderators != 1 || guests != 2) {
            throw new InvalidStartConditionsException(
                    "INVALID_ROLE_COMPOSITION",
                    "사회자 1명과 객원 2명이 역할을 선택해야 합니다.");
        }

        if (members.stream().anyMatch(player -> !player.isReady())) {
            throw new InvalidStartConditionsException(
                    "NOT_ALL_READY",
                    "모든 참가자가 Ready 상태여야 합니다.");
        }
    }
    private void createInitialSituationCards(Room room, ProcurementItem item) {
        if (situationCardRepository == null) {
            throw new IllegalStateException("상황 카드 저장소가 설정되지 않았습니다.");
        }

        int cardsPerSide = room.getMaxCardCount();

        for (int i = 1; i <= cardsPerSide; i++) {
            saveSituationCard(room, item, DebateRole.PRO, i);
            saveSituationCard(room, item, DebateRole.CON, i);
        }
}

private void saveSituationCard(
        Room room,
        ProcurementItem item,
        DebateRole side,
        int index
) {
    SituationCard card = new SituationCard();
    card.setRoom(room);
    card.setProcurementItem(item);
    card.setOwnerRole(side);
    card.setKeyword(side.name() + " 키워드 " + index);
    card.setContent(
            item.getItemName() + "에 관한 " + side.name() +
            " 측 상황 카드 " + index
    );
    situationCardRepository.save(card);
}

    // R09/R10: lobby assignment (NONE/GUEST/MODERATOR) with 1 moderator / 2 guests.
    // Temporarily exposed as REST /api/rooms/{roomCode}/role until WebSocket SET_ROLE is implemented.
    // R12: a successful SET_ROLE (even reselecting the same role) clears Ready,
    // while a ROLE_FULL rejection leaves the original role/Ready/stance intact.
    // setReady below is independent of role selection.
    @Transactional
    public void selectRole(Long playerId, String role) {
        if (playerId == null || playerId <= 0) {
            throw new IllegalArgumentException("존재하지 않는 플레이어입니다.");
        }

        LobbyRole selectedRole;
        try {
            selectedRole = LobbyRole.valueOf(role.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("로비 역할은 NONE, GUEST, MODERATOR 중에서 선택해야 합니다.");
        }

        // First fetch only the room ID; do not load the Player entity before locking.
        // MySQL REPEATABLE READ may otherwise retain a stale role snapshot while waiting.
        Long roomId = playerRepository.findRoomIdByPlayerId(playerId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플레이어입니다."));

        // Same room-row lock as R05 join / R07 leave. Concurrent SET_ROLE requests
        // for the same room are serialized until the enclosing transaction commits.
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new IllegalStateException("참가 중인 방을 찾을 수 없습니다."));
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new IllegalStateException("대기 중인 로비에서만 역할을 변경할 수 있습니다.");
        }

        // Locking read sees current committed roles even when the earlier room-ID
        // lookup established a REPEATABLE READ snapshot. At most 3 players per room.
        List<Player> members = playerRepository.findByRoomForUpdate(room);
        Player player = members.stream()
                .filter(p -> Objects.equals(p.getId(), playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("해당 방의 참가자가 아닙니다."));

        int limit = switch (selectedRole) {
            case MODERATOR -> 1;
            case GUEST -> 2;
            case NONE -> Integer.MAX_VALUE;
        };
        long occupiedByOthers = members.stream()
                .filter(p -> !Objects.equals(p.getId(), playerId))
                .filter(p -> selectedRole == p.getRole())
                .count();
        if (occupiedByOthers >= limit) {
            // No mutation on failure: original role / Ready / stance remain intact.
            throw new RoleFullException();
        }

        // Per-item PRO/CON choices are never assigned here.
        player.setRole(selectedRole);
        player.setStance(null);
        player.setReady(false);
        lobbyChanged(room.getRoomCode(), null, false);
    }
    // R11: separate Ready/Ready-cancel action. Reuse R05/R10 room-then-player
    // locking order to serialize SET_READY with joins, leaves and role changes.
    // WebSocket will call this method on SET_READY; temporary REST is for Swagger.
    @Transactional
    public void setReady(String roomCode, Long playerId, boolean ready) {
        String normalizedCode = roomCode == null ? "" : roomCode.strip().toUpperCase(Locale.ROOT);
        if (!normalizedCode.matches("[A-Z0-9]{6}")) {
            throw new InvalidRoomCodeException("방을 찾을 수 없습니다.");
        }
        if (playerId == null || playerId <= 0) {
            throw new PlayerNotInRoomException();
        }

        // Acquire room lock FIRST, just like join/leave/SET_ROLE.
        Room room = roomRepository.findByRoomCodeForUpdate(normalizedCode)
                .orElseThrow(() -> new InvalidRoomCodeException("방을 찾을 수 없습니다."));
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new AlreadyStartedException();
        }

        // A locking read avoids stale role/member counts on MySQL REPEATABLE READ.
        List<Player> members = playerRepository.findByRoomForUpdate(room);
        Player player = members.stream()
                .filter(p -> Objects.equals(p.getId(), playerId))
                .findFirst()
                .orElseThrow(PlayerNotInRoomException::new);

        if (ready) {
            // R11 policy update (confirmed): Ready represents individual willingness.
            // One or two players may Ready; requiring 3 belongs to R16 Start validation.
            if (player.getRole() == null || player.getRole() == LobbyRole.NONE) {
                throw new RoleRequiredException();
            }
        }

        // Explicit assignment supports both Ready and Ready cancellation. Repeating
        // the same value is harmless. Game start/AI generation belongs to R16/G03.
        player.setReady(ready);
        lobbyChanged(room.getRoomCode(), null, false);
    }
}
