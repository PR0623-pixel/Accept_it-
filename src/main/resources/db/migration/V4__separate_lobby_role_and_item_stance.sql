-- R09: room role (NONE/GUEST/MODERATOR) is independent of per-item stance.
-- Old player.role values used PRO/CON as permanent lobby roles, so convert
-- them to GUEST. We intentionally DO NOT carry those old values to stance:
-- no item-specific vote was actually recorded by the old lobby API.
-- Use VARCHAR between the two ENUM definitions to allow safe conversion.
ALTER TABLE player MODIFY COLUMN role VARCHAR(20) NULL;

UPDATE player
SET role = CASE
    WHEN role IN ('PRO', 'CON') THEN 'GUEST'
    WHEN role IS NULL OR role = '' THEN 'NONE'
    ELSE role
END;

ALTER TABLE player
    MODIFY COLUMN role ENUM('GUEST', 'MODERATOR', 'NONE') NOT NULL DEFAULT 'NONE',
    ADD COLUMN stance ENUM('CON', 'PRO') NULL;
