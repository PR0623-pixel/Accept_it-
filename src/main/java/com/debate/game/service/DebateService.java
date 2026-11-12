package com.debate.game.service;

import com.debate.game.dto.ChainRequest;
import com.debate.game.dto.ChainResponse;
import com.debate.game.dto.DebateLogResponse;
import com.debate.game.dto.DebateStateResponse;
import com.debate.game.entity.*;
import com.debate.game.entity.enums.DebateRole;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DebateService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final SituationCardRepository cardRepository;
    private final DebateLogRepository debateLogRepository;

    // 카드 체인도 발언으로 기록하고 차례를 넘긴 뒤 기존 평가 응답 형식으로 반환한다.
    @Transactional
    public ChainResponse evaluateChain(ChainRequest request) {
        if (request == null || request.getCardId() == null) {
            throw new IllegalArgumentException("카드를 선택해야 합니다.");
        }
        DebateLog log = submitTurn(request.getRoomId(), request.getPlayerId(),
                request.getChatContext(), request.getCardId());
        return new ChainResponse(log.getAudienceReaction(), log.getScoreDelta(), true);
    }

    // 카드 없이 발언만 제출하는 경우에도 동일한 차례 검증·기록·교대 흐름을 사용한다.
    @Transactional
    public DebateStateResponse submitSpeech(Long roomId, Long playerId, String text) {
        submitTurn(roomId, playerId, text, null);
        return getState(roomId);
    }

    // 클라이언트가 현재 차례와 지금까지의 발언 기록을 동기화할 때 사용한다.
    @Transactional(readOnly = true)
    public DebateStateResponse getState(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));
        var logs = debateLogRepository.findByRoom_IdOrderByIdAsc(roomId).stream()
                .map(DebateLogResponse::from)
                .toList();
        return new DebateStateResponse(room.getId(), room.getStatus(),
                room.getCurrentTurnRole(), room.getTurnTimeLimit(), logs);
    }

    // 사회자 본인인지 확인한 뒤 방을 종료하고 이후 발언을 막는다.
    @Transactional
    public DebateStateResponse finishDebate(Long roomId, Long playerId) {
        requireIdentifiers(roomId, playerId);
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("플레이어를 찾을 수 없습니다."));
        if (player.getRoom() == null || !player.getRoom().getId().equals(roomId)) {
            throw new IllegalArgumentException("해당 방의 플레이어가 아닙니다.");
        }
        if (player.getRole() != DebateRole.MODERATOR) {
            throw new IllegalStateException("사회자만 토론을 종료할 수 있습니다.");
        }
        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            throw new IllegalStateException("현재 토론이 진행 중인 방이 아닙니다.");
        }

        room.setStatus(RoomStatus.FINISHED);
        room.setCurrentTurnRole(null);
        return getState(roomId);
    }

    // 일반 발언과 카드 체인의 공통 처리: 검증, 로그 저장, 다음 진영으로 차례 교대.
    private DebateLog submitTurn(Long roomId, Long playerId, String text, Long cardId) {
        requireIdentifiers(roomId, playerId);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("발언 내용은 비어 있을 수 없습니다.");
        }
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));
        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            throw new IllegalStateException("현재 토론이 진행 중인 방이 아닙니다.");
        }

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("플레이어를 찾을 수 없습니다."));
        if (player.getRoom() == null || !player.getRoom().getId().equals(roomId)) {
            throw new IllegalArgumentException("해당 방의 플레이어가 아닙니다.");
        }
        if (player.getRole() != room.getCurrentTurnRole()) {
            throw new IllegalStateException("현재 발언 차례가 아닙니다.");
        }
        if (player.getRole() != DebateRole.PRO && player.getRole() != DebateRole.CON) {
            throw new IllegalStateException("찬성 또는 반대 역할만 발언할 수 있습니다.");
        }

        SituationCard card = null;
        if (cardId != null) {
            card = cardRepository.findById(cardId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카드입니다."));
            if (card.getRoom() == null || !card.getRoom().getId().equals(roomId)) {
                throw new IllegalArgumentException("해당 방의 카드가 아닙니다.");
            }
            if (card.isUsed()) {
                throw new IllegalStateException("이미 사용된 카드입니다.");
            }
            if (card.getOwnerRole() != player.getRole()) {
                throw new IllegalStateException("본인 진영의 카드가 아닙니다.");
            }
            card.setUsed(true);
        }

        // 일반 발언은 점수 변화가 없고, 카드 체인 평가는 현재 더미 점수·반응을 기록한다.
        DebateLog log = new DebateLog();
        log.setRoom(room);
        log.setSpeakerRole(player.getRole());
        log.setChatContext(text);
        log.setUsedCard(card);
        if (card != null) {
            log.setAudienceReaction("시민: " + card.getKeyword() + " 카드를 활용한 지적이 아주 날카롭군요! 동의합니다.");
            log.setScoreDelta(10);
        }
        debateLogRepository.save(log);

        room.setCurrentTurnRole(player.getRole() == DebateRole.PRO
                ? DebateRole.CON : DebateRole.PRO);
        return log;
    }

    // 누락된 식별자를 저장소 조회 전에 거부해 잘못된 요청이 서버 오류로 처리되지 않게 한다.
    private void requireIdentifiers(Long roomId, Long playerId) {
        if (roomId == null || playerId == null) {
            throw new IllegalArgumentException("방 ID와 플레이어 ID는 필수입니다.");
        }
    }
}