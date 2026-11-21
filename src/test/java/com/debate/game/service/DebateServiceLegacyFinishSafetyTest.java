package com.debate.game.service;

import com.debate.game.repository.DebateLogRepository;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.repository.SituationCardRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Prevent the legacy REST route from finishing the DB while WS is still in DEBATE. */
class DebateServiceLegacyFinishSafetyTest {
    @Test
    void legacyRestFinishNeverMutatesRoomOrDbStatus() {
        RoomRepository rooms = mock(RoomRepository.class);
        PlayerRepository players = mock(PlayerRepository.class);
        SituationCardRepository cards = mock(SituationCardRepository.class);
        DebateLogRepository logs = mock(DebateLogRepository.class);
        DebateService service = new DebateService(rooms, players, cards, logs);
        assertThrows(IllegalStateException.class, () -> service.finishDebate(1L, 13L));
        verifyNoInteractions(rooms, players, cards, logs);
    }
}
