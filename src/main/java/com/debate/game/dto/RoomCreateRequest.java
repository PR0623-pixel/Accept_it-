package com.debate.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** R02: frontend sends only nickname; legacy room settings are temporarily optional. */
@Getter @Setter
public class RoomCreateRequest {
    @Schema(description = "Host nickname (leading/trailing spaces stripped, 1-10 chars)",
            example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 10)
    private String nickname;

    @Schema(description = "Optional legacy card count (1~5). Defaults to 3; full settings UI uses UPDATE_SETTINGS.", example = "3")
    private Integer maxCardCount;

    @Schema(description = "Optional legacy time limit (3~20 minutes). Defaults to 5; full settings UI uses UPDATE_SETTINGS.", example = "5")
    private Integer turnTimeLimit;
}
