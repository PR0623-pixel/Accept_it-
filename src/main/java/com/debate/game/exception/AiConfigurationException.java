package com.debate.game.exception;

public class AiConfigurationException extends RuntimeException {
    public AiConfigurationException() {
        super("AI service is not configured.");
    }
}
