package com.debate.game.repository;

import com.debate.game.entity.ProcurementItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcurementItemRepository extends JpaRepository<ProcurementItem, Long> {
}