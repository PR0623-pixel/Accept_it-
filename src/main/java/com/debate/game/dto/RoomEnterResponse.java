package com.debate.game.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Frontend REST contract: identifiers are serialized as strings. */
public record RoomEnterResponse(
        @Schema(description = "6-character room code", example = "ABC123") String roomCode,
        @Schema(description = "Host player identifier", example = "1") String playerId
) {}
