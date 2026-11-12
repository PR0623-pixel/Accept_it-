package com.debate.game.dto;

import lombok.Getter;
import lombok.Setter;

// 일반 발언 제출에 필요한 방, 플레이어, 발언 내용을 전달한다.
@Getter @Setter
public class TurnRequest {
    private Long roomId;
    private Long playerId;
    private String text;
}
