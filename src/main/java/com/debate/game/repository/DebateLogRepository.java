package com.debate.game.repository;

import com.debate.game.entity.DebateLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DebateLogRepository extends JpaRepository<DebateLog, Long> {
}
