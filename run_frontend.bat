@echo off
TITLE AgentCraft - Frontend Server Runner
COLOR 0B
echo ===================================================
echo   Starting AgentCraft React Frontend (Vite)
echo ===================================================
echo.

cd /d "%~dp0Frontend"

:: Step 1: Install node_modules if missing
if not exist "node_modules\" (
    echo [1/2] Installing npm dependencies...
    call npm install
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] npm install failed. Make sure Node.js is installed.
        pause
        exit /b 1
    )
    echo.
) else (
    echo [1/2] node_modules already present - skipping install.
    echo.
)

:: Step 2: Launch Vite Development Server
echo [2/2] Starting Vite Dev Server...
echo     Navigate to: http://localhost:5173
echo.
call npm run dev

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ===================================================
    echo [ERROR] Frontend Dev Server failed to start.
    echo Check the output above for details.
    echo ===================================================
)
pause
