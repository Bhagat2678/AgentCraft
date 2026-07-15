# AgentCraft: Server Startup & URL Configuration Guide

This guide details how to start the backend, frontend, and secure tunnels, as well as where to update the tunnel URLs in the codebase when they change.

---

## 🚀 1. How to Start the Servers

### Step 1: Start Database & Redis (Docker)
Ensure your Docker containers for Postgres and Redis are running:
```powershell
docker-compose up -d
```
*   **Postgres Port:** `5433`
*   **Redis Port:** `6379`

### Step 2: Start the Backend (Spring Boot)
Open a new terminal window, load your Telegram Bot variables, and run Maven:
```powershell
# Set Environment Variables
$env:TELEGRAM_BOT_TOKEN="8841120098:AAFqvWqxNTRQPQ8J2jp4pZECAs0288YWxNk"
$env:TELEGRAM_WEBHOOK_SECRET="c2c77d61b365ff349a60e0a54e9bc36d"

# Start Server
cd backend
.\mvnw.cmd spring-boot:run
```
*   **Local Port:** `8080`

### Step 3: Start the Frontend (Vite + React)
Open a new terminal window, navigate to the Frontend directory, and start the development server:
```powershell
cd Frontend
npm run dev
```
*   **Local Port:** `5173` (accessible on `0.0.0.0` inside your network)

### Step 4: Establish the Tunnels (Serveo)
Open a new terminal window to start the reverse tunnels:
```powershell
# Backend Tunnel
ssh -o StrictHostKeyChecking=no -R 80:127.0.0.1:8080 serveo.net

# Frontend Tunnel
ssh -o StrictHostKeyChecking=no -R 80:127.0.0.1:5173 serveo.net
```
*Always target `127.0.0.1` explicitly (instead of `localhost`) to prevent Windows IPv6 loopback resolving issues.*

---

## 🛠️ 2. Where to Update the Code with New Tunnel URLs

Whenever you restart the tunnels, Serveo will generate new public URLs. You must update these two places:

### 1. The React Proxy (Vite Config)
Update the target URL in [vite.config.js](file:///c:/Users/bhaga/FLowZint/AgentCraft/Frontend/vite.config.js) under the `proxy` property to match the **new Backend Tunnel URL**:
```javascript
  server: {
    host: '0.0.0.0',
    port: 5173,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'https://<NEW_BACKEND_TUNNEL_URL>.serveousercontent.com', // ◀️ UPDATE THIS
        changeOrigin: true,
        secure: false,
      }
    }
  }
```

### 2. Register the Telegram Webhook
Run the following PowerShell command in your terminal using the **new Backend Tunnel URL** and the webhook secret token:
```powershell
Invoke-RestMethod -Uri "https://api.telegram.org/bot8841120098:AAFqvWqxNTRQPQ8J2jp4pZECAs0288YWxNk/setWebhook?url=https://<NEW_BACKEND_TUNNEL_URL>.serveousercontent.com/api/v1/telegram/webhook&secret_token=c2c77d61b365ff349a60e0a54e9bc36d"
```
You can verify the webhook registration status at any time by running:
```powershell
Invoke-RestMethod "https://api.telegram.org/bot8841120098:AAFqvWqxNTRQPQ8J2jp4pZECAs0288YWxNk/getWebhookInfo"
```
