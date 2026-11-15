// ErrorResponse.java (Unity 작업자가 파싱할 에러 규격)
package com.debate.game.dto;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String errorMessage;

    public ErrorResponse(String errorMessage) {
        this("UNKNOWN_ERROR", errorMessage);
    }
}
