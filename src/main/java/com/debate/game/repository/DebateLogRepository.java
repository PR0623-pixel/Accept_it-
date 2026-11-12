package com.debate.game.repository;

import com.debate.game.entity.DebateLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebateLogRepository extends JpaRepository<DebateLog, Long> {
    // ID 순으로 조회해 기록된 토론 순서가 API 응답에서도 유지되도록 한다.
    java.util.List<DebateLog> findByRoom_IdOrderByIdAsc(Long roomId);
}
