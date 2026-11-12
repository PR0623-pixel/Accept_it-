ALTER TABLE room
    ADD COLUMN current_turn_role ENUM('CON', 'MODERATOR', 'PRO') NULL;
