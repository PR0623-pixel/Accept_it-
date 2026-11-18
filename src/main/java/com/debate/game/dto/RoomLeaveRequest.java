package com.debate.game.dto;

/** R07 REST test endpoint payload. playerId is the string returned by create/join. */
public record RoomLeaveRequest(String playerId) {
    public Long playerIdAsLong() {
        try { return Long.valueOf(playerId); }
        catch (NumberFormatException | NullPointerException ex) {
            throw new com.debate.game.exception.PlayerNotInRoomException();
        }
    }
}
