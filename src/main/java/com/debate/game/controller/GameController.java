package com.debate.game.controller;

import com.debate.game.service.GameStartService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.debate.game.dto.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Game", description = "게임 시작")
@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {

    private final GameStartService gameStartService;

    // 인게임 진입: 역할 선정이 끝난 후 방장이 게임 시작 호출
    @Operation(summary = "게임 시작", description = "참가자 3명의 준비 상태를 검증하고 방을 시작합니다. 현재는 카드 10장을 더미로 생성합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "방 ID 또는 요청 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "준비 미완료 또는 이미 시작된 게임",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{roomId}/start")
    public ResponseEntity<String> startGame(@PathVariable Long roomId) {
        gameStartService.tryStartGame(roomId);
        return ResponseEntity.ok("게임이 성공적으로 시작되었으며, 카드가 배급되었습니다.");
    }
}