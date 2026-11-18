package com.debate.game.validation;

import com.debate.game.exception.InvalidNicknameException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NicknameValidatorTest {
    private final NicknameValidator validator = new NicknameValidator();

    @Test
    void acceptsKoreanAndTrimsOuterWhitespace() {
        assertEquals("홍길동", validator.normalizeAndValidate("  홍길동  "));
        assertEquals("가나다라마바사아자차", validator.normalizeAndValidate("가나다라마바사아자차"));
    }

    @Test
    void rejectsNullEmptyAndWhitespaceOnly() {
        assertThrows(InvalidNicknameException.class, () -> validator.normalizeAndValidate(null));
        assertThrows(InvalidNicknameException.class, () -> validator.normalizeAndValidate(""));
        assertThrows(InvalidNicknameException.class, () -> validator.normalizeAndValidate("   "));
        assertThrows(InvalidNicknameException.class, () -> validator.normalizeAndValidate("\t\n"));
    }

    @Test
    void rejectsMoreThanTenCharactersAfterTrimming() {
        assertThrows(InvalidNicknameException.class,
                () -> validator.normalizeAndValidate("가나다라마바사아자차카"));
    }

    @Test
    void rejectsEmbeddedControlCharacters() {
        assertThrows(InvalidNicknameException.class,
                () -> validator.normalizeAndValidate("가\n나"));
    }
}
