-- V16: Add provisioning retry tracking columns to tenant table

ALTER TABLE tenant ADD COLUMN IF NOT EXISTS provision_retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS provision_error VARCHAR(1000);
