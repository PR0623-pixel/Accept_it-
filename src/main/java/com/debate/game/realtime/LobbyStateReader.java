package com.debate.game.realtime;

import com.debate.game.dto.LobbySnapshotResponse;
import com.debate.game.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Fresh DB snapshot in a distinct transaction after a lobby mutation commits. */
@Component
@RequiredArgsConstructor
public class LobbyStateReader {
    private final RoomService roomService;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public LobbySnapshotResponse read(String roomCode) {
        return roomService.getLobby(roomCode);
    }
}
