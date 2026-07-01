-- Add missing updated_at column to message_templates table
ALTER TABLE message_templates ADD COLUMN updated_at TIMESTAMPTZ DEFAULT NOW();
