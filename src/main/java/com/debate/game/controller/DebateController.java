package com.debate.game.controller;

import com.debate.game.dto.ChainRequest;
import com.debate.game.dto.ChainResponse;
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
}