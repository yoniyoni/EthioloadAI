# EthioLoadAI — Project Analysis

## 1. What This Project Is

**EthioLoadAI** is an AI-powered freight matching and backhaul optimization platform built specifically for Ethiopia. The core business problem it solves: Ethiopian trucks often return empty after delivering cargo (especially on the Metema/Humera → Gondar → Bahir Dar → Addis Ababa corridor), which is economically wasteful. The platform matches shippers who need to move cargo with truck drivers and fleet owners who have available capacity, while an AI engine predicts fair prices, recommends the best trucks, and finds backhaul (return-load) opportunities so trucks don't make empty trips.

**Business model:** Shippers post cargo requests → truck drivers bid or directly accept → the platform takes a commission (10% standard, 5% for fleet bookings) → payment is settled in-app or manually via Ethiopian payment gateways.

---

## 2. Tech Stack

### Backend (Active — Spring Boot)
| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.0 |
| Security | Spring Security (stateless JWT) + BCrypt |
| ORM | Spring Data JPA + Hibernate |
| Database migrations | Flyway (60+ versioned migrations) |
| Database | PostgreSQL 15 + PostGIS 3.3 |
| Connection pooling | HikariCP |
| JWT | JJWT 0.12.3 |
| DTO mapping | MapStruct 1.5.5 |
| Boilerplate reduction | Lombok |
| API docs | SpringDoc OpenAPI 2.3.0 (Swagger UI at `/api/swagger-ui.html`) |
| Real-time | Spring WebSocket |
| Observability | Micrometer + Prometheus |
| File storage | AWS S3 SDK |
| Email | Spring Mail via Brevo (SMTP relay) |

### AI/ML Microservice
| Layer | Technology |
|-------|-----------|
| Language | Python 3.10+ |
| Framework | FastAPI |
| Server | Uvicorn |
| ML libraries | XGBoost, scikit-learn (referenced; current logic is rule-based) |
| Data processing | NumPy, Pandas, GeoPandas |
| DB access | psycopg2-binary (direct PostgreSQL for live fleet injection) |
| Config | python-dotenv |

### Web Frontend (Admin Panel — `freight-link`)
| Layer | Technology |
|-------|-----------|
| Language | TypeScript |
| Framework | React 18 |
| Build tool | Vite |
| Styling | TailwindCSS + Radix UI |
| Data fetching | TanStack React Query |
| Routing | Wouter |
| Forms | React Hook Form + Zod |
| Charts | Recharts |
| Animations | Framer Motion |

### Mobile App
| Layer | Technology |
|-------|-----------|
| Language | Dart |
| Framework | Flutter (SDK ≥3.0) |
| State management | flutter_riverpod 2.4.0 |
| Navigation | go_router 13.2.0 |
| HTTP client | Dio 5.3.0 |
| Real-time | web_socket_channel 3.0.1 |
| Maps | flutter_map 7.0.2 + OpenStreetMap (no API key needed) |
| GPS | geolocator 10.1.0 |
| Token storage | flutter_secure_storage |
| Localization | easy_localization (English + Amharic) |

### Infrastructure
| Component | Technology |
|-----------|-----------|
| Containerization | Docker Compose |
| Services | PostgreSQL/PostGIS (port 5432), Spring Boot (port 8001), FastAPI (port 8000) |
| Routing | OSRM (`router.project-osrm.org`) |
| Geocoding | Nominatim / OpenStreetMap |
| Payments | Chapa, TeleBirr, CBE Birr (Ethiopian payment gateways) |

> **Note on tech evolution:** The project started on Laravel 12. `composer.json`, `composer.lock`, and the `vendor/` directory at the root are legacy artifacts. All active backend development is in `backend-spring-boot/`. A `MobileAuthCompatibilityController` maintains backward compatibility for older API paths (`/register`, `/login`, `/logout`).

---

## 3. Project Structure

