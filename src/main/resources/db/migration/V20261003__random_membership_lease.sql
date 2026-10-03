-- Non-destructive lease timestamp for reclaiming random-room capacity after crashes/logouts.
-- MySQL does not consistently support ADD COLUMN IF NOT EXISTS across the
-- versions used in production, so make the DDL repeatable via information_schema.
SET @last_active_at_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'chatroom_users'
      AND COLUMN_NAME = 'last_active_at'
);

SET @last_active_at_ddl = IF(
    @last_active_at_exists = 0,
    'ALTER TABLE chatroom_users ADD COLUMN last_active_at DATETIME(6) NULL AFTER left_at',
    'SELECT ''chatroom_users.last_active_at already exists'' AS migration_info'
);

PREPARE last_active_at_statement FROM @last_active_at_ddl;
EXECUTE last_active_at_statement;
DEALLOCATE PREPARE last_active_at_statement;

UPDATE chatroom_users
SET last_active_at = COALESCE(joined_at, CURRENT_TIMESTAMP(6))
WHERE active = TRUE AND last_active_at IS NULL;
