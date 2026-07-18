@echo off
TITLE AgentCraft - Backend Server Runner
COLOR 0A
echo ===================================================
echo   Starting AgentCraft Spring Boot Backend Server
echo ===================================================
echo.

:: Step 1: Ensure Docker containers for Postgres ^& Redis are running
echo [1/3] Checking ^& Starting Docker Containers (Postgres ^& Redis)...
docker-compose up -d
if %ERRORLEVEL% NEQ 0 (
    echo [WARNING] Docker Compose failed to start or Docker is not running.
    echo Please make sure Docker Desktop is started and try again.
    echo.
    echo Attempting to continue anyway...
)
echo.

:: Step 2: Set Environment Variables
echo [2/3] Configuring Environment Variables...
set TELEGRAM_BOT_TOKEN=8841120098:AAFqvWqxNTRQPQ8J2jp4pZECAs0288YWxNk
set TELEGRAM_WEBHOOK_SECRET=c2c77d61b365ff349a60e0a54e9bc36d
set SPRING_PROFILES_ACTIVE=dev
echo     TELEGRAM_BOT_TOKEN  = [SET]
echo     TELEGRAM_WEBHOOK_SECRET = [SET]
echo     SPRING_PROFILES_ACTIVE  = dev
echo.

:: Step 3: Run Spring Boot Server
echo [3/3] Launching Spring Boot Application...
echo     Navigate to: http://localhost:8080
echo.
cd /d "%~dp0backend"
call .\mvnw.cmd spring-boot:run

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ===================================================
    echo [ERROR] Backend Server crashed or failed to start.
    echo Check the output above for details.
    echo ===================================================
)
pause
