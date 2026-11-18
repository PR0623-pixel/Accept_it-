package com.debate.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/** R01: 프론트엔드의 POST /api/rooms/{roomCode}/join JSON 요청. */
@Getter @Setter
public class JoinRoomRequest {
    @Schema(description = "입장할 플레이어의 닉네임(앞뒤 공백 제거, 최대 10자)",
            example = "홍길동", maxLength = 10)
    private String nickname;
}
