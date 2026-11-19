package com.debate.game.exception;

/** Start requires three players (Ready itself is allowed with 1~2 members). */
public class NotEnoughPlayersException extends RuntimeException {
    public NotEnoughPlayersException() {
        super("인원이 부족합니다. 3명이 모여야 시작할 수 있습니다.");
    }
}
