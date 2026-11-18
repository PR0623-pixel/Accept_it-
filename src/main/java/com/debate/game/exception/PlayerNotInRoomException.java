package com.debate.game.exception;

public class PlayerNotInRoomException extends RuntimeException {
    public PlayerNotInRoomException() { super("PLAYER_NOT_IN_ROOM"); }
}
