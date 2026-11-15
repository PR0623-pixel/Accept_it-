package com.debate.game.exception;

public class RoomFullException extends IllegalStateException {
    public RoomFullException(String message) {
        super(message);
    }
}
