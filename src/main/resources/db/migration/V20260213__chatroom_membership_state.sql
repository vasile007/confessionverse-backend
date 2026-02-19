ALTER TABLE chatroom_users
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE chatroom_users
    ADD COLUMN IF NOT EXISTS joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE chatroom_users
    ADD COLUMN IF NOT EXISTS left_at TIMESTAMP NULL;

ALTER TABLE chatroom_users
    ADD COLUMN IF NOT EXISTS hidden_at TIMESTAMP NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_chatroom_users_room_user
    ON chatroom_users (chatroom_id, user_id);

UPDATE chatroom_users
SET active = TRUE
WHERE active IS NULL;

UPDATE chatroom_users
SET joined_at = CURRENT_TIMESTAMP
WHERE joined_at IS NULL;
