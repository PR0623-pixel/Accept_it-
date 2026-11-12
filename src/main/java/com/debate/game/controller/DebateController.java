package com.debate.game.controller;

import com.debate.game.dto.ChainRequest;
import com.debate.game.dto.ChainResponse;
import com.debate.game.dto.DebateStateResponse;
import com.debate.game.dto.FinishDebateRequest;
import com.debate.game.dto.TurnRequest;
import com.debate.game.service.DebateService;
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

@Tag(name = "Debate", description = "발언, 카드 체인, 상태 조회 및 사회자 종료")
@RestController
@RequestMapping("/api/debate")
@RequiredArgsConstructor
public class DebateController {

    private final DebateService debateService;

    // 체인(카드 제시) 발동 시
    @Operation(summary = "카드 체인 제출", description = "현재 턴의 플레이어가 카드와 발언을 제출합니다. 점수/반응은 아직 더미 로직입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "유효하지 않은 발언, 카드 또는 플레이어",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "현재 차례가 아니거나 이미 사용된 카드",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/chain")
    public ResponseEntity<ChainResponse> submitChain(@RequestBody ChainRequest request) {
        ChainResponse response = debateService.evaluateChain(request);
        return ResponseEntity.ok(response);
    }

    // 현재 차례인 진영의 일반 발언을 저장하고 다음 차례의 토론 상태를 반환한다.
    @Operation(summary = "일반 발언 제출", description = "현재 차례인 PRO 또는 CON 플레이어의 일반 발언을 저장하고 다음 차례로 전환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 플레이어",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "발언 차례나 방 상태 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/turn")
    public ResponseEntity<DebateStateResponse> submitTurn(@RequestBody TurnRequest request) {
        return ResponseEntity.ok(
                debateService.submitSpeech(request.getRoomId(), request.getPlayerId(), request.getText()));
    }

    // 현재 방의 진행 상태와 시간순 토론 기록을 조회한다.
    @Operation(summary = "토론 상태 조회", description = "방 상태, 다음 발언 역할, 턴 제한 및 발언 로그를 ID 순서대로 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "존재하지 않는 방",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{roomId}/state")
    public ResponseEntity<DebateStateResponse> getState(@PathVariable Long roomId) {
        return ResponseEntity.ok(debateService.getState(roomId));
    }

    // 해당 방의 사회자 요청만 토론을 종료할 수 있다.
    @Operation(summary = "사회자 토론 종료", description = "해당 방의 MODERATOR만 진행 중 토론을 종료할 수 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 플레이어",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "권한 없음 또는 종료할 수 없는 방 상태",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{roomId}/finish")
    public ResponseEntity<DebateStateResponse> finishDebate(
            @PathVariable Long roomId, @RequestBody FinishDebateRequest request) {
        return ResponseEntity.ok(debateService.finishDebate(roomId, request.getPlayerId()));
    }
}