package com.debate.game.dto;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ChainRequest {
    private Long roomId;
    private Long playerId;
    private Long cardId;
    private String chatContext; // 채팅창에 친 반박 내용이나 누적 맥락
}