# EthioLoadAI

**AI-powered Freight Matching & Backhaul Optimization Platform**

## Overview

EthioLoadAI is a hybrid Laravel + FastAPI platform for smart logistics in Ethiopia's main freight corridor (Metema/Humera → Gondar → Bahir Dar → Addis Ababa).

- Shippers post cargo requests
- Truck owners/drivers register trucks and find loads
- AI recommends best trucks, predicts empty returns, and finds backhaul opportunities
- Commission-based booking with in-app payment deduction

## Architecture

The current Spring Boot backend is in `backend-spring-boot`. See [local run instructions](LOCAL_RUN.md), [Carrier Company Phase 1](backend-spring-boot/CARRIER_COMPANY_PHASE1.md), and [Phase 2 membership and invitations](backend-spring-boot/CARRIER_COMPANY_PHASE2.md). The historical Laravel overview below predates this backend.

- **Laravel 12**: Main business backend (API, Auth, Booking, Payments, Admin, etc.)
- **FastAPI**: AI/ML microservice (recommendation, backhaul, pricing, route optimization)
- **PostgreSQL + PostGIS**: Main database
- **Docker-ready**: For local and production deployment

## Folder Structure

- `/backend` — Laravel API backend
- `/ai_engine` — FastAPI AI/ML microservice
- `/docker` — Docker and deployment files
- `/docs` — Documentation

## Quick Start (Development)

### Prerequisites

- Docker & Docker Compose
- Node.js (for Laravel frontend assets)
- Python 3.10+
- PostgreSQL with PostGIS

### 1. Clone the Repo

```sh
git clone <repo-url>
cd EthioLoadAI
```

### 2. Environment Setup

- Create an ignored root `.env` from `.env.example` for the Compose stack; set local values and never commit it.
- Set DB and API keys

### 3. Docker Compose Up

```sh
docker compose --env-file .env -f docker/docker-compose.yml up --build
```

### 4. Laravel Backend

- From `/backend`, migrate a local disposable DB: `php artisan migrate`
- Seed only synthetic data in an explicitly approved disposable local database; do not use production or shared data.
- API docs: `/docs`

### 5. FastAPI AI Engine

- Run: `uvicorn main:app --reload`
- Docs: `http://localhost:8000/docs`

## API Endpoints

### Laravel

- `/api/register`
- `/api/login`
- `/api/vehicle/register`
- `/api/cargo/create`
- `/api/booking/create`
- `/api/vehicle/nearby`

### FastAPI

- `/ai/recommend-truck`
- `/ai/backhaul-opportunities`
- `/ai/predict-price`

## Production Deployment

- Use Docker Compose or Kubernetes
- Configure environment variables for production

## License

MIT


For React/Flutter testing against Spring Boot, see [TEAM_E2E_SETUP.md](TEAM_E2E_SETUP.md). The Compose stack above is the legacy Laravel + AI stack; it does not launch Spring Boot.
