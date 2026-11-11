package com.debate.game.entity;

import com.debate.game.entity.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Room {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 6)
    private String roomCode;

    private String currentTopic; // 현재 방의 토론 주제
    
    private int turnTimeLimit; // 턴 시간 제한 (3~20분)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_item_id")
    private ProcurementItem currentProcurementItem; // AI가 선택해 중앙에 띄운 조달 물품

    @Enumerated(EnumType.STRING)
    private RoomStatus status = RoomStatus.WAITING;
}