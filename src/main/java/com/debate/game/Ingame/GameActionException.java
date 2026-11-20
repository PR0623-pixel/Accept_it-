package com.debate.game.Ingame;

import lombok.Getter;

@Getter
public class GameActionException extends RuntimeException {
    private final String code;

    public GameActionException(String code, String message) {
        super(message);
        this.code = code;
    }
}