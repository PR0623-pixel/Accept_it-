ALTER TABLE situation_card
    ADD COLUMN procurement_item_id BIGINT NULL;

-- 기존 카드는 현재 방에 저장된 물품을 기준으로 연결합니다.
UPDATE situation_card sc
JOIN room r ON r.id = sc.room_id
SET sc.procurement_item_id = r.current_item_id
WHERE sc.procurement_item_id IS NULL
  AND r.current_item_id IS NOT NULL;

CREATE INDEX idx_situation_card_procurement_item
    ON situation_card (procurement_item_id);

ALTER TABLE situation_card
    ADD CONSTRAINT fk_situation_card_procurement_item
        FOREIGN KEY (procurement_item_id)
        REFERENCES procurement_item (id);