# AgentCraft — 100% Telegram-Native Business Operations Platform

> **AgentCraft** is an intelligent, automated business portal and task management platform built **100% natively on Telegram** (Telegram Bots & Telegram Mini Apps). Powered by Spring Boot 3, Redis session persistence, and Quartz scheduling, AgentCraft drives operational workflows across Retail, Service, and Software/Tech enterprises through interactive conversational flows and real-time dashboards.

---

## 🏗️ System Architecture

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   TELEGRAM CLIENT                                      │
│              Telegram Bot Chat Interface   │   Telegram Mini App (React Dashboard)    │
└─────────────────────────────────────┬──────────────────┬───────────────────────────────┘
                                      │ Webhook          │ REST (Bearer JWT)
                                      ▼                  ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 SPRING BOOT BACKEND                                    │
│                                                                                        │
│   ┌────────────────────────┐  ┌─────────────────────────┐  ┌────────────────────────┐   │
│   │ TelegramWebhookControl │  │ TelegramMiniAppControl  │  │  Quartz Scheduler Job  │   │
│   │ (Bot Message Handler)  │  │ (InitData HMAC -> JWT)  │  │  (Session & Reminders) │   │
│   └───────────┬────────────┘  └────────────┬────────────┘  └───────────┬────────────┘   │
│               │                            │                           │                │
│               ▼                            ▼                           ▼                │
│   ┌────────────────────────────────────────────────────────────────────────────────┐   │
│   │                    Conversation FSM Engine & RoleFlowRouter                    │   │
│   │   - Step History Stack (← Back)    - Role Tracks (CEO/Manager/Lead/Employee)   │   │
│   │   - Dynamic Inline Keyboards       - Multi-Business Context Switching (/switch)│   │
│   │   - End-of-Action Exit Loop        - Email & Proof Attachment Handlers         │   │
│   └────────────────────────────────────────┬───────────────────────────────────────┘   │
└────────────────────────────────────────────┼───────────────────────────────────────────┘
                                             │
                       ┌─────────────────────┴─────────────────────┐
                       ▼                                           ▼
