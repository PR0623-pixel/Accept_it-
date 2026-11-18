package com.debate.game.dto;

import com.debate.game.exception.PlayerNotInRoomException;
import io.swagger.v3.oas.annotations.media.Schema;

/** R16 temporary REST check: identity is supplied by the client, not authenticated. */
public record StartCheckRequest(
        @Schema(description = "시작 요청자 ID (방의 Host)", example = "53") String playerId) {

    public Long playerIdAsLong() {
        try {
            long id = Long.parseLong(playerId);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException | NullPointerException ex) {
            throw new PlayerNotInRoomException();
        }
    }
}
