package com.debate.game.validation;

import com.debate.game.exception.InvalidRoomSettingsException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoomSettingsPolicyTest {
    @Test
    void frontendThreeSettingsMatchDocumentedBounds() {
        assertEquals(1, RoomSettingsPolicy.cardsPerItem(1));
        assertEquals(5, RoomSettingsPolicy.cardsPerItem(5));
        assertEquals(1, RoomSettingsPolicy.maxItems(1));
        assertEquals(5, RoomSettingsPolicy.maxItems(5));
        assertEquals(180, RoomSettingsPolicy.itemSeconds(180));
        assertEquals(1200, RoomSettingsPolicy.itemSeconds(1200));
    }

    @Test
    void frontendSettingsRejectOutOfRangeAndOffStepInputs() {
        for (Integer value : new Integer[]{-1, 0, 6, null}) {
            assertThrows(InvalidRoomSettingsException.class, () -> RoomSettingsPolicy.cardsPerItem(value));
            assertThrows(InvalidRoomSettingsException.class, () -> RoomSettingsPolicy.maxItems(value));
        }
        for (Integer value : new Integer[]{-1, 179, 1201, null}) {
            assertThrows(InvalidRoomSettingsException.class, () -> RoomSettingsPolicy.itemSeconds(value));
        }
        assertEquals("itemSeconds는 60초 단위여야 합니다.",
                assertThrows(InvalidRoomSettingsException.class,
                        () -> RoomSettingsPolicy.itemSeconds(479)).getMessage());
    }

    @Test
    void speechAndRebuttalHaveEditableTemporaryServerBounds() {
        assertEquals(1, RoomSettingsPolicy.speechSeconds(1));
        assertEquals(3600, RoomSettingsPolicy.speechSeconds(3600));
        assertEquals(1, RoomSettingsPolicy.rebuttalSeconds(1));
        assertEquals(3600, RoomSettingsPolicy.rebuttalSeconds(3600));
        for (Integer value : new Integer[]{0, 3601, null}) {
            assertThrows(InvalidRoomSettingsException.class, () -> RoomSettingsPolicy.speechSeconds(value));
            assertThrows(InvalidRoomSettingsException.class, () -> RoomSettingsPolicy.rebuttalSeconds(value));
        }
    }

    @Test
    void legacyCreationFieldsHaveSameBoundsAndDefaults() {
        assertEquals(3, RoomSettingsPolicy.initialCardsPerItem(null));
        assertEquals(5, RoomSettingsPolicy.initialItemMinutes(null));
        assertEquals(1, RoomSettingsPolicy.initialCardsPerItem(1));
        assertEquals(3, RoomSettingsPolicy.initialItemMinutes(3));
        assertEquals(20, RoomSettingsPolicy.initialItemMinutes(20));
        assertThrows(InvalidRoomSettingsException.class,
                () -> RoomSettingsPolicy.initialItemMinutes(Integer.MAX_VALUE));
        assertThrows(InvalidRoomSettingsException.class,
                () -> RoomSettingsPolicy.initialItemMinutes(2));
        assertThrows(InvalidRoomSettingsException.class,
                () -> RoomSettingsPolicy.initialItemMinutes(21));
        assertThrows(InvalidRoomSettingsException.class,
                () -> RoomSettingsPolicy.initialCardsPerItem(6));
    }
}
