package com.debate.game.service;

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

    // 1. 방 생성 (Host)
    @Transactional
    public String createRoom(int maxCardCount, int turnTimeLimit) {
        Room room = new Room();
        // 6자리 난수 코드 생성 (간단한 예시)
        String roomCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        room.setRoomCode(roomCode);
        room.setMaxCardCount(maxCardCount); // 1개의 쟁점에 대한 최대 카드 매수
        room.setTurnTimeLimit(turnTimeLimit); // 턴 시간 설정

        roomRepository.save(room);
        return roomCode;
    }

    // 2. 방 입장 (Join) 및 3명 인원 제한 체크
    @Transactional
    public Long joinRoom(String roomCode, String playerName) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 방 코드입니다."));

        // 현재 방의 인원 수 확인 (3명 제한)
        long currentPlayerCount = playerRepository.countByRoom(room);
        if (currentPlayerCount >= 3) {
            throw new IllegalStateException("방 인원이 초과되었습니다. (최대 3명)"); // 에러 발생
        }

        Player player = new Player();
        player.setRoom(room);
        player.setPlayerName(playerName);
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