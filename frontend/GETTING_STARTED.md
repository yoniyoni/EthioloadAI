# 🚀 Getting Started - Freight Management Platform

This guide will help you set up and run the entire Freight Management Platform locally.

## Prerequisites

- **PostgreSQL 14+** - Download from [postgresql.org](https://www.postgresql.org/download/)
- **Node.js 22+** - Download from [nodejs.org](https://nodejs.org/)
- **pnpm** - Install globally with `npm install -g pnpm`
- **Git** - For version control

### PostgreSQL Setup (First Time Only)

1. **Install PostgreSQL:**
   - Windows: Use the official installer from postgresql.org
   - During installation, set the password to: `Mar212227`
   - Keep default port: `5432`
   - Remember the superuser password (default: `postgres`)

2. **Create Database User (Optional - if not created during install):**
   ```bash
   psql -U postgres
   # In psql console:
   CREATE USER korebdan WITH PASSWORD 'Mar212227';
   ALTER ROLE korebdan CREATEDB;
   \q
   ```

## Step 1: Install Dependencies

```bash
# From the workspace root
pnpm install
```

This will install all dependencies for all 9 packages in the monorepo.

## Step 2: Configure Environment

The `.env` file has already been created with your PostgreSQL credentials:
```
DATABASE_URL=postgresql://korebdan:Mar212227@localhost:5432/freight
PORT=5000
NODE_ENV=development
```

If you need to modify these values, edit the `.env` file in the root directory.

## Step 3: Create & Migrate Database

### Option A: Using PowerShell (Windows) 🪟

```powershell
# From the workspace root
.\scripts\setup-db.ps1
```

This script will:
- ✓ Create the `freight` database if it doesn't exist
- ✓ Run Drizzle migrations to create all tables
- ✓ Display migration results

### Option B: Using Bash (Mac/Linux) 🐧

```bash
# From the workspace root
bash scripts/setup-db.sh
```

### Option C: Manual Setup

If the scripts don't work, follow these steps:

1. **Create the database:**
   ```bash
   psql -U korebdan -h localhost -c "CREATE DATABASE freight;"
   ```

2. **Run migrations:**
   ```bash
   pnpm --filter @workspace/db run push
   ```

## Step 4: Test Database Connection

Before starting the API, verify the database is working:

```bash
pnpm --filter @workspace/db exec node test-connection.mjs
```

Expected output:
```
✓ Connected to PostgreSQL
✓ PostgreSQL Version: PostgreSQL 14.10...
✓ Server Time: 2024-01-15 10:30:45...
✓ Database Tables (12):
   - users
   - drivers
   - vehicles
   - freight
   - applications
   - ratings
   - tracking
   - ai_history
   - payments
   - disputes
   - messages
   - contracts
```

## Step 5: Build the Project

```bash
# From the workspace root
pnpm run build
```

This will:
- ✓ Type-check all TypeScript files
- ✓ Build the API server (esbuild → dist/index.mjs)
- ✓ Generate API client and Zod schemas
- ✓ Prepare frontend assets

## Step 6: Start the Backend API Server

```powershell
# Windows PowerShell - Set PORT and start dev server
$env:PORT = 5000
pnpm --filter @workspace/api-server run dev
```

Or in one command:
```bash
# Windows Command Prompt
set PORT=5000 && pnpm --filter @workspace/api-server run dev

# Mac/Linux Bash
PORT=5000 pnpm --filter @workspace/api-server run dev
```

Expected output:
```
> @workspace/api-server@1.0.0 dev
> pnpm run build && pnpm run start

(build logs...)

Listening on http://localhost:5000
```

### Verify Backend is Running

Open a new terminal and test the health endpoint:

```bash
curl http://localhost:5000/api/health/healthz
# Expected response: {"status":"ok"}

curl http://localhost:5000/api/health/db
# Expected response: {"status":"healthy","message":"Database connection successful",...}

curl http://localhost:5000/api/health/tables
# Expected response: {"status":"success","tableCount":12,"tables":["users","drivers",...],...}
```

## Step 7: Start the Frontend (Optional)

In a new terminal:

```bash
pnpm --filter @workspace/freight-link run dev
```

Expected output:
```
> @workspace/freight-link@1.0.0 dev
> vite --config vite.config.ts --host 0.0.0.0

VITE v7.3.3  ready in 234 ms

➜  Local:   http://localhost:5173/
➜  press h to show help
```

Then open http://localhost:5173/ in your browser.

**Note:** The frontend has a known issue with the `lightningcss` module. If you encounter an error, the backend API will still work at http://localhost:5000/api.

## Troubleshooting

### Database Connection Error
```
❌ Error: connect ECONNREFUSED 127.0.0.1:5432
```
**Solution:** PostgreSQL is not running. Start it:
- Windows: Services → PostgreSQL → Restart
- Mac: `brew services restart postgresql`
- Linux: `sudo systemctl restart postgresql`

### "Database does not exist" Error
```
FATAL: database "freight" does not exist
```
**Solution:** Create the database:
```bash
pnpm setup-db.ps1  # Windows
bash scripts/setup-db.sh  # Mac/Linux
```

### PORT Already in Use
```
Error: listen EADDRINUSE :::5000
```
**Solution:** Change the PORT or kill the existing process:
```bash
# Windows PowerShell
$env:PORT = 5001
pnpm --filter @workspace/api-server run dev

# Find process using port 5000:
netstat -ano | findstr :5000
# Kill it:
taskkill /PID <PID> /F
```

### Dependency Issues
```
npm ERR! code ERESOLVE
npm ERR! ERESOLVE unable to resolve dependency tree
```
**Solution:** Clean install:
```bash
pnpm install --no-frozen-lockfile
pnpm run build
```

### TypeScript Compilation Error
```
error TS2307: Cannot find module '@workspace/db'
```
**Solution:** Rebuild from scratch:
```bash
pnpm run build
pnpm --filter @workspace/api-server run dev
```

## Project Structure

```
├── artifacts/
│   ├── api-server/              # Express API backend
│   │   └── src/routes/          # API route handlers
│   ├── freight-link/            # React frontend SPA
│   │   └── src/components/      # React components
│   └── mockup-sandbox/          # UI mockup sandbox
├── lib/
│   ├── db/                      # Drizzle ORM & migrations
│   │   └── src/schema/          # Database table definitions
│   ├── api-client-react/        # React API client hooks
│   ├── api-spec/                # OpenAPI specifications
│   └── api-zod/                 # Zod schema validators
├── scripts/
│   ├── setup-db.ps1             # Database setup (Windows)
│   ├── setup-db.sh              # Database setup (Mac/Linux)
│   └── seed.ts                  # Database seeding script
└── .env                         # Environment configuration
```

## Environment Variables

Edit `.env` in the root directory:

```env
# Database
DATABASE_URL=postgresql://korebdan:Mar212227@localhost:5432/freight

# API Server
PORT=5000
NODE_ENV=development

# Optional: Add your configuration here
# LOG_LEVEL=debug
# FRONTEND_URL=http://localhost:5173
```

## API Endpoints

### Health Checks
- `GET /api/health/healthz` - Basic health check
- `GET /api/health/db` - Database connection status
- `GET /api/health/tables` - List all database tables

### Authentication
- `POST /api/auth/register` - Register new user
- `POST /api/auth/login` - User login
- `POST /api/auth/logout` - User logout

### Users
- `GET /api/users` - List users
- `GET /api/users/:id` - Get user by ID
- `PUT /api/users/:id` - Update user
- `DELETE /api/users/:id` - Delete user

### Freight (Shipments)
- `GET /api/freight` - List freight shipments
- `POST /api/freight` - Create new shipment
- `GET /api/freight/:id` - Get shipment details
- `PUT /api/freight/:id` - Update shipment
- `DELETE /api/freight/:id` - Delete shipment

### Drivers
- `GET /api/drivers` - List drivers
- `POST /api/drivers` - Register new driver
- `GET /api/drivers/:id` - Get driver profile

### Tracking
- `GET /api/tracking/:freightId` - Track shipment

(See OpenAPI spec in `lib/api-spec/openapi.yaml` for full API documentation)

## Development Workflow

### Adding a New Route

1. Create a new file in `artifacts/api-server/src/routes/`
2. Define your Express router:
   ```typescript
   import { Router } from "express";
   
   const router = Router();
   
   router.get("/", (req, res) => {
     res.json({ message: "Hello" });
   });
   
   export default router;
   ```

3. Import in `artifacts/api-server/src/app.ts`:
   ```typescript
   import myRoute from "./routes/my-route";
   app.use("/api/my-route", myRoute);
   ```

4. Restart the dev server (Ctrl+C, then run dev command again)

### Adding a Database Table

1. Create schema file in `lib/db/src/schema/my-table.ts`
2. Define Drizzle table:
   ```typescript
   import { pgTable, serial, text, timestamp } from "drizzle-orm/pg-core";
   
   export const myTable = pgTable("my_table", {
     id: serial("id").primaryKey(),
     name: text("name").notNull(),
     createdAt: timestamp("created_at").defaultNow(),
   });
   ```

3. Export from `lib/db/src/schema/index.ts`
4. Run migrations:
   ```bash
   pnpm --filter @workspace/db run push
   ```

### Seeding Database

```bash
pnpm --filter @workspace/scripts exec node src/seed.ts
```

## Next Steps

1. ✅ Install dependencies: `pnpm install`
2. ✅ Create database: `pnpm setup-db.ps1` or `bash scripts/setup-db.sh`
3. ✅ Test connection: `pnpm --filter @workspace/db exec node test-connection.mjs`
4. ✅ Start backend: `pnpm --filter @workspace/api-server run dev`
5. ✅ (Optional) Start frontend: `pnpm --filter @workspace/freight-link run dev`
6. 🔄 Explore API at http://localhost:5000/api
7. 🔄 View frontend at http://localhost:5173

## Getting Help

- Check the troubleshooting section above
- Review error messages carefully - they usually indicate what's wrong
- Check `.env` file is properly configured
- Ensure PostgreSQL is running
- Run `pnpm run build` to rebuild all packages

---

**Last Updated:** January 2024
**Project:** Freight Management Platform
**Status:** Ready for development 🎉
