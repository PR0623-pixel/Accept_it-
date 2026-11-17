package com.debate.game.dto;

import com.debate.game.entity.enums.DebateRole;
import com.debate.game.entity.enums.RoomStatus;

import java.util.List;

// 방의 진행 상태와 현재 발언 차례, 지금까지의 토론 기록을 묶어 반환한다.
public record DebateStateResponse(
        Long roomId,
        RoomStatus status,
        DebateRole currentTurnRole,
        int turnTimeLimit,
        List<DebateLogResponse> logs) {
}
