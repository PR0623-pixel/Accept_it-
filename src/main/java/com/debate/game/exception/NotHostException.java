package com.debate.game.exception;

public class NotHostException extends RuntimeException {
    public NotHostException() {
        super("방장만 설정을 변경할 수 있습니다.");
    }

    public NotHostException(String message) {
        super(message);
    }
}
