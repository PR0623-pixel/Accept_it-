package com.debate.game.exception;

/** Invalid room setting values, safe to display to the API caller. */
public class InvalidRoomSettingsException extends IllegalArgumentException {
    public InvalidRoomSettingsException(String message) {
        super(message);
    }
}
