# 🛠️ AgentCraft
### *100% Telegram-Native Business Operations & Portal Management Platform*

[![Build Status](https://img.shields.io/badge/Build-Success-16a34a?style=for-the-badge&logo=github)](https://github.com)
[![Platform](https://img.shields.io/badge/Platform-Telegram_Bot_&_TMA-0088cc?style=for-the-badge&logo=telegram)](https://telegram.org)
[![Backend](https://img.shields.io/badge/Backend-Spring_Boot_3.3-6db33f?style=for-the-badge&logo=springboot)](https://spring.io)
[![Frontend](https://img.shields.io/badge/Frontend-React_18_&_Vite-61dafb?style=for-the-badge&logo=react)](https://react.dev)

**AgentCraft** (powered by **FlowZint**) is an automated business management portal and task management engine built **100% natively on Telegram** (Conversational Bots and Telegram Mini Apps). It enables small-to-medium enterprises (Retail, Service, Tech) to run complex business flows directly within Telegram using standard conversational adapters and real-time dashboard visualization.

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

## 🌟 Key Functional Tracks

### 🤖 1. Conversational FSM Engine
- **Universal Flow Navigation**: Support for `/start`, `/menu`, `/switch`, `/help`, `/cancel`, and stateful `← Back` buttons to traverse setup wizards without losing inputs.
- **Multi-Portal Support**: Allows business owners or employees to switch operational business contexts on-the-fly (`/switch`).
- **Nudge Schedules**: Background Quartz jobs inspect Redis for idle multi-step session states and send automated reminder warnings every 30 minutes.

### 🎭 2. Enterprise Domain Tracks (CEO / Manager / Lead / Employee)
- **🛒 Retail**: Sales summaries, restock requests, invoice matching, batch packaging, and courier dispatch logs.
- **💼 Service**: Client rosters, project allocations, QA review processes, and revision loops.
- **💻 Tech / Dev**: Sprint planning, feature triaging, PR submission references, and release validation checklists.

### 📱 3. React Portal Dashboard (Telegram Mini App)
- **Stat Cards & KPIs**: Real-time analytical snapshots of business health, completion ratios, active staff, and department scoring.
- **Task Workspace**: Filter, search, create, delegate, reassign, approve, or reject tasks. Timeline timeline history views display detailed change histories.
- **Team Directory**: Invite new workers via telephone invitations, assign roles dynamically on each user, or suspend/activate access.
- **Depts & Roles Manager**: Custom role generation interface enabling direct permission checkbox configurations.
- **Profile & Bot Control**: Dedicated settings panel showing copyable Telegram bot handles, webhook active indicators, connection urls, and manual sync commands.

---

## 📂 Project Organization

```
AgentCraft/
├── backend/                    # Spring Boot 3.3 Application
│   ├── src/main/java/          # Java Sources (fsm, entity, controller, service, security)
│   ├── src/main/resources/     # Schema migrations (Flyway V1..V7) & app properties
│   └── pom.xml                 # Maven dependency descriptor
├── Frontend/                   # React Vite SPA Dashboard
│   ├── src/                    # Components, Screens, Styles & main.jsx Portal
│   ├── vite.config.js          # Build & Reverse Proxy mappings
│   └── package.json            # Node JS packages
├── docs/                       # Specifications & Developer Guides
│   ├── PHASE_2_FRONTEND_WIRING.md
│   ├── PHASE_3_BACKEND_AUDIT.md
│   ├── ai_chatbot_flow_prompt.md
│   ├── correction_prompt.md
│   ├── TELEGRAM_README.md
│   └── RUN_SERVERS.md
├── .env.example                # Config template file
├── docker-compose.yml          # Container stack orchestration (Db, Redis)
├── run_backend.bat             # Quick startup script (backend)
└── run_frontend.bat            # Quick startup script (frontend)
```

---

## 🚀 Setup & Execution Guide

### 1. Start Services Stack (Database & Redis)
Ensure Docker is active, then initialize PostgreSQL 15 and Redis:
```bash
docker-compose up -d
```
*   **Postgres Interface:** `localhost:5433`
*   **Redis Interface:** `localhost:6379`

### 2. Configure Local Environment Variables
Create a file named `.env` in the root folder using `.env.example` as a template:
```ini
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/contextcraft
SPRING_DATASOURCE_USERNAME=admin
SPRING_DATASOURCE_PASSWORD=password

TELEGRAM_BOT_TOKEN=YOUR_BOT_TOKEN_FROM_BOTFATHER
TELEGRAM_WEBHOOK_SECRET=YOUR_SECURE_SECRET_TOKEN
```

### 3. Launch Spring Boot Backend
Configure environment variables and start the server:
```powershell
# Set Environment Variables
$env:TELEGRAM_BOT_TOKEN="YOUR_BOT_TOKEN"
$env:TELEGRAM_WEBHOOK_SECRET="YOUR_SECRET"

# Start Server
cd backend
.\mvnw.cmd spring-boot:run
```
*The REST API will host locally at `http://localhost:8080`.*

### 4. Launch React Frontend
Navigate to the Frontend directory, install dependencies, and run the development bundle:
```bash
cd Frontend
npm install
npm run dev
```
*The frontend dashboard will be available at `http://localhost:5173`.*

### 5. Establish Reverse Tunneling (Serveo / Ngrok)
To allow Telegram webhook calls and local React TMA resource delivery, expose your local ports to the internet:
```bash
# Expose Backend (Port 8080)
ssh -o StrictHostKeyChecking=no -R 80:127.0.0.1:8080 serveo.net

# Expose Frontend (Port 5173)
ssh -o StrictHostKeyChecking=no -R 80:127.0.0.1:5173 serveo.net
```

### 6. Register Telegram Bot Webhook
Register the public endpoint with Telegram API using your new **Backend Tunnel URL**:
```powershell
Invoke-RestMethod -Uri "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/setWebhook?url=https://<YOUR_BACKEND_TUNNEL_URL>/api/v1/telegram/webhook&secret_token=<YOUR_SECRET>"
```

Verify the current configuration at any time:
```powershell
Invoke-RestMethod "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/getWebhookInfo"
```

---

## 🔒 Security Hardening

- **Stateless Authentication**: Frontend requests authenticate via Bearer JWTs generated securely using HMAC-SHA256 signatures.
- **HMAC validation**: Launcher verification (`initData`) ensures safe requests right from Telegram Mini Apps.
- **BCrypt Encryption**: Portal login credentials set during FSM onboarding are encrypted with BCrypt hashing before database persistence.

---

## 🛠️ Validation Mappings
To ensure stability, you can execute standard maven testing and compilation validation checks:
```bash
# Run Maven Compilation
cd backend
.\mvnw.cmd compile

# Run Suite Unit Testing
.\mvnw.cmd test
```
