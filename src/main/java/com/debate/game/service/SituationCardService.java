package com.debate.game.service;

import com.debate.game.entity.Room;
import com.debate.game.entity.enums.DebateRole;
import com.debate.game.websocket.GameActionException;
import com.debate.game.exception.InvalidRoomCodeException;
import com.debate.game.repository.RoomRepository;
import com.debate.game.repository.SituationCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SituationCardService {
    private final RoomRepository roomRepository;
    private final SituationCardRepository situationCardRepository;

    @Transactional
    public void markUsed(String roomCode, Long cardId, DebateRole ownerRole) {
        Room room = findRoom(roomCode);
        validateCardArguments(cardId, ownerRole);

        int updated = situationCardRepository.markUsed(
                room.getId(),
                cardId,
                ownerRole
        );

        if (updated != 1) {
            throw new GameActionException(
                    "CARD_REQUIRED",
                    "사용 가능한 내 진영의 카드가 없습니다."
            );
        }
    }

    @Transactional
    public void restore(String roomCode, Long cardId, DebateRole ownerRole) {
        Room room = findRoom(roomCode);
        validateCardArguments(cardId, ownerRole);

        int updated = situationCardRepository.restoreUsedCard(
                room.getId(),
                cardId,
                ownerRole
        );

        if (updated != 1) {
            throw new GameActionException(
                    "INVALID",
                    "사용한 카드를 복구할 수 없습니다."
            );
        }
    }

    private Room findRoom(String roomCode) {
        String code = roomCode == null
                ? ""
                : roomCode.strip().toUpperCase(Locale.ROOT);

        if (!code.matches("[A-Z0-9]{6}")) {
            throw new InvalidRoomCodeException("방을 찾을 수 없습니다.");
        }

        return roomRepository.findByRoomCode(code)
                .orElseThrow(() ->
                        new InvalidRoomCodeException("방을 찾을 수 없습니다."));
    }

    private void validateCardArguments(Long cardId, DebateRole ownerRole) {
        if (cardId == null || cardId <= 0 || ownerRole == null) {
            throw new GameActionException(
                    "CARD_REQUIRED",
                    "카드 정보가 올바르지 않습니다."
            );
        }
    }
}