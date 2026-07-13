-- Remove WhatsApp legacy columns
ALTER TABLE businesses DROP COLUMN IF EXISTS waba_phone_id;
