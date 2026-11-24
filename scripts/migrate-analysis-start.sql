-- Run after team settings migration. Before this script, start the updated
-- schema initialization once so version_id and its index are present.
ALTER TABLE conversations DROP INDEX uk_conversations_team_external;
ALTER TABLE messages DROP INDEX uk_messages_conversation_idx;
