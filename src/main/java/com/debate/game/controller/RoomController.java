// RoomController.java
package com.debate.game.controller;

import com.debate.game.dto.RoleSelectRequest;
import com.debate.game.dto.RoomCreateRequest;
import com.debate.game.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    // 1. 방 만들기 버튼 클릭 시[cite: 1]
    @PostMapping
    public ResponseEntity<String> createRoom(@RequestBody RoomCreateRequest request) {
        String roomCode = roomService.createRoom(request.getMaxCardCount(), request.getTurnTimeLimit());
        return ResponseEntity.ok(roomCode);
    }

    // 2. 방 Code 입력 후 Join 클릭 시[cite: 1]
    @PostMapping("/{roomCode}/join")
    public ResponseEntity<Long> joinRoom(@PathVariable String roomCode, @RequestParam String playerName) {
        Long playerId = roomService.joinRoom(roomCode, playerName);
        return ResponseEntity.ok(playerId); // 부여받은 고유 Player ID 반환
    }

    // 3. 누른 사람 역할군에 맞는 맵핑 (준비 완료)[cite: 1]
    @PostMapping("/ready")
    public ResponseEntity<Void> selectRole(@RequestBody RoleSelectRequest request) {
        roomService.selectRole(request.getPlayerId(), request.getRole());
        return ResponseEntity.ok().build();
    }
}