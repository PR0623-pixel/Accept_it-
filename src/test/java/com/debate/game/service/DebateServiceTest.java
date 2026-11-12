package com.debate.game.service;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.DebateRole;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.repository.DebateLogRepository;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.repository.SituationCardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DebateServiceTest {
    @Mock private RoomRepository roomRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private SituationCardRepository cardRepository;
    @Mock private DebateLogRepository debateLogRepository;

    private DebateService service;
    private Room room;
    private Player player;

    @BeforeEach
    void setUp() {
        service = new DebateService(roomRepository, playerRepository, cardRepository, debateLogRepository);
        room = new Room();
        room.setId(7L);
        room.setStatus(RoomStatus.IN_PROGRESS);
        room.setCurrentTurnRole(DebateRole.PRO);
        room.setTurnTimeLimit(5);

        player = new Player();
        player.setId(12L);
        player.setRoom(room);
        player.setRole(DebateRole.PRO);

        when(roomRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(room));
        lenient().when(roomRepository.findById(7L)).thenReturn(Optional.of(room));
        when(playerRepository.findById(12L)).thenReturn(Optional.of(player));
        lenient().when(debateLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(debateLogRepository.findByRoom_IdOrderByIdAsc(7L)).thenReturn(List.of());
    }

    // 찬성 측 발언이 기록되고 다음 차례가 반대 측으로 넘어가는지 확인한다.
    @Test
    void submittedSpeechIsSavedAndAdvancesToOpposingSide() {
        var state = service.submitSpeech(7L, 12L, "발언 내용");

        assertEquals(DebateRole.CON, room.getCurrentTurnRole());
        assertEquals(DebateRole.CON, state.currentTurnRole());
        verify(debateLogRepository).save(argThat(log ->
                log.getSpeakerRole() == DebateRole.PRO
                        && "발언 내용".equals(log.getChatContext())
                        && log.getUsedCard() == null));
    }

    // 현재 차례가 아닌 진영의 발언은 기록되거나 차례를 바꾸면 안 된다.
    @Test
    void playerCannotSpeakOutOfTurn() {
        player.setRole(DebateRole.CON);

        assertThrows(IllegalStateException.class, () -> service.submitSpeech(7L, 12L, "발언 내용"));
        verify(debateLogRepository, never()).save(any());
    }
}
