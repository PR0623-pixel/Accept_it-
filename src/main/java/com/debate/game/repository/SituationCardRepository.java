package com.debate.game.repository;

import com.debate.game.entity.SituationCard;
import com.debate.game.entity.enums.DebateRole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SituationCardRepository extends JpaRepository<SituationCard, Long> {

    List<SituationCard> findByRoom_IdAndProcurementItem_IdAndOwnerRoleAndIsUsedFalseOrderByIdAsc(
            Long roomId,
            Long procurementItemId,
            DebateRole ownerRole
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update SituationCard card
        set card.isUsed = true
        where card.id = :cardId
        and card.room.id = :roomId
        and card.ownerRole = :ownerRole
        and card.procurementItem.id = :procurementItemId
        and card.isUsed = false
        """)
    int markUsed(
            @Param("roomId") Long roomId,
            @Param("cardId") Long cardId,
            @Param("procurementItemId") Long procurementItemId,
            @Param("ownerRole") DebateRole ownerRole
    );

    @Modifying
    @Query("""
        update SituationCard card
        set card.isUsed = false
        where card.id = :cardId
        and card.room.id = :roomId
        and card.ownerRole = :ownerRole
        and card.procurementItem.id = :procurementItemId
        and card.isUsed = true
        """)
    int restoreUsedCard(
            @Param("roomId") Long roomId,
            @Param("cardId") Long cardId,
            @Param("procurementItemId") Long procurementItemId,
            @Param("ownerRole") DebateRole ownerRole
    );
}