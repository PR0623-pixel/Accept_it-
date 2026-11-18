package com.debate.game.exception;

public class InvalidNicknameException extends IllegalArgumentException {
    public InvalidNicknameException(String message) {
        super(message);
    }
}
