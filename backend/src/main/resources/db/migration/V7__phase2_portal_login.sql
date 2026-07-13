-- Phase 2: Add portal_password to businesses for login flow
-- Also adds username and email columns to users for the new onboarding wizard

ALTER TABLE businesses
    ADD COLUMN IF NOT EXISTS portal_password VARCHAR(255);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username VARCHAR(100);

-- email column already exists on users (added in V1 or later migrations)
-- This migration is idempotent via IF NOT EXISTS.
