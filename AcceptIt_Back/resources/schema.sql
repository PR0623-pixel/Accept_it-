CREATE DATABASE IF NOT EXISTS debate_db;
USE debate_db;

-- 1. 토론 방 및 정책 데이터
CREATE TABLE debate_room (
    room_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    policy_topic TEXT NOT NULL,
    is_custom BOOLEAN DEFAULT FALSE,
    current_status VARCHAR(50) DEFAULT 'WAITING', -- WAITING, IN_PROGRESS, FINISHED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. 상황/체인 카드 Pool
CREATE TABLE situation_card (
    card_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    keyword VARCHAR(50) NOT NULL,
    card_type VARCHAR(50) DEFAULT 'SITUATION', -- SITUATION, CORE_TOKEN
    FOREIGN KEY (room_id) REFERENCES debate_room(room_id) ON DELETE CASCADE
);

-- 3. 토론 진행 내역 및 AI 평가 로그
CREATE TABLE debate_log (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    speaker_role VARCHAR(20) NOT NULL, -- PRO, CON, MODERATOR
    message TEXT NOT NULL,
    used_card_id BIGINT NULL,
    audience_score_change INT DEFAULT 0,
    audience_reaction TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);