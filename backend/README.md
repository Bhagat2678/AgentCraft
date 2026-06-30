# ContextCraft Backend - Developer Guide

This directory contains the Spring Boot 3 Java 17 backend for the **ContextCraft WhatsApp Business Portal**.

---

## 🛠️ Tech Stack & Architecture

- **Runtime:** Java 17 + Spring Boot 3
- **Database:** PostgreSQL 15 (durable store) + Flyway (database migrations)
- **Caching/FSM Store:** Redis 7 (primary Finite State Machine storage)
- **Background Jobs:** Quartz Scheduler (handles daily alerts/reminders & weekly analytics)
- **Security:** Spring Security + Stateless JWT
- **WhatsApp Integration:** Meta Cloud API with Webhook Controller (validates `X-Hub-Signature-256` HMAC payloads)

---

## 🚀 Quick-Start

### 1. Prerequisites
- Docker & Docker Compose
- JDK 17
- Maven 3.8+

### 2. Run Local Infrastructure
Start PostgreSQL and Redis containers:
```bash
docker-compose up -d
```

### 3. Configure Environment
Copy `.env.example` to `.env` or set up the values in `backend/src/main/resources/application.properties`.

Ensure the following properties are configured for local runtime:
- `spring.datasource.url`: `jdbc:postgresql://localhost:5432/contextcraft`
- `spring.datasource.username`: `admin`
- `spring.datasource.password`: `password`
- `spring.data.redis.host`: `localhost`
- `spring.data.redis.port`: `6379`
- `app.jwt.secret`: A base64 256-bit string (e.g. `Y2hhbmdlbWVjaGFuZ2VtZWNoYW5nZW1lY2hhbmdlbWVjaGFuZ2VtZWNoYW5nZW0=`)

### 4. Build & Run Application
```bash
cd backend
mvn spring-boot:run
```
On startup, Flyway will automatically run the schema and seeding scripts:
1. `V1__initial_schema.sql` (Creates businesses, users, tasks, audit tables, etc.)
2. `V2__seed_demo.sql` (Seeds Demo CEO, Manager, and Employee)

---

## 🧪 Webhook Integration & Testing

### Testing Local Webhook
To test webhook callbacks locally, use a tool like `ngrok` to expose port `8080` to the internet:
```bash
ngrok http 8080
```
Update your Meta Developer Dashboard Webhook subscription endpoint:
- **Callback URL:** `https://<your-ngrok-subdomain>.ngrok-free.app/api/v1/webhook`
- **Verify Token:** The value configured in `app.whatsapp.verify-token`

### Simulating Webhooks with curl
You can skip WhatsApp verification and send simulated WhatsApp messages locally:
```bash
curl -X POST http://localhost:8080/api/v1/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "object": "whatsapp_business_account",
    "entry": [{
      "id": "WABA_ID",
      "changes": [{
        "value": {
          "messaging_product": "whatsapp",
          "messages": [{
            "from": "15550000001",
            "id": "wamid.test_msg_123",
            "type": "text",
            "text": { "body": "Hi" }
          }]
        },
        "field": "messages"
      }]
    }]
  }'
```

---

## 📁 File Structure

- `/src/main/java/com/contextcraft/portal/`
  - `config/` - WhatsApp properties and Quartz beans configuration
  - `controller/` - REST Controllers for Businesses, Users, Tasks, Roles, Analytics, and Authentication
  - `dto/` - Validated request/response models
  - `entity/` - All JPA models mapping database tables
  - `exception/` - Centralized Global Exception handler
  - `fsm/` - Finite State Machine (FSM) engine, Context, and Redis/DB store drivers
  - `repository/` - JPA database access layers
  - `security/` - Stateless JWT authentication, UserDetailsService, and PermissionEvaluator
  - `service/` - Domain logic services (Business, User, Role, Task services)
  - `webhook/` - Controller parsing webhook payload and routing messages to FSM
  - `whatsapp/` - Client adapter wrapper for Meta API requests
