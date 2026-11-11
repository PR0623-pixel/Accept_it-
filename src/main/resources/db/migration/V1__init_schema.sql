
-- AcceptIt 초기 데이터베이스 스키마
-- Java Entity 7개 기준
-- MySQL 8.0 / Hibernate 7

-- =========================================
-- 1. ProcurementItem: 조달 물품
-- =========================================

CREATE TABLE procurement_item (
                                  id BIGINT NOT NULL AUTO_INCREMENT,
                                  item_name VARCHAR(255) NOT NULL,
                                  topic_category VARCHAR(255),

                                  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =========================================
-- 2. Room: 토론 방
-- =========================================

CREATE TABLE room (
                      id BIGINT NOT NULL AUTO_INCREMENT,
                      room_code VARCHAR(6) NOT NULL,
                      current_topic VARCHAR(255),
                      max_card_count INT NOT NULL,
                      turn_time_limit INT NOT NULL,
                      current_item_id BIGINT,
                      status ENUM(
        'FINISHED',
        'IN_PROGRESS',
        'WAITING'
    ),

                      PRIMARY KEY (id),

                      CONSTRAINT uk_room_room_code
                          UNIQUE (room_code),

                      CONSTRAINT fk_room_current_item
                          FOREIGN KEY (current_item_id)
                              REFERENCES procurement_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =========================================
-- 3. Player: 참가자
-- =========================================

CREATE TABLE player (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        room_id BIGINT,
                        player_name VARCHAR(255),
                        role ENUM('CON', 'MODERATOR', 'PRO'),
                        is_ready BIT(1) NOT NULL DEFAULT b'0',

                        PRIMARY KEY (id),

                        CONSTRAINT fk_player_room
                            FOREIGN KEY (room_id)
                                REFERENCES room(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =========================================
-- 4. SituationCard: 상황 카드
-- =========================================

CREATE TABLE situation_card (
                                id BIGINT NOT NULL AUTO_INCREMENT,
                                room_id BIGINT,
                                keyword VARCHAR(50) NOT NULL,
                                content TEXT NOT NULL,
                                owner_role ENUM('CON', 'MODERATOR', 'PRO') NOT NULL,
                                is_used BIT(1) NOT NULL DEFAULT b'0',

                                PRIMARY KEY (id),

                                CONSTRAINT fk_situation_card_room
                                    FOREIGN KEY (room_id)
                                        REFERENCES room(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =========================================
-- 5. CardTemplate: 카드 원본
-- =========================================

CREATE TABLE card_template (
                               id BIGINT NOT NULL AUTO_INCREMENT,
                               item_id BIGINT,
                               target_role ENUM('CON', 'MODERATOR', 'PRO'),
                               keyword VARCHAR(255) NOT NULL,
                               content TEXT NOT NULL,

                               PRIMARY KEY (id),

                               CONSTRAINT fk_card_template_item
                                   FOREIGN KEY (item_id)
                                       REFERENCES procurement_item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =========================================
-- 6. InGameCard: 지급받은 카드
-- =========================================

CREATE TABLE in_game_card (
                              id BIGINT NOT NULL AUTO_INCREMENT,
                              room_id BIGINT,
                              player_id BIGINT,
                              template_id BIGINT,
                              is_used BIT(1) NOT NULL DEFAULT b'0',

                              PRIMARY KEY (id),

                              CONSTRAINT fk_in_game_card_room
                                  FOREIGN KEY (room_id)
                                      REFERENCES room(id),

                              CONSTRAINT fk_in_game_card_player
                                  FOREIGN KEY (player_id)
                                      REFERENCES player(id),

                              CONSTRAINT fk_in_game_card_template
                                  FOREIGN KEY (template_id)
                                      REFERENCES card_template(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- =========================================
-- 7. DebateLog: 토론 기록
-- =========================================

CREATE TABLE debate_log (
                            id BIGINT NOT NULL AUTO_INCREMENT,
                            room_id BIGINT,
                            speaker_role ENUM('CON', 'MODERATOR', 'PRO'),
                            chat_context TEXT,
                            used_card_id BIGINT,
                            score_delta INT NOT NULL,
                            audience_reaction TEXT,
                            created_at DATETIME(6),

                            PRIMARY KEY (id),

                            CONSTRAINT fk_debate_log_room
                                FOREIGN KEY (room_id)
                                    REFERENCES room(id),

                            CONSTRAINT fk_debate_log_used_card
                                FOREIGN KEY (used_card_id)
                                    REFERENCES situation_card(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
