package com.debate.game.repository;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findByRoom(Room room);

    long countByRoom(Room room);

    // R10: scalar lookup so Player is not hydrated before the room row lock.
    @Query("select p.room.id from Player p where p.id = :playerId")
    Optional<Long> findRoomIdByPlayerId(@Param("playerId") Long playerId);

    // R10: current read (FOR UPDATE), avoiding a stale REPEATABLE READ role snapshot.
    // Always acquire the Room row lock FIRST (RoomService.selectRole).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Player p where p.room = :room")
    List<Player> findByRoomForUpdate(@Param("room") Room room);
}
