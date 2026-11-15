package com.debate.game.exception;

public class InvalidRoomCodeException extends IllegalArgumentException {
    public InvalidRoomCodeException(String message) {
        super(message);
    }
}

