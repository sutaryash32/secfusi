ALTER TABLE extension_policy
    ADD COLUMN IF NOT EXISTS fk_landingpage_id VARCHAR(36);
