package com.debate.game.service;

import com.debate.game.dto.ChainRequest;
import com.debate.game.dto.ChainResponse;
import com.debate.game.entity.*;
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
    private final DebateLogRepository debateLogRepository; // JpaRepository<DebateLog, Long> 생성 필요

    @Transactional
    public ChainResponse evaluateChain(ChainRequest request) {
        // 1. 기본 검증
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));
        if (room.getStatus() != RoomStatus.IN_PROGRESS) {
            throw new IllegalStateException("현재 토론이 진행 중인 방이 아닙니다.");
        }

        Player player = playerRepository.findById(request.getPlayerId())
                .orElseThrow(() -> new IllegalArgumentException("플레이어를 찾을 수 없습니다."));
        
        SituationCard card = cardRepository.findById(request.getCardId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카드입니다."));

        // 2. 카드 유효성 검증 (아이템 소모 개념)
        if (card.isUsed()) {
            throw new IllegalStateException("이미 사용된 카드입니다.");
        }
        if (card.getOwnerRole() != player.getRole()) {
            throw new IllegalStateException("본인 진영의 카드가 아닙니다.");
        }

        // 3. 카드 소모 처리
        card.setUsed(true);

        // 4. AI 청중 평가 로직 (현재는 더미 데이터)
        // TODO: 향후 chatContext와 card.getContent()를 OpenAI Prompt로 묶어서 전송
        String dummyReaction = "시민: " + card.getKeyword() + " 카드를 활용한 지적이 아주 날카롭군요! 동의합니다.";
        int dummyScoreDelta = 10; // 10점 데미지(또는 획득) 부여
        boolean dummySuccess = true;

        // 5. 토론 로그(DebateLog) 기록
        DebateLog log = new DebateLog();
        log.setRoom(room);
        log.setSpeakerRole(player.getRole());
        log.setChatContext(request.getChatContext());
        log.setUsedCard(card);
        log.setAudienceReaction(dummyReaction);
        log.setScoreDelta(dummyScoreDelta);
        debateLogRepository.save(log);

        return new ChainResponse(dummyReaction, dummyScoreDelta, dummySuccess);
    }
}