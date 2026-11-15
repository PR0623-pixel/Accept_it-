package com.debate.game.entity;

import com.debate.game.entity.enums.DebateRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Player {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    private String playerName;

    @Enumerated(EnumType.STRING)
    private DebateRole role; // PRO, CON, MODERATOR

    private boolean isReady = false;
}