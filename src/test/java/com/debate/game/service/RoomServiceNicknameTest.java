package com.debate.game.service;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.exception.InvalidNicknameException;
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
class RoomServiceNicknameTest {
    @Mock private RoomRepository roomRepository;
    @Mock private PlayerRepository playerRepository;
    private RoomService service;
    private Room room;

    @BeforeEach
    void setUp() {
        service = new RoomService(roomRepository, playerRepository, new NicknameValidator(), mock(RoomCodeGenerator.class));
        room = new Room();
        room.setRoomCode("ABC123");
    }

    @Test
    void joinPersistsNormalizedNickname() {
        when(roomRepository.findByRoomCode("ABC123")).thenReturn(Optional.of(room));
        when(playerRepository.countByRoom(room)).thenReturn(0L);
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0);
            player.setId(77L);
            return player;
        });

        Long id = service.joinRoom("ABC123", "  홍길동  ");

        assertEquals(77L, id);
        verify(playerRepository).save(argThat(p -> "홍길동".equals(p.getPlayerName()) && p.getRoom() == room));
    }

    @Test
    void invalidNicknameRejectedWithoutDatabaseChanges() {
        assertThrows(InvalidNicknameException.class, () -> service.joinRoom("ABC123", "   "));
        verifyNoInteractions(roomRepository, playerRepository);
    }

    @Test
    void tooLongNicknameRejectedWithoutDatabaseChanges() {
        assertThrows(InvalidNicknameException.class, () -> service.joinRoom("ABC123", "가나다라마바사아자차카"));
        verifyNoInteractions(roomRepository, playerRepository);
    }
}
