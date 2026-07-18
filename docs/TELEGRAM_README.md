# Telegram Business Portal — Complete Implementation Plan

> **Stack:** Java 17 + Spring Boot 3 · PostgreSQL 15 · Redis 7 · Telegram Bot API · React 18 (Mini App / Web App)
> **Build:** Maven · Flyway migrations · Docker Compose / Kubernetes · GitHub Actions CI/CD

---

## Table of Contents

1. [High-Level Architecture](#1-high-level-architecture)
2. [End-to-End Sequence Diagrams](#2-end-to-end-sequence-diagrams)
3. [Data Model — ER Diagram & SQL Schema](#3-data-model)
4. [Role & Permissions Matrix](#4-role--permissions-matrix)
5. [Backend API Specification (OpenAPI)](#5-backend-api-specification)
6. [Conversation Scripts](#6-conversation-scripts)
7. [Telegram Message Formats & Keyboards](#7-telegram-message-formats)
8. [Core Backend Scaffolding (Java / Spring Boot)](#8-core-backend-scaffolding)
9. [Telegram Chat Adapter](#9-telegram-chat-adapter)
10. [Frontend — Telegram Mini App (Web App)](#10-frontend)
11. [CI/CD & Deployment](#11-cicd--deployment)
12. [Security Checklist & GDPR](#12-security-checklist)

---

## 1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        EXTERNAL ACTORS                              │
│  CEO Telegram (@ceo)       Manager Telegram (@mgr)   Employee Telegram  │
└────────────┬────────────────────┬──────────────────────┬────────────┘
             │ Telegram App       │ Telegram App         │ Telegram App
             ▼                    ▼                      ▼
┌────────────────────────────────────────────────────────────────────┐
│                       Telegram Bot API                             │
│                  (https://api.telegram.org)                        │
└───────────────────────────────┬────────────────────────────────────┘
                                │ HTTPS Webhook POST /api/v1/telegram/webhook
                                ▼
┌────────────────────────────────────────────────────────────────────┐
│                     API GATEWAY / NGINX                            │
│         TLS termination · Rate limiting                            │
└───────────────────────────────┬────────────────────────────────────┘
                                │
          ┌─────────────────────┼──────────────────────┐
          ▼                     ▼                        ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────┐
│  Webhook Handler │  │  REST API Server │  │  Scheduler Service   │
│  (Spring Boot)   │  │  (Spring Boot)   │  │  (Spring Scheduling) │
│                  │  │                  │  │  reminders · reports │
│  - Parse Update  │  │  - Business CRUD │  │  - Quartz jobs       │
│  - Route to FSM  │  │  - Task CRUD     │  │                      │
│  - Publish Redis │  │                  │  │                      │
└────────┬─────────┘  └────────┬─────────┘  └──────────┬───────────┘
         │                     │                         │
         ▼                     ▼                         ▼
┌────────────────────────────────────────────────────────────────────┐
│                     Redis (Session + Queue)                        │
│   conversation_state:{chatId}  ·  task_events (pub/sub)            │
└───────────────────────────────┬────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────┐
│                     PostgreSQL 15 (Primary DB)                     │
│   businesses · users · roles · departments · tasks                 │
└───────────────────────────────┬────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility |
|-----------|---------------|
| **Webhook Handler** | Receives Telegram Updates, validates secret token, routes to Conversation FSM |
| **Conversation FSM** | Maintains per-chat state machine stored in Redis; drives setup wizard and task flows |
| **REST API** | Authenticated REST endpoints for Telegram Mini App and external integrations |
| **Scheduler** | Quartz-based reminder engine; generates analytics snapshots; sends notifications |
| **Chat Adapter** | Wraps Telegram Bot API; handles `sendMessage`, `sendPhoto`, and Inline Keyboards |
| **Telegram Mini App**| Minimal React SPA opened directly inside Telegram via Web App button |

---

## 2. End-to-End Sequence Diagrams

### 2.1 Portal Creation Flow

```
CEO Telegram               Telegram Bot API         Webhook Handler        FSM / DB
    │                             │                       │                    │
    │──/start────────────────────►│                       │                    │
    │                             │──webhook POST────────►│                    │
    │                             │                       │──resolve chatId───►│
    │                             │                       │◄─state=NEW─────────│
    │◄─"Welcome! Create portal?"──│                       │                    │
    │──[Yes] (Inline Button)─────►│                       │                    │
    │                             │──webhook POST────────►│                    │
    │                             │                       │──transition NEW→BNAME│
    │◄─"Enter business name:"─────│                       │                    │
    │──"Acme Corp"───────────────►│                       │                    │
    │                             │──webhook POST────────►│                    │
    │                             │                       │──create business──►│
    │◄─"Portal created! #A1B2"────│                       │                    │
```

---

## 3. Data Model

The data model is identical to the previous SQL Schema (PostgreSQL / Flyway), with minor modifications:
- `user_phones` is replaced or augmented with `telegram_users` (storing `chat_id` and `username`).
- Tasks, Businesses, Roles, and Departments remain the same.

```sql
CREATE TABLE telegram_users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    chat_id         BIGINT NOT NULL UNIQUE,
    username        VARCHAR(255),
    verified_at     TIMESTAMPTZ,
    invite_token    VARCHAR(64)
);
```

---

## 4. Role & Permissions Matrix

Same hierarchical access (CEO > Director > Manager > Lead > Employee).
Permissions evaluated via `@PreAuthorize("@permissionEvaluator.hasPermission(...)")`.

---

## 5. Backend API Specification

Key differences for Telegram:
- The `/webhook` endpoint is now `/api/v1/telegram/webhook`
- Requires a `X-Telegram-Bot-Api-Secret-Token` header to verify the webhook authenticity.

```yaml
paths:
  /telegram/webhook:
    post:
      summary: Receive Telegram Updates
      parameters:
        - in: header
          name: X-Telegram-Bot-Api-Secret-Token
          schema: {type: string}
```

---

## 6. Conversation Scripts & 7. Telegram Message Formats

Telegram provides robust interactive elements like `ReplyKeyboardMarkup` and `InlineKeyboardMarkup`.

### Example Inline Keyboard JSON Payload:
```json
{
  "chat_id": 123456789,
  "text": "Choose your business type:",
  "reply_markup": {
    "inline_keyboard": [
      [{"text": "Retail", "callback_data": "type_retail"}],
      [{"text": "Services", "callback_data": "type_services"}],
      [{"text": "Other", "callback_data": "type_other"}]
    ]
  }
}
```

---

## 8. Core Backend Scaffolding & 9. Telegram Chat Adapter

### 9.1 Telegram Client

```java
// TelegramClient.java
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramClient {

    @Value("${telegram.bot-token}") private String botToken;
    private final RestTemplate restTemplate;

    public void sendMessage(Long chatId, String text, ReplyMarkup replyMarkup) {
        String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        Map<String, Object> payload = new HashMap<>();
        payload.put("chat_id", chatId);
        payload.put("text", text);
        payload.put("parse_mode", "MarkdownV2");
        if (replyMarkup != null) {
            payload.put("reply_markup", replyMarkup);
        }
        
        restTemplate.postForEntity(url, payload, String.class);
    }
}
```

---

## 10. Frontend — Telegram Mini App (Web App)

Instead of a static popup link, Telegram allows embedding the React app directly as a Mini App.
In the bot, you send an inline button that opens the Web App:

```json
{
  "text": "Open Dashboard",
  "web_app": {
    "url": "https://portal.yourdomain.com/dashboard"
  }
}
```

Inside React:
```javascript
// Access Telegram Web App object
const tg = window.Telegram.WebApp;
tg.expand();
const user = tg.initDataUnsafe.user;
```

---

## 11. Local Deployment & Webhook Setup

Since this project will be run entirely locally, there are no cloud hosting costs or Kubernetes configurations needed.

### 11.1 Local Services (`docker-compose.yml`)
Run the following dependencies locally using Docker:
- **PostgreSQL**: For the main relational database.
- **Redis**: For state management, session caching, and rate limiting.
- **MinIO**: A local S3-compatible object storage for handling file attachments.

### 11.2 Exposing the Webhook via Ngrok
To allow the Telegram Bot API to send updates to your locally running Spring Boot application, you must expose your local port securely.

1. Start your Spring Boot application (default port `8080`).
2. Run Ngrok to create a secure tunnel:
   ```bash
   ngrok http 8080
   ```
3. Copy the secure `https` URL provided by Ngrok (e.g., `https://1a2b3c4d.ngrok.app`).
4. Register this URL as your bot's webhook:
   ```bash
   curl -X POST "https://api.telegram.org/bot<YOUR_TELEGRAM_BOT_TOKEN>/setWebhook" \
        -d "url=https://1a2b3c4d.ngrok.app/api/v1/telegram/webhook" \
        -d "secret_token=<YOUR_SECRET_TOKEN>"
   ```

### 11.3 Local Telegram Mini App
Your React frontend can be served locally (e.g., via Vite on port `5173`).
- You can run a second Ngrok tunnel to expose the frontend port.
- In your Telegram bot, configure the Mini App URL to point to this Ngrok frontend URL.

---

## 12. Security Checklist

- [ ] **Webhook Validation**: Validate `X-Telegram-Bot-Api-Secret-Token`.
- [ ] **Mini App Validation**: Validate `initData` provided by Telegram in the React frontend using HMAC-SHA256 of the bot token.
- [ ] **Rate Limiting**: Telegram has strict rate limits (`30 msgs/sec`, `20 msgs/min per group`). Implement retry with backoff.
