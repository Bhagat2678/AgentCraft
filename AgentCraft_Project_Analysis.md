# AgentCraft — Full Project Analysis & Bug Report

> Analyzed on 2026-07-03. Backend by Codex. Reviewed thoroughly.

> [!CAUTION]
> **A live Telegram bot token is committed to the repo in a file named `env` at the root.** Revoke it immediately via @BotFather before doing anything else.

---

## 1. What Is This Project?

AgentCraft (internally named **ContextCraft / BizPortal**) is a **WhatsApp-first business management platform**. The core idea:

- A business owner sets up their company via a **WhatsApp chatbot conversation**
- Employees are invited, roles are assigned, and tasks are created — all through WhatsApp
- A **React web dashboard** (Telegram Mini App style UI) is the web companion for analytics, viewing tasks, managing users, etc.
- The backend is a **Spring Boot 3.4 / Java 17** REST API with PostgreSQL, Redis, and Flyway

**Tech Stack:**
| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.4.1, Java 17, Spring Security (JWT), JPA/Hibernate |
| Database | PostgreSQL 15 (via Flyway migrations) |
| Cache / Session | Redis 7 (FSM state store) |
| Scheduler | Quartz |
| WhatsApp | Meta Cloud API v19.0 |
| Frontend | React 18 + Vite (single-file, no router) |
| Deployment | Docker Compose (Postgres + Redis only) |

> [!WARNING]
> **Architecture Mismatch:** The backend is entirely WhatsApp-based (Meta Cloud API). The frontend is titled "BizPortal Telegram Mini App" and has a `TELEGRAM_README.md` at the root. There is a Telegram bot token in `env` — but **zero Telegram backend code exists**. The project appears mid-pivot from WhatsApp to Telegram, with neither side fully functional.

---

## 2. Architecture Overview

```
┌─────────────────────────────────┐
│  Meta WhatsApp Cloud API        │
│  (webhook POST /api/v1/webhook) │
└────────────┬────────────────────┘
             ▼
┌────────────────────────────────────────────────────────────┐
│  Spring Boot Backend (com.contextcraft.portal)             │
│                                                            │
│  WebhookController ──▶ ConversationFSM ──▶ WhatsAppAdapter │
│                              │                             │
│              ┌───────────────┴──────────────┐             │
│              ▼                              ▼             │
│         BusinessService              TaskService          │
│         UserService                  RoleService          │
│              │                              │             │
│         PostgreSQL (JPA)             Redis (FSM store)    │
│                                                            │
│  REST API Controllers (JWT-protected):                     │
│   AuthController  BusinessController  TaskController       │
│   UserController  RoleController      AnalyticsController  │
└────────────────────────────────────────────────────────────┘
             ▲
             │ (JWT Bearer — NOT connected yet)
             ▼
┌───────────────────────────────────┐
│  Frontend (React/Vite SPA)        │
│  src/main.jsx (805 lines, 1 file) │
│  src/styles.css (747 lines)       │
│  — Static UI prototype only —     │
│  — No API calls whatsoever —      │
└───────────────────────────────────┘
```

---

## 3. What Is Actually Implemented

### ✅ Backend — Reasonably Complete

| Component | Status | Notes |
|---|---|---|
| PostgreSQL schema | ✅ Done | 3 Flyway migrations, 11 tables, proper indexes |
| WhatsApp FSM chatbot | ✅ Done | Full business setup, task create, invite flows |
| WhatsApp adapter | ✅ Done | sendText, sendTemplate, markAsRead, exponential backoff retry |
| Webhook handler | ✅ Done | HMAC-SHA256 validation, hub challenge, event routing |
| JWT auth | ✅ Done | jjwt 0.12.6, stateless, filter chain |
| Spring Security | ✅ Done | Role-based, method-level @PreAuthorize |
| TaskService | ✅ Done | Create, assign, updateStatus, approve/reject, audit history |
| UserService | ✅ Done | Invite flow, accept invite, suspend, role assign |
| RoleService | ✅ Partial | Seeding default roles — implementation not fully read |
| BusinessService | ✅ Partial | Basic CRUD, no WhatsApp phone registration |
| AnalyticsController | ✅ Partial | Endpoints exist, KPI summary works |
| Redis FSM store | ✅ Done | Conversation context persistence |
| Quartz scheduler | ⚠️ Skeleton | Config exists, no jobs implemented |
| Docker Compose | ✅ Done | Postgres + Redis, but backend itself not in compose |
| Tests | ❌ None | Test directory exists but no test classes |

