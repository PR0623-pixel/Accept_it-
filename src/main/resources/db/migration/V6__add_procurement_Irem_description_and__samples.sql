ALTER TABLE procurement_item
    ADD COLUMN description TEXT NULL;

UPDATE procurement_item
SET description = CONCAT(item_name, ' 관련 토론 쟁점')
WHERE description IS NULL OR description = '';

ALTER TABLE procurement_item
    MODIFY COLUMN description TEXT NOT NULL;

INSERT INTO procurement_item (item_name, topic_category, description)
SELECT '전기 통근버스 100대', '환경',
       '공공기관 통근버스를 전기버스로 교체합니다. 친환경 vs 초기 비용 부담'
WHERE NOT EXISTS (
    SELECT 1 FROM procurement_item WHERE item_name = '전기 통근버스 100대'
);

INSERT INTO procurement_item (item_name, topic_category, description)
SELECT '학교 급식용 친환경 식자재', '교육',
       '전국 학교 급식에 친환경 식자재를 일괄 조달합니다. 품질 vs 단가'
WHERE NOT EXISTS (
    SELECT 1 FROM procurement_item WHERE item_name = '학교 급식용 친환경 식자재'
);

INSERT INTO procurement_item (item_name, topic_category, description)
SELECT '중소기업 혁신제품 우선구매', '산업',
       '혁신제품을 수의계약으로 우선 구매합니다. 육성 vs 공정경쟁'
WHERE NOT EXISTS (
    SELECT 1 FROM procurement_item WHERE item_name = '중소기업 혁신제품 우선구매'
);

INSERT INTO procurement_item (item_name, topic_category, description)
SELECT '비축 마스크 500만 장', '보건',
       '감염병 대비 비축물자로 마스크를 확보합니다. 안보 vs 보관 비용'
WHERE NOT EXISTS (
    SELECT 1 FROM procurement_item WHERE item_name = '비축 마스크 500만 장'
);

INSERT INTO procurement_item (item_name, topic_category, description)
SELECT 'AI 민원 챗봇 시스템', '기술',
       '공공기관 민원 응대용 AI 챗봇을 조달합니다. 효율 vs 안전성'
WHERE NOT EXISTS (
    SELECT 1 FROM procurement_item WHERE item_name = 'AI 민원 챗봇 시스템'
);