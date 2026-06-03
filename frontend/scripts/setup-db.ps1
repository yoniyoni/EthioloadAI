# PostgreSQL Database Setup Script (Windows PowerShell)
# This script creates the database if it doesn't exist

$DB_HOST = "localhost"
$DB_PORT = "5432"
$DB_USER = "korebdan"
$DB_PASSWORD = "Mar212227"
$DB_NAME = "freight"

Write-Host "Creating PostgreSQL database '$DB_NAME' if it doesn't exist..." -ForegroundColor Cyan

# Check if database exists
$env:PGPASSWORD = $DB_PASSWORD
$output = & psql -h $DB_HOST -p $DB_PORT -U $DB_USER -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" 2>$null

if ($output -notlike "*1*") {
    Write-Host "Database does not exist. Creating..." -ForegroundColor Yellow
    & psql -h $DB_HOST -p $DB_PORT -U $DB_USER -c "CREATE DATABASE $DB_NAME;" 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Database '$DB_NAME' created successfully" -ForegroundColor Green
    } else {
        Write-Host "✗ Failed to create database" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "✓ Database '$DB_NAME' already exists" -ForegroundColor Green
}

# Clear the password from environment
$env:PGPASSWORD = ""

Write-Host ""
Write-Host "Running migrations with Drizzle Kit..." -ForegroundColor Cyan
pnpm --filter @workspace/db run push

if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Migrations completed successfully" -ForegroundColor Green
} else {
    Write-Host "✗ Migrations failed" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✓ Database setup complete! You can now start the application." -ForegroundColor Green
