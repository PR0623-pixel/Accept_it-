// RoomController.java
package com.debate.game.controller;

import com.debate.game.dto.RoleSelectRequest;
import com.debate.game.dto.RoomCreateRequest;
import com.debate.game.service.RoomService;
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

@Tag(name = "Room", description = "방 생성, 코드 입장 및 역할 준비")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    // 1. 방 만들기 버튼 클릭 시[cite: 1]
    @Operation(summary = "방 생성", description = "카드 수와 턴 시간 설정을 저장하고 6자리 방 코드를 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "잘못된 요청",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<String> createRoom(@RequestBody RoomCreateRequest request) {
        String roomCode = roomService.createRoom(request.getMaxCardCount(), request.getTurnTimeLimit());
        return ResponseEntity.ok(roomCode);
    }

    // 2. 방 Code 입력 후 Join 클릭 시[cite: 1]
    @Operation(summary = "방 입장", description = "방 코드와 이름으로 방에 입장하고 생성된 플레이어 ID를 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "존재하지 않는 방 코드 또는 잘못된 요청",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "방 정원 3명 초과",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{roomCode}/join")
    public ResponseEntity<Long> joinRoom(@PathVariable String roomCode, @RequestParam String playerName) {
        Long playerId = roomService.joinRoom(roomCode, playerName);
        return ResponseEntity.ok(playerId); // 부여받은 고유 Player ID 반환
    }

    // 3. 누른 사람 역할군에 맞는 맵핑 (준비 완료)[cite: 1]
    @Operation(summary = "역할 선택 및 준비 완료", description = "PRO, CON, MODERATOR 중 중복되지 않는 역할을 선택하고 준비 완료 처리합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "존재하지 않는 플레이어 또는 잘못된 역할",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "이미 사용 중인 역할",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/ready")
    public ResponseEntity<Void> selectRole(@RequestBody RoleSelectRequest request) {
        roomService.selectRole(request.getPlayerId(), request.getRole());
        return ResponseEntity.ok().build();
    }
}