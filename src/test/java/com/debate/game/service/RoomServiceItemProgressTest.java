package com.debate.game.service;

import com.debate.game.entity.ProcurementItem;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.ProcurementItemRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.validation.NicknameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** S05 DB progression tests. The real transaction commit must also be checked against MySQL. */
class RoomServiceItemProgressTest {
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final ProcurementItemRepository items = mock(ProcurementItemRepository.class);
    private final RoomService service = new RoomService(rooms, mock(PlayerRepository.class),
            mock(NicknameValidator.class), mock(RoomCodeGenerator.class));
    private Room room;
    private ProcurementItem first;

    @BeforeEach
    void setup() {
        service.setProcurementItemRepository(items);
        first = new ProcurementItem();
        first.setId(1L);
        first.setTopicCategory("A");
        room = new Room();
        room.setId(101L);
        room.setRoomCode("ABC123");
        room.setStatus(RoomStatus.IN_PROGRESS);
        room.setCurrentProcurementItem(first);
    }

    @Test
    void finalDecisionMarksRoomFinishedAndFlushes() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        service.commitItemProgress("abc123", 1L, null);
        assertEquals(RoomStatus.FINISHED, room.getStatus());
        assertNull(room.getCurrentTurnRole());
        verify(rooms).saveAndFlush(room);
        verifyNoInteractions(items);
    }

    @Test
    void nextItemAdvancesWithoutFinishing() {
        ProcurementItem second = new ProcurementItem();
        second.setId(2L);
        second.setTopicCategory("B");
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(items.findById(2L)).thenReturn(Optional.of(second));
        service.commitItemProgress("ABC123", 1L, 2L);
        assertEquals(RoomStatus.IN_PROGRESS, room.getStatus());
        assertSame(second, room.getCurrentProcurementItem());
        assertEquals("B", room.getCurrentTopic());
        verify(rooms).saveAndFlush(room);
    }

    @Test
    void staleItemIsRejectedWithoutDbWrite() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        assertThrows(IllegalStateException.class,
                () -> service.commitItemProgress("ABC123", 99L, null));
        assertEquals(RoomStatus.IN_PROGRESS, room.getStatus());
        verify(rooms, never()).saveAndFlush(any());
    }

    @Test
    void finishedRoomRejectsRepeatedDecision() {
        room.setStatus(RoomStatus.FINISHED);
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        assertThrows(IllegalStateException.class,
                () -> service.commitItemProgress("ABC123", 1L, null));
        verify(rooms, never()).saveAndFlush(any());
    }

    @Test
    void invalidItemIdAndInvalidRoomRejectedBeforeWrite() {
        assertThrows(IllegalArgumentException.class,
                () -> service.commitItemProgress("bad-code", 1L, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.commitItemProgress("ABC123", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.commitItemProgress("ABC123", 1L, 1L));
        verifyNoInteractions(rooms, items);
    }

    @Test
    void missingNextItemDoesNotFinishRoom() {
        when(rooms.findByRoomCodeForUpdate("ABC123")).thenReturn(Optional.of(room));
        when(items.findById(2L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> service.commitItemProgress("ABC123", 1L, 2L));
        assertEquals(RoomStatus.IN_PROGRESS, room.getStatus());
        assertSame(first, room.getCurrentProcurementItem());
        verify(rooms, never()).saveAndFlush(any());
    }
}
