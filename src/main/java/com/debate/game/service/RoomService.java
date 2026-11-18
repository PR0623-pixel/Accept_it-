package com.debate.game.service;

import com.debate.game.exception.InvalidRoomCodeException;
import com.debate.game.exception.RoomFullException;
import com.debate.game.exception.AlreadyStartedException;
import com.debate.game.exception.RoomCodeGenerationException;
import com.debate.game.validation.NicknameValidator;
import com.debate.game.dto.RoomEnterResponse;
import com.debate.game.entity.Player;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.DebateRole;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;


@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final NicknameValidator nicknameValidator;
    private final RoomCodeGenerator roomCodeGenerator;

    private static final int MAX_CODE_ATTEMPTS = 5;

    // R02: create a room and its Host player atomically.
    // R03: pre-check and 5 retries; database UNIQUE handles rare concurrent collisions.
    // Game settings policy is R13/R14.
    @Transactional
    public RoomEnterResponse createRoom(String nickname, Integer maxCardCount, Integer turnTimeLimit) {
        // Validate before any database write, reusing the tested R01 rules.
        String normalizedName = nicknameValidator.normalizeAndValidate(nickname);
        int cards = maxCardCount == null ? 3 : maxCardCount;
        int minutes = turnTimeLimit == null ? 5 : turnTimeLimit;
        if (cards <= 0 || minutes <= 0) {
            throw new IllegalArgumentException("방 설정은 양수여야 합니다.");
        }

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
        roomRepository.save(room);

        Player host = new Player();
        host.setRoom(room);
        host.setPlayerName(normalizedName);
        // Leave role null (= no lobby role selected); R09 will introduce LobbyRole.NONE.
        // The Host permission is stored separately on Room, never in DebateRole.
        host.setReady(false);
        playerRepository.save(host);

        // The same @Transactional boundary commits room, player and host reference together.
        room.setHostPlayer(host);
        roomRepository.save(room); // update host_player_id (also explicit for detached repository mocks)
        return new RoomEnterResponse(roomCode, host.getId().toString());
    }

    // R04: frontend-compatible room join. Concurrent capacity safety is R05.
    @Transactional
    public RoomEnterResponse joinRoom(String roomCode, String playerName) {
        // Reuse R01 nickname rules, and accept lowercase room codes from manual input.
        String normalizedName = nicknameValidator.normalizeAndValidate(playerName);
        String normalizedCode = roomCode == null ? "" : roomCode.strip().toUpperCase(Locale.ROOT);
        if (!normalizedCode.matches("[A-Z0-9]{6}")) {
            throw new InvalidRoomCodeException("방을 찾을 수 없습니다.");
        }

        Room room = roomRepository.findByRoomCode(normalizedCode)
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
        player.setReady(false);
        playerRepository.save(player);

        // RoomEnterResponse uses String playerId (same as frontend/types.ts Player.id).
        return new RoomEnterResponse(room.getRoomCode(), player.getId().toString());
    }

    // 3. 역할군 선정 (찬성, 반대, 사회자)
    @Transactional
    public void selectRole(Long playerId, String role) {

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 플레이어입니다."));

        Room room = player.getRoom();

        // String을 DebateRole enum으로 변환
        DebateRole selectedRole;

        try {
            selectedRole = DebateRole.valueOf(
                    role.trim().toUpperCase(java.util.Locale.ROOT)
            );
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "올바르지 않은 역할입니다: " + role);
        }

        // 동일 방의 다른 플레이어가 역할을 선택했는지 확인
        boolean isRoleTaken = playerRepository.findByRoom(room)
                .stream()
                .anyMatch(p ->
                        !p.getId().equals(playerId)
                                && p.getRole() == selectedRole
                );

        if (isRoleTaken) {
            throw new IllegalStateException(
                    "이미 다른 유저가 선택한 역할입니다.");
        }

        player.setRole(selectedRole);
        player.setReady(true);
    }

}