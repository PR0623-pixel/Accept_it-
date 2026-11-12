package com.debate.game.dto;

import lombok.Getter;
import lombok.Setter;

// 토론 종료 요청자를 식별해 사회자 역할인지 확인하는 데 사용한다.
@Getter @Setter
public class FinishDebateRequest {
    private Long playerId;
}
