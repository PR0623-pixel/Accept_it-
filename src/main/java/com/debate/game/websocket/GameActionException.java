package com.debate.game.websocket;

/**
 * WebSocket 게임 액션 검증 실패 예외.
 */
public class GameActionException extends RuntimeException {

    private final String code;

    public GameActionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}