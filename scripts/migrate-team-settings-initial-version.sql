-- Run once after migrate-team-settings-history.sql.
-- In v4, version 0 means "not configured yet". Settings saved in v3 were left at version 0,
-- so move only those that already have a target period and an active task type to version 1.
UPDATE team_settings
SET version = 1
WHERE version = 0
  AND is_current = TRUE
  AND target_from IS NOT NULL
  AND target_to IS NOT NULL
  AND EXISTS (
    SELECT 1
    FROM team_task_types
    WHERE team_task_types.team_id = team_settings.team_id
      AND team_task_types.is_active = TRUE
  );
