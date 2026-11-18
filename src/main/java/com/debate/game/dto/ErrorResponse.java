// Frontend error contract: { "code": "...", "message": "한국어 안내 문구" }.
package com.debate.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    @Schema(description = "클라이언트가 구분할 수 있는 오류 코드", example = "ROOM_NOT_FOUND")
    private String code;
    @Schema(description = "사용자에게 표시할 오류 메시지", example = "존재하지 않는 방 코드입니다.")
    private String message;

    public ErrorResponse(String message) {
        this("UNKNOWN_ERROR", message);
    }
}
