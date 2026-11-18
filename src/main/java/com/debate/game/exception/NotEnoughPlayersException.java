package com.debate.game.exception;

/** Ready=true requires three players in the waiting room. */
public class NotEnoughPlayersException extends RuntimeException {
    public NotEnoughPlayersException() {
        super("인원이 부족합니다. 3명이 모여야 시작할 수 있습니다.");
    }
}
