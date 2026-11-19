package com.debate.game.exception;

/** A lobby role must be selected before setting Ready=true. */
public class RoleRequiredException extends RuntimeException {
    public RoleRequiredException() {
        super("역할을 먼저 설정해주세요.");
    }
}
