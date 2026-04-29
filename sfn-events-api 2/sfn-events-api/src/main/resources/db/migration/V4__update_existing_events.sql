-- =====================================================
-- V4: Backfill data for existing events
-- =====================================================

-- Update existing events to have default event_type
UPDATE events
SET event_type = 'WEBSITE_VISIT'
WHERE event_type IS NULL;

-- Extract and populate domain from URL for existing records
UPDATE events
SET domain = CASE
    WHEN url IS NOT NULL AND url LIKE 'http%://%' THEN
        SUBSTRING(url FROM '(?:https?://)?(?:www\.)?([^/:]+)')
    ELSE NULL
END
WHERE domain IS NULL AND url IS NOT NULL;

-- Set default value for is_policy_violation
UPDATE events
SET is_policy_violation = FALSE
WHERE is_policy_violation IS NULL;
