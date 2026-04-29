-- V23: Add email tracking columns to Users table
--
-- Purpose:
--   Track when welcome email and reset-password (required-action) email were last sent
--   and how many times they have been sent.
--   Used by finalizeTenant() to avoid duplicate emails on retry, and exposed on the
--   tenant detail API so the UI can show delivery status and allow targeted resends.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS welcome_email_sent_at    TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS welcome_email_sent_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reset_email_sent_at      TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS reset_email_sent_count   INT NOT NULL DEFAULT 0;
