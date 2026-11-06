package com.debate.game.dto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChainResponse {
    private String audienceReaction; // 예: "해당 상황 카드는 현재 논점의 맹점을 잘 짚었네요."
    private int scoreDelta;          // 예: 10 (점수 10점 획득/상대 데미지)
    private boolean isSuccess;       // 체인(반박) 성공 여부
}