package com.confessionverse.backend.config;

import com.confessionverse.backend.service.ChatRoomService;
import com.confessionverse.backend.service.ChatInvitationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ChatRoomStartupMaintenance implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ChatRoomStartupMaintenance.class);
    private static final String MEMBERSHIP_UNIQUE_KEY = "uk_chatroom_users_room_user";

    private final ChatRoomService chatRoomService;
    private final ChatInvitationService chatInvitationService;
    private final JdbcTemplate jdbcTemplate;

    public ChatRoomStartupMaintenance(ChatRoomService chatRoomService,
                                      ChatInvitationService chatInvitationService,
                                      JdbcTemplate jdbcTemplate) {
        this.chatRoomService = chatRoomService;
        this.chatInvitationService = chatInvitationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureChatRoomMembershipStateColumns();
        ensureChatRoomMembershipUniqueConstraint();
        chatRoomService.ensureBaseRoomsExist();
        int added = chatRoomService.backfillStandardRoomMemberships();
        int inviteMembershipsFixed = chatInvitationService.backfillAcceptedInviteMemberships();
        log.info("chat.startup baseRoomsReady=true membershipsBackfilled={} inviteMembershipsFixed={}",
                added,
                inviteMembershipsFixed);
    }

    private void ensureChatRoomMembershipStateColumns() {
        try {
            if (!columnExists("chatroom_users", "active")) {
                jdbcTemplate.execute("ALTER TABLE chatroom_users ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE");
            }
            if (!columnExists("chatroom_users", "joined_at")) {
                jdbcTemplate.execute("ALTER TABLE chatroom_users ADD COLUMN joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            }
            if (!columnExists("chatroom_users", "left_at")) {
                jdbcTemplate.execute("ALTER TABLE chatroom_users ADD COLUMN left_at TIMESTAMP NULL");
            }
            if (!columnExists("chatroom_users", "hidden_at")) {
                jdbcTemplate.execute("ALTER TABLE chatroom_users ADD COLUMN hidden_at TIMESTAMP NULL");
            }
            jdbcTemplate.execute("ALTER TABLE chatroom_users MODIFY COLUMN active BOOLEAN NOT NULL DEFAULT TRUE");
            jdbcTemplate.execute("ALTER TABLE chatroom_users MODIFY COLUMN joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            jdbcTemplate.execute("UPDATE chatroom_users SET active = TRUE WHERE active IS NULL");
            jdbcTemplate.execute("UPDATE chatroom_users SET joined_at = CURRENT_TIMESTAMP WHERE joined_at IS NULL");
        } catch (Exception ex) {
            log.warn("Could not ensure chatroom_users state columns: {}", ex.getMessage());
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try {
            String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (databaseName == null || databaseName.isBlank()) {
                return false;
            }
            Integer existing = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(1)
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = ?
                      AND TABLE_NAME = ?
                      AND COLUMN_NAME = ?
                    """,
                    Integer.class,
                    databaseName,
                    tableName,
                    columnName
            );
            return existing != null && existing > 0;
        } catch (Exception ex) {
            log.warn("Could not inspect column {}.{}: {}", tableName, columnName, ex.getMessage());
            return false;
        }
    }

    private void ensureChatRoomMembershipUniqueConstraint() {
        try {
            String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (databaseName == null || databaseName.isBlank()) {
                return;
            }
            Integer existing = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(1)
                    FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_SCHEMA = ?
                      AND TABLE_NAME = 'chatroom_users'
                      AND INDEX_NAME = ?
                    """,
                    Integer.class,
                    databaseName,
                    MEMBERSHIP_UNIQUE_KEY
            );
            if (existing != null && existing > 0) {
                return;
            }
            jdbcTemplate.execute(
                    "ALTER TABLE chatroom_users " +
                            "ADD CONSTRAINT " + MEMBERSHIP_UNIQUE_KEY + " UNIQUE (chatroom_id, user_id)"
            );
            log.info("Schema migration applied: chatroom_users unique membership constraint created");
        } catch (Exception ex) {
            log.warn("Could not ensure chatroom_users unique membership constraint: {}", ex.getMessage());
        }
    }
}