```
ethiolodeee-main/
├── .env.example                      ← Environment variable template
├── README.md
├── composer.json / composer.lock     ← Legacy Laravel files (inactive)
├── vendor/                           ← Legacy Laravel vendor (inactive)
│
├── backend-spring-boot/              ← Spring Boot REST API (primary backend)
│   └── src/main/
│       ├── java/com/ethioloadai/
│       │   ├── EthioloadAiApplication.java
│       │   ├── auth/                 ← Registration, login, logout, profile management
│       │   ├── security/             ← JWT filter, token revocation, rate limiting
│       │   ├── config/               ← SecurityConfig, JwtConfig, CORS, OpenAPI
│       │   ├── user/                 ← User entity and CRUD
│       │   ├── vehicle/              ← Vehicle registration and admin approval workflow
│       │   ├── freight/              ← Cargo request lifecycle (core domain)
│       │   ├── bid/                  ← Driver bidding on freight requests
│       │   ├── booking/              ← Booking creation from accepted bids
│       │   ├── trip/                 ← Trip lifecycle and multi-stop management
│       │   ├── tracking/             ← Location updates and history
│       │   ├── payment/              ← Payment processing and commission calculation
│       │   ├── rating/               ← Post-trip ratings (shippers ↔ drivers)
│       │   ├── driver/               ← Driver-specific operations
│       │   ├── document/             ← KYC document uploads (stored in S3)
│       │   ├── fleet/                ← Fleet owner management
│       │   ├── carrier/              ← Carrier companies + team memberships
│       │   ├── contract/             ← Contract management
│       │   ├── dispute/              ← Dispute resolution
│       │   ├── message/              ← In-app messaging
│       │   ├── notification/         ← Push/in-app notifications
│       │   ├── email/                ← Email service (Brevo SMTP)
│       │   ├── websocket/            ← Real-time location broadcasting
│       │   ├── ai/                   ← HTTP client proxy to AI engine
│       │   ├── recommendation/       ← AI recommendation persistence
│       │   ├── analytics/            ← Business analytics
│       │   ├── routing/              ← OSRM/Nominatim integration
│       │   ├── exception/            ← Global exception handling
│       │   └── setting/              ← Admin-configurable platform settings
│       └── resources/
│           ├── application.yml       ← Master configuration
│           ├── application-dev.yml   ← Dev profile overrides
│           ├── application-prod.yml  ← Prod profile (disables Swagger, etc.)
│           └── db/migration/         ← V1–V60+ Flyway SQL migrations
│
├── ai_engine/                        ← FastAPI AI microservice
│   ├── main.py                       ← App entry point + router registration
│   ├── routers/                      ← recommendation, backhaul, pricing, route_optimizer, empty_return
│   ├── services/                     ← Business logic for each AI feature
│   │   ├── _geo.py                   ← Geographic utilities (distances, city data)
│   │   ├── pricing_service.py        ← Price prediction
│   │   ├── recommendation_service.py ← Truck scoring and ranking
│   │   ├── backhaul_service.py       ← Backhaul opportunity matching
│   │   ├── route_optimizer_service.py
│   │   └── empty_return_service.py
│   ├── schemas/                      ← Pydantic request/response models
│   └── requirements.txt
│
├── frontend/
│   └── artifacts/
│       ├── freight-link/             ← React/TypeScript web admin panel
│       │   └── src/pages/            ← admin, bookings, drivers, freight, vehicles, tracking …
│       └── mobile-app/               ← Flutter mobile app
│           └── lib/src/features/     ← ai, auth, bookings, driver, fleet, shipper, tracking …
│
├── docker/
│   ├── docker-compose.yml
│   ├── Dockerfile.backend
│   └── Dockerfile.ai_engine
│
└── *.md                              ← 40+ milestone and audit documentation files
```

Each Spring Boot domain module follows the same layered structure:
`controller/` → `service/` → `repository/` → `entity/` + `dto/` + `mapper/` + `converter/`

---

## 4. Key Features and Functionality

