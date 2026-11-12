package com.debate.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ChainRequest {
    @Schema(description = "방 ID", example = "1")
    private Long roomId;
    @Schema(description = "카드를 사용하는 플레이어 ID", example = "2")
    private Long playerId;
    @Schema(description = "사용할 상황 카드 ID", example = "10")
    private Long cardId;
    @Schema(description = "카드를 사용하면서 입력한 발언", example = "이 카드가 제 주장의 근거입니다.")
    private String chatContext; // 채팅창에 친 반박 내용이나 누적 맥락
}