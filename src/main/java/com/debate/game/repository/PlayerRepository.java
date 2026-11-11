package com.debate.game.repository;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByRoom(Room room);

    long countByRoom(Room room);
}
