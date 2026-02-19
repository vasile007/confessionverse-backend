CREATE TABLE IF NOT EXISTS chat_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chat_room_id BIGINT NOT NULL,
    inviter_user_id BIGINT NOT NULL,
    invitee_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    pending_guard TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END
    ) STORED,
    created_at DATETIME NOT NULL,
    responded_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_invitations_chat_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_room(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_invitations_inviter
        FOREIGN KEY (inviter_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_invitations_invitee
        FOREIGN KEY (invitee_user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_chat_invitation_invitee_status_created
    ON chat_invitations (invitee_user_id, status, created_at);

CREATE INDEX idx_chat_invitation_pending_lookup
    ON chat_invitations (chat_room_id, invitee_user_id, status);

CREATE UNIQUE INDEX uk_chat_invitation_single_pending
    ON chat_invitations (chat_room_id, invitee_user_id, pending_guard);
