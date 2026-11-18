package com.debate.game.realtime;

/** Raised inside the room mutation transaction; handled AFTER_COMMIT only. */
public record LobbyChangedEvent(String roomCode, Long leftPlayerId, boolean roomClosed) {}
