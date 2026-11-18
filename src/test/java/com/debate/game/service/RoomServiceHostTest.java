package com.debate.game.service;

import com.debate.game.dto.RoomEnterResponse;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceHostTest {
    @Mock private RoomRepository roomRepository;
    @Mock private PlayerRepository playerRepository;
    private RoomService service;

    @BeforeEach
    void setUp() {
        service = new RoomService(roomRepository, playerRepository, new NicknameValidator());
    }

    @Test
    void createRoomPersistsHostWithNormalizedNameAndNoAssignedRole() {
        when(roomRepository.save(any(Room.class))).thenAnswer(i -> {
            Room r = i.getArgument(0);
            r.setId(10L);
            return r;
        });
        when(playerRepository.save(any(Player.class))).thenAnswer(i -> {
            Player p = i.getArgument(0);
            p.setId(15L);
            return p;
        });

        RoomEnterResponse response = service.createRoom("  홍길동  ", null, null);

        assertEquals(6, response.roomCode().length());
        assertTrue(response.roomCode().matches("[A-Z0-9]{6}"));
        assertEquals("15", response.playerId());
        verify(playerRepository).save(argThat(p ->
                "홍길동".equals(p.getPlayerName()) &&
                p.getRoom() != null &&
                p.getRoom().getHostPlayer() == p &&
                p.getRole() == null && !p.isReady()));
        verify(roomRepository, times(2)).save(argThat(r ->
                r.getMaxCardCount() == 3 && r.getTurnTimeLimit() == 5));
    }

    @Test
    void invalidHostNicknameDoesNotWriteAnything() {
        assertThrows(InvalidNicknameException.class,
                () -> service.createRoom("    ", null, null));
        verifyNoInteractions(roomRepository, playerRepository);
    }

    @Test
    void invalidLegacySettingsDoNotWriteAnything() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createRoom("Host", 0, 5));
        verifyNoInteractions(roomRepository, playerRepository);
    }
}
