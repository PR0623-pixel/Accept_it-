package com.debate.game.controller;

import com.debate.game.service.GameStartService;
import com.debate.game.exception.GameStartPendingException;
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

    // Legacy unsafe route: no Host identity. Disabled until G03 provides an atomic start.
    @Operation(summary = "구형 게임 시작 API (비활성화)", description = "Host 인증 없는 레거시 경로는 R16부터 차단합니다. G03에서 실제 시작 트랜잭션을 구현합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "방 ID 또는 요청 오류",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "GAME_START_NOT_IMPLEMENTED: G03 전까지 구형 시작 API 차단",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{roomId}/start")
    public ResponseEntity<String> startGame(@PathVariable Long roomId) {
        // Must not bypass R16 Host/role/Ready checks or generate dummy cards.
        throw new GameStartPendingException();
    }
}