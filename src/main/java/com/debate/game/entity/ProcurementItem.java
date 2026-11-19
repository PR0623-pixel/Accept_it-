package com.debate.game.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class ProcurementItem {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String itemName; // 예: "의료 키트", "전술 드론"

    private String topicCategory; // 예: "국방", "의료"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;
}