### ❌ Frontend — Pure UI Mockup, Not Functional

The entire frontend is a **high-fidelity static prototype** — it renders beautifully but does absolutely nothing functional.

| Feature | Status |
|---|---|
| UI layout & design system | ✅ Done (CSS only) |
| Multi-step setup wizard (UI) | ✅ Done (form renders) |
| Dashboard with role-based nav | ✅ Done (mock role select) |
| Telegram Web App SDK | ❌ Not integrated |
| Any backend API calls | ❌ Zero fetch() calls exist |
| Authentication / JWT | ❌ Completely missing |
| Form data capture | ❌ Forms are uncontrolled, data lost |
| URL routing | ❌ useState only, no react-router |
| Real bot token validation | ❌ Faked with setTimeout |
| Search / filter / pagination | ❌ UI only, no logic |
| Any CRUD operations | ❌ None |

---

## 4. Bugs & Issues Found

### 🚨 EMERGENCY — Security (Fix Before Anything Else)

**SEC 0: Live Telegram bot token committed to git**
- File: `env` (root level, NOT `.env.example`)
- `TELEGRAM_BOT_TOKEN=8841120098:AAFqvWqxNTRQPQ8J2jp4pZECAs0288YWxNk` is a real active token
- `TELEGRAM_WEBHOOK_SECRET=c2c77d61b365ff349a60e0a54e9bc36d` also exposed
- The `env` file is **NOT in `.gitignore`** (gitignore only ignores `.env` with a dot, not `env`)
- **Action: Immediately revoke via @BotFather. Add `env` to `.gitignore`. Purge from git history.**

---

### 🔴 CRITICAL — Backend

**BUG 1: `approveTask()` crashes with NullPointerException when called from WhatsApp FSM**
- File: [`TaskService.java`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/java/com/contextcraft/portal/service/TaskService.java#L98-L124)
- `approveTask(taskId, assignmentId, ...)` calls `taskAssignmentRepository.findById(assignmentId)` — but the FSM passes `ctx.getPendingReviewAssignmentId()` which is **never set anywhere in ConversationFsm.java**
- `handleReviewDecision()` and `handleRejectReason()` reference `ctx.getPendingReviewTaskId()` and `ctx.getPendingReviewAssignmentId()` — these values are never populated in the FSM flow (there's no state that sets them)
- **Effect**: Task review/approval via WhatsApp will always throw `NPE`

**BUG 2: `listByBusiness()` ignores `assigneeId` and `priority` filters**
- File: [`TaskService.java`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/java/com/contextcraft/portal/service/TaskService.java#L155-L159)
- The method signature accepts `assigneeId` and `priority` but the implementation completely ignores them — only `status` filter works
- **Effect**: REST API filtering by assignee or priority silently returns wrong/unfiltered data

**BUG 3: `UserService.inviteUser()` — double phone lookup and anonymous inner class anti-pattern**
- File: [`UserService.java`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/java/com/contextcraft/portal/service/UserService.java#L83-L94)
- Line 83: `userPhoneRepository.findByPhoneNumber(phoneNumber).get()` — called immediately after the same query on line 64 — an extra unnecessary DB round-trip
- Lines 92–94: `new Department() {{ setId(departmentId); }}` — double-brace initialization creates an anonymous subclass. JPA will not recognize this as a managed entity proxy — will fail or cause detached entity issues
- **Effect**: Role assignment with a department during invite will likely fail with JPA errors

**BUG 4: HMAC security bypass in dev mode is never disabled**
- File: [`WebhookController.java`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/java/com/contextcraft/portal/webhook/WebhookController.java#L145-L153)
- If `app.whatsapp.app-secret=REPLACE_ME` (the default value in `application.properties`), the HMAC check is **completely skipped**
- `application.properties` ships with `REPLACE_ME` as the default — meaning if anyone runs this without changing the config, the webhook accepts **any forged request**
- **Effect**: Critical security hole in default configuration

**BUG 5: Hardcoded credentials in `application.properties` checked into git**
- File: [`application.properties`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/resources/application.properties)
- `spring.datasource.password=password`, `app.jwt.secret=Y2hhbmdlbWVj...` are hardcoded defaults
- The JWT secret is a static Base64 string — anyone with the source can forge tokens
- **Effect**: Security risk; should use environment variables (`${DB_PASSWORD}`, `${JWT_SECRET}`)

**BUG 6: `application.properties` doesn't reference `.env.example` variables**
- The `.env.example` defines `DB_HOST`, `DB_PASSWORD`, `JWT_SECRET`, `WA_PHONE_NUMBER_ID`, etc.
- `application.properties` uses **hardcoded values**, not `${DB_HOST}`, `${JWT_SECRET}` etc.
- The two files are completely disconnected — the env example is documentation only, not wired up
- **Effect**: You can't actually configure the app via environment variables as documented

**BUG 7: `getKpiSummary()` loads ALL assignments in memory**
- File: [`TaskService.java`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/java/com/contextcraft/portal/service/TaskService.java#L140-L144)
- `taskAssignmentRepository.findAll()` loads every assignment across ALL businesses, then filters in-memory
- **Effect**: Severe performance/memory issue at scale — should use a filtered query

**BUG 8: Missing CORS configuration**
- No `@CrossOrigin` or `CorsConfigurationSource` bean anywhere in the codebase
- **Effect**: The React frontend cannot call the backend API from a different origin (browser will block all requests)

**BUG 9: No `@Async` processing for webhook — blocking Meta's webhook**
- File: [`WebhookController.java`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/java/com/contextcraft/portal/webhook/WebhookController.java#L73)
- The comment even acknowledges this: *"can be moved to async"* but it wasn't done
- FSM + DB write + WhatsApp API calls all happen synchronously in the webhook thread
- **Effect**: If WhatsApp API is slow, Meta will get a timeout, assume delivery failed, and **retry** — causing duplicate message processing

**BUG 13: `DONE` command never handled in FSM IDLE state**
- File: [`ConversationFsm.java`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/java/com/contextcraft/portal/fsm/ConversationFsm.java#L280-L306) `handleIdle()`
- Employees get a WhatsApp notification: *"Reply DONE when complete"* — but the FSM IDLE switch has no `"DONE"` case
- **Effect**: Employees cannot mark tasks done via WhatsApp. Core workflow is broken.

**BUG 14: `pendingReviewTaskId` and `pendingReviewAssignmentId` are NEVER set in FSM**
- File: [`ConversationFsm.java`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/java/com/contextcraft/portal/fsm/ConversationFsm.java#L531)
- `handleReviewDecision()` and `handleRejectReason()` call `ctx.getPendingReviewTaskId()` / `ctx.getPendingReviewAssignmentId()` — but nothing in the entire FSM ever sets these fields
- **Effect**: Manager review/approval via WhatsApp is entirely non-functional. The state machine can never reach or exit `TASK_REVIEW_DECISION` state with valid data.

**BUG 15: `MessageTemplate` entity is a stub — missing ALL domain fields**
- File: [`MessageTemplate.java`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/java/com/contextcraft/portal/entity/MessageTemplate.java)
- The entity only has `id`, `createdAt`, `updatedAt`. Missing: `business_id`, `name`, `body`, `variables`, `wa_template_name`, `is_active`
- The V1 migration defines ALL these columns in the DB
- **Effect**: With `spring.jpa.hibernate.ddl-auto=validate`, Spring Boot will **FAIL TO START** because the DB has columns the entity doesn't map

**BUG 16: V2 seed demo users have NO role assignments**
- File: [`V2__seed_demo.sql`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/resources/db/migration/V2__seed_demo.sql)
- Demo users (Alice CEO, Bob Manager, Carol Employee) are inserted but `user_roles` table has NO entries for them
- **Effect**: Demo users cannot perform any role-restricted API actions — every `@PreAuthorize` check fails

**BUG 17: Quartz jobs will crash with NPE — missing `SpringBeanJobFactory`**
- File: [`QuartzConfig.java`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/java/com/contextcraft/portal/config/QuartzConfig.java)
- `TaskReminderJob` and `AnalyticsSnapshotJob` depend on Spring-injected repositories and adapters
- When Quartz fires jobs via JDBC store, it instantiates them via reflection — Spring injection is bypassed unless `SpringBeanJobFactory` is configured
- No `SpringBeanJobFactory` bean exists in `QuartzConfig.java`
- **Effect**: Both scheduled jobs crash with `NullPointerException` every single time they run

**BUG 18: `PortalUserDetailsService` will throw `LazyInitializationException`**
- Calling `user.getPhones()` (a `LAZY` collection) inside `JwtAuthFilter` which runs outside a transaction boundary
- **Effect**: Every authenticated API request will fail in production

**BUG 19: `TASK_VIEW_OWN` permission doesn't actually restrict to own tasks**
- File: [`TaskController.java`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/src/main/java/com/contextcraft/portal/controller/TaskController.java#L39)
- The `@PreAuthorize` uses OR — if user has `TASK_VIEW_OWN`, they still see ALL tasks via `listByBusiness`
- Service doesn't filter by the caller's userId when only `TASK_VIEW_OWN` is held
- **Effect**: Security/data isolation bug — employees can see all other employees' tasks

**BUG 20: `BusinessController.createBusiness()` — owner is never assigned CEO role**
- When a business is created via REST API, `roleService.seedDefaultRoles()` runs but the authenticated user is never assigned CEO role and `business.ownerUserId` is never set
- `principal` is injected but never used
- **Effect**: REST-created businesses have no owner

**BUG 10: Backend not included in Docker Compose**
- [`docker-compose.yml`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/docker-compose.yml) only starts Postgres and Redis
- There is a `Dockerfile` in `/backend` but it is not referenced in compose
- **Effect**: Running `docker-compose up` gives you infrastructure but no working application

**BUG 11: `pom.xml` description says "WhatsApp Business Portal" but project is "AgentCraft"**
- [`pom.xml`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/backend/pom.xml#L15): `<description>WhatsApp Business Portal</description>`, groupId is `com.contextcraft`, artifactId is `portal`
- None of these match the project name "AgentCraft"

**BUG 12: Missing `Lombok` / `Jackson` JSONB converters for entity fields**
- `Task.java`, `TaskHistory.java` use `Map<String, Object>` for `oldValue`/`newValue` mapped to `JSONB`
- No `@Convert` annotation or `AttributeConverter` for Map → JSONB is defined anywhere in the entities
- **Effect**: JPA will fail to persist/read JSONB columns unless Hibernate's built-in JSONB support is enabled (requires explicit config for PostgreSQL)

---

### 🔴 CRITICAL — Frontend

**BUG 21: Zero API integration — the entire frontend is disconnected from backend**
- No `fetch()`, no `axios`, no HTTP client of any kind in 805 lines of code
- No `.env` file, no `VITE_API_URL`, no base URL
- **Effect**: Deploying both frontend and backend together produces a UI that shows hardcoded zeros and placeholder text forever

**BUG 22: No Telegram Web App SDK**
- [`index.html`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/Frontend/index.html) has no `<script src="https://telegram.org/js/telegram-web-app.js">`
- Form field has `placeholder: 'Resolved from initData'` but `window.Telegram` is never read
- **Effect**: Cannot function as a Telegram Mini App at all

**BUG 23: Bot verification is completely fake**
- File: [`main.jsx`](file:///C:/Users/achyu/OneDrive/Desktop/PROJECTS/FlowZint/AgentCraft/Frontend/src/main.jsx) — `VerificationPanel`
- Calls `window.setTimeout(() => setVerificationStatus(botToken.trim() ? 'success' : 'error'), 1100)` 
- Any non-empty string passes as a valid bot token
- **Effect**: Users will "verify" with garbage tokens and think setup succeeded

**BUG 24: No `vite.config.js` — React plugin is never applied**
- `@vitejs/plugin-react` is a dependency but there's no config file to register it
- Without `vite.config.js`, Vite uses defaults — JSX transformation may fail in some environments
- **Effect**: Potential build failures; the plugin serves no purpose without config

**BUG 17: All dependencies pinned to `"latest"`**
- `package.json`: every dep is `"latest"` — React, Vite, lucide-react all unpinned
- **Effect**: Any `npm install` in the future could pull a breaking major version; no reproducible builds

**BUG 18: `index.html` title is still "BizPortal Telegram Mini App"**
- Wrong brand name visible in browser tabs and search engine results

**BUG 19: `AsyncState` component defined but never used (dead code)**
- Lines 611–620 in `main.jsx` — orphaned component

---

### 🟠 HIGH — Missing Implementations

| Missing Feature | Impact |
|---|---|
| No Quartz jobs implemented | No scheduled analytics snapshots, no notification retries |
| No notification delivery service | `notifications` table exists but nothing reads/sends from it |
| No `NotificationController` / REST endpoint | Frontend can't poll notifications |
| No business registration endpoint (REST) | Frontend setup flow has nowhere to POST |
| No `KnowledgeBase` entity or API | Knowledge screen is pure placeholder |
| No `ConversationController` | Conversations screen has no API |
| No `AnalyticsController` detail | Just stub — no time-series data |
| No employee directory listing endpoint in format frontend expects | UserController exists but not wired |
| No attachment upload / S3 integration | `attachments` table + entity exist; no upload logic |
| No WhatsApp template registration | Templates stored in DB but no Meta registration flow |
| `topPerformer` in KPI always returns `"—"` | Hardcoded placeholder comment says "Phase 6" |

---

## 5. Is It Running?

**No. It cannot run in its current state.** There are startup-blocking bugs:

> [!CAUTION]
> The app **will fail to start** due to BUG 15 (`MessageTemplate` entity/DB schema mismatch with `ddl-auto=validate`) and BUG 18 (`LazyInitializationException` in JWT filter on first request). Fix those before attempting to run.

### Backend — Can Potentially Run, But Won't Work End-to-End

To even start the Spring Boot app you need:
1. ✅ Java 17 installed
2. ✅ Maven (`./mvnw`) — included
3. ❌ PostgreSQL running with `contextcraft` DB — not auto-started unless you use Docker Compose
4. ❌ Redis running on port 6379 — not auto-started unless you use Docker Compose
5. ❌ Real WhatsApp credentials (`phone-number-id`, `access-token`) — all `REPLACE_ME`
6. ❌ A publicly accessible URL for Meta's webhook (needs ngrok or deployment)
7. ⚠️ The JSONB → Map mapping may crash on startup due to missing converters

**To start with Docker Compose + Maven:**
```bash
docker-compose up -d          # Start Postgres + Redis
cd backend
./mvnw spring-boot:run        # Start Spring Boot (will fail if JSONB mapping is broken)
```

### Frontend — Runs, But is Purely Decorative

```bash
cd Frontend
npm install
npm run dev                   # Starts Vite dev server
```
The UI will load and look good, but every data table shows "Waiting for API response" forever and every form submit does nothing.

---

## 6. Summary Table

| Dimension | Score | Notes |
|---|---|---|
| **Backend Architecture** | 7/10 | Good structure, real FSM, proper JWT, good DB schema |
| **Backend Functionality** | 3/10 | App fails to start (MessageTemplate), DONE command missing, review flow broken, Quartz crashes |
| **Backend Security** | 3/10 | Live token committed; hardcoded secrets; HMAC bypass in default config; CORS missing |
| **Frontend Functionality** | 1/10 | 100% static — no API calls, no auth, no real forms |
| **Frontend-Backend Integration** | 0/10 | Completely disconnected |
| **Runnability** | 1/10 | Cannot run: startup crash + multiple critical blockers |
| **Test Coverage** | 0/10 | No tests anywhere |
| **Overall** | **3/10** | Strong architectural ideas; significant implementation gaps and Codex-generated bugs |

---

## 7. Priority Fix List

### 🚨 EMERGENCY (Do RIGHT NOW)

1. **Revoke Telegram bot token** — Go to @BotFather and revoke `8841120098:AAFqvWqxNTRQPQ8J2jp4pZECAs0288YWxNk`
2. **Add `env` to `.gitignore`** — The file without a dot prefix is NOT ignored

### Do These First (App Won't Start Without These)

3. **Fix `MessageTemplate` entity** — Add all missing JPA fields (`businessId`, `name`, `body`, `variables`, `waTemplateName`, `isActive`) to match the DB schema, or `ddl-auto=validate` will crash on startup
4. **Fix `PortalUserDetailsService` lazy loading** — Add `@Transactional` to `loadUserById()` or change `user.getPhones()` fetch to EAGER
5. **Fix `application.properties`** — Replace hardcoded values with `${DB_PASSWORD}`, `${JWT_SECRET}`, `${WA_ACCESS_TOKEN}`, `${WA_VERIFY_TOKEN}`, `${WA_APP_SECRET}`
6. **Add CORS config** — Add `CorsConfigurationSource` bean in `SecurityConfig.java`

### Do These Next (Core WhatsApp Flow Is Broken)

7. **Handle `DONE` command in FSM IDLE** — Add `case "DONE"` to trigger task status update
8. **Fix task review FSM** — Populate `pendingReviewTaskId` and `pendingReviewAssignmentId` in the notification flow; add state transition into `TASK_REVIEW_DECISION` when manager taps review
9. **Fix Quartz `SpringBeanJobFactory`** — Add `SpringBeanJobFactory` bean to `QuartzConfig` so both Quartz jobs can use Spring-injected dependencies
10. **Fix `inviteUser()` detached Department** — Use `departmentRepository.getReferenceById(departmentId)` instead of anonymous class
11. **Fix `handleSetupConfirm()` ownerUserId not saved** — Call `businessRepository.save(business)` after setting owner
12. **Add V2 seed roles** — Add `user_roles` inserts in the demo migration for Alice/Bob/Carol
13. **Fix task approval NPE** — Add null guard or `@NotBlank` validation on `assignmentId` in `ApproveTaskRequest`

### Do These After (Performance, Correctness)

14. **Fix `getKpiSummary()` full table scan** — Replace `findAll()` with `findByTask_Business_Id(businessId)`
15. **Fix `listByBusiness()` ignores filters** — Implement assignee/priority filtering in JPA queries
16. **Fix `TASK_VIEW_OWN` data isolation** — Filter tasks by `principal.userId` when user only has `TASK_VIEW_OWN`
17. **Add `@Async` to webhook processing** — Return 200 immediately, process FSM in background thread
18. **Add backend to `docker-compose.yml`** — Include Spring Boot service

### Frontend (Must Be Built From Scratch Effectively)

19. **Add `vite.config.js`** — Register `@vitejs/plugin-react`
20. **Add Telegram Web App SDK** — `<script>` in `index.html`, read `initData` on mount
21. **Build API service layer** — Create `src/api.js` with `VITE_API_URL`, all fetch calls
22. **Wire authentication** — POST `/auth/token`, store JWT, attach as `Authorization: Bearer` header
23. **Implement all form submissions** — Setup flow must POST to `/businesses`, verify webhook endpoint
24. **Replace all placeholder screens** — Tasks, Users, Analytics must fetch real data
