package com.debate.game.validation;

import com.debate.game.exception.InvalidNicknameException;
import org.springframework.stereotype.Component;

/**
 * R01: 서버 측 닉네임 형식 검증.
 * 중복 허용 여부 및 미입력 시 자동 생성 여부는 기획 확정 후 별도로 적용한다.
 */
@Component
public class NicknameValidator {
    // 프론트엔드 MainPage.tsx의 input maxLength=10과 동일한 길이 기준(UTF-16 코드 단위).
    public static final int MAX_LENGTH = 10;

    /** 앞뒤 공백을 제거하고, 유효한 닉네임을 반환한다. */
    public String normalizeAndValidate(String rawNickname) {
        if (rawNickname == null || rawNickname.isBlank()) {
            throw new InvalidNicknameException("닉네임을 입력하세요.");
        }

        String nickname = rawNickname.strip();
        if (nickname.isEmpty()) {
            throw new InvalidNicknameException("닉네임을 입력하세요.");
        }
        if (nickname.length() > MAX_LENGTH) {
            throw new InvalidNicknameException("닉네임은 10자 이하여야 합니다.");
        }
        if (nickname.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidNicknameException("닉네임에 제어 문자를 사용할 수 없습니다.");
        }
        return nickname;
    }
}
