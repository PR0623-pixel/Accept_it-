package com.debate.game.repository;

import com.debate.game.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// JpaRepository<다룰 엔티티, PK의 타입>
public interface RoomRepository extends JpaRepository<Room, Long> {
    
    // 코드 6자리로 방을 찾는 기능이 필요하다면 이렇게 한 줄만 적으면 끝.
    // Room.sql에 SELECT * FROM room WHERE room_code = ? 라고 짤 필요가 X
    Optional<Room> findByRoomCode(String roomCode);
}