# Database Setup Guide

This guide explains how to set up a fresh PostgreSQL database for the Spring Boot backend.

## Prerequisites

- PostgreSQL installed and running on port 5433
- Access to create databases

## Step 1: Create the Database

Connect to PostgreSQL and create a new database named `ethioloadai_spring`:

```bash
# Using psql command line
psql -h 127.0.0.1 -p 5433 -U postgres
```

Then run:
```sql
CREATE DATABASE ethioloadai_spring;
\q
```

Or use a single command:
```bash
psql -h 127.0.0.1 -p 5433 -U postgres -c "CREATE DATABASE ethioloadai_spring;"
```

## Step 2: Configure Environment Variables

Copy the example environment file and configure it:

```bash
# In the backend-spring-boot directory
cp .env.example .env
```

Edit `.env` and set the following variables:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/ethioloadai_spring
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=secret
```

**Important:** Replace `secret` with your actual PostgreSQL password.

Additional required variables:
```env
JWT_SECRET=your-32-character-or-longer-secret-here
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev
```

## Step 3: Run Flyway Migrations

Flyway migrations run automatically when the Spring Boot application starts. The application will:

1. Connect to the database using the configured datasource
2. Create the `flyway_schema_history` table to track migrations
3. Execute all pending migration scripts in order
4. Create the required tables

To run migrations manually without starting the full application, you can use the Flyway Maven plugin:

```bash
cd backend-spring-boot
./mvnw flyway:migrate
```

## Step 4: Verify Database Initialization

After the application starts (or after running migrations manually), verify the database was initialized:

```bash
psql -h 127.0.0.1 -p 5433 -U postgres -d ethioloadai_spring
```

Then run:

```sql
-- Check if flyway schema history table exists
\dt

-- Check if users table was created
\d users

-- Verify the migration was recorded
SELECT * FROM flyway_schema_history;

-- Exit
\q
```

Expected output:
- `flyway_schema_history` table should exist with one entry for V1__create_users_table
- `users` table should exist with all columns defined in the migration

## Current Migration

The project currently has one migration:

- **V1__create_users_table.sql**: Creates the users table with columns for user management (id, full_name, phone, email, password, role, location, coordinates, verification status, etc.)

## Troubleshooting

### Connection Refused
- Ensure PostgreSQL is running on port 5433
- Check firewall settings
- Verify the host (127.0.0.1 vs localhost)

### Authentication Failed
- Verify the username and password in `.env` match your PostgreSQL credentials
- Check that the user has permission to connect to the database

### Database Does Not Exist
- Ensure you created the database in Step 1
- Verify the database name matches exactly: `ethioloadai_spring`

### Migration Failures
- Check the application logs for specific error messages
- Ensure the database user has CREATE TABLE permissions
- If migrations fail, Flyway will record the failed attempt and require manual intervention to repair

## Security Notes

- Never commit `.env` to version control
- Use strong passwords in production
- Consider using a secrets manager in production environments
- Restrict database user permissions to only what's needed