┌──────────────────────────────────────────────┐ ┌──────────────────────────────────────┐
│            Redis (Session & Cache)           │ │        PostgreSQL 15 (Database)       │
│  - FSM State Stack (TTL 48h)                 │ │  - Businesses, Users, Roles, Depts   │
│  - Staging areas (Task, Invite, Email)       │ │  - Tasks, Assignments, Attachments   │
│  - Rate Limiting Counters                    │ │  - Telegram Users & Audit Log        │
└──────────────────────────────────────────────┘ └──────────────────────────────────────┘
```

---

## 🌟 Key Features

### 🤖 1. Blizbot Conversational FSM Engine
- **Universal Commands**: `/start`, `/menu`, `/switch`, `/help`, `/cancel`, `/back` work instantly from any state.
- **Step-by-Step Back Navigation**: Integrated `pushHistory()` and `popHistory()` stack allows users to step backward (`← Back`) through setup wizards without losing context.
- **Multi-Portal Support (`/switch`)**: Multi-business owners can seamlessly view and switch active business portals right within Telegram.
- **Universal End-of-Action Loop**: After every action, Blizbot asks *"Is there anything else you'd like to manage?"* and performs a pending-task audit before closing the session cleanly.
- **Idle Session Nudge**: Quartz-powered background job scans Redis for inactive mid-flow sessions every 30 minutes and sends gentle resumption prompts.

### 🎭 2. Business Domain & Role-Based Workflows
Supports **3 Business Types** (Retail, Service, Tech) across **4 Enterprise Roles** (CEO, Manager, Lead, Employee):

| Domain | CEO Track | Manager Track | Lead Track | Employee Track |
|---|---|---|---|---|
| **🛒 Retail** | Sales Summaries, Low-Stock Restock Alerts, Expense Breakdowns | Order Assignment, Priority Setting, Complaint Escalation | Batch Order Distribution, Team Progress Tracking | Order Packing/Shipping Cycles, Problem Reporting |
| **💼 Service** | Client Overview, High-Level Project Status | Project Allocation, QA Review & Amber Revisions | Project Tasking, Employee Reassignments | Progress Updates, Amber Revision Acknowledgments |
| **💻 Tech / Dev** | Sprint Overviews, Production Release Authorizations | Bug Triage, PR Code Reviews, Security Scans | Sprint Planning, Feature Assignment | Bug Status Updates, PR Link Submissions, Staging Deploys |

### ✉️ 3. Integrated Email & Attachment Services
- **Meeting Email Composer**: Managers can compose custom emails or let Blizbot auto-generate meeting announcements with date, time, and subject details.
- **Employee-to-Manager Email**: Direct email dispatch channel from employees to managers within Telegram.
- **Proof Attachments**: Workers upload photos and documents as task completion proof; files are tracked via `telegram_file_id` in Postgres.

### 📱 4. Telegram Mini App (React Dashboard) Integration
- **Secure initData Validation**: Authenticates Telegram WebApp launcher `initData` using HMAC-SHA256 signature verification.
- **JWT Issuance**: Issues short-lived JWTs to authorize the frontend React dashboard for REST endpoints (`/api/tma/auth`).

---

## 🛠️ Technology Stack

- **Backend Framework**: Java 17, Spring Boot 3.3.x, Spring Data JPA, Spring Security
- **Caching & Session Store**: Redis 7 (StringRedisTemplate with JSON serialization & PostgreSQL fallback)
- **Database**: PostgreSQL 15 with JSONB support & Flyway Migrations (V1 to V7)
- **Scheduling**: Quartz Scheduler (Daily reminders, weekly analytics snapshots, 30-min idle session nudges)
- **Messaging**: Telegram Bot API (Inline Keyboards, Callback Queries, Document/Photo uploads)
- **Frontend / Mini App**: React 18, Vite, Vanilla CSS design system
- **Containerization**: Docker & Docker Compose

---

## 📂 Project Structure

```
AgentCraft/
├── backend/
│   ├── src/main/java/com/contextcraft/portal/
│   │   ├── config/             # Spring & Quartz configurations
│   │   ├── controller/         # REST & Telegram Mini App API controllers
│   │   ├── dto/                # Request/Response Data Transfer Objects
│   │   ├── entity/             # JPA Entities (Business, User, Role, Task, etc.)
│   │   ├── fsm/                # Core FSM Engine (ConversationFsm, RoleFlowRouter, FsmContext)
│   │   ├── repository/         # Spring Data JPA Repositories
│   │   ├── scheduler/          # Quartz Jobs (TaskReminder, Analytics, SessionTimeout)
│   │   ├── security/           # JWT Utils & Security Config
│   │   ├── service/            # Business Logic Services
│   │   ├── telegram/           # Telegram Chat Adapter & Bot API client
│   │   └── webhook/            # Telegram Webhook Controller
│   └── src/main/resources/
│       ├── db/migration/       # Flyway SQL schema migrations (V1__... to V7__...)
│       └── application.properties
├── Frontend/                   # React Vite Telegram Mini App Dashboard
├── docs/                       # Specifications & Workflow Guides
└── docker-compose.yml          # Container orchestration for Postgres, Redis, Backend & Frontend
```

---

## 🚀 Quick-Start Guide

### Prerequisites
- JDK 17+
- Docker & Docker Compose
- A Telegram Bot Token from [@BotFather](https://t.me/BotFather)

### 1. Environment Setup
Copy `.env.example` to `.env` and fill in your credentials:

```ini
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/contextcraft
SPRING_DATASOURCE_USERNAME=admin
SPRING_DATASOURCE_PASSWORD=password
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379

TELEGRAM_BOT_TOKEN=123456789:ABCdefGHIjklMNOpqrsTUVwxyz
TELEGRAM_BOT_USERNAME=Blizbot
TELEGRAM_WEBHOOK_SECRET=your_secret_token
JWT_SECRET=d3VzdXBhcnNlY3VyZWtleTF5b3VyY3VzdG9tand0c2VjcmV0a2V5aGVyZQ==
```

### 2. Run with Docker Compose
To launch Postgres, Redis, Backend, and Frontend simultaneously:

```bash
docker-compose up --build
```

### 3. Run Backend Locally (Maven)
If running local database and Redis services:

```bash
cd backend
./mvnw clean spring-boot:run
```

Verify backend health and compilation:
```bash
./mvnw test-compile
```

---

## 🗄️ Database Migrations (Flyway)

- `V1__initial_schema.sql`: Core schema (businesses, users, roles, departments, tasks, attachments).
- `V2__seed_demo.sql`: Initial default roles and seed records.
- `V3__Add_updated_at_to_message_templates.sql`: Template timestamps.
- `V4__add_telegram_tables.sql`: Telegram users & chat bindings.
- `V5__add_telegram_file_id_to_attachments.sql`: Attachment storage standardizations.
- `V6__remove_whatsapp_fields.sql`: Purged legacy WhatsApp columns and normalized schemas.
- `V7__phase2_portal_login.sql`: Portal login password & username schema additions.

---

## 📜 License & Acknowledgments

Built by the **ContextCraft / AgentCraft Team**. Designed for scalable, role-aware operational execution on Telegram.
