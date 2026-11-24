-- Run once on an existing v3 MySQL database before deploying the versioned settings code.
-- The existing row becomes the current baseline (version 0).
ALTER TABLE team_settings
    ADD COLUMN version INT NOT NULL DEFAULT 0,
    ADD COLUMN is_current BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT uk_team_settings_team_version UNIQUE (team_id, version),
    ADD INDEX idx_team_settings_current (team_id, is_current);

-- Keep the foreign key supported by the new composite indexes when removing the old unique index.
SET @drop_indexes = (
    SELECT GROUP_CONCAT(CONCAT('DROP INDEX `', REPLACE(index_name, '`', '``'), '`') SEPARATOR ', ')
    FROM (
        SELECT index_name
        FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'team_settings' AND non_unique = 0
        GROUP BY index_name
        HAVING COUNT(*) = 1 AND MAX(column_name) = 'team_id'
    ) AS old_indexes
);
SET @drop_statement = COALESCE(CONCAT('ALTER TABLE team_settings ', @drop_indexes), 'DO 0');
PREPARE drop_legacy_indexes FROM @drop_statement;
EXECUTE drop_legacy_indexes;
DEALLOCATE PREPARE drop_legacy_indexes;
