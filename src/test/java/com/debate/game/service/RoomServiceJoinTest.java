package com.debate.game.service;

import com.debate.game.entity.Player;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.exception.AlreadyStartedException;
import com.debate.game.exception.InvalidRoomCodeException;
import com.debate.game.exception.RoomFullException;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.validation.NicknameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceJoinTest {
    @Mock RoomRepository roomRepository;
    @Mock PlayerRepository playerRepository;
    RoomService service;
    Room room;

    @BeforeEach
    void setUp() {
        service = new RoomService(roomRepository, playerRepository,
                new NicknameValidator(), mock(RoomCodeGenerator.class));
        room = new Room();
        room.setRoomCode("ABC123");
        room.setStatus(RoomStatus.WAITING);
    }

    @Test
    void joinsWaitingRoomAndReturnsFrontendResponse() {
        when(roomRepository.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoom(room)).thenReturn(1L);
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0);
            player.setId(31L);
            return player;
        });

        var result = service.joinRoom(" abc123 ", "  둘째  ");

        assertEquals("ABC123", result.roomCode());
        assertEquals("31", result.playerId());
        verify(playerRepository).save(argThat(p ->
                "둘째".equals(p.getPlayerName()) &&
                p.getRoom() == room && p.getRole() == LobbyRole.NONE && p.getStance() == null && !p.isReady()));
    }

    @Test
    void missingRoomDoesNotInsertPlayer() {
        when(roomRepository.findByRoomCodeForUpdate("ZZZZZZ")).thenReturn(Optional.empty());
        assertThrows(InvalidRoomCodeException.class, () -> service.joinRoom("ZZZZZZ", "둘째"));
        verifyNoInteractions(playerRepository);
    }

    @Test
    void malformedRoomCodeReturnsNotFoundWithoutDbAccess() {
        assertThrows(InvalidRoomCodeException.class, () -> service.joinRoom("###", "둘째"));
        verifyNoInteractions(roomRepository, playerRepository);
    }

    @Test
    void locksRoomBeforeCountingCapacity() {
        when(roomRepository.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoom(room)).thenReturn(3L);

        assertThrows(RoomFullException.class, () -> service.joinRoom("ABC123", "넷째"));

        var inOrder = inOrder(roomRepository, playerRepository);
        inOrder.verify(roomRepository).findByRoomCodeForUpdate("ABC123");
        inOrder.verify(playerRepository).countByRoom(room);
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void rejectsFullRoom() {
        when(roomRepository.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoom(room)).thenReturn(3L);
        assertThrows(RoomFullException.class, () -> service.joinRoom("ABC123", "넷째"));
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    void rejectsStartedRoomEvenIfItHasSpace() {
        room.setStatus(RoomStatus.IN_PROGRESS);
        when(roomRepository.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        assertThrows(AlreadyStartedException.class, () -> service.joinRoom("ABC123", "둘째"));
        verifyNoInteractions(playerRepository);
    }

    @Test
    void rejectsFinishedRoom() {
        room.setStatus(RoomStatus.FINISHED);
        when(roomRepository.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        assertThrows(AlreadyStartedException.class, () -> service.joinRoom("ABC123", "둘째"));
        verifyNoInteractions(playerRepository);
    }
}
