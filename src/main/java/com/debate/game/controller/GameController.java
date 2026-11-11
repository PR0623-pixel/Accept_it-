package com.debate.game.controller;

import com.debate.game.service.GameStartService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {

    private final GameStartService gameStartService;

    // 인게임 진입: 역할 선정이 끝난 후 방장이 게임 시작 호출
    @PostMapping("/{roomId}/start")
    public ResponseEntity<String> startGame(@PathVariable Long roomId) {
        gameStartService.tryStartGame(roomId);
        return ResponseEntity.ok("게임이 성공적으로 시작되었으며, 카드가 배급되었습니다.");
    }
}