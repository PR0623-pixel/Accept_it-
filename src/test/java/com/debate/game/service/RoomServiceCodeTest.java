package com.debate.game.service;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.exception.RoomCodeGenerationException;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.validation.NicknameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceCodeTest {
    @Mock RoomRepository roomRepository;
    @Mock PlayerRepository playerRepository;
    @Mock RoomCodeGenerator roomCodeGenerator;
    RoomService service;

    @BeforeEach
    void init() {
        service = new RoomService(roomRepository, playerRepository,
                new NicknameValidator(), roomCodeGenerator);
    }

    @Test
    void retriesAlreadyUsedRoomCodeAndSavesHost() {
        when(roomCodeGenerator.generate()).thenReturn("ABC123", "Q7Z9X2");
        when(roomRepository.existsByRoomCode("ABC123")).thenReturn(true);
        when(roomRepository.existsByRoomCode("Q7Z9X2")).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(9L);
            return room;
        });
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0);
            player.setId(12L);
            return player;
        });

        var result = service.createRoom("  Host  ", null, null);

        assertEquals("Q7Z9X2", result.roomCode());
        assertEquals("12", result.playerId());
        verify(roomRepository, times(2)).save(argThat(room ->
                "Q7Z9X2".equals(room.getRoomCode()) && room.getHostPlayer() != null));
        verify(playerRepository).save(argThat(player -> "Host".equals(player.getPlayerName())));
    }

    @Test
    void stopsAfterFiveUsedCodesWithoutSaving() {
        when(roomCodeGenerator.generate()).thenReturn("AAAAAA", "BBBBBB", "CCCCCC", "DDDDDD", "EEEEEE");
        when(roomRepository.existsByRoomCode(anyString())).thenReturn(true);

        assertThrows(RoomCodeGenerationException.class,
                () -> service.createRoom("Host", null, null));

        verify(roomCodeGenerator, times(5)).generate();
        verify(roomRepository, never()).save(any(Room.class));
        verifyNoInteractions(playerRepository);
    }
}
