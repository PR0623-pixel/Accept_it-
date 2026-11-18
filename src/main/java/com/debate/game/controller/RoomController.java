// RoomController.java
package com.debate.game.controller;

import com.debate.game.dto.RoleSelectRequest;
import com.debate.game.dto.JoinRoomRequest;
import com.debate.game.dto.RoomCreateRequest;
import com.debate.game.dto.RoomEnterResponse;
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

    // R02: the frontend creates the room with { "nickname": "..." }.
    @Operation(summary = "방 생성 및 Host 등록",
            description = "닉네임을 검증한 뒤 방과 Host 플레이어를 함께 저장하고 roomCode/playerId를 반환합니다. " +
                    "Host 권한은 로비 역할과 별도입니다. 방 코드는 6자리 영숫자로 발급하며 중복 시 최대 5회 재생성합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "방 생성 성공",
                content = @Content(schema = @Schema(implementation = RoomEnterResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 닉네임 또는 방 설정",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "사용 가능한 방 코드 발급 실패",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<RoomEnterResponse> createRoom(@RequestBody RoomCreateRequest request) {
        RoomEnterResponse result = roomService.createRoom(
                request.getNickname(), request.getMaxCardCount(), request.getTurnTimeLimit());
        return ResponseEntity.ok(result);
    }

    // 2. 방 Code 입력 후 Join 클릭 시[cite: 1]
    @Operation(summary = "방 입장", description = "JSON 요청 {nickname}으로 방에 입장합니다. "+
            "이전 클라이언트의 playerName 쿼리 파라미터도 임시로 지원합니다. "+
            "입장 응답은 기존 Long playerId이며, 프론트 계약에 맞춘 응답 변경은 R04/X01에서 처리합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "유효하지 않은 닉네임(INVALID_NICKNAME), 잘못된 방 코드 또는 요청",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "방 정원 3명 초과",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{roomCode}/join")
    public ResponseEntity<Long> joinRoom(@PathVariable String roomCode,
                                         @RequestBody(required = false) JoinRoomRequest request,
                                         @RequestParam(name = "playerName", required = false) String legacyPlayerName) {
        // JSON 요청이 있으면 해당 값을 사용하고, 없을 때만 기존 쿼리 파라미터를 사용한다.
        String nickname = request != null ? request.getNickname() : legacyPlayerName;
        Long playerId = roomService.joinRoom(roomCode, nickname);
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