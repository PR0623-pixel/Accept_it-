package com.debate.game.exception;

public class RoleFullException extends RuntimeException {
    public RoleFullException() {
        super("해당 역할 자리가 가득 찼습니다.");
    }
}
