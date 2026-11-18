package com.debate.game.service;

import com.debate.game.exception.InvalidRoomCodeException;
import com.debate.game.exception.RoomFullException;
import com.debate.game.validation.NicknameValidator;
import com.debate.game.dto.RoomEnterResponse;
import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.DebateRole;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final NicknameValidator nicknameValidator;

    // R02: create a room and its Host player atomically.
    // Code uniqueness/retries are left to R03; game settings policy is R13/R14.
    @Transactional
    public RoomEnterResponse createRoom(String nickname, Integer maxCardCount, Integer turnTimeLimit) {
        // Validate before any database write, reusing the tested R01 rules.
        String normalizedName = nicknameValidator.normalizeAndValidate(nickname);
        int cards = maxCardCount == null ? 3 : maxCardCount;
        int minutes = turnTimeLimit == null ? 5 : turnTimeLimit;
        if (cards <= 0 || minutes <= 0) {
            throw new IllegalArgumentException("방 설정은 양수여야 합니다.");
        }

        Room room = new Room();
        String roomCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
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

    // 2. 방 입장 (Join) 및 3명 인원 제한 체크
    @Transactional
    public Long joinRoom(String roomCode, String playerName) {
        // R01: 저장 전 서버에서 형식을 검증하고 앞뒤 공백을 정리한다.
        // TODO(R01): 같은 방 내 닉네임 중복 허용 여부는 팀 협의 후 확정.
        String normalizedName = nicknameValidator.normalizeAndValidate(playerName);

        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new InvalidRoomCodeException("존재하지 않는 방 코드입니다."));

        // 현재 방의 인원 수 확인 (3명 제한)
        long currentPlayerCount = playerRepository.countByRoom(room);
        if (currentPlayerCount >= 3) {
            throw new RoomFullException("방 인원이 초과되었습니다. (최대 3명)"); // 에러 발생
        }

        Player player = new Player();
        player.setRoom(room);
        player.setPlayerName(normalizedName);
        playerRepository.save(player);

        return player.getId();
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