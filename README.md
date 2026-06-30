# WhatsApp Business Portal — Complete Implementation Plan

> **Stack:** Java 17 + Spring Boot 3 · PostgreSQL 15 · Redis 7 · WhatsApp Business Cloud API (Meta) · React 18 (popup dashboard)
> **Build:** Maven · Flyway migrations · Docker Compose / Kubernetes · GitHub Actions CI/CD

---

## Table of Contents

1. [High-Level Architecture](#1-high-level-architecture)
2. [End-to-End Sequence Diagrams](#2-end-to-end-sequence-diagrams)
3. [Data Model — ER Diagram & SQL Schema](#3-data-model)
4. [Role & Permissions Matrix](#4-role--permissions-matrix)
5. [Backend API Specification (OpenAPI)](#5-backend-api-specification)
6. [Conversation Scripts](#6-conversation-scripts)
7. [WhatsApp Message Templates](#7-whatsapp-message-templates)
8. [Core Backend Scaffolding (Java / Spring Boot)](#8-core-backend-scaffolding)
9. [WhatsApp Chat Adapter](#9-whatsapp-chat-adapter)
10. [Frontend — React Popup Dashboard](#10-frontend--react-popup-dashboard)
11. [CI/CD & Deployment](#11-cicd--deployment)
12. [Security Checklist & GDPR](#12-security-checklist--gdpr)
13. [Postman Collection & Demo Accounts](#13-postman-collection--demo-accounts)
14. [Monitoring & Alerting Rules](#14-monitoring--alerting-rules)
15. [Timeline & Effort Breakdown](#15-timeline--effort-breakdown)
16. [Developer README & Quick-Start](#16-developer-readme--quick-start)

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        EXTERNAL ACTORS                              │
│  CEO Phone (+1-xxx-CEO)  Manager Phone (+1-xxx-MGR)  Employee Phone │
└────────────┬────────────────────┬──────────────────────┬────────────┘
             │ WhatsApp           │ WhatsApp              │ WhatsApp
             ▼                    ▼                        ▼
┌────────────────────────────────────────────────────────────────────┐
│              Meta WhatsApp Business Cloud API                      │
│         (WABA ID per business · Webhook callbacks)                 │
└───────────────────────────────┬────────────────────────────────────┘
                                │ HTTPS Webhook POST /api/v1/webhook
                                ▼
┌────────────────────────────────────────────────────────────────────┐
│                     API GATEWAY / NGINX                            │
│         TLS termination · Rate limiting · Auth headers             │
└───────────────────────────────┬────────────────────────────────────┘
                                │
          ┌─────────────────────┼──────────────────────┐
          ▼                     ▼                        ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐
│  Webhook Handler │  │  REST API Server │  │  Scheduler Service   │
│  (Spring Boot)   │  │  (Spring Boot)   │  │  (Spring Scheduling) │
│                  │  │                  │  │  reminders · reports │
│  - Parse event   │  │  - Auth          │  │  - Quartz jobs       │
│  - Route to FSM  │  │  - Business CRUD │  │                      │
│  - Publish Redis │  │  - Task CRUD     │  │                      │
└────────┬─────────┘  └────────┬─────────┘  └──────────┬───────────┘
         │                     │                         │
         ▼                     ▼                         ▼
┌────────────────────────────────────────────────────────────────────┐
│                     Redis (Session + Queue)                        │
│   conversation_state:{phone}  ·  task_events (pub/sub)            │
│   notification_queue          ·  rate_limit counters              │
└───────────────────────────────┬────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────┐
│                     PostgreSQL 15 (Primary DB)                     │
│   businesses · users · roles · departments · tasks                 │
│   task_history · attachments · notifications · analytics           │
└───────────────────────────────┬────────────────────────────────────┘
                                │
                    ┌───────────┴────────────┐
                    ▼                         ▼
          ┌──────────────────┐    ┌──────────────────────┐
          │  S3-compatible   │    │  React Popup Dashboard│
          │  Object Storage  │    │  (charts · file view) │
          │  (attachments)   │    │  served from /static  │
          └──────────────────┘    └──────────────────────┘
```

### Component Responsibilities

| Component | Responsibility |
|-----------|---------------|
| **Webhook Handler** | Receives WhatsApp events, validates HMAC signature, routes to Conversation FSM |
| **Conversation FSM** | Maintains per-phone state machine stored in Redis; drives setup wizard and task flows |
| **REST API** | Authenticated REST endpoints for web dashboard and external integrations |
| **Scheduler** | Quartz-based reminder engine; generates analytics snapshots; sends template messages for out-of-session notifications |
| **Chat Adapter** | Wraps Meta Cloud API; handles template vs. session messages; retry/backoff logic |
| **Dashboard** | Minimal React SPA popup; served as static assets; uses JWT from chat to authenticate |

---

## 2. End-to-End Sequence Diagrams

### 2.1 Portal Creation Flow

```
CEO Phone          WhatsApp Cloud API       Webhook Handler        FSM / DB
    │                     │                       │                    │
    │──"Hi" / first msg──►│                       │                    │
    │                     │──webhook POST─────────►│                    │
    │                     │                       │──resolve phone─────►│
    │                     │                       │◄─state=NEW─────────│
    │◄────"Welcome! Create a new portal?"─────────│                    │
    │──"1" (Yes)──────────►│                       │                    │
    │                     │──webhook POST─────────►│                    │
    │                     │                       │──transition NEW→BNAME│
    │◄────"Enter business name:"──────────────────│                    │
    │──"Acme Corp"─────────►│                      │                    │
    │                     │──webhook POST─────────►│                    │
    │                     │                       │──save name────────►│
    │                     │                       │──transition→BTYPE  │
    │◄────"Choose type: 1.Retail 2.Services…"─────│                    │
    │  ... (industry, location, hours, depts) ...  │                    │
    │──"Confirm: Yes"──────►│                      │                    │
    │                     │──webhook POST─────────►│                    │
    │                     │                       │──create business──►│
    │                     │                       │◄─businessId────────│
    │◄────"Portal created! Portal ID: #A1B2"──────│                    │
    │◄────"Now set up your hierarchy…"────────────│                    │
```

### 2.2 Employee Onboarding

```
CEO Phone       Webhook Handler       DB          Employee Phone
    │                  │               │                 │
    │──"Add employee +1-555-EMP, role: Developer"──►│   │
    │                  │──lookup/create user────────►│   │
    │                  │──create invite_token────────►│   │
    │                  │──send template EMPLOYEE_INVITE─────────────────►│
    │◄─"Invite sent to +1-555-EMP"                   │   │
    │                  │               │   ◄──"1" (Accept)───────────────│
    │                  │──activate user─────────────►│   │
    │                  │──notify CEO via template────────────────────────►│
    │◄─"Employee accepted invitation"                │   │
```

### 2.3 Task Assignment & Completion

```
Manager         Webhook Handler       DB          Employee         CEO
   │                  │               │               │             │
   │──"Assign task"──►│               │               │             │
   │                  │──FSM: TASK_CREATE─────────────►│            │
   │◄─"Title? Due date? Priority? Assign to?"         │            │
   │──(answers)───────►│              │               │             │
   │◄─"Confirm task?"                 │               │             │
   │──"Yes"───────────►│              │               │             │
   │                  │──create task──►│               │             │
   │                  │──notify employee (in-session msg or template)─►│
   │                  │──notify CEO (template)──────────────────────────►│
   │                  │               │  ◄──"Task #42 done + photo"──│
   │                  │──update status─►│              │             │
   │                  │──notify manager for verification───────────►│
   │◄─"Employee submitted proof. Approve? 1.Yes 2.Reject"           │
   │──"1"─────────────►│              │               │             │
   │                  │──approve task──►│              │             │
   │                  │──notify employee────────────────────────────►│
   │                  │──update analytics──────────────►│           │
```

### 2.4 Reporting Summary

```
CEO Phone       Webhook Handler       DB / Analytics
    │                  │                    │
    │──"Stats"─────────►│                   │
    │                  │──query KPIs─────────►│
    │                  │◄─task_counts, rates──│
    │◄─"📊 Weekly Summary:                   │
    │   • Open: 12  Done: 8  Overdue: 2      │
    │   • Top performer: Alice (95%)          │
    │   • Avg completion: 1.4d               │
    │   • [View full dashboard →]"            │
```

---

## 3. Data Model

### 3.1 ER Diagram (text representation)

```
businesses ─────────┬──── departments
    │                │
    │                └──── roles ─────────── role_permissions
    │
    └──── users ──────────┬──── user_roles (junction)
              │            │
              │            └──── user_phones
              │
              └──── tasks ──────────┬──── task_assignments
                        │            │
                        │            └──── task_history
                        │            │
                        │            └──── attachments
                        │
                        └──── notifications
```

### 3.2 SQL Schema (PostgreSQL / Flyway)

```sql
-- V1__initial_schema.sql

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
```

### 3.3 Seed Data

```sql
-- V2__seed_demo.sql

INSERT INTO businesses (id, name, type, industry, location)
VALUES ('aaaaaaaa-0000-0000-0000-000000000001', 'Acme Corp', 'SERVICES', 'Technology', 'New York, NY');

-- Demo CEO
INSERT INTO users (id, business_id, display_name, status)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'Alice (CEO)', 'ACTIVE');
INSERT INTO user_phones (user_id, phone_number, is_primary, verified_at)
VALUES ('bbbbbbbb-0000-0000-0000-000000000001', '+15550000001', TRUE, NOW());

-- Demo Manager
INSERT INTO users (id, business_id, display_name, status)
VALUES ('bbbbbbbb-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'Bob (Manager)', 'ACTIVE');
INSERT INTO user_phones (user_id, phone_number, is_primary, verified_at)
VALUES ('bbbbbbbb-0000-0000-0000-000000000002', '+15550000002', TRUE, NOW());

-- Demo Employee
INSERT INTO users (id, business_id, display_name, status)
VALUES ('bbbbbbbb-0000-0000-0000-000000000003', 'aaaaaaaa-0000-0000-0000-000000000001', 'Carol (Employee)', 'ACTIVE');
INSERT INTO user_phones (user_id, phone_number, is_primary, verified_at)
VALUES ('bbbbbbbb-0000-0000-0000-000000000003', '+15550000003', TRUE, NOW());
```

---

## 4. Role & Permissions Matrix

### 4.1 Default Permission Sets

| Permission | CEO | Director | Manager | Lead | Employee |
|------------|:---:|:--------:|:-------:|:----:|:--------:|
| BUSINESS_CONFIGURE | ✅ | ❌ | ❌ | ❌ | ❌ |
| USER_MANAGE | ✅ | ✅ | ✅ | ❌ | ❌ |
| USER_VIEW | ✅ | ✅ | ✅ | ✅ | ❌ |
| ROLE_MANAGE | ✅ | ✅ | ❌ | ❌ | ❌ |
| DEPT_MANAGE | ✅ | ✅ | ✅ | ❌ | ❌ |
| TASK_CREATE | ✅ | ✅ | ✅ | ✅ | ❌ |
| TASK_ASSIGN | ✅ | ✅ | ✅ | ✅ | ❌ |
| TASK_APPROVE | ✅ | ✅ | ✅ | ❌ | ❌ |
| TASK_COMPLETE | ✅ | ✅ | ✅ | ✅ | ✅ |
| TASK_VIEW_ALL | ✅ | ✅ | ✅ | ✅ | ❌ |
| TASK_VIEW_OWN | ✅ | ✅ | ✅ | ✅ | ✅ |
| REPORT_VIEW | ✅ | ✅ | ✅ | ❌ | ❌ |
| REPORT_EXPORT | ✅ | ✅ | ❌ | ❌ | ❌ |
| TEMPLATE_MANAGE | ✅ | ❌ | ❌ | ❌ | ❌ |
| WEBHOOK_MANAGE | ✅ | ❌ | ❌ | ❌ | ❌ |

### 4.2 Permission Evaluation Algorithm (Java pseudocode)

```java
// PermissionEvaluator.java
@Service
public class PermissionEvaluator {

    private final RolePermissionRepository rolePermRepo;
    private final UserRoleRepository userRoleRepo;

    /**
     * Evaluates whether a user has a given permission within a business context.
     * 1. Load all roles assigned to the user in this business.
     * 2. For each role, load its permission grants.
     * 3. Any explicit DENY on ANY role blocks access (deny-overrides model).
     * 4. At least one GRANT (with no DENY) = access allowed.
     */
    public boolean hasPermission(UUID userId, UUID businessId, String permission) {
        List<UUID> roleIds = userRoleRepo.findRoleIdsByUserAndBusiness(userId, businessId);

        boolean anyGrant = false;
        for (UUID roleId : roleIds) {
            Optional<RolePermission> rp = rolePermRepo.findByRoleIdAndPermission(roleId, permission);
            if (rp.isPresent()) {
                if (!rp.get().isGranted()) return false;  // Explicit DENY — short-circuit
                anyGrant = true;
            }
        }
        return anyGrant;
    }

    /**
     * Hierarchical permission: a user with roleLevel <= targetRoleLevel can manage target.
     * E.g., CEO (level 1) can manage Manager (level 3); Employee cannot manage Manager.
     */
    public boolean canManageRole(UUID actorId, UUID businessId, int targetRoleLevel) {
        int actorLevel = userRoleRepo.getMinRoleLevel(actorId, businessId);
        return actorLevel <= targetRoleLevel;
    }
}

// Spring Security method-level usage example:
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    @PostMapping
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, " +
                  "#req.businessId, 'TASK_CREATE')")
    public ResponseEntity<TaskResponse> createTask(@RequestBody TaskCreateRequest req) {
        // ...
    }

    @PutMapping("/{taskId}/approve")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, " +
                  "@taskService.getBusinessId(#taskId), 'TASK_APPROVE')")
    public ResponseEntity<Void> approveTask(@PathVariable UUID taskId) {
        // ...
    }
}
```

---

## 5. Backend API Specification

```yaml
# openapi.yaml  (abridged — key endpoints)
openapi: "3.0.3"
info:
  title: WhatsApp Business Portal API
  version: "1.0.0"

servers:
  - url: https://api.yourdomain.com/api/v1

security:
  - BearerAuth: []

paths:

  # ─── WEBHOOK ─────────────────────────────────────────────────────────────
  /webhook:
    get:
      summary: Meta webhook verification
      parameters:
        - name: hub.mode
          in: query
          schema: {type: string}
        - name: hub.verify_token
          in: query
          schema: {type: string}
        - name: hub.challenge
          in: query
          schema: {type: string}
      responses:
        "200": {description: Challenge echo}

    post:
      summary: Receive WhatsApp events
      security: []
      requestBody:
        content:
          application/json:
            example:
              object: whatsapp_business_account
              entry:
                - id: "WABA_ID"
                  changes:
                    - value:
                        messages:
                          - from: "+15550000001"
                            type: text
                            text: {body: "Hello"}
      responses:
        "200": {description: OK}

  # ─── BUSINESSES ──────────────────────────────────────────────────────────
  /businesses:
    post:
      summary: Create business portal
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [name, type]
              properties:
                name: {type: string}
                type: {type: string, enum: [RETAIL, SERVICES, MANUFACTURING, OTHER]}
                industry: {type: string}
                location: {type: string}
                workingHours: {type: object}
                basePolicies: {type: string}
      responses:
        "201":
          content:
            application/json:
              example:
                id: "aaaaaaaa-0000-0000-0000-000000000001"
                name: "Acme Corp"
                status: "ACTIVE"

  /businesses/{businessId}:
    get:
      summary: Get business details
      parameters:
        - name: businessId
          in: path
          schema: {type: string, format: uuid}
      responses:
        "200": {description: Business object}

  # ─── USERS ───────────────────────────────────────────────────────────────
  /businesses/{businessId}/users:
    get:
      summary: List users
      responses:
        "200":
          content:
            application/json:
              example:
                users:
                  - id: "bbbb..."
                    displayName: "Alice"
                    phoneNumber: "+15550000001"
                    roles: ["CEO"]
                    status: "ACTIVE"
    post:
      summary: Invite employee by phone number
      requestBody:
        content:
          application/json:
            example:
              phoneNumber: "+15550000003"
              roleId: "role-uuid"
              departmentId: "dept-uuid"
      responses:
        "201": {description: Invite sent}

  /businesses/{businessId}/users/{userId}/roles:
    put:
      summary: Grant or revoke role
      requestBody:
        content:
          application/json:
            example:
              roleId: "role-uuid"
              action: "GRANT"   # or REVOKE
      responses:
        "200": {description: Updated}

  # ─── TASKS ───────────────────────────────────────────────────────────────
  /businesses/{businessId}/tasks:
    get:
      summary: List tasks (with filters)
      parameters:
        - name: status
          in: query
          schema: {type: string}
        - name: assigneeId
          in: query
          schema: {type: string}
        - name: priority
          in: query
          schema: {type: string}
      responses:
        "200":
          content:
            application/json:
              example:
                tasks:
                  - id: "task-uuid"
                    title: "Deploy v2.0"
                    status: "IN_PROGRESS"
                    priority: "HIGH"
                    dueDate: "2025-08-01T18:00:00Z"
                    assignee: {name: "Carol", phone: "+15550000003"}
    post:
      summary: Create task
      requestBody:
        content:
          application/json:
            example:
              title: "Deploy v2.0"
              description: "Push to prod after QA approval"
              dueDate: "2025-08-01T18:00:00Z"
              priority: "HIGH"
              assigneeId: "bbbb...003"
              tags: ["deployment", "critical"]
      responses:
        "201": {description: Task created}

  /businesses/{businessId}/tasks/{taskId}/status:
    patch:
      summary: Update task status
      requestBody:
        content:
          application/json:
            example:
              status: "SUBMITTED"
              note: "Done! See attached proof."
      responses:
        "200": {description: Updated}

  /businesses/{businessId}/tasks/{taskId}/approve:
    post:
      summary: Approve or reject task submission
      requestBody:
        content:
          application/json:
            example:
              approved: true
              reason: "Looks good, merged."
      responses:
        "200": {description: Decision recorded}

  /businesses/{businessId}/tasks/{taskId}/attachments:
    post:
      summary: Upload attachment
      requestBody:
        content:
          multipart/form-data:
            schema:
              type: object
              properties:
                file:
                  type: string
                  format: binary
      responses:
        "201":
          content:
            application/json:
              example:
                attachmentId: "att-uuid"
                presignedUrl: "https://s3.../proof.jpg?X-Amz-..."

  # ─── ANALYTICS ───────────────────────────────────────────────────────────
  /businesses/{businessId}/analytics:
    get:
      summary: Get KPI summary
      parameters:
        - name: period
          in: query
          schema: {type: string, example: "2025-07"}
      responses:
        "200":
          content:
            application/json:
              example:
                period: "2025-07"
                tasksOpen: 12
                tasksDone: 45
                tasksOverdue: 3
                avgCompletionHours: 28.4
                topPerformer: {name: "Carol", completionRate: 0.95}
                workloadByEmployee:
                  - {name: "Carol", assigned: 20, completed: 19}
                  - {name: "Dave", assigned: 15, completed: 10}

  /businesses/{businessId}/reports/export:
    get:
      summary: Export CSV report
      parameters:
        - name: type
          in: query
          schema: {type: string, enum: [tasks, users, audit]}
      responses:
        "200":
          content:
            text/csv: {}

  # ─── ROLES ───────────────────────────────────────────────────────────────
  /businesses/{businessId}/roles:
    get:
      summary: List roles
      responses: {"200": {description: Role list}}
    post:
      summary: Create custom role
      requestBody:
        content:
          application/json:
            example:
              name: "Senior Engineer"
              level: 3
              departmentId: "dept-uuid"
              permissions: ["TASK_CREATE", "TASK_ASSIGN", "TASK_VIEW_ALL"]
      responses:
        "201": {description: Role created}

  # ─── NOTIFICATIONS & TEMPLATES ───────────────────────────────────────────
  /businesses/{businessId}/templates:
    get:
      summary: List message templates
      responses: {"200": {description: Template list}}
    post:
      summary: Create message template
      requestBody:
        content:
          application/json:
            example:
              name: "weekly_reminder"
              body: "Hi {{1}}, you have {{2}} open tasks. Reply VIEW to see them."
              variables: ["name", "taskCount"]
      responses:
        "201": {description: Created}

  /businesses/{businessId}/broadcasts:
    post:
      summary: Send scheduled broadcast
      requestBody:
        content:
          application/json:
            example:
              templateId: "tmpl-uuid"
              targetRoles: ["EMPLOYEE"]
              scheduledAt: "2025-08-01T09:00:00Z"
              variables: {taskCount: "5"}
      responses:
        "202": {description: Broadcast queued}

  # ─── WEBHOOKS (outbound integrations) ────────────────────────────────────
  /businesses/{businessId}/webhooks:
    post:
      summary: Register outbound webhook
      requestBody:
        content:
          application/json:
            example:
              url: "https://crm.acme.com/webhook"
              events: ["task.created", "task.approved", "user.added"]
              secret: "hmac-secret"
      responses:
        "201": {description: Registered}

components:
  securitySchemes:
    BearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

---

## 6. Conversation Scripts

### 6.1 Creator / CEO Flow

```
STATE: NEW_USER
────────────────────────────────────────────────────────────────────────────────
BOT:  👋 Welcome to *BizPortal*! I can help you create and manage your business
      portal entirely through WhatsApp.

      What would you like to do?
      1️⃣  Create a new business portal
      2️⃣  Access an existing portal
      3️⃣  Learn more

USER: 1

STATE: COLLECT_BIZ_NAME
────────────────────────────────────────────────────────────────────────────────
BOT:  Great! Let's set up your portal.

      *Step 1 of 6* — What is your business name?

USER: Acme Corp

BOT:  Got it: *Acme Corp*. Is that correct?
      ✅ Yes, continue | ✏️ Edit

USER: Yes, continue

STATE: COLLECT_BIZ_TYPE
────────────────────────────────────────────────────────────────────────────────
BOT:  *Step 2 of 6* — Choose your business type:
      1️⃣  Retail
      2️⃣  Services
      3️⃣  Manufacturing
      4️⃣  Other (reply with your type)

USER: 2

STATE: COLLECT_INDUSTRY
────────────────────────────────────────────────────────────────────────────────
BOT:  *Step 3 of 6* — What industry are you in?
      e.g. Technology, Healthcare, Finance, Education, Logistics…

USER: Technology

STATE: COLLECT_LOCATION
────────────────────────────────────────────────────────────────────────────────
BOT:  *Step 4 of 6* — Where is your business located?
      (City, Country or full address)

USER: New York, USA

STATE: COLLECT_HOURS
────────────────────────────────────────────────────────────────────────────────
BOT:  *Step 5 of 6* — Working hours?
      1️⃣  Standard (Mon–Fri 9am–5pm)
      2️⃣  Custom (I'll specify)
      3️⃣  24/7

USER: 1

STATE: COLLECT_DEPARTMENTS
────────────────────────────────────────────────────────────────────────────────
BOT:  *Step 6 of 6* — List your departments (comma-separated):
      e.g. Engineering, Sales, HR, Operations

USER: Engineering, Sales, HR

STATE: CONFIRM_SETUP
────────────────────────────────────────────────────────────────────────────────
BOT:  ✅ *Review your portal setup:*
      • Name: Acme Corp
      • Type: Services
      • Industry: Technology
      • Location: New York, USA
      • Hours: Mon–Fri 9am–5pm
      • Departments: Engineering, Sales, HR

      Confirm to create?
      1️⃣  Yes, create portal ✅
      2️⃣  Edit something ✏️

USER: 1

BOT:  🎉 *Portal created!* Your Portal ID is *#ACM001*.

      Now let's set up your team hierarchy.
      1️⃣  Use default (CEO → Manager → Employee)
      2️⃣  Custom hierarchy

USER: 1

STATE: HIERARCHY_CREATED
────────────────────────────────────────────────────────────────────────────────
BOT:  ✅ Default hierarchy set:
      • CEO (you)
      • Manager
      • Employee

      *Add your first employee?*
      Reply with their phone number and role:
      e.g. +15550000002 Manager

USER: +15550000002 Manager

STATE: INVITE_SENT
────────────────────────────────────────────────────────────────────────────────
BOT:  📨 Invite sent to +15550000002 as *Manager*.
      They'll receive a WhatsApp invitation to join *Acme Corp* portal.

      Add another? (send number + role, or type DONE)

USER: +15550000003 Employee

BOT:  📨 Invite sent to +15550000003 as *Employee*.

USER: DONE

STATE: SETUP_COMPLETE
────────────────────────────────────────────────────────────────────────────────
BOT:  🏢 *Acme Corp portal is live!*

      Quick commands:
      • ASSIGN TASK — create a new task
      • STATS — view analytics
      • TEAM — manage your team
      • HELP — all commands

      👉 Full dashboard: https://portal.yourdomain.com/#ACM001
```

### 6.2 Manager Flow — Task Assignment

```
STATE: IDLE (Manager authenticated)
────────────────────────────────────────────────────────────────────────────────
MANAGER: ASSIGN TASK

BOT:  📋 *New Task — Step 1 of 4*
      What's the task title?

MANAGER: Deploy v2.0 to production

BOT:  *Step 2 of 4* — Due date?
      e.g. Aug 1, tomorrow, in 3 days

MANAGER: Aug 1

BOT:  *Step 3 of 4* — Priority?
      1️⃣  Low   2️⃣  Medium   3️⃣  High   4️⃣  Critical

MANAGER: 3

BOT:  *Step 4 of 4* — Assign to:
      1️⃣  Carol (+1-555-0003)  — Engineering
      2️⃣  Dave (+1-555-0004)   — Engineering
      3️⃣  All Engineering employees

MANAGER: 1

BOT:  📎 Attach files? (send file/image now, or type SKIP)

MANAGER: SKIP

BOT:  ✅ *Confirm Task:*
      • Title: Deploy v2.0 to production
      • Due: Aug 1, 2025 (in 5 days)
      • Priority: 🔴 High
      • Assigned to: Carol

      1️⃣  Create task ✅   2️⃣  Edit ✏️

MANAGER: 1

BOT:  ✅ Task #T042 created and assigned to Carol.
      She'll be notified immediately.
```

### 6.3 Employee Flow — Task Acknowledgement & Completion

```
[Employee receives template message]
BOT:  📌 *New Task Assigned — Acme Corp*
      Task: Deploy v2.0 to production
      Due: Aug 1 | Priority: 🔴 High
      From: Bob (Manager)

      1️⃣  Acknowledge   2️⃣  Request clarification

EMPLOYEE: 1

BOT:  ✅ Acknowledged! Bob has been notified.
      When done, reply COMPLETE #T042

[Employee later]
EMPLOYEE: COMPLETE #T042

BOT:  Great! Please submit proof of completion:
      📸 Send a photo, document, or text description.

EMPLOYEE: [sends image]

BOT:  ✅ Proof received! Submitted to Bob for verification.
      Your task is now *Under Review*.

[Manager receives]
BOT:  🔔 Carol submitted proof for *Task #T042*.
      [View proof: https://portal.yourdomain.com/attachments/att-uuid]

      1️⃣  Approve ✅   2️⃣  Reject & reassign ❌

MANAGER: 1

BOT:  ✅ Task #T042 *approved*! Carol has been notified.

[Employee receives]
BOT:  🎉 Your task "Deploy v2.0 to production" was *approved* by Bob!
      Well done! 🏆
```

### 6.4 Analytics / Stats Command

```
CEO: STATS

BOT:  📊 *Acme Corp — Weekly Report (Jul 21–27)*

      Tasks:
      • ✅ Completed: 8
      • 🔄 In Progress: 12
      • ⚠️ Overdue: 2
      • 📋 Total Open: 14

      Top performers:
      🥇 Carol — 95% completion rate
      🥈 Dave  — 80% completion rate

      Avg completion time: 1.4 days

      👉 Full charts: https://portal.yourdomain.com/dashboard/#ACM001

      1️⃣  Export CSV   2️⃣  Monthly view   3️⃣  Per-employee breakdown
```

### 6.5 Multi-Portal Switch

```
CEO: SWITCH PORTAL

BOT:  You have access to 2 portals:
      1️⃣  Acme Corp (#ACM001) — active
      2️⃣  Beta Ventures (#BET002)

CEO: 2

BOT:  ✅ Switched to *Beta Ventures* (#BET002).
      All commands now apply to this portal.
      Type SWITCH PORTAL anytime to change.
```

### 6.6 Plain-Text SMS Fallback

```
[If user has disabled WhatsApp rich messages or is on SMS]

BOT: Welcome to BizPortal. Reply:
     1 = Create portal
     2 = Access portal
     3 = Help

[No buttons, no list messages — pure text dialogue]
Entire flow mirrors above but uses numbered replies only.
```

---

## 7. WhatsApp Message Templates

All out-of-session (>24h) notifications must use approved Meta template messages.

### Template Library

```json
[
  {
    "name": "employee_invite",
    "language": "en_US",
    "category": "UTILITY",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "You're invited to {{1}}"
      },
      {
        "type": "BODY",
        "text": "Hi! {{2}} has invited you to join *{{1}}* as {{3}} on BizPortal. Tap below to accept."
      },
      {
        "type": "BUTTONS",
        "buttons": [
          {"type": "QUICK_REPLY", "text": "Accept ✅"},
          {"type": "QUICK_REPLY", "text": "Decline ❌"}
        ]
      }
    ]
  },
  {
    "name": "task_assigned",
    "language": "en_US",
    "category": "UTILITY",
    "components": [
      {
        "type": "HEADER",
        "format": "TEXT",
        "text": "New Task: {{1}}"
      },
      {
        "type": "BODY",
        "text": "You've been assigned a task in *{{2}}*:\n📌 {{1}}\n📅 Due: {{3}}\n⚡ Priority: {{4}}\n\nReply ACK to acknowledge."
      }
    ]
  },
  {
    "name": "task_reminder",
    "language": "en_US",
    "category": "UTILITY",
    "components": [
      {
        "type": "BODY",
        "text": "⏰ Reminder: Task *{{1}}* is due in {{2}}. Reply VIEW {{3}} for details."
      }
    ]
  },
  {
    "name": "task_approved",
    "language": "en_US",
    "category": "UTILITY",
    "components": [
      {
        "type": "BODY",
        "text": "🎉 Your task *{{1}}* has been approved by {{2}}! Great work."
      }
    ]
  },
  {
    "name": "task_rejected",
    "language": "en_US",
    "category": "UTILITY",
    "components": [
      {
        "type": "BODY",
        "text": "❌ Task *{{1}}* was returned by {{2}}.\nReason: {{3}}\n\nPlease resubmit. Reply VIEW {{4}} for details."
      }
    ]
  },
  {
    "name": "weekly_summary",
    "language": "en_US",
    "category": "UTILITY",
    "components": [
      {
        "type": "BODY",
        "text": "📊 Weekly Summary — {{1}}\n✅ Done: {{2}} | 🔄 Open: {{3}} | ⚠️ Overdue: {{4}}\n\nReply STATS for full report."
      }
    ]
  }
]
```

### Interactive List Message Payload (in-session)

```json
{
  "messaging_product": "whatsapp",
  "to": "{{PHONE_NUMBER}}",
  "type": "interactive",
  "interactive": {
    "type": "list",
    "header": {"type": "text", "text": "Choose business type"},
    "body": {"text": "Select the category that best describes your business:"},
    "action": {
      "button": "Select type",
      "sections": [
        {
          "title": "Business Types",
          "rows": [
            {"id": "RETAIL", "title": "Retail", "description": "Shops, e-commerce, consumer goods"},
            {"id": "SERVICES", "title": "Services", "description": "Consulting, IT, professional services"},
            {"id": "MANUFACTURING", "title": "Manufacturing", "description": "Production, factories, supply chain"},
            {"id": "OTHER", "title": "Other", "description": "Type your category"}
          ]
        }
      ]
    }
  }
}
```

### Reply Button Payload

```json
{
  "messaging_product": "whatsapp",
  "to": "{{PHONE_NUMBER}}",
  "type": "interactive",
  "interactive": {
    "type": "button",
    "body": {"text": "Task #T042 completed by Carol. Verify?"},
    "action": {
      "buttons": [
        {"type": "reply", "reply": {"id": "APPROVE", "title": "✅ Approve"}},
        {"type": "reply", "reply": {"id": "REJECT", "title": "❌ Reject"}}
      ]
    }
  }
}
```

---

## 8. Core Backend Scaffolding

### 8.1 Project Structure

```
bizportal-backend/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/bizportal/
│   │   │   ├── BizPortalApplication.java
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   ├── RedisConfig.java
│   │   │   │   └── WhatsAppConfig.java
│   │   │   ├── webhook/
│   │   │   │   ├── WebhookController.java
│   │   │   │   ├── WebhookValidator.java
│   │   │   │   └── WhatsAppEventRouter.java
│   │   │   ├── fsm/
│   │   │   │   ├── ConversationFSM.java
│   │   │   │   ├── ConversationState.java
│   │   │   │   └── handlers/
│   │   │   │       ├── SetupFlowHandler.java
│   │   │   │       ├── TaskFlowHandler.java
│   │   │   │       └── ReportFlowHandler.java
│   │   │   ├── api/
│   │   │   │   ├── controller/
│   │   │   │   │   ├── BusinessController.java
│   │   │   │   │   ├── UserController.java
│   │   │   │   │   ├── TaskController.java
│   │   │   │   │   ├── RoleController.java
│   │   │   │   │   └── AnalyticsController.java
│   │   │   │   ├── dto/
│   │   │   │   └── mapper/
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── Business.java
│   │   │   │   │   ├── User.java
│   │   │   │   │   ├── Task.java
│   │   │   │   │   └── ...
│   │   │   │   └── repository/
│   │   │   ├── service/
│   │   │   │   ├── BusinessService.java
│   │   │   │   ├── UserService.java
│   │   │   │   ├── TaskService.java
│   │   │   │   ├── NotificationService.java
│   │   │   │   └── AnalyticsService.java
│   │   │   ├── security/
│   │   │   │   ├── JwtTokenProvider.java
│   │   │   │   ├── PhoneAuthFilter.java
│   │   │   │   └── PermissionEvaluator.java
│   │   │   ├── whatsapp/
│   │   │   │   ├── WhatsAppClient.java
│   │   │   │   ├── TemplateMessageBuilder.java
│   │   │   │   └── InteractiveMessageBuilder.java
│   │   │   └── scheduler/
│   │   │       ├── ReminderJob.java
│   │   │       └── AnalyticsSnapshotJob.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │           ├── V1__initial_schema.sql
│   │           └── V2__seed_demo.sql
│   └── test/
│       └── java/com/bizportal/
│           ├── api/TaskControllerTest.java
│           ├── fsm/ConversationFSMTest.java
│           └── service/TaskServiceTest.java
```

### 8.2 `pom.xml` (key dependencies)

```xml
<dependencies>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-quartz</artifactId>
  </dependency>
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
  </dependency>
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
  </dependency>
  <dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
  </dependency>
  <dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
    <version>2.25.0</version>
  </dependency>
  <dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
  </dependency>
  <dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
  </dependency>
  <!-- Test -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
  </dependency>
</dependencies>
```

### 8.3 `application.yml`

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bizportal
    username: ${DB_USER:bizportal}
    password: ${DB_PASS:secret}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
  flyway:
    enabled: true

whatsapp:
  api-url: https://graph.facebook.com/v18.0
  token: ${WHATSAPP_TOKEN}
  phone-number-id: ${WHATSAPP_PHONE_NUMBER_ID}
  webhook-verify-token: ${WEBHOOK_VERIFY_TOKEN}
  app-secret: ${WHATSAPP_APP_SECRET}

storage:
  s3:
    bucket: ${S3_BUCKET:bizportal-attachments}
    region: ${AWS_REGION:us-east-1}
    endpoint: ${S3_ENDPOINT:}   # Override for MinIO/local

jwt:
  secret: ${JWT_SECRET}
  expiration-ms: 86400000  # 24 hours

notifications:
  reminder-cron: "0 0 9 * * ?"     # 9am daily
  analytics-cron: "0 0 8 * * MON"  # 8am Monday

data-retention:
  audit-log-years: 1
```

### 8.4 Webhook Controller

```java
// WebhookController.java
@RestController
@RequestMapping("/api/v1/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookValidator validator;
    private final WhatsAppEventRouter router;

    /** Meta webhook verification (GET) */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(mode) && validator.verifyToken(token)) {
            return ResponseEntity.ok(challenge);
        }
        return ResponseEntity.status(403).build();
    }

    /** Receive WhatsApp events (POST) */
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestBody String rawBody) {
        if (!validator.validateSignature(rawBody, signature)) {
            log.warn("Invalid webhook signature");
            return ResponseEntity.status(401).build();
        }
        // Parse and route asynchronously — always return 200 immediately
        router.routeAsync(rawBody);
        return ResponseEntity.ok().build();
    }
}
```

### 8.5 Conversation FSM

```java
// ConversationState.java
public enum ConversationState {
    NEW_USER,
    COLLECT_BIZ_NAME, COLLECT_BIZ_TYPE, COLLECT_INDUSTRY,
    COLLECT_LOCATION, COLLECT_HOURS, COLLECT_DEPARTMENTS,
    CONFIRM_SETUP, HIERARCHY_SETUP, INVITE_EMPLOYEES,
    IDLE,
    TASK_COLLECT_TITLE, TASK_COLLECT_DUE, TASK_COLLECT_PRIORITY,
    TASK_COLLECT_ASSIGNEE, TASK_CONFIRM,
    TASK_COMPLETE_PROOF, TASK_VERIFY,
    STATS_MENU, TEAM_MENU, SWITCH_PORTAL
}

// ConversationContext.java
@Data
@Builder
public class ConversationContext {
    private String phoneNumber;
    private UUID userId;
    private UUID businessId;
    private ConversationState state;
    private Map<String, Object> scratch;   // temp data during multi-step flows
    private List<UUID> accessibleBusinessIds;
    private Instant lastActivity;
}

// ConversationFSM.java
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationFSM {

    private final RedisTemplate<String, ConversationContext> redisTemplate;
    private final Map<ConversationState, FlowHandler> handlers;
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    public void process(String phoneNumber, InboundMessage message) {
        ConversationContext ctx = loadOrCreate(phoneNumber);
        FlowHandler handler = handlers.get(ctx.getState());
        if (handler == null) {
            log.error("No handler for state {}", ctx.getState());
            return;
        }
        ConversationState nextState = handler.handle(ctx, message);
        ctx.setState(nextState);
        ctx.setLastActivity(Instant.now());
        save(phoneNumber, ctx);
    }

    private ConversationContext loadOrCreate(String phone) {
        String key = "conv:" + phone;
        ConversationContext ctx = redisTemplate.opsForValue().get(key);
        if (ctx == null) {
            ctx = ConversationContext.builder()
                    .phoneNumber(phone)
                    .state(ConversationState.NEW_USER)
                    .scratch(new HashMap<>())
                    .lastActivity(Instant.now())
                    .build();
        }
        return ctx;
    }

    private void save(String phone, ConversationContext ctx) {
        redisTemplate.opsForValue().set("conv:" + phone, ctx, SESSION_TTL);
        // Mirror to DB for recovery
        // conversationStateRepository.upsert(ctx);
    }
}

// FlowHandler interface
public interface FlowHandler {
    ConversationState handle(ConversationContext ctx, InboundMessage message);
}

// SetupFlowHandler.java (excerpt)
@Component
@RequiredArgsConstructor
public class SetupFlowHandler implements FlowHandler {

    private final WhatsAppClient whatsApp;
    private final BusinessService businessService;

    @Override
    public ConversationState handle(ConversationContext ctx, InboundMessage msg) {
        return switch (ctx.getState()) {
            case NEW_USER -> handleNewUser(ctx, msg);
            case COLLECT_BIZ_NAME -> handleBizName(ctx, msg);
            case COLLECT_BIZ_TYPE -> handleBizType(ctx, msg);
            // ...
            default -> ConversationState.IDLE;
        };
    }

    private ConversationState handleNewUser(ConversationContext ctx, InboundMessage msg) {
        whatsApp.sendListMessage(ctx.getPhoneNumber(),
            "Welcome to *BizPortal*! What would you like to do?",
            List.of(
                new ListRow("CREATE", "Create new portal", "Set up from scratch"),
                new ListRow("ACCESS", "Access existing portal", "Join with invite"),
                new ListRow("HELP", "Learn more", "How it works")
            ));
        return ConversationState.NEW_USER;   // Wait for reply
    }

    private ConversationState handleBizName(ConversationContext ctx, InboundMessage msg) {
        String name = msg.getText().trim();
        ctx.getScratch().put("bizName", name);
        whatsApp.sendButtonMessage(ctx.getPhoneNumber(),
            "Got it: *" + name + "*. Is that correct?",
            List.of(new Button("CONFIRM_NAME", "Yes, continue ✅"),
                    new Button("EDIT_NAME", "Edit ✏️")));
        return ConversationState.COLLECT_BIZ_TYPE;
    }
    // ... other handlers
}
```

### 8.6 Task Service

```java
// TaskService.java
@Service
@Transactional
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepo;
    private final TaskAssignmentRepository assignmentRepo;
    private final TaskHistoryRepository historyRepo;
    private final NotificationService notificationService;
    private final PermissionEvaluator permEval;

    public Task createTask(UUID creatorId, UUID businessId, TaskCreateRequest req) {
        if (!permEval.hasPermission(creatorId, businessId, "TASK_CREATE")) {
            throw new AccessDeniedException("Insufficient permissions");
        }

        Task task = Task.builder()
                .businessId(businessId)
                .title(req.getTitle())
                .description(req.getDescription())
                .dueDate(req.getDueDate())
                .priority(req.getPriority())
                .status(TaskStatus.ASSIGNED)
                .createdBy(creatorId)
                .tags(req.getTags())
                .build();
        taskRepo.save(task);

        TaskAssignment assignment = TaskAssignment.builder()
                .taskId(task.getId())
                .assigneeId(req.getAssigneeId())
                .assignedBy(creatorId)
                .build();
        assignmentRepo.save(assignment);

        // Audit trail
        historyRepo.save(TaskHistory.builder()
                .taskId(task.getId())
                .actorId(creatorId)
                .action("CREATED")
                .newValue(Map.of("assignee", req.getAssigneeId(), "priority", req.getPriority()))
                .build());

        // Notify assignee and CEO
        notificationService.notifyTaskAssigned(task, assignment);

        return task;
    }

    public void submitCompletion(UUID userId, UUID taskId, String note, List<UUID> attachmentIds) {
        TaskAssignment assignment = assignmentRepo.findByTaskIdAndAssigneeId(taskId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Task assignment not found"));

        assignment.setCompletedAt(Instant.now());
        assignmentRepo.save(assignment);

        Task task = taskRepo.findById(taskId).orElseThrow();
        task.setStatus(TaskStatus.SUBMITTED);
        taskRepo.save(task);

        historyRepo.save(TaskHistory.builder()
                .taskId(taskId).actorId(userId).action("SUBMITTED")
                .note(note).build());

        notificationService.notifyTaskSubmitted(task, assignment);
    }

    public void approveTask(UUID managerId, UUID taskId, boolean approved, String reason) {
        if (!permEval.hasPermission(managerId, getBusinessId(taskId), "TASK_APPROVE")) {
            throw new AccessDeniedException("Insufficient permissions");
        }
        Task task = taskRepo.findById(taskId).orElseThrow();
        task.setStatus(approved ? TaskStatus.APPROVED : TaskStatus.REJECTED);
        taskRepo.save(task);

        TaskAssignment assignment = assignmentRepo.findLatestByTaskId(taskId).orElseThrow();
        assignment.setVerifiedBy(managerId);
        assignment.setVerifiedAt(Instant.now());
        if (!approved) assignment.setRejectionReason(reason);
        assignmentRepo.save(assignment);

        historyRepo.save(TaskHistory.builder()
                .taskId(taskId).actorId(managerId)
                .action(approved ? "APPROVED" : "REJECTED").note(reason).build());

        notificationService.notifyVerificationResult(task, assignment, approved, reason);
    }

    public UUID getBusinessId(UUID taskId) {
        return taskRepo.findById(taskId).map(Task::getBusinessId)
                .orElseThrow(() -> new EntityNotFoundException("Task not found"));
    }
}
```

### 8.7 Sample Unit Test

```java
// TaskControllerTest.java
@WebMvcTest(TaskController.class)
@Import({SecurityConfig.class, PermissionEvaluator.class})
class TaskControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean TaskService taskService;
    @MockBean PermissionEvaluator permEval;

    @Test
    @WithMockUser(username = "bbbbbbbb-0000-0000-0000-000000000002")
    void createTask_withPermission_returns201() throws Exception {
        when(permEval.hasPermission(any(), any(), eq("TASK_CREATE"))).thenReturn(true);
        when(taskService.createTask(any(), any(), any()))
                .thenReturn(Task.builder().id(UUID.randomUUID()).title("Test").build());

        mockMvc.perform(post("/api/v1/businesses/{bId}/tasks",
                        "aaaaaaaa-0000-0000-0000-000000000001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Test","priority":"HIGH","dueDate":"2025-08-01T18:00:00Z",
                         "assigneeId":"bbbbbbbb-0000-0000-0000-000000000003"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test"));
    }

    @Test
    @WithMockUser(username = "bbbbbbbb-0000-0000-0000-000000000003")
    void createTask_withoutPermission_returns403() throws Exception {
        when(permEval.hasPermission(any(), any(), eq("TASK_CREATE"))).thenReturn(false);

        mockMvc.perform(post("/api/v1/businesses/{bId}/tasks",
                        "aaaaaaaa-0000-0000-0000-000000000001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Test","priority":"HIGH"}"""))
                .andExpect(status().isForbidden());
    }
}
```

---

## 9. WhatsApp Chat Adapter

### 9.1 WhatsApp Client (Meta Cloud API)

```java
// WhatsAppClient.java
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppClient {

    @Value("${whatsapp.api-url}") private String apiUrl;
    @Value("${whatsapp.token}") private String token;
    @Value("${whatsapp.phone-number-id}") private String phoneNumberId;

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    /** Send a plain text message (in-session) */
    public void sendText(String to, String text) {
        sendPayload(Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "text",
                "text", Map.of("body", text)
        ));
    }

    /** Send interactive button message (in-session) */
    public void sendButtonMessage(String to, String bodyText, List<Button> buttons) {
        var buttonPayload = buttons.stream().map(b ->
                Map.of("type", "reply", "reply", Map.of("id", b.getId(), "title", b.getTitle()))
        ).toList();
        sendPayload(Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "button",
                        "body", Map.of("text", bodyText),
                        "action", Map.of("buttons", buttonPayload)
                )
        ));
    }

    /** Send interactive list message (in-session) */
    public void sendListMessage(String to, String bodyText, List<ListRow> rows) {
        var rowPayload = rows.stream().map(r ->
                Map.of("id", r.getId(), "title", r.getTitle(), "description", r.getDescription())
        ).toList();
        sendPayload(Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "interactive",
                "interactive", Map.of(
                        "type", "list",
                        "body", Map.of("text", bodyText),
                        "action", Map.of(
                                "button", "Select option",
                                "sections", List.of(Map.of("rows", rowPayload))
                        )
                )
        ));
    }

    /** Send template message (out-of-session, >24h) */
    public void sendTemplate(String to, String templateName, List<String> parameters) {
        var components = List.of(Map.of(
                "type", "body",
                "parameters", parameters.stream().map(p ->
                        Map.of("type", "text", "text", p)
                ).toList()
        ));
        sendPayload(Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "template",
                "template", Map.of(
                        "name", templateName,
                        "language", Map.of("code", "en_US"),
                        "components", components
                )
        ));
    }

    private void sendPayload(Map<String, Object> payload) {
        String url = apiUrl + "/" + phoneNumberId + "/messages";
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        try {
            restTemplate.postForEntity(url, entity, String.class);
        } catch (HttpClientErrorException e) {
            log.error("WhatsApp API error: {} — {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new WhatsAppSendException("Failed to send message to " + payload.get("to"), e);
        }
    }
}
```

### 9.2 To Switch to Twilio

```java
// TwilioWhatsAppClient.java (alternative adapter)
// Replace WhatsAppClient with this bean, same interface

// application.yml changes:
// twilio.account-sid: ${TWILIO_ACCOUNT_SID}
// twilio.auth-token: ${TWILIO_AUTH_TOKEN}
// twilio.from-number: whatsapp:+14155238886

@Service
public class TwilioWhatsAppClient {
    // Uses Twilio Java SDK: com.twilio.sdk:twilio:9.x
    // Twilio.init(accountSid, authToken);
    // Message.creator(new PhoneNumber("whatsapp:+1555..."),
    //                  new PhoneNumber("whatsapp:+14155238886"), body).create();
}
```

### 9.3 SMS Fallback Adapter

```java
// SmsAdapter.java — used when WhatsApp unavailable
@Service
@ConditionalOnProperty(name = "channel.sms.enabled", havingValue = "true")
public class SmsAdapter {

    @Value("${twilio.account-sid}") private String accountSid;
    @Value("${twilio.auth-token}") private String authToken;
    @Value("${twilio.sms-number}") private String fromNumber;

    public void sendSms(String to, String text) {
        // Strip rich formatting, send plain text
        String plainText = text.replaceAll("\\*|_|~|`", "");
        Twilio.init(accountSid, authToken);
        Message.creator(new PhoneNumber(to), new PhoneNumber(fromNumber), plainText).create();
    }
}
```

---

## 10. Frontend — React Popup Dashboard

```jsx
// Dashboard.jsx — minimal React popup for charts and file uploads
import { useState, useEffect } from "react";
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend
} from "recharts";

const API_BASE = "/api/v1";
const COLORS = ["#22c55e", "#3b82f6", "#f59e0b", "#ef4444"];

export default function Dashboard({ businessId, token }) {
  const [analytics, setAnalytics] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [tab, setTab] = useState("overview");

  useEffect(() => {
    fetchAnalytics();
    fetchTasks();
  }, [businessId]);

  async function fetchAnalytics() {
    const res = await fetch(`${API_BASE}/businesses/${businessId}/analytics`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    setAnalytics(await res.json());
  }

  async function fetchTasks() {
    const res = await fetch(`${API_BASE}/businesses/${businessId}/tasks?status=OPEN`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const data = await res.json();
    setTasks(data.tasks || []);
  }

  if (!analytics) return <div className="loading">Loading...</div>;

  const statusData = [
    { name: "Done", value: analytics.tasksDone },
    { name: "Open", value: analytics.tasksOpen },
    { name: "Overdue", value: analytics.tasksOverdue },
  ];

  return (
    <div style={styles.popup}>
      <h2 style={styles.title}>📊 Business Dashboard</h2>

      <div style={styles.tabs}>
        {["overview", "tasks", "team"].map((t) => (
          <button
            key={t}
            style={tab === t ? styles.activeTab : styles.tab}
            onClick={() => setTab(t)}
          >
            {t.charAt(0).toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      {tab === "overview" && (
        <div>
          <div style={styles.kpiRow}>
            <KpiCard label="Tasks Done" value={analytics.tasksDone} color="#22c55e" />
            <KpiCard label="Open Tasks" value={analytics.tasksOpen} color="#3b82f6" />
            <KpiCard label="Overdue" value={analytics.tasksOverdue} color="#ef4444" />
            <KpiCard label="Avg Days" value={`${(analytics.avgCompletionHours / 24).toFixed(1)}d`} color="#8b5cf6" />
          </div>

          <h3>Task Status Breakdown</h3>
          <ResponsiveContainer width="100%" height={200}>
            <PieChart>
              <Pie data={statusData} cx="50%" cy="50%" outerRadius={70} dataKey="value" label>
                {statusData.map((_, i) => <Cell key={i} fill={COLORS[i]} />)}
              </Pie>
              <Legend />
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>

          <h3>Workload by Employee</h3>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={analytics.workloadByEmployee}>
              <XAxis dataKey="name" />
              <YAxis />
              <Tooltip />
              <Bar dataKey="assigned" fill="#3b82f6" name="Assigned" />
              <Bar dataKey="completed" fill="#22c55e" name="Completed" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}

      {tab === "tasks" && (
        <div>
          <h3>Open Tasks ({tasks.length})</h3>
          {tasks.map((task) => (
            <div key={task.id} style={styles.taskCard}>
              <span style={priorityBadge(task.priority)}>{task.priority}</span>
              <strong>{task.title}</strong>
              <span style={styles.meta}>→ {task.assignee?.name} · Due {task.dueDate?.split("T")[0]}</span>
            </div>
          ))}
        </div>
      )}

      {tab === "team" && (
        <div>
          <h3>Top Performers</h3>
          {analytics.workloadByEmployee?.map((emp) => (
            <div key={emp.name} style={styles.taskCard}>
              <strong>{emp.name}</strong>
              <span style={styles.meta}>
                {emp.completed}/{emp.assigned} tasks ·{" "}
                {Math.round((emp.completed / emp.assigned) * 100)}% rate
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function KpiCard({ label, value, color }) {
  return (
    <div style={{ ...styles.kpiCard, borderTop: `4px solid ${color}` }}>
      <div style={{ fontSize: 28, fontWeight: 700, color }}>{value}</div>
      <div style={{ fontSize: 12, color: "#6b7280" }}>{label}</div>
    </div>
  );
}

function priorityBadge(p) {
  const colors = { LOW: "#22c55e", MEDIUM: "#f59e0b", HIGH: "#f97316", CRITICAL: "#ef4444" };
  return { background: colors[p] || "#6b7280", color: "#fff", borderRadius: 4,
           padding: "2px 8px", fontSize: 11, marginRight: 8 };
}

const styles = {
  popup: { fontFamily: "Inter, system-ui, sans-serif", padding: 24, maxWidth: 680,
           margin: "0 auto", background: "#fff", borderRadius: 12, boxShadow: "0 4px 24px rgba(0,0,0,.12)" },
  title: { fontSize: 20, fontWeight: 700, marginBottom: 16 },
  tabs: { display: "flex", gap: 8, marginBottom: 20 },
  tab: { padding: "6px 16px", border: "1px solid #d1d5db", borderRadius: 6, cursor: "pointer",
         background: "#f9fafb", color: "#374151" },
  activeTab: { padding: "6px 16px", border: "1px solid #3b82f6", borderRadius: 6, cursor: "pointer",
               background: "#3b82f6", color: "#fff" },
  kpiRow: { display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 12, marginBottom: 24 },
  kpiCard: { padding: 16, border: "1px solid #e5e7eb", borderRadius: 8, textAlign: "center" },
  taskCard: { padding: "10px 12px", border: "1px solid #e5e7eb", borderRadius: 8,
              marginBottom: 8, display: "flex", alignItems: "center", gap: 10 },
  meta: { color: "#6b7280", fontSize: 13, marginLeft: "auto" },
};
```

---

## 11. CI/CD & Deployment

### 11.1 `docker-compose.yml` (local dev)

```yaml
version: "3.9"
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: bizportal
      POSTGRES_USER: bizportal
      POSTGRES_PASSWORD: secret
    ports: ["5432:5432"]
    volumes: [pg_data:/var/lib/postgresql/data]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports: ["9000:9000", "9001:9001"]
    volumes: [minio_data:/data]

  app:
    build: .
    ports: ["8080:8080"]
    environment:
      DB_USER: bizportal
      DB_PASS: secret
      REDIS_HOST: redis
      WHATSAPP_TOKEN: ${WHATSAPP_TOKEN}
      WHATSAPP_PHONE_NUMBER_ID: ${WHATSAPP_PHONE_NUMBER_ID}
      WEBHOOK_VERIFY_TOKEN: ${WEBHOOK_VERIFY_TOKEN}
      WHATSAPP_APP_SECRET: ${WHATSAPP_APP_SECRET}
      JWT_SECRET: ${JWT_SECRET}
      S3_ENDPOINT: http://minio:9000
      S3_BUCKET: bizportal-attachments
      AWS_ACCESS_KEY_ID: minioadmin
      AWS_SECRET_ACCESS_KEY: minioadmin
    depends_on: [postgres, redis, minio]

volumes:
  pg_data:
  minio_data:
```

### 11.2 `Dockerfile`

```dockerfile
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 11.3 Kubernetes Manifests (excerpt)

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bizportal-app
spec:
  replicas: 2
  selector:
    matchLabels:
      app: bizportal
  template:
    metadata:
      labels:
        app: bizportal
    spec:
      containers:
        - name: app
          image: your-registry/bizportal:latest
          ports:
            - containerPort: 8080
          envFrom:
            - secretRef:
                name: bizportal-secrets
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "1000m"
              memory: "1Gi"
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 15
---
apiVersion: v1
kind: Service
metadata:
  name: bizportal-svc
spec:
  type: ClusterIP
  selector:
    app: bizportal
  ports:
    - port: 80
      targetPort: 8080
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: bizportal-ingress
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
    - hosts: [api.yourdomain.com]
      secretName: bizportal-tls
  rules:
    - host: api.yourdomain.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: bizportal-svc
                port:
                  number: 80
```

### 11.4 GitHub Actions CI

```yaml
# .github/workflows/ci.yml
name: CI
on:
  push:
    branches: [main, develop]
  pull_request:
jobs:
  build-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env: {POSTGRES_DB: bizportal, POSTGRES_USER: bizportal, POSTGRES_PASSWORD: secret}
        ports: ["5432:5432"]
      redis:
        image: redis:7
        ports: ["6379:6379"]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: "17"
          distribution: temurin
          cache: maven
      - run: ./mvnw verify
      - name: Build Docker image
        run: docker build -t bizportal:${{ github.sha }} .
      - name: Push to registry
        if: github.ref == 'refs/heads/main'
        run: |
          echo ${{ secrets.REGISTRY_TOKEN }} | docker login -u ${{ secrets.REGISTRY_USER }} --password-stdin
          docker push bizportal:${{ github.sha }}
```

---

## 12. Security Checklist & GDPR

### Security Checklist

- [ ] **Webhook HMAC-SHA256 validation** on every inbound POST (reject if signature mismatch)
- [ ] **Phone number verification** — users can only act on their verified phone; no spoofing
- [ ] **JWT expiry** — 24h access tokens; rotate signing secret via environment variable
- [ ] **Role-based access control** on every API endpoint via `@PreAuthorize`
- [ ] **Rate limiting** — Redis-based counter per phone: max 60 messages/min; 5 auth attempts/hour
- [ ] **S3 attachment access** — pre-signed URLs with 15-minute expiry; never expose raw S3 URLs
- [ ] **Encryption at rest** — enable AWS S3 SSE-S3 or SSE-KMS for attachments; PostgreSQL transparent data encryption on managed DB
- [ ] **TLS everywhere** — HTTPS only; HSTS header; minimum TLS 1.2
- [ ] **Input sanitisation** — escape all text inserted into messages; prevent template injection
- [ ] **No PII in logs** — mask phone numbers and names in log output
- [ ] **Secrets management** — all credentials via environment variables or Kubernetes Secrets; never committed to source
- [ ] **Dependency scanning** — OWASP Dependency-Check in CI
- [ ] **Meta Business Verification** — complete WhatsApp Business Verification for higher messaging limits

### GDPR / PII Considerations

| Item | Implementation |
|------|----------------|
| **Lawful basis** | Legitimate interest (employee task management); document in privacy policy |
| **Data minimisation** | Collect only phone number, name, task data — no unnecessary PII |
| **Right to erasure** | `/api/v1/users/{id}/gdpr-delete` endpoint — anonymises name, nulls phone, retains task audit hashes |
| **Data retention** | Audit logs: 1 year (configurable). Attachments: 1 year then auto-delete via S3 lifecycle rule |
| **Data portability** | CSV export endpoint for user's own data |
| **WhatsApp compliance** | Template messages only outside 24h session; opt-out handled by "STOP" keyword |
| **Consent for invites** | Employee must explicitly accept invite before any business data is shared with them |
| **Breach notification** | Monitoring alert triggers within 72h SLA; incident response runbook in `/docs/incident-response.md` |

---

## 13. Postman Collection & Demo Accounts

### Demo Accounts

| Role | Phone | Name | Portal |
|------|-------|------|--------|
| CEO | +15550000001 | Alice | Acme Corp (#ACM001) |
| Manager | +15550000002 | Bob | Acme Corp (#ACM001) |
| Employee | +15550000003 | Carol | Acme Corp (#ACM001) |

### Postman Collection (JSON structure)

```json
{
  "info": {"name": "BizPortal API", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
  "variable": [
    {"key": "base_url", "value": "http://localhost:8080/api/v1"},
    {"key": "ceo_token", "value": "{{auto-filled after auth}}"},
    {"key": "business_id", "value": "aaaaaaaa-0000-0000-0000-000000000001"}
  ],
  "item": [
    {
      "name": "Auth",
      "item": [
        {
          "name": "Authenticate (phone-based)",
          "request": {
            "method": "POST",
            "url": "{{base_url}}/auth/token",
            "body": {"mode": "raw", "raw": "{\"phoneNumber\": \"+15550000001\"}"}
          }
        }
      ]
    },
    {
      "name": "Business",
      "item": [
        {"name": "Create Business", "request": {"method": "POST", "url": "{{base_url}}/businesses"}},
        {"name": "Get Business", "request": {"method": "GET", "url": "{{base_url}}/businesses/{{business_id}}"}}
      ]
    },
    {
      "name": "Tasks",
      "item": [
        {"name": "List Tasks", "request": {"method": "GET", "url": "{{base_url}}/businesses/{{business_id}}/tasks"}},
        {"name": "Create Task", "request": {"method": "POST", "url": "{{base_url}}/businesses/{{business_id}}/tasks"}},
        {"name": "Approve Task", "request": {"method": "POST", "url": "{{base_url}}/businesses/{{business_id}}/tasks/:taskId/approve"}}
      ]
    },
    {
      "name": "Analytics",
      "item": [
        {"name": "Get KPIs", "request": {"method": "GET", "url": "{{base_url}}/businesses/{{business_id}}/analytics"}},
        {"name": "Export CSV", "request": {"method": "GET", "url": "{{base_url}}/businesses/{{business_id}}/reports/export?type=tasks"}}
      ]
    }
  ]
}
```

### WhatsApp QA Checklist

- [ ] CEO number receives welcome message on first contact
- [ ] All 6 setup steps complete and confirm before portal creation
- [ ] Employee invite template received on correct number within 30s
- [ ] Employee accept/decline recorded and CEO notified
- [ ] Task assignment notifies employee (in-session: interactive button; out-of-session: template)
- [ ] Employee can acknowledge and complete task with proof
- [ ] Manager receives verification prompt with attachment link
- [ ] Approve/reject updates task status and notifies employee
- [ ] STATS command returns correct KPIs matching database
- [ ] SWITCH PORTAL command shows only portals user has access to
- [ ] Out-of-session (>24h) notifications use approved Meta templates
- [ ] STOP keyword removes user from broadcasts
- [ ] SMS fallback works for all critical flows without rich messages

---

## 14. Monitoring & Alerting Rules

```yaml
# prometheus-rules.yaml
groups:
  - name: bizportal.critical
    rules:
      - alert: WhatsAppMessageFailureRate
        expr: rate(whatsapp_send_errors_total[5m]) / rate(whatsapp_send_total[5m]) > 0.05
        for: 2m
        labels: {severity: critical}
        annotations:
          summary: "WhatsApp message failure rate > 5%"

      - alert: WebhookQueueBacklog
        expr: redis_list_length{key="notification_queue"} > 500
        for: 5m
        labels: {severity: warning}
        annotations:
          summary: "Notification queue backing up ({{$value}} messages)"

      - alert: TaskBacklog
        expr: bizportal_tasks_overdue_count > 50
        for: 0m
        labels: {severity: warning}
        annotations:
          summary: "{{$value}} tasks are overdue across all portals"

      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_pending > 5
        for: 1m
        labels: {severity: critical}
        annotations:
          summary: "DB connection pool exhausted"

      - alert: HighApiLatency
        expr: histogram_quantile(0.95, http_server_requests_seconds_bucket) > 2.0
        for: 3m
        labels: {severity: warning}
        annotations:
          summary: "95th percentile API latency > 2s"

      - alert: FSMStateStuck
        expr: increase(conversation_fsm_stuck_total[10m]) > 3
        for: 0m
        labels: {severity: warning}
        annotations:
          summary: "{{$value}} conversations stuck in FSM state"
```

### Key Metrics to Expose

| Metric | Type | Description |
|--------|------|-------------|
| `whatsapp_send_total` | Counter | Total messages sent |
| `whatsapp_send_errors_total` | Counter | Failed sends by error type |
| `whatsapp_template_send_total` | Counter | Template messages sent (out-of-session) |
| `conversation_fsm_transitions_total` | Counter | FSM state transitions |
| `conversation_fsm_stuck_total` | Counter | Conversations stuck > 10 min |
| `tasks_created_total` | Counter | Tasks created |
| `tasks_completed_total` | Counter | Tasks completed |
| `task_completion_duration_seconds` | Histogram | Time from create to approve |
| `notification_queue_length` | Gauge | Redis queue depth |
| `notification_retry_total` | Counter | Notification retries |

---

## 15. Timeline & Effort Breakdown

### MVP (v1.0) — 5 Sprints × 2 Weeks

| Sprint | Focus | Deliverables | Effort |
|--------|-------|-------------|--------|
| **S1** | Foundation | DB schema, Flyway, Spring Boot skeleton, auth, webhook handler, FSM skeleton | 2 devs × 10d |
| **S2** | Core flows | Portal setup flow (chat), employee invite, hierarchy management | 2 devs × 10d |
| **S3** | Task lifecycle | Task CRUD, assignment, completion, proof upload, approval/rejection | 2 devs × 10d |
| **S4** | Notifications & Analytics | Reminder engine (Quartz), template messages, KPI queries, analytics snapshots | 2 devs × 10d |
| **S5** | Polish & Deploy | Dashboard popup, Docker/K8s, CI/CD, security hardening, QA, documentation | 2 devs + 1 QA × 10d |

**Total: ~10 weeks / 2–3 engineers**

### v2.0 Optional Features

| Feature | Effort |
|---------|--------|
| CRM/ERP webhook integrations | 1 sprint |
| Multi-language support (i18n) | 1 sprint |
| Voice note transcription | 1 sprint |
| AI-powered auto-task suggestions | 2 sprints |
| Advanced reporting + BI export | 1 sprint |
| Native mobile app (React Native) | 4 sprints |

### Recommended Team

| Role | Count | Responsibility |
|------|-------|---------------|
| Backend Engineer | 2 | Spring Boot, FSM, DB, integrations |
| Frontend Engineer | 1 | React dashboard, UX polish |
| QA Engineer | 1 | WhatsApp flow testing, automation |
| DevOps | 0.5 | K8s, CI/CD, monitoring |

---

## 16. Developer README & Quick-Start

```markdown
# BizPortal — Quick-Start Guide

## Prerequisites
- Java 17+, Maven 3.9+
- Docker & Docker Compose
- ngrok (for local WhatsApp webhook testing)
- Meta Developer account with WhatsApp Business API access

## Local Setup (5 minutes)

### 1. Clone & configure
git clone https://github.com/your-org/bizportal
cd bizportal
cp .env.example .env
# Edit .env: add WHATSAPP_TOKEN, WHATSAPP_PHONE_NUMBER_ID, JWT_SECRET

### 2. Start infrastructure
docker-compose up -d postgres redis minio

### 3. Build and run
./mvnw spring-boot:run

### 4. Expose webhook for local testing
ngrok http 8080
# Copy the https URL, e.g. https://abc123.ngrok.io

### 5. Configure Meta webhook
- Go to developers.facebook.com → Your App → WhatsApp → Configuration
- Webhook URL: https://abc123.ngrok.io/api/v1/webhook
- Verify token: (value of WEBHOOK_VERIFY_TOKEN in .env)
- Subscribe to: messages, message_deliveries, message_reads

### 6. Seed demo data
./mvnw flyway:migrate  # runs V1 + V2 automatically on startup

### 7. Test the flow
- Send a WhatsApp message from +15550000001 (CEO demo number)
- Follow the portal setup conversation
- Add +15550000002 and +15550000003 as employees

## Running Tests
./mvnw test

## Access Dashboard
http://localhost:8080/dashboard?businessId=aaaaaaaa-0000-0000-0000-000000000001&token=<jwt>

## Environment Variables Reference
| Variable | Description |
|----------|-------------|
| WHATSAPP_TOKEN | Meta Cloud API permanent token |
| WHATSAPP_PHONE_NUMBER_ID | Phone number ID from Meta dashboard |
| WEBHOOK_VERIFY_TOKEN | Any random string for webhook verification |
| WHATSAPP_APP_SECRET | App Secret from Meta App settings (for HMAC) |
| DB_USER / DB_PASS | PostgreSQL credentials |
| REDIS_HOST | Redis hostname |
| JWT_SECRET | Min 32-char random string for JWT signing |
| S3_BUCKET | S3/MinIO bucket name for attachments |
| S3_ENDPOINT | Override for local MinIO (leave blank for AWS) |

## Production Deployment
kubectl apply -f k8s/
kubectl create secret generic bizportal-secrets --from-env-file=.env

## Switching WhatsApp Provider
# To use Twilio instead of Meta Cloud API:
# 1. Set channel.provider=TWILIO in application.yml
# 2. Add TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN to .env
# 3. The adapter is auto-selected via @ConditionalOnProperty

## Next Steps for Dev Team
Priority order for MVP:
1. Complete FSM handlers for all setup states (SetupFlowHandler)
2. Implement NotificationService retry logic with backoff
3. Register all template messages in Meta Business Manager
4. Complete TaskFlowHandler for full task lifecycle
5. Add integration tests for end-to-end WhatsApp flows
6. Security review: HMAC validation, rate limiting, PII masking in logs
```

---

*Generated by BizPortal Architecture Generator — v1.0*
*Last updated: 2025*
