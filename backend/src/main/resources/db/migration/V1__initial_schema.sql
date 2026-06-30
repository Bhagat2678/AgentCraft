-- ─── BUSINESSES ──────────────────────────────────────────────────────────────
CREATE TABLE businesses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    type            VARCHAR(50) NOT NULL,          -- RETAIL, SERVICES, MANUFACTURING, OTHER
    industry        VARCHAR(100),
    location        TEXT,
    working_hours   JSONB,                         -- {"mon":"09:00-17:00", ...}
    base_policies   TEXT,
    waba_phone_id   VARCHAR(100),                  -- WhatsApp Business Account phone number ID
    owner_user_id   UUID,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

-- ─── USERS ───────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id     UUID NOT NULL REFERENCES businesses(id),
    display_name    VARCHAR(255),
    email           VARCHAR(255),
    status          VARCHAR(20) DEFAULT 'PENDING', -- PENDING, ACTIVE, SUSPENDED
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ─── USER PHONES ─────────────────────────────────────────────────────────────
CREATE TABLE user_phones (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    phone_number    VARCHAR(20) NOT NULL UNIQUE,   -- E.164 format
    is_primary      BOOLEAN DEFAULT FALSE,
    verified_at     TIMESTAMPTZ,
    invite_token    VARCHAR(64),
    invite_expires  TIMESTAMPTZ
);

-- ─── DEPARTMENTS ─────────────────────────────────────────────────────────────
CREATE TABLE departments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id     UUID NOT NULL REFERENCES businesses(id),
    name            VARCHAR(255) NOT NULL,
    parent_dept_id  UUID REFERENCES departments(id),  -- for sub-departments
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ─── ROLES ───────────────────────────────────────────────────────────────────
CREATE TABLE roles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id     UUID NOT NULL REFERENCES businesses(id),
    name            VARCHAR(100) NOT NULL,          -- CEO, DIRECTOR, MANAGER, LEAD, EMPLOYEE
    level           INTEGER NOT NULL,               -- 1=CEO, 2=Director, ...5=Employee
    is_default      BOOLEAN DEFAULT FALSE,
    department_id   UUID REFERENCES departments(id),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ─── ROLE PERMISSIONS ────────────────────────────────────────────────────────
CREATE TABLE role_permissions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id         UUID NOT NULL REFERENCES roles(id),
    permission      VARCHAR(100) NOT NULL,  -- e.g. TASK_CREATE, TASK_APPROVE, USER_MANAGE
    granted         BOOLEAN DEFAULT TRUE,
    UNIQUE(role_id, permission)
);

-- ─── USER ROLES (junction) ───────────────────────────────────────────────────
CREATE TABLE user_roles (
    user_id         UUID NOT NULL REFERENCES users(id),
    role_id         UUID NOT NULL REFERENCES roles(id),
    department_id   UUID REFERENCES departments(id),
    assigned_by     UUID REFERENCES users(id),
    assigned_at     TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (user_id, role_id)
);

-- ─── TASKS ───────────────────────────────────────────────────────────────────
CREATE TABLE tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id     UUID NOT NULL REFERENCES businesses(id),
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    due_date        TIMESTAMPTZ,
    priority        VARCHAR(20) DEFAULT 'MEDIUM',   -- LOW, MEDIUM, HIGH, CRITICAL
    status          VARCHAR(30) DEFAULT 'OPEN',     -- OPEN, ASSIGNED, IN_PROGRESS, SUBMITTED, APPROVED, REJECTED, CLOSED
    created_by      UUID NOT NULL REFERENCES users(id),
    tags            TEXT[],
    auto_assign_rule VARCHAR(100),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ─── TASK ASSIGNMENTS ────────────────────────────────────────────────────────
CREATE TABLE task_assignments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id         UUID NOT NULL REFERENCES tasks(id),
    assignee_id     UUID NOT NULL REFERENCES users(id),
    assigned_by     UUID REFERENCES users(id),
    assigned_at     TIMESTAMPTZ DEFAULT NOW(),
    acknowledged_at TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    verified_by     UUID REFERENCES users(id),
    verified_at     TIMESTAMPTZ,
    rejection_reason TEXT
);

-- ─── TASK HISTORY (audit trail) ──────────────────────────────────────────────
CREATE TABLE task_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id         UUID NOT NULL REFERENCES tasks(id),
    actor_id        UUID REFERENCES users(id),
    action          VARCHAR(50) NOT NULL,           -- CREATED, ASSIGNED, ACKNOWLEDGED, SUBMITTED, APPROVED, REJECTED
    old_value       JSONB,
    new_value       JSONB,
    note            TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ─── ATTACHMENTS ─────────────────────────────────────────────────────────────
CREATE TABLE attachments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id         UUID REFERENCES tasks(id),
    uploader_id     UUID NOT NULL REFERENCES users(id),
    file_key        VARCHAR(500) NOT NULL,          -- S3 object key
    file_name       VARCHAR(255),
    mime_type       VARCHAR(100),
    size_bytes      BIGINT,
    whatsapp_media_id VARCHAR(200),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ─── NOTIFICATIONS ───────────────────────────────────────────────────────────
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    type            VARCHAR(50) NOT NULL,
    payload         JSONB,
    channel         VARCHAR(20) DEFAULT 'WHATSAPP',
    status          VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, SENT, FAILED, CANCELLED
    retry_count     INTEGER DEFAULT 0,
    scheduled_at    TIMESTAMPTZ DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ─── MESSAGE TEMPLATES ───────────────────────────────────────────────────────
CREATE TABLE message_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id     UUID NOT NULL REFERENCES businesses(id),
    name            VARCHAR(100) NOT NULL,
    body            TEXT NOT NULL,
    variables       TEXT[],                         -- {{1}}, {{2}} placeholders
    wa_template_name VARCHAR(100),                  -- registered Meta template name
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ─── CONVERSATION STATE (mirrored from Redis for recovery) ───────────────────
CREATE TABLE conversation_states (
    phone_number    VARCHAR(20) PRIMARY KEY,
    business_id     UUID REFERENCES businesses(id),
    user_id         UUID REFERENCES users(id),
    current_state   VARCHAR(100) NOT NULL,
    context         JSONB,
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- ─── ANALYTICS SNAPSHOTS ─────────────────────────────────────────────────────
CREATE TABLE analytics_snapshots (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id     UUID NOT NULL REFERENCES businesses(id),
    period          DATE NOT NULL,
    metrics         JSONB NOT NULL,                 -- {open:n, done:n, avg_completion_hours:n, ...}
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(business_id, period)
);

-- ─── INDEXES ─────────────────────────────────────────────────────────────────
CREATE INDEX idx_users_business ON users(business_id);
CREATE INDEX idx_tasks_business_status ON tasks(business_id, status);
CREATE INDEX idx_task_assignments_assignee ON task_assignments(assignee_id);
CREATE INDEX idx_notifications_user_status ON notifications(user_id, status, scheduled_at);
CREATE INDEX idx_user_phones_number ON user_phones(phone_number);
CREATE INDEX idx_task_history_task ON task_history(task_id, created_at);
