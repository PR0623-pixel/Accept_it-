package com.debate.game.exception;

/** All candidate room codes were already in use. */
public class RoomCodeGenerationException extends RuntimeException {
    public RoomCodeGenerationException() {
        super("방 코드를 발급하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }
}
