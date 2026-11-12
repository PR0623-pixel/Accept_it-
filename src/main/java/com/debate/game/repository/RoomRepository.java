package com.debate.game.repository;

import com.debate.game.entity.Room;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

// JpaRepository<다룰 엔티티, PK의 타입>
public interface RoomRepository extends JpaRepository<Room, Long> {
    
    // 코드 6자리로 방을 찾는 기능이 필요하다면 이렇게 한 줄만 적으면 끝.
    // Room.sql에 SELECT * FROM room WHERE room_code = ? 라고 짤 필요가 X
    Optional<Room> findByRoomCode(String roomCode);

    // 발언 처리 중 같은 방에 대한 동시 요청이 차례를 중복 진행하지 않도록 행을 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") Long id);
    //같은 방에 대한 동시 발언 요청 잠금
}