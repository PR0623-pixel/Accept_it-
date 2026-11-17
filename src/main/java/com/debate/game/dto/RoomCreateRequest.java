package com.debate.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RoomCreateRequest {
    @Schema(description = "사용자에게 지급할 카드 수", example = "5")
    private int maxCardCount; // 1개의 쟁점에 대한 최대 카드 매수
    @Schema(description = "턴 제한 시간 (현재 저장되지만 서버에서 시간 경과를 강제하지 않음)", example = "5")
    private int turnTimeLimit; // 턴 시간 (3~20분)[cite: 1]
}