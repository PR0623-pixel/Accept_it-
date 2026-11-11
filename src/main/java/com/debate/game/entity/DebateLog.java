package com.debate.game.entity;
import com.debate.game.entity.enums.DebateRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
public class DebateLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @Enumerated(EnumType.STRING)
    private DebateRole speakerRole; // 발언자 진영

    @Column(columnDefinition = "TEXT")
    private String chatContext; // 체인을 걸기 직전까지의 핵심 채팅 내용

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_card_id")
    private SituationCard usedCard; // 제시한 카드

    private int scoreDelta; // 청중 평가에 따른 점수 변동치 (예: +5, -3)
    
    @Column(columnDefinition = "TEXT")
    private String audienceReaction; // 청중(AI)의 평가 대사

    private LocalDateTime createdAt = LocalDateTime.now();
}