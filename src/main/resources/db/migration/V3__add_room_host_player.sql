-- R02: keep host authority independent from lobby roles and per-item PRO/CON stance.
-- Existing rooms remain without an assigned host until explicitly migrated/retired.
ALTER TABLE room
    ADD COLUMN host_player_id BIGINT NULL,
    ADD CONSTRAINT uk_room_host_player_id UNIQUE (host_player_id),
    ADD CONSTRAINT fk_room_host_player FOREIGN KEY (host_player_id) REFERENCES player(id);
