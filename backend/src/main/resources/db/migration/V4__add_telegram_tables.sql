-- ─── TELEGRAM USERS ──────────────────────────────────────────────────────────
CREATE TABLE telegram_users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    chat_id         BIGINT NOT NULL UNIQUE,
    username        VARCHAR(255),
    verified_at     TIMESTAMPTZ,
    invite_token    VARCHAR(64),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_telegram_users_chat_id ON telegram_users(chat_id);
CREATE INDEX idx_telegram_users_user_id ON telegram_users(user_id);

-- Widen conversation_states PK to accommodate "telegram:{chatId}" identifiers
ALTER TABLE conversation_states ALTER COLUMN phone_number TYPE VARCHAR(100);
