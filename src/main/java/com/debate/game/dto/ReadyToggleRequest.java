package com.debate.game.dto;

import com.debate.game.exception.PlayerNotInRoomException;
import io.swagger.v3.oas.annotations.media.Schema;

/** R11 temporary REST inspection payload. Official client event: SET_READY {ready}. */
public record ReadyToggleRequest(
        @Schema(description = "방 생성/입장 응답에서 받은 플레이어 ID", example = "12") String playerId,
        @Schema(description = "준비=true, 준비 취소=false", example = "true") Boolean ready) {

    public Long playerIdAsLong() {
        try {
            long value = Long.parseLong(playerId);
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException | NullPointerException ex) {
            throw new PlayerNotInRoomException();
        }
    }

    public boolean readyValue() {
        if (ready == null) {
            throw new IllegalArgumentException("ready 값은 필수입니다.");
        }
        return ready;
    }
}
