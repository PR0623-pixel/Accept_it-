package com.debate.game.exception;

import com.debate.game.dto.ErrorResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(InvalidNicknameException.class)
    public ResponseEntity<Object> handleInvalidNickname(InvalidNicknameException e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", e.getMessage());
    }

    @ExceptionHandler(InvalidRoomCodeException.class)
    public ResponseEntity<Object> handleInvalidRoomCode(InvalidRoomCodeException e) {
        return error(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "방을 찾을 수 없습니다.");
    }

    @ExceptionHandler(RoomCodeGenerationException.class)
    public ResponseEntity<Object> handleRoomCodeGeneration(RoomCodeGenerationException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "ROOM_CODE_GENERATION_FAILED", e.getMessage());
    }

    @ExceptionHandler(PlayerNotInRoomException.class)
    public ResponseEntity<Object> handlePlayerNotInRoom(PlayerNotInRoomException e) {
        return error(HttpStatus.NOT_FOUND, "PLAYER_NOT_IN_ROOM", "해당 방의 참가자를 찾을 수 없습니다.");
    }

    @ExceptionHandler(NotHostException.class)
    public ResponseEntity<Object> handleNotHost(NotHostException e) {
        return error(HttpStatus.FORBIDDEN, "NOT_HOST", e.getMessage());
    }

    @ExceptionHandler(RoleFullException.class)
    public ResponseEntity<Object> handleRoleFull(RoleFullException e) {
        return error(HttpStatus.CONFLICT, "ROLE_FULL", e.getMessage());
    }

    @ExceptionHandler(RoleRequiredException.class)
    public ResponseEntity<Object> handleRoleRequired(RoleRequiredException e) {
        return error(HttpStatus.CONFLICT, "ROLE_REQUIRED", e.getMessage());
    }

    @ExceptionHandler(NotEnoughPlayersException.class)
    public ResponseEntity<Object> handleNotEnoughPlayers(NotEnoughPlayersException e) {
        return error(HttpStatus.CONFLICT, "NOT_ENOUGH_PLAYERS", e.getMessage());
    }

    @ExceptionHandler(RoomFullException.class)
    public ResponseEntity<Object> handleRoomFull(RoomFullException e) {
        return error(HttpStatus.CONFLICT, "ROOM_FULL", "방이 가득 찼습니다.");
    }

    @ExceptionHandler(AlreadyStartedException.class)
    public ResponseEntity<Object> handleAlreadyStarted(AlreadyStartedException e) {
        return error(HttpStatus.CONFLICT, "ALREADY_STARTED", "이미 시작된 방입니다.");
    }

    @ExceptionHandler(AiConfigurationException.class)
    public ResponseEntity<Object> handleAiConfiguration(AiConfigurationException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "AI 서비스 설정이 준비되지 않았습니다.");
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<Object> handleAiService(AiServiceException e) {
        return error(HttpStatus.BAD_GATEWAY, "AI_SERVICE_ERROR", "AI 서비스 요청을 처리하지 못했습니다.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException e) {
        return standard(HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> handleIllegalState(IllegalStateException e) {
        return standard(HttpStatus.CONFLICT, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception e) {
        return standard(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY);
    }

    // Keep framework headers such as Allow while replacing ProblemDetail/error details.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return standard(status, headers);
    }

    private ResponseEntity<Object> standard(HttpStatusCode status, HttpHeaders headers) {
        String code;
        String message;
        switch (status.value()) {
            case 400 -> { code = "BAD_REQUEST"; message = "잘못된 요청입니다."; }
            case 404 -> { code = "NOT_FOUND"; message = "요청한 리소스를 찾을 수 없습니다."; }
            case 405 -> { code = "METHOD_NOT_ALLOWED"; message = "지원하지 않는 HTTP 메서드입니다."; }
            case 409 -> { code = "CONFLICT"; message = "현재 상태에서는 요청을 처리할 수 없습니다."; }
            case 415 -> { code = "UNSUPPORTED_MEDIA_TYPE"; message = "지원하지 않는 콘텐츠 형식입니다."; }
            case 502 -> { code = "BAD_GATEWAY"; message = "외부 서비스 요청을 처리하지 못했습니다."; }
            case 503 -> { code = "SERVICE_UNAVAILABLE"; message = "서비스를 일시적으로 사용할 수 없습니다."; }
            default -> { code = "INTERNAL_SERVER_ERROR"; message = "서버 오류가 발생했습니다."; }
        }
        return new ResponseEntity<>(new ErrorResponse(code, message), headers, status);
    }

    private ResponseEntity<Object> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
