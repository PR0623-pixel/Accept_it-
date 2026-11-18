package com.debate.game.dto;

import com.debate.game.exception.PlayerNotInRoomException;
import io.swagger.v3.oas.annotations.media.Schema;

/** R13 Swagger-only REST payload. Frontend WebSocket UPDATE_SETTINGS has three fields. */
public record RoomSettingsUpdateRequest(
        @Schema(description = "요청자 플레이어 ID (Host)", example = "12") String playerId,
        @Schema(description = "물품당 키워드 카드 수", example = "3") Integer cardsPerItem,
        @Schema(description = "일반 발언 시간(초)", example = "120") Integer speechSeconds,
        @Schema(description = "반론 시간(초)", example = "60") Integer rebuttalSeconds,
        @Schema(description = "물품당 토론 시간(초; 60초 단위)", example = "300") Integer itemSeconds,
        @Schema(description = "최대 물품(라운드) 수", example = "3") Integer maxItems) {

    public Long playerIdAsLong() {
        try {
            long value = Long.parseLong(playerId);
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException | NullPointerException ex) {
            throw new PlayerNotInRoomException();
        }
    }
}
