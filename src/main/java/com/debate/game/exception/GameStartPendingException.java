package com.debate.game.exception;

/** Legacy direct-start route is disabled until the G03 game-start transaction exists. */
public class GameStartPendingException extends RuntimeException {
    public GameStartPendingException() {
        super("게임 시작 처리는 G03 단계에서 연결됩니다. 현재는 시작 조건만 검사할 수 있습니다.");
    }
}
