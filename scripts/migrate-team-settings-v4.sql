-- Run once on an existing MySQL database, after migrate-team-settings-history.sql
-- and migrate-team-settings-initial-version.sql.
-- Before this script, start the updated application once so the new columns and the
-- idx_team_task_types_team_active index (which then supports the team_id foreign key) are present.

-- Same name may be reused by a new task type when its conditions change.
ALTER TABLE team_task_types
    DROP INDEX uk_team_task_types_team_name,
    MODIFY COLUMN name VARCHAR(200) NOT NULL,
    MODIFY COLUMN success_definition TEXT NOT NULL;

ALTER TABLE analysis_items
    MODIFY COLUMN task_name_snapshot VARCHAR(200) NULL,
    MODIFY COLUMN success_definition_snapshot TEXT NULL;

-- v3 stored only a success definition. Failure and unknown conditions stay empty until the next save.
UPDATE team_task_types
SET success_conditions = JSON_ARRAY(success_definition)
WHERE JSON_LENGTH(success_conditions) = 0;

-- Link the current settings to the active task type. Earlier versions cannot be recovered.
UPDATE team_settings
SET task_type_id = (
    SELECT MIN(team_task_types.id)
    FROM team_task_types
    WHERE team_task_types.team_id = team_settings.team_id
      AND team_task_types.is_active = TRUE
)
WHERE is_current = TRUE
  AND task_type_id IS NULL
  AND target_from IS NOT NULL
  AND target_to IS NOT NULL;