### Cargo / Freight Lifecycle
The `Freight` entity is the core domain object. Shippers create freight requests with:
- Pickup and destination locations (city names + lat/lon)
- Material type, weight (tons), volume (m³), budget, urgency level, deadline
- **Service type:** `intercity` or `intracity` — affects pricing rules and bidding
- **Price type:** `negotiable` (drivers submit bids) or `fixed` (drivers directly accept)
- A bid deadline for the negotiable flow

Freight statuses: `POSTED → MATCHED → IN_TRANSIT → DELIVERED → COMPLETED / CANCELLED`

### Bidding System
Drivers bid on freight requests (one bid per vehicle per cargo, so a fleet owner can submit multiple bids via different vehicles). Key features:
- Standard bid + counter-offer support (both shipper and driver can counter with `counter_amount` + `counter_note`)
- `ai_score` (0.0–1.0) and `is_recommended` flag set by the AI engine — informational only, not client input
- Bid statuses: `PENDING → ACCEPTED / REJECTED / EXPIRED / COUNTERED`

### Booking
Created atomically when a bid is accepted or a direct booking is made. Key design decisions:
- Commission rate is **always server-calculated** (10% standard, 5% fleet) — never accepted from client input
- Supports both bid-based and direct (fleet dispatch) bookings — the `bid` FK is nullable
- One booking → one trip → one payment → multiple ratings (up to 2 per booking: shipper + driver)

### Trip Management
- Multi-stop trip support (created via Flyway V9 — `trips` + `trip_stops` tables)
- DB-level triggers and views for automated status transitions (V17, V18 migrations)
- Detailed trip lifecycle documentation in `MILESTONE_5_TRIP_LIFECYCLE.md`

### Real-time Location Tracking
- WebSocket infrastructure with JWT authentication at the handshake level
- Driver locations broadcast in real-time via Spring WebSocket
- Location history persisted in a partitioned time-series table (`location_history`, V55)

### Payment
- One payment per booking (unique constraint on `booking_id` prevents double-charging)
- Financial formula: `amount = estimatedPrice`, `commissionAmount = amount × rate`, `driverNetAmount = amount − commissionAmount`
- Supported methods: cash, bank transfer, TeleBirr, CBE Birr, Chapa
- All monetary fields use `BigDecimal` (never `double`/`float`)

### Vehicle Approval Workflow
Vehicles go through `UNDER_REVIEW → APPROVED / REJECTED / SUSPENDED`. Admins review via the React admin panel. Audit documented in `ADMIN_UI_VEHICLE_APPROVAL_AUDIT.md`.

### Carrier Companies
Fleet owners can create carrier companies, invite drivers via email (with expiry, cooldown, and rate limiting), and manage team memberships. Added in Flyway V57–V60.

### KYC Documents
Drivers upload license and supporting documents stored in AWS S3. Admin review workflow built in.

### In-app Messaging + Disputes
Shippers and drivers can exchange messages per freight request. A separate dispute management flow allows escalation.

---

## 5. Authentication and Authorization

**Mechanism:** Stateless JWT — no server-side sessions. Every request must include a `Bearer` token in the `Authorization` header.

**Token contents:** `sub` (email or phone number), `role`, `verification_status`, `is_active`, `jti` (UUID for unique identification).

**Token lifecycle:**
- Access token: 15 minutes (`JWT_EXPIRATION=900000` ms)
- Refresh token: 7 days
- On logout: token SHA-256 hash is stored in the `revoked_tokens` table. Every `validateToken()` call checks this table — so tokens are truly invalidated, not just expected to expire.
- Expired revoked tokens are garbage-collected automatically on each new revocation.

**User roles:** `SHIPPER`, `DRIVER`, `ADMIN`, `FLEET_OWNER`

**Login flexibility:** Users can authenticate with email or phone number. Drivers may register phone-only (email is optional, enforced by a partial unique index `WHERE email IS NOT NULL`).

**Public endpoints (no token required):**
- `POST /auth/register`, `POST /auth/login` (and mobile compat equivalents `/register`, `/login`, `/logout`)
- Swagger UI: `/api-docs/**`, `/swagger-ui/**`
- Actuator health/info
- WebSocket handshake: `/ws/**`
- CORS preflight: `OPTIONS /**`
- Company invitation validation: `POST /company-invitations/validate`

