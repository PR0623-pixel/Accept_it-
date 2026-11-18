package com.debate.game.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomCodeGeneratorTest {
    @Test
    void producesSixUppercaseAlphanumericCharacters() {
        RoomCodeGenerator generator = new RoomCodeGenerator();
        for (int i = 0; i < 100; i++) {
            assertTrue(generator.generate().matches("[A-Z0-9]{6}"));
        }
    }
}
