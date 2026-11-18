package com.debate.game.exception;

/** Room is no longer in the WAITING (frontend LOBBY) state. */
public class AlreadyStartedException extends IllegalStateException {
    public AlreadyStartedException() {
        super("이미 시작된 방입니다.");
    }
}
