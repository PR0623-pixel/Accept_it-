package com.debate.game.dto;

import com.debate.game.entity.DebateLog;
import com.debate.game.entity.enums.DebateRole;

import java.time.LocalDateTime;

// 토론 기록 엔티티를 그대로 노출하지 않고 클라이언트용 발언 정보만 반환한다.
public record DebateLogResponse(
        Long id,
        DebateRole speakerRole,
        String text,
        Long usedCardId,
        int scoreDelta,
        String audienceReaction,
        LocalDateTime createdAt) {

    // 저장된 기록에서 카드 ID 등 응답에 필요한 값만 추출한다.
    public static DebateLogResponse from(DebateLog log) {
        return new DebateLogResponse(
                log.getId(),
                log.getSpeakerRole(),
                log.getChatContext(),
                log.getUsedCard() == null ? null : log.getUsedCard().getId(),
                log.getScoreDelta(),
                log.getAudienceReaction(),
                log.getCreatedAt());
    }
}
