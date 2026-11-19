package com.debate.game.entity;

import com.debate.game.entity.enums.LobbyRole;
import com.debate.game.entity.enums.Stance;
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

    // Persistent room assignment; Host is stored separately on Room.hostPlayer.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LobbyRole role = LobbyRole.NONE;

    // Current procurement item's PRO/CON selection, not a lobby role.
    // A voting stage will set and reset it for each item (G-stage work).
    @Enumerated(EnumType.STRING)
    private Stance stance;

    private boolean isReady = false;
}
