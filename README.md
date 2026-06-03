# EthioLoadAI

**AI-powered Freight Matching & Backhaul Optimization Platform**

## Overview

EthioLoadAI is a hybrid Laravel + FastAPI platform for smart logistics in Ethiopia's main freight corridor (Metema/Humera → Gondar → Bahir Dar → Addis Ababa).

- Shippers post cargo requests
- Truck owners/drivers register trucks and find loads
- AI recommends best trucks, predicts empty returns, and finds backhaul opportunities
- Commission-based booking with in-app payment deduction

## Architecture

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

- Copy `.env.example` to `.env` in both `/backend` and `/ai_engine` as needed
- Set DB and API keys

### 3. Docker Compose Up

```sh
docker-compose up --build
```

### 4. Laravel Backend

- Migrate DB: `php artisan migrate`
- Seed demo data: `php artisan db:seed`
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