**Rate limiting (token bucket):**
- Login: 5 attempts per 60 seconds
- Registration: 10 per hour
- General API: 100 per 60 seconds

**Password storage:** BCrypt

---

## 6. Database Schema

All schema changes are managed through Flyway migrations (V1 through V60+). The database is PostgreSQL 15 with PostGIS for geospatial queries. Enums are stored as lowercase strings matching PostgreSQL CHECK constraints (custom JPA `AttributeConverter` classes, not `@Enumerated(EnumType.STRING)` which would write uppercase).

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `users` | Platform users | `id`, `full_name`, `phone` (unique), `email` (nullable/partial unique), `password`, `role`, `fleet_owner_id`, `lat`, `lon`, `verification_status`, `is_active` |
| `vehicles` | Registered trucks | `id`, `user_id`, `fleet_owner_id`, `truck_type`, `vehicle_category`, `plate_number` (unique), `capacity`, `current_city`, `lat/lon`, `availability_status`, `approval_status`, `rating` |
| `freight` | Cargo requests | `id`, `shipper_id`, `pickup_location`, `destination`, `material_type`, `weight_tons`, `budget`, `urgency_level`, `service_type`, `price_type`, `bid_deadline`, `status`, `matched_driver_id`, `matched_vehicle_id`, `matched_price` |
| `bids` | Driver bids on freight | `id`, `cargo_request_id`, `driver_id`, `vehicle_id`, `amount`, `status`, `ai_score`, `is_recommended`, `distance_km`, `counter_amount`, `counter_by` |
| `bookings` | Confirmed bookings | `id`, `cargo_id`, `vehicle_id`, `driver_id`, `bid_id` (nullable), `booking_status`, `estimated_price`, `commission_fee` |
| `trips` + `trip_stops` | Trip execution | Multi-stop support, timestamps, DB-level triggers |
| `payments` | Payment records | `id`, `booking_id` (unique), `amount`, `commission_amount`, `driver_net_amount`, `paid_by`, `payment_method`, `payment_status`, `transaction_ref` |
| `ratings` | Post-trip ratings | Shipper-rates-driver + driver-rates-shipper per booking |
| `driver_documents` | KYC docs | S3 paths, doc type, review status |
| `location_history` | Driver GPS tracks | Partitioned time-series table (V55) |
| `revoked_tokens` | JWT revocation store | SHA-256 hash, expiry timestamp |
| `messages` | In-app messaging | Per freight request |
| `disputes` | Dispute management | Linked to bookings |
| `carrier_companies` | Fleet company entities | Name, owner, status |
| `company_memberships` | Team assignments | Company ↔ user membership |
| `company_invitations` | Email invitations | Token, expiry, status |
| `backhaul_recommendations` | AI backhaul matches | Persisted AI output |
| `ai_recommendations` | General AI history | Persisted AI output |
| `platform_settings` | Admin configuration | Key-value platform settings |

---

## 7. AI Engine (`ai_engine/`)

A standalone FastAPI microservice running on port 8000. The Spring Boot backend calls it via HTTP (configurable via `AI_ENGINE_URL`). Exposes 5 endpoints under the `/ai` prefix.

### a) Price Prediction — `POST /ai/predict-price`
**File:** `services/pricing_service.py`

Rule-based formula: `distance_km × rate (18–28 ETB/km/ton) × material_multiplier × urgency_multiplier`

Material multipliers:
- Fragile / electronic / machinery / medical: **×1.25**
- Perishables (vegetables, coffee, grain, fish): **×1.15**
- Bulk / construction (cement, sand, gravel): **×0.90**
- General: **×1.00**

Urgency multipliers: express ×1.4, high ×1.2, normal ×1.0, low ×0.85

Output: price range rounded to nearest 500 ETB + estimated (midpoint) + distance + confidence (0.85).

### b) Truck Recommendation — `POST /ai/recommend-truck`
**File:** `services/recommendation_service.py`

