-- V9: Clean up duplicate extensions and ensure unique constraint
-- This migration removes duplicate installed_extensions records (keeping the oldest)
-- and ensures the unique constraint is properly enforced

-- Step 1: Delete duplicate extensions (keep the oldest record for each device+extension combination)
DELETE FROM installed_extensions
WHERE pk_installed_extension_id IN (
    SELECT pk_installed_extension_id FROM (
        SELECT pk_installed_extension_id,
               ROW_NUMBER() OVER (PARTITION BY fk_device_id, extension_id ORDER BY first_seen_at ASC, pk_installed_extension_id ASC) as rn
        FROM installed_extensions
    ) sub WHERE rn > 1
);

-- Step 2: Ensure unique constraint exists (recreate if needed)
-- Drop if exists and recreate to ensure it's properly applied
ALTER TABLE installed_extensions DROP CONSTRAINT IF EXISTS uk_device_extension;
ALTER TABLE installed_extensions ADD CONSTRAINT uk_device_extension UNIQUE (fk_device_id, extension_id);

-- Step 3: Create index to speed up lookups (if not exists)
CREATE INDEX IF NOT EXISTS idx_installed_ext_device_ext ON installed_extensions(fk_device_id, extension_id);
