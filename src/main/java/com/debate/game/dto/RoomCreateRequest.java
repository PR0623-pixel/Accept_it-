package com.debate.game.dto;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RoomCreateRequest {
    private int maxCardCount; // 1개의 쟁점에 대한 최대 카드 매수
    private int turnTimeLimit; // 턴 시간 (3~20분)[cite: 1]
}