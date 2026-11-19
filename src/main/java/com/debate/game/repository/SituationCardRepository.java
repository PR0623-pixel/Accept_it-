package com.debate.game.repository;

import com.debate.game.entity.SituationCard;
import com.debate.game.entity.enums.DebateRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SituationCardRepository extends JpaRepository<SituationCard, Long> {

    List<SituationCard> findByRoom_IdAndOwnerRoleAndIsUsedFalseOrderByIdAsc(
            Long roomId,
            DebateRole ownerRole
    );
}