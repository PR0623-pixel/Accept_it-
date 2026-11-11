package com.debate.game.entity;

import com.debate.game.entity.enums.DebateRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class CardTemplate {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private ProcurementItem item; // 엮여있는 조달 물품

    @Enumerated(EnumType.STRING)
    private DebateRole targetRole; // 찬성(PRO)용인지 반대(CON)용인지 구분

    @Column(nullable = false)
    private String keyword; // 핵심 키워드

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // 상황 설명 원본 텍스트
}