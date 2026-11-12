package com.debate.game.controller;

import com.debate.game.dto.ChainRequest;
import com.debate.game.dto.ChainResponse;
import com.debate.game.dto.DebateStateResponse;
import com.debate.game.dto.FinishDebateRequest;
import com.debate.game.dto.TurnRequest;
import com.debate.game.service.DebateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/debate")
@RequiredArgsConstructor
public class DebateController {

    private final DebateService debateService;

    // 체인(카드 제시) 발동 시
    @PostMapping("/chain")
    public ResponseEntity<ChainResponse> submitChain(@RequestBody ChainRequest request) {
        ChainResponse response = debateService.evaluateChain(request);
        return ResponseEntity.ok(response);
    }

    // 현재 차례인 진영의 일반 발언을 저장하고 다음 차례의 토론 상태를 반환한다.
    @PostMapping("/turn")
    public ResponseEntity<DebateStateResponse> submitTurn(@RequestBody TurnRequest request) {
        return ResponseEntity.ok(
                debateService.submitSpeech(request.getRoomId(), request.getPlayerId(), request.getText()));
    }

    // 현재 방의 진행 상태와 시간순 토론 기록을 조회한다.
    @GetMapping("/{roomId}/state")
    public ResponseEntity<DebateStateResponse> getState(@PathVariable Long roomId) {
        return ResponseEntity.ok(debateService.getState(roomId));
    }

    // 해당 방의 사회자 요청만 토론을 종료할 수 있다.
    @PostMapping("/{roomId}/finish")
    public ResponseEntity<DebateStateResponse> finishDebate(
            @PathVariable Long roomId, @RequestBody FinishDebateRequest request) {
        return ResponseEntity.ok(debateService.finishDebate(roomId, request.getPlayerId()));
    }
}