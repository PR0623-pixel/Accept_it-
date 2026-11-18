package com.debate.game.service;

import com.debate.game.exception.InvalidRoomSettingsException;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.validation.NicknameValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomServiceSettingsCreationTest {
    RoomRepository rooms;
    PlayerRepository players;
    NicknameValidator validator;
    RoomCodeGenerator generator;
    RoomService service;

    @BeforeEach
    void setup() {
        rooms = mock(RoomRepository.class);
        players = mock(PlayerRepository.class);
        validator = mock(NicknameValidator.class);
        generator = mock(RoomCodeGenerator.class);
        service = new RoomService(rooms, players, validator, generator);
        when(validator.normalizeAndValidate("Host")).thenReturn("Host");
    }

    @Test
    void roomCreationRejectsLegacyOutOfRangeInputsBeforePersistence() {
        assertThrows(InvalidRoomSettingsException.class, () -> service.createRoom("Host", 6, 5));
        assertThrows(InvalidRoomSettingsException.class, () -> service.createRoom("Host", 3, 2));
        assertThrows(InvalidRoomSettingsException.class, () -> service.createRoom("Host", 3, 21));
        assertThrows(InvalidRoomSettingsException.class, () -> service.createRoom("Host", 3, Integer.MAX_VALUE));
        verifyNoInteractions(rooms, players, generator);
    }
}
