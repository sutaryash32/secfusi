-- =====================================================
-- V6: Add file operation columns for Browser Analytics
-- =====================================================

-- Add file operation specific columns to events table
ALTER TABLE events ADD COLUMN IF NOT EXISTS file_operation_type VARCHAR(20);
ALTER TABLE events ADD COLUMN IF NOT EXISTS file_name VARCHAR(500);
ALTER TABLE events ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS file_type VARCHAR(100);
ALTER TABLE events ADD COLUMN IF NOT EXISTS is_blocked BOOLEAN DEFAULT FALSE;

-- Create indexes for file operation queries
CREATE INDEX IF NOT EXISTS idx_events_file_op_type ON events(file_operation_type);
CREATE INDEX IF NOT EXISTS idx_events_is_blocked ON events(is_blocked);

-- Composite index for file operation analytics queries
CREATE INDEX IF NOT EXISTS idx_events_file_analytics
    ON events(fk_tenant_id, file_operation_type, time_stamp)
    WHERE file_operation_type IS NOT NULL;

-- Composite index for blocked operations queries
CREATE INDEX IF NOT EXISTS idx_events_blocked_ops
    ON events(fk_tenant_id, file_operation_type, is_blocked, time_stamp)
    WHERE is_blocked = TRUE;

-- Add comments for new columns
COMMENT ON COLUMN events.file_operation_type IS 'Type of file operation: DOWNLOAD, UPLOAD, PRINT, CLIPBOARD_COPY, CLIPBOARD_PASTE';
COMMENT ON COLUMN events.file_name IS 'Name of the file involved in the operation';
COMMENT ON COLUMN events.file_size IS 'Size of the file in bytes';
COMMENT ON COLUMN events.file_type IS 'MIME type or file extension';
COMMENT ON COLUMN events.is_blocked IS 'Flag indicating if this operation was blocked by DLP policy';
