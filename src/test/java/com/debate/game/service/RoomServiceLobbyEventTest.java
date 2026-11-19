package com.debate.game.service;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.exception.RoleRequiredException;
import com.debate.game.realtime.LobbyChangedEvent;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.validation.NicknameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomServiceLobbyEventTest {
    RoomRepository rooms = mock(RoomRepository.class);
    PlayerRepository players = mock(PlayerRepository.class);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    RoomService service = new RoomService(rooms, players, new NicknameValidator(), mock(RoomCodeGenerator.class));
    Room room = new Room();
    Player host = new Player();

    @BeforeEach
    void setup() {
        service.setLobbyEventPublisher(publisher);
        room.setId(3L);
        room.setRoomCode("ABC123");
        host.setId(11L);
        host.setRoom(room);
        room.setHostPlayer(host);
    }

    @Test
    void successfulReadyEmitsLobbyChange() {
        host.setRole(LobbyRole.GUEST);
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findByRoomForUpdate(room)).thenReturn(List.of(host));
        service.setReady("ABC123", 11L, true);
        assertTrue(host.isReady());
        ArgumentCaptor<LobbyChangedEvent> capture = ArgumentCaptor.forClass(LobbyChangedEvent.class);
        verify(publisher).publishEvent(capture.capture());
        assertEquals(new LobbyChangedEvent("ABC123", null, false), capture.getValue());
    }

    @Test
    void failedReadyDoesNotPublishEvent() {
        host.setRole(LobbyRole.NONE);
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findByRoomForUpdate(room)).thenReturn(List.of(host));
        assertThrows(RoleRequiredException.class, () -> service.setReady("ABC123", 11L, true));
        verifyNoInteractions(publisher);
    }

    @Test
    void socketSettingsPreserveSpeechAndRebuttalAndNotify() {
        room.setSpeechSeconds(155);
        room.setRebuttalSeconds(72);
        host.setReady(true);
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(players.findRoomIdByPlayerId(11L)).thenReturn(Optional.of(3L));
        when(players.findByRoomForUpdate(room)).thenReturn(List.of(host));
        service.updateSocketSettings("ABC123", 11L, 4, 480, 2);
        assertEquals(155, room.getSpeechSeconds());
        assertEquals(72, room.getRebuttalSeconds());
        assertEquals(4, room.getMaxItems());
        assertEquals(2, room.getMaxCardCount());
        assertEquals(8, room.getTurnTimeLimit());
        assertFalse(host.isReady());
        verify(publisher).publishEvent(new LobbyChangedEvent("ABC123", null, false));
    }
}
