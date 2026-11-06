package com.debate.game.dto;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RoleSelectRequest {
    private Long playerId;
    private String role; // PRO, CON, MODERATOR
}