Weighted scoring per truck:
- Capacity fit: **40%** (how well the truck's tonnage matches the load)
- Proximity to pickup: **30%** (closer trucks score higher)
- Urgency bonus: **15%** (express loads favor faster trucks)
- Material type bonus: **15%** (fragile/perishable loads prefer specialised trucks)

Returns trucks ranked by score with per-truck estimated price. Works with live DB fleet data or falls back to a demo fleet.

### c) Backhaul Optimization — `POST /ai/backhaul-opportunities`
**File:** `services/backhaul_service.py`

Finds return-load opportunities to avoid empty return trips. Scores cargo by:
- Direction match along the Ethiopian corridor: **35%**
- Destination match to truck's home city: **30%**
- Proximity from truck's current location to cargo pickup: **20%**
- Weight penalty for overloaded requests: **15%**

Returns top 5 opportunities sorted by score.

### d) Route Optimization — `POST /ai/route-optimize`
### e) Empty Return Prediction — `POST /ai/empty-return`

### Geographic Utilities — `services/_geo.py`
This is the foundation for all AI calculations:
- Hardcoded **road distances from Addis Ababa to 40+ Ethiopian cities** (sourced from real route data, not estimates)
- For city-to-city distances not involving Addis: **Haversine formula × 1.6 road factor** (calibrated for Ethiopian highland terrain — roads average ~60% longer than straight-line)
- City name normalization and aliasing (e.g., "Addis Abeba" → "addis ababa", "Nazret" → "adama", "Dire Dewa" → "dire dawa")

---

## 8. Frontend Applications

### React Admin Panel (`frontend/artifacts/freight-link/`)
Built with React 18 + Vite + TypeScript + TailwindCSS. Pages include:

`admin`, `admin-companies`, `bids`, `bookings`, `carrier-companies`, `contract`, `dispute`, `documents`, `driver-detail`, `drivers`, `fleet`, `freight-detail`, `freight-list`, `freight-new`, `history`, `home`, `invitation-accept`, `login`, `messages`, `notifications`, `payment`, `profile`, `register`, `tracking`, `vehicles`

### Flutter Mobile App (`frontend/artifacts/mobile-app/`)
Feature modules: `ai`, `auth`, `bookings`, `driver`, `fleet`, `shipper`, `tracking`, plus shared screens (splash, landing, profile, notifications).

- Shipper flow: create freight, browse nearby trucks, select bid, track shipment
- Driver flow: browse available freight, submit bids, manage active trip, navigate, upload documents
- Localization: English (en_US) and Amharic (am_ET) — Ethiopia's official language
- Maps: `flutter_map` + OpenStreetMap (avoids Google Maps API costs)
- Firebase: prepared but commented out (awaiting `google-services.json`)

---

## 9. External Integrations

| Service | Purpose |
|---------|---------|
| **Chapa** | Ethiopian payment gateway |
| **TeleBirr** | Ethio Telecom mobile money |
| **CBE Birr** | Commercial Bank of Ethiopia mobile money |
| **AWS S3** | Driver KYC document storage |
| **Brevo (SMTP relay)** | Transactional email (invitations, notifications) |
| **OSRM** | Real road routing (`router.project-osrm.org`) |
| **Nominatim / OpenStreetMap** | Geocoding (no API key needed) |
| **Prometheus + Micrometer** | Metrics and monitoring |

---

## 10. Architecture and Design Patterns

**API-first, fully decoupled:** The backend exposes REST under `/api/*`. The web panel and mobile app are completely separate clients — no server-side rendering.

**Security hardening highlights:**
- JWT revocation via SHA-256 hashed token store (not just expiry-based — tokens are truly invalidated on logout)
- All monetary calculations (commission, driver net amount) are server-calculated — client input is explicitly rejected
- `@JsonIgnore` on `User.password` prevents accidental exposure
- Rate limiting on auth endpoints
- Method-level security with `@EnableMethodSecurity` and `@PreAuthorize`

**Domain patterns:**
- Repository pattern (Spring Data JPA repositories)
- MapStruct for type-safe DTO ↔ entity mapping
- Custom JPA `AttributeConverter` classes for all enum fields (lowercase string persistence)
- Lazy loading on all relationships to prevent N+1 queries
- Builder pattern on all entities (Lombok `@Builder`)
- Flyway for auditable, versioned schema evolution

**Notable design decisions:**
- Email is optional for drivers — partial unique index (`WHERE email IS NOT NULL`) allows phone-only registration without breaking uniqueness
- One bid per **vehicle** per cargo (not per driver), enabling fleet owners to submit multiple bids
- Bid field `bid_id` on `Booking` is nullable — direct bookings (fleet dispatch) bypass the bidding flow
- Location history table is partitioned by time (V55) for query performance at scale
- DB-level triggers for trip automation (V17) reduce application-layer complexity

---

## 11. Configuration Overview

Key environment variables (from `.env.example` and `application.yml`):

| Variable | Purpose |
|----------|---------|
| `DB_*` | PostgreSQL connection |
| `JWT_SECRET` | HMAC-SHA256 signing key |
| `JWT_EXPIRATION` | Access token TTL (default 15 min) |
| `JWT_REFRESH_EXPIRATION` | Refresh token TTL (default 7 days) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins |
| `AI_ENGINE_URL` | FastAPI AI service URL (default `http://localhost:8000`) |
| `AI_ENGINE_ENABLED` | Toggle AI engine integration on/off |
| `SMTP_*` | Brevo mail relay credentials |
| `CHAPA_API_KEY` / `TELEBIRR_API_KEY` / `CBE_BIRR_API_KEY` | Payment gateway keys |
| `FRONTEND_BASE_URL` | Used in invitation email links |
| `SPRING_PROFILES_ACTIVE` | `dev` (default) or `prod` |
| `SERVER_PORT` | Backend port (default 8080, Docker maps to 8001) |

Spring profiles: `dev` enables Swagger UI, verbose logging, and relaxed settings. `prod` disables Swagger, tightens CORS, and should be used with environment-specific secrets.

---

## 12. Documentation Files

The root contains 40+ Markdown documentation files tracking the migration from Laravel to Spring Boot across multiple milestones:

| File Pattern | Content |
|-------------|---------|
| `MILESTONE_*.md` | Per-milestone design, review, and completion reports (1–7) |
| `AUTH_*.md` | Authentication design, endpoint compatibility audit |
| `SPRING_BOOT_*.md` | Architecture overview and migration roadmap |
| `API_*.md` | Migration report, transition guide, WebSocket audit |
| `CARRIER_COMPANY_*.md` | Phase 1 & 2 carrier company feature verification |
| `FLUTTER_E2E_FINAL_REPORT.md` | End-to-end Flutter integration test report |
| `FINAL_PRODUCTION_READINESS_REPORT.md` | Production readiness checklist |
| `SECURITY_TEST_SCENARIOS.md` | Security test cases |
| `LOCAL_RUN.md` | Instructions for running locally |

---

## 13. Summary Assessment

**Strengths:**
- Well-structured domain-driven design with clear separation between modules
- Strong security posture: real JWT revocation, server-side financial calculations, rate limiting, BCrypt
- Ethiopian market specificity: hardcoded road distances calibrated to actual Ethiopian routes, Amharic localization, local payment gateways
- Comprehensive schema migration history (60+ Flyway files) giving a full audit trail
- API-first design supports multiple clients (web admin + mobile) from one backend
- AI microservice is cleanly decoupled and can be scaled independently

**Areas to watch:**
- The AI scoring is currently rule-based despite XGBoost/sklearn being in requirements — the ML models are not yet trained on real data
- Legacy Laravel artifacts (`vendor/`, `composer.*`) at the root add clutter but are inactive
- The `.env.example` references both Laravel and Spring Boot conventions — the Spring Boot `application.yml` is the authoritative config
- Firebase/push notifications are prepared but not activated (awaiting `google-services.json`)
- The 40+ documentation `.md` files in the root are useful historically but make the root directory busy
