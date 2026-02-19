package com.confessionverse.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ConfessionVoteSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(ConfessionVoteSchemaMigration.class);

    public ConfessionVoteSchemaMigration(JdbcTemplate jdbcTemplate) {
        try {
            String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            if (databaseName == null || databaseName.isBlank()) {
                return;
            }

            Integer isNullable = jdbcTemplate.queryForObject(
                    """
                    SELECT CASE WHEN IS_NULLABLE = 'YES' THEN 1 ELSE 0 END
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = ?
                      AND TABLE_NAME = 'confession_votes'
                      AND COLUMN_NAME = 'voter_ip'
                    """,
                    Integer.class,
                    databaseName
            );

            if (isNullable != null && isNullable == 0) {
                jdbcTemplate.execute("ALTER TABLE confession_votes MODIFY COLUMN voter_ip VARCHAR(255) NULL");
                log.info("Schema migration applied: confession_votes.voter_ip is now nullable");
            }
        } catch (Exception ex) {
            log.warn("Could not auto-migrate confession_votes.voter_ip to nullable: {}", ex.getMessage());
        }
    }
}
