-- R13: room's five persisted settings are:
-- max_card_count (existing) -> cardsPerItem
-- turn_time_limit (existing, minutes) -> itemSeconds = minutes * 60
-- max_items, speech_seconds, rebuttal_seconds (new)
-- Preserve existing rooms and their two legacy setting values.
ALTER TABLE room
    ADD COLUMN max_items INT NOT NULL DEFAULT 3,
    ADD COLUMN speech_seconds INT NOT NULL DEFAULT 120,
    ADD COLUMN rebuttal_seconds INT NOT NULL DEFAULT 60;
