package com.debate.game.exception;

/** A R16 start precondition that is not met (no room/session mutation). */
public class InvalidStartConditionsException extends RuntimeException {
    private final String code;

    public InvalidStartConditionsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
