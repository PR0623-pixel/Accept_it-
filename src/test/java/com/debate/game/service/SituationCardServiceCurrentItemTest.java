package com.debate.game.service;

import com.debate.game.entity.ProcurementItem;
import com.debate.game.entity.Room;
import com.debate.game.entity.enums.DebateRole;
import com.debate.game.repository.RoomRepository;
import com.debate.game.repository.SituationCardRepository;
import com.debate.game.websocket.GameActionException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SituationCardServiceCurrentItemTest {
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final SituationCardRepository cards = mock(SituationCardRepository.class);
    private final SituationCardService service = new SituationCardService(rooms, cards);

    private Room currentRoom() {
        Room room = new Room();
        room.setId(10L);
        ProcurementItem item = new ProcurementItem();
        item.setId(20L);
        room.setCurrentProcurementItem(item);
        when(rooms.findByRoomCode("ABC123")).thenReturn(Optional.of(room));
        return room;
    }

    @Test
    void usesOnlyMatchingRoomCurrentItemAndSide() {
        currentRoom();
        when(cards.markUsed(10L, 101L, 20L, DebateRole.PRO)).thenReturn(1);
        service.markUsed("ABC123", 101L, DebateRole.PRO);
        verify(cards).markUsed(10L, 101L, 20L, DebateRole.PRO);
    }

    @Test
    void restoresOnlyMatchingRoomCurrentItemAndSide() {
        currentRoom();
        when(cards.restoreUsedCard(10L, 101L, 20L, DebateRole.CON)).thenReturn(1);
        service.restore("ABC123", 101L, DebateRole.CON);
        verify(cards).restoreUsedCard(10L, 101L, 20L, DebateRole.CON);
    }

    @Test
    void refusedDbUpdateThrowsAndLeavesDecisionToCaller() {
        currentRoom();
        GameActionException error = assertThrows(GameActionException.class,
                () -> service.markUsed("ABC123", 101L, DebateRole.PRO));
        assertEquals("CARD_REQUIRED", error.getCode());
    }

    @Test
    void cannotUseAnyCardWithoutCurrentItem() {
        Room room = new Room();
        room.setId(10L);
        when(rooms.findByRoomCode("ABC123")).thenReturn(Optional.of(room));
        assertThrows(GameActionException.class,
                () -> service.markUsed("ABC123", 101L, DebateRole.PRO));
        verifyNoInteractions(cards);
    }
}
