#!/bin/bash
# PostgreSQL Database Setup Script
# This script creates the database if it doesn't exist

DB_HOST="localhost"
DB_PORT="5432"
DB_USER="korebdan"
DB_PASSWORD="Mar212227"
DB_NAME="freight"

# Create database if it doesn't exist
echo "Creating PostgreSQL database '$DB_NAME' if it doesn't exist..."

PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -tc "SELECT 1 FROM pg_database WHERE datname = '$DB_NAME'" | grep -q 1 || \
PGPASSWORD="$DB_PASSWORD" psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -c "CREATE DATABASE $DB_NAME;"

if [ $? -eq 0 ]; then
    echo "✓ Database '$DB_NAME' is ready"
else
    echo "✗ Failed to create database"
    exit 1
fi

echo "Running migrations with Drizzle Kit..."
pnpm --filter @workspace/db run push

if [ $? -eq 0 ]; then
    echo "✓ Migrations completed successfully"
else
    echo "✗ Migrations failed"
    exit 1
fi

echo ""
echo "Database setup complete! You can now start the application."
