package com.debate.game.entity;

import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.entity.enums.DebateRole;
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

    // R02: Host is a room-management authority, NOT PRO/CON/MODERATOR.
    // Existing rooms are allowed to have a null host; new rooms always assign one.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_player_id", unique = true)
    private Player hostPlayer;

    private String currentTopic; // 현재 방의 토론 주제

    private int maxCardCount; // 받는 카드 수 제한

    private int turnTimeLimit; // 턴 시간 제한 (3~20분)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_item_id")
    private ProcurementItem currentProcurementItem; // AI가 선택해 중앙에 띄운 조달 물품

    @Enumerated(EnumType.STRING)
    private RoomStatus status = RoomStatus.WAITING;

    // 진행 중인 토론에서 다음 발언 권한을 가진 진영이며, 시작 전·종료 후에는 null이다.
    @Enumerated(EnumType.STRING)
    private DebateRole currentTurnRole;
}