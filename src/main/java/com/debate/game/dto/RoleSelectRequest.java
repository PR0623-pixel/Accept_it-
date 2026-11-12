package com.debate.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RoleSelectRequest {
    @Schema(description = "역할을 선택하는 플레이어 ID", example = "1")
    private Long playerId;
    @Schema(description = "선택할 역할", allowableValues = {"PRO", "CON", "MODERATOR"}, example = "PRO")
    private String role; // PRO, CON, MODERATOR
}