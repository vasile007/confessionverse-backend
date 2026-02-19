UPDATE chat_room
SET room_type = 'DIRECT'
WHERE room_type IS NULL OR room_type = '';

ALTER TABLE chat_room
    MODIFY COLUMN room_type VARCHAR(50) NOT NULL DEFAULT 'DIRECT';
