package com.debate.game.entity;

import com.debate.game.entity.enums.DebateRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class SituationCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 1. 해당 카드가 속한 토론 방 (다대일 매핑)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    // 2. AI가 추출하거나 커스텀 모드에서 설정한 쟁점 키워드 (예: "여성의 임신률 증가")
    @Column(nullable = false, length = 50)
    private String keyword;

    // 3. 정황 및 상황에 대한 구체적인 설명 텍스트
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    // 4. 이 카드를 소유한 진영 (찬성 PRO / 반대 CON)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebateRole ownerRole;

    // 5. 체인(반박/의견 제시)용 아이템으로 소모되었는지 여부
    @Column(nullable = false)
    private boolean isUsed = false;
}