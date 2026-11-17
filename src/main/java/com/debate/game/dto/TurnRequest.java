package com.debate.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

// 일반 발언 제출에 필요한 방, 플레이어, 발언 내용을 전달한다.
@Getter @Setter
public class TurnRequest {
    @Schema(description = "토론 중인 방의 ID", example = "1")
    private Long roomId;
    @Schema(description = "현재 차례인 플레이어 ID", example = "2")
    private Long playerId;
    @Schema(description = "일반 발언 내용", example = "이 물품은 예산 대비 활용도가 높습니다.")
    private String text;
}
