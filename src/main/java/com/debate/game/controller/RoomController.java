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

    // R04: frontend/rest.ts -> {nickname} / {roomCode, playerId}.
    @Operation(summary = "방 입장", description = "프론트엔드 JSON {nickname}으로 방에 입장하고 " +
            "{roomCode, playerId}를 반환합니다. 기존 playerName 쿼리 방식도 임시 지원합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "방 입장 성공",
                content = @Content(schema = @Schema(implementation = RoomEnterResponse.class))),
        @ApiResponse(responseCode = "400", description = "INVALID_NICKNAME 등 잘못된 요청",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND: 존재하지 않는 방",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "ROOM_FULL 또는 ALREADY_STARTED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{roomCode}/join")
    public ResponseEntity<RoomEnterResponse> joinRoom(@PathVariable String roomCode,
                                         @RequestBody(required = false) JoinRoomRequest request,
                                         @RequestParam(name = "playerName", required = false) String legacyPlayerName) {
        String nickname = request != null ? request.getNickname() : legacyPlayerName;
        return ResponseEntity.ok(roomService.joinRoom(roomCode, nickname));
    }

    // R07/R08: REST leave endpoint; frontend lobby transport remains WebSocket.
    @Operation(summary = "로비 나가기 / Host 방 폐쇄",
            description = "WAITING 로비에서 일반 참가자는 본인만 퇴장하고, Host는 모든 참가자와 방을 삭제합니다. " +
                    "Host 퇴장 시 방 코드는 즉시 무효화됩니다. WebSocket 알림은 후속 구현 범위입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "퇴장 완료"),
        @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND 또는 PLAYER_NOT_IN_ROOM"),
        @ApiResponse(responseCode = "409", description = "ALREADY_STARTED: 시작된 게임에서 퇴장 불가")
    })
    @PostMapping("/{roomCode}/leave")
    public ResponseEntity<Void> leaveRoom(@PathVariable String roomCode,
            @RequestBody com.debate.game.dto.RoomLeaveRequest request) {
        roomService.leaveRoom(roomCode, request.playerIdAsLong());
        return ResponseEntity.noContent().build();
    }

    // R06: inspection endpoint. Frontend currently consumes WebSocket ROOM_STATE, not this REST route.
    @Operation(summary = "로비 정보 조회", description = "방 코드, Host, 참가자, 역할, Ready 및 설정값을 조회합니다. WebSocket ROOM_STATE 연동은 추후 단계입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "ROOM_NOT_FOUND", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{roomCode}")
    public ResponseEntity<com.debate.game.dto.LobbySnapshotResponse> getLobby(@PathVariable String roomCode) {
        return ResponseEntity.ok(roomService.getLobby(roomCode));
    }

    // R09: temporary REST inspection route; SET_ROLE will use WebSocket.
    @Operation(summary = "로비 역할 선택 (임시 REST)", description = "NONE, GUEST, MODERATOR 중 로비 역할을 선택합니다. PRO/CON은 물품별 투표에서만 선택하며, 이 요청은 Ready를 false로 초기화합니다. R10에서 역할별 인원 제한, R11에서 Ready 토글을 구현합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "존재하지 않는 플레이어 또는 잘못된 역할",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "진행 중인 방에서는 역할 변경 불가",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/ready")
    public ResponseEntity<Void> selectRole(@RequestBody RoleSelectRequest request) {
        roomService.selectRole(request.getPlayerId(), request.getRole());
        return ResponseEntity.ok().build();
    }
}