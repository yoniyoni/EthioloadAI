# Start all EthioLoadAI project services
$ErrorActionPreference = 'Stop'
$rootDir = $PSScriptRoot

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "   Starting EthioLoadAI Full Stack Platform   " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# 1. Check PostgreSQL
$pgCheck = Get-NetTCPConnection -State Listen -LocalPort 5432 -ErrorAction SilentlyContinue
if (-not $pgCheck) {
    Write-Warning "PostgreSQL is not detected on port 5432. Please ensure PostgreSQL service is running."
} else {
    Write-Host "[OK] PostgreSQL is active on port 5432." -ForegroundColor Green
}

# 2. AI Engine
$aiPort = Get-NetTCPConnection -State Listen -LocalPort 8000 -ErrorAction SilentlyContinue
if ($aiPort) {
    Write-Host "[OK] AI Engine is already running on port 8000." -ForegroundColor Green
} else {
    Write-Host "Starting AI Engine on port 8000..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "& 'C:\Program Files\PostgreSQL\18\pgAdmin 4\python\python.exe' '$rootDir\ai_engine\run_local.py'" -WorkingDirectory $rootDir
}

# 3. Spring Boot Backend
$backendPort = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
if ($backendPort) {
    Write-Host "[OK] Spring Boot Backend is already running on port 8080." -ForegroundColor Green
} else {
    Write-Host "Starting Spring Boot Backend on port 8080..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoExit", "-File", "$rootDir\backend-spring-boot\start-local.ps1", "-UseJar" -WorkingDirectory "$rootDir\backend-spring-boot"
}

# 4. Web App (freight-link Vite)
$webPort = Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -in 5173, 5174 }
if ($webPort) {
    Write-Host "[OK] Web App is already running on port $($webPort.LocalPort[0])." -ForegroundColor Green
} else {
    Write-Host "Starting Freight-Link Web App..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "pnpm.cmd --filter @workspace/freight-link dev" -WorkingDirectory "$rootDir\frontend"
}

# 5. Mobile App Preview
$mobilePort = Get-NetTCPConnection -State Listen -LocalPort 8765 -ErrorAction SilentlyContinue
if ($mobilePort) {
    Write-Host "[OK] Mobile Preview is already running on port 8765." -ForegroundColor Green
} else {
    Write-Host "Starting Mobile Preview on port 8765..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "-NoExit", "-File", "$rootDir\frontend\artifacts\mobile-app\start-preview.ps1" -WorkingDirectory "$rootDir"
}

Write-Host "`nAll services have been launched!" -ForegroundColor Green
Write-Host "---------------------------------------------"
Write-Host "Web App:                http://localhost:5174/ (or http://localhost:5173/)"
Write-Host "Mobile Browser Preview: http://127.0.0.1:8765/"
Write-Host "Backend Health:         http://127.0.0.1:8080/api/actuator/health"
Write-Host "AI Engine Docs:         http://127.0.0.1:8000/docs"
Write-Host "---------------------------------------------"
