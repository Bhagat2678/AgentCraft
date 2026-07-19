# 🛠️ AgentCraft
### *100% Telegram-Native AI Business Operations & Management Platform*

[![Build Status](https://img.shields.io/badge/Build-Success-16a34a?style=for-the-badge&logo=github)](https://github.com/Bhagat2678/AgentCraft)
[![Platform](https://img.shields.io/badge/Platform-Telegram_Bot_&_TMA-0088cc?style=for-the-badge&logo=telegram)](https://telegram.org)
[![AI Engine](https://img.shields.io/badge/AI_Engine-Gemini_2.0_Flash-4285f4?style=for-the-badge&logo=google)](https://ai.google.dev)
[![Backend](https://img.shields.io/badge/Backend-Spring_Boot_3.4-6db33f?style=for-the-badge&logo=springboot)](https://spring.io)
[![Frontend](https://img.shields.io/badge/Frontend-React_18_&_Vite-61dafb?style=for-the-badge&logo=react)](https://react.dev)

**AgentCraft** (by **FlowZint**) is a next-generation AI business portal and operational task engine built **100% natively on Telegram** (Conversational AI Bots & Telegram Mini Apps). It enables small-to-medium enterprises (Retail, Service, Tech) to run complex business flows, create departments, delegate tasks, and monitor real-time KPIs directly inside Telegram using natural language AI or an interactive web dashboard.

---

- **🤖 Telegram Assistant Bot**: [@Agentcraft_B_Bot](https://t.me/Agentcraft_B_Bot)
- **🐳 Cloud & Container Ready**: Includes root `docker-compose.yml` and `backend/Dockerfile` for 1-click cloud hosting (Render, Railway, AWS, DigitalOcean).

---

## 🏗️ System Architecture

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                   TELEGRAM CLIENT                                      │
│         Telegram Bot Chat (@Agentcraft_B_Bot)   │   Telegram Mini App (React TMA)      │
└─────────────────────────────────────┬──────────────────┬───────────────────────────────┘
                                      │ Webhook          │ REST (Bearer JWT / CORS)
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
│   │   - End-of-Action Exit Loop        - Gemini NLU Intent & Entity Resolver       │   │
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

## 🌟 Key Functional Tracks

### 🤖 1. Hybrid AI + FSM Conversational Engine
- **Gemini 2.0 Flash NLU**: Parses natural text like *"Create an urgent task for Rahul to update inventory by Friday"* into structured intent & entity payloads.
- **Transactional State Machine**: Ensures database integrity and business relationship constraints (e.g., automatically binding newly created departments to parent businesses).
- **Universal Navigation**: Full support for `/start`, `/menu`, `/switch`, `/help`, `/cancel`, and stateful `← Back` buttons.
- **Nudge Schedules**: Background Quartz jobs inspect idle sessions in Redis and send automated reminder warnings.

### 🎭 2. Enterprise Domain Support
- **🛒 Retail**: Sales summaries, restock requests, invoice matching, batch packaging, and courier dispatch logs.
- **💼 Service**: Client rosters, project allocations, QA review processes, and revision loops.
- **💻 Tech / Dev**: Sprint planning, feature triaging, PR submission references, and release validation checklists.

### 📱 3. React Portal Dashboard (Telegram Mini App)
- **KPI Metrics & Stat Cards**: Analytical snapshots of business health, task completion ratios, staff counts, and department performance.
- **Task Workspace**: Filter, search, create, delegate, reassign, approve, or reject tasks with complete audit logs.
- **Team Directory**: Invite staff via telephone numbers, assign granular roles (CEO, Manager, Lead, Employee), or toggle access status.
- **Depts & Roles Manager**: Custom role generation interface enabling direct permission checkbox configurations.

---

## 📂 Project Organization

```
AgentCraft/
├── backend/                    # Spring Boot 3.4 Application
│   ├── src/main/java/          # Controllers, Services, Entities, Security & FSM Engine
│   ├── src/main/resources/     # Flyway Migrations (V1..V7) & app properties
│   └── pom.xml                 # Maven configuration
├── Frontend/                   # React 18 + Vite Telegram Mini App
│   ├── src/                    # UI Components, Screens, Design Tokens & main.jsx
│   ├── vite.config.js          # Build configuration & dev proxy
│   └── package.json            # Node.js dependencies
├── docs/                       # Architectural specifications & guides
├── .env.example                # Environment variable configuration template
├── docker-compose.yml          # Container stack (Postgres 15 + Redis)
├── run_backend.bat             # One-click backend startup script
└── run_frontend.bat            # One-click frontend startup script
```

---

## 🚀 Setup & Execution Guide

### 1. Start Database & Redis Stack
Ensure Docker Desktop is running, then launch PostgreSQL and Redis:
```bash
docker-compose up -d
```
- **Postgres Interface:** `localhost:5433` (Database: `contextcraft`)
- **Redis Interface:** `localhost:6379`

### 2. Configure Local Environment Variables
Create a `.env` file in the root directory based on `.env.example`:
```ini
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/contextcraft
SPRING_DATASOURCE_USERNAME=admin
SPRING_DATASOURCE_PASSWORD=password

TELEGRAM_BOT_TOKEN=8841120098:AAFqvWqxNTRQPQ8J2jp4pZECAs0288YWxNk
TELEGRAM_WEBHOOK_SECRET=c2c77d61b365ff349a60e0a54e9bc36d
TELEGRAM_PUBLIC_WEBHOOK_URL=https://squishy-linseed-prewashed.ngrok-free.dev/api/v1/telegram/webhook

GEMINI_API_KEY=YOUR_GEMINI_API_KEY
```

### 3. Launch Spring Boot Backend
```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
*Backend API runs locally at `http://localhost:8080`.*

### 4. Launch React Frontend
```bash
cd Frontend
npm install
npm run dev
```
*Frontend runs locally at `http://localhost:5173`.*

### 5. Reverse Tunneling (Ngrok)
Expose the backend port to receive Telegram webhooks:
```bash
ngrok http 8080 --url https://squishy-linseed-prewashed.ngrok-free.dev
```

---

## 🔒 Security Hardening

- **HMAC SHA-256 WebApp Validation**: Verifies Telegram `initData` signatures before issuing JWT tokens.
- **JWT Authentication**: Full Bearer token security with CORS preflight support (`Access-Control-Allow-Headers`).
- **BCrypt Encryption**: Passwords created during conversational onboarding are encrypted using strong BCrypt hashing.
