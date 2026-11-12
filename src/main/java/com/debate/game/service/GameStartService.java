package com.debate.game.service;

import com.debate.game.entity.Player;
import com.debate.game.entity.Room;
import com.debate.game.entity.SituationCard;
import com.debate.game.entity.enums.DebateRole;
import com.debate.game.entity.enums.RoomStatus;
import com.debate.game.repository.PlayerRepository;
import com.debate.game.repository.RoomRepository;
import com.debate.game.repository.SituationCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GameStartService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final SituationCardRepository cardRepository;
    private final OpenAiService openAiService;

    @Transactional
    public void tryStartGame(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new IllegalStateException("이미 시작되었거나 종료된 방입니다.");
        }

        List<Player> players = playerRepository.findByRoom(room);
        
        // 1. 방 상태 검증: 3명이 다 모였는지 & 모두 Ready 상태인지
        if (players.size() < 3) {
            throw new IllegalStateException("아직 3명이 모이지 않았습니다.");
        }
        boolean allReady = players.stream().allMatch(Player::isReady);
        if (!allReady) {
            throw new IllegalStateException("아직 역할을 선택하지 않은 플레이어가 있습니다.");
        }

        // 2. 방 상태를 '진행중'으로 변경
        room.setStatus(RoomStatus.IN_PROGRESS);
        // 토론은 찬성 진영부터 시작하며, 이후 발언마다 반대 진영과 교대한다.
        room.setCurrentTurnRole(DebateRole.PRO);

        // 3. AI를 통한 상황 카드 생성 및 분배 (최대 카드 매수 5장)
        generateAndDistributeCards(room);
    }

    private void generateAndDistributeCards(Room room) {
        // TODO: 향후 OpenAI API를 호출하여 해당 쟁점(Topic)에 맞는 텍스트를 받아오는 부분
        // 지금은 하드코딩으로 찬성측 5장, 반대측 5장 더미 데이터를 DB에 넣어줍니다.
        int maxPickCount = 5; // 다이어그램의 최대 뽑을 수 (5개)

        for (int i = 0; i < maxPickCount; i++) {
            // 찬성측 카드 부여
            SituationCard proCard = new SituationCard();
            proCard.setRoom(room);
            proCard.setKeyword("찬성 키워드 " + (i + 1));
            proCard.setContent("이러이러한 정황상 찬성 측에 유리한 상황입니다.");
            proCard.setOwnerRole(DebateRole.PRO);
            cardRepository.save(proCard);

            // 반대측 카드 부여
            SituationCard conCard = new SituationCard();
            conCard.setRoom(room);
            conCard.setKeyword("반대 키워드 " + (i + 1));
            conCard.setContent("이러이러한 정황상 정책 시행이 어렵다는 반대 측 카드입니다.");
            conCard.setOwnerRole(DebateRole.CON);
            cardRepository.save(conCard);
        }
        
        // 사회자는 카드를 받지 않습니다 (권한만 존재).
    }
}