# Laravel to Spring Boot Migration Workbook
## EthioloadAI Logistics Platform

**Project:** EthioloadAI  
**Source:** Laravel 11.x (PHP)  
**Target:** Spring Boot 3 (Java 21)  
**Document Version:** 1.0  
**Created:** July 8, 2026

---

## TABLE OF CONTENTS

1. [Route Inventory](#1-route-inventory)
2. [Controller Inventory](#2-controller-inventory)
3. [Model Inventory](#3-model-inventory)
4. [Service Inventory](#4-service-inventory)
5. [Middleware Inventory](#5-middleware-inventory)
6. [Validation Inventory](#6-validation-inventory)
7. [Database Relationship Diagram](#7-database-relationship-diagram)
8. [External Service Inventory](#8-external-service-inventory)
9. [Authentication Flow](#9-authentication-flow)
10. [API Contract Inventory](#10-api-contract-inventory)
11. [Migration Priority](#11-migration-priority)
12. [Progress Checklist](#12-progress-checklist)

---

## 1. ROUTE INVENTORY

### 1.1 Authentication Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| POST | /register | AuthController | register | throttle:login | P0 |
| POST | /login | AuthController | login | throttle:login | P0 |
| POST | /auth/login | AdminApiController | login | throttle:login | P0 |
| POST | /logout | AuthController | logout | auth:sanctum | P0 |
| GET | /me | AuthController | me | auth:sanctum | P0 |
| PATCH | /me | AuthController | updateProfile | auth:sanctum | P1 |
| PATCH | /me/password | AuthController | changePassword | auth:sanctum | P1 |

### 1.2 Freight/Cargo Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| GET | /freight | CargoRequestController | freightIndex | auth:sanctum | P0 |
| GET | /freight/{id} | CargoRequestController | freightShow | auth:sanctum | P0 |
| POST | /freight | CargoRequestController | store | auth:sanctum | P0 |
| GET | /cargo-requests | CargoRequestController | index | auth:sanctum | P0 |
| POST | /cargo-requests | CargoRequestController | store | auth:sanctum | P0 |
| GET | /cargo-requests/{id} | CargoRequestController | show | auth:sanctum | P0 |
| PATCH | /cargo-requests/{id} | CargoRequestController | update | auth:sanctum | P1 |
| DELETE | /cargo-requests/{id} | CargoRequestController | destroy | auth:sanctum | P1 |
| POST | /cargo-requests/{cargo}/book-direct | CargoRequestController | bookDirect | auth:sanctum | P0 |
| POST | /cargo-requests/{cargo}/accept-price | CargoRequestController | acceptPrice | auth:sanctum | P0 |
| GET | /cargo-requests/{cargo}/nearby-drivers | CargoRequestController | nearbyDrivers | auth:sanctum | P2 |
| GET | /driver/return-cargo | CargoRequestController | returnCargo | auth:sanctum | P2 |

### 1.3 Vehicle Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| GET | /vehicles | VehicleController | index | auth:sanctum | P0 |
| POST | /vehicles | VehicleController | store | auth:sanctum | P0 |
| GET | /vehicles/{id} | VehicleController | show | auth:sanctum | P1 |
| PATCH | /vehicles/{id} | VehicleController | update | auth:sanctum | P1 |
| DELETE | /vehicles/{id} | VehicleController | destroy | auth:sanctum | P1 |
| POST | /vehicle/register | VehicleController | register | auth:sanctum | P0 |
| GET | /my-vehicles | VehicleController | myVehicles | auth:sanctum | P0 |
| PATCH | /vehicles/{vehicle}/location | VehicleController | updateLocation | auth:sanctum | P0 |
| GET | /vehicle/nearby | VehicleController | nearby | auth:sanctum | P2 |
| GET | /nearby-trucks | VehicleController | nearbyTrucks | auth:sanctum | P2 |
| POST | /driver/location | VehicleController | driverLocation | auth:sanctum | P0 |
| PATCH | /driver/current-city | VehicleController | updateCurrentCity | auth:sanctum | P1 |

### 1.4 Booking Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| GET | /bookings | BookingController | index | auth:sanctum | P0 |
| POST | /bookings | BookingController | store | auth:sanctum | P0 |
| GET | /bookings/{id} | BookingController | show | auth:sanctum | P1 |
| PATCH | /bookings/{id} | BookingController | update | auth:sanctum | P1 |
| DELETE | /bookings/{id} | BookingController | destroy | auth:sanctum | P1 |
| PATCH | /bookings/{booking}/cancel | BookingController | cancel | auth:sanctum | P0 |

### 1.5 Bid Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| GET | /cargo-requests/{cargo}/bids | BidController | index | auth:sanctum | P0 |
| POST | /cargo-requests/{cargo}/bids | BidController | store | auth:sanctum | P0 |
| PATCH | /bids/{bid} | BidController | update | auth:sanctum | P1 |
| PATCH | /bids/{bid}/accept | BidController | accept | auth:sanctum | P0 |
| PATCH | /bids/{bid}/reject | BidController | reject | auth:sanctum | P0 |
| PATCH | /bids/{bid}/withdraw | BidController | withdraw | auth:sanctum | P1 |
| PATCH | /bids/{bid}/counter | BidController | counter | auth:sanctum | P0 |
| PATCH | /bids/{bid}/accept-counter | BidController | acceptCounter | auth:sanctum | P0 |
| GET | /driver/bids | BidController | myBids | auth:sanctum | P0 |

### 1.6 Trip Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| GET | /trips | TripController | index | auth:sanctum | P1 |
| POST | /trips | TripController | store | auth:sanctum | P0 |
| GET | /trips/{id} | TripController | show | auth:sanctum | P1 |
| PATCH | /trips/{id}/status | TripController | updateStatus | auth:sanctum | P0 |
| PATCH | /trips/{id}/location | TripController | updateLocation | auth:sanctum | P0 |
| GET | /trips/{trip}/location | TripController | getLocation | auth:sanctum | P0 |

### 1.7 Trip Stop Routes (Multi-stop)

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| GET | /trips/{trip}/stops | TripStopController | index | auth:sanctum | P1 |
| POST | /trips/{trip}/stops | TripStopController | store | auth:sanctum | P1 |
| PATCH | /trips/{trip}/stops/{stop}/arrive | TripStopController | arrive | auth:sanctum | P1 |
| PATCH | /trips/{trip}/stops/{stop}/load | TripStopController | load | auth:sanctum | P1 |
| PATCH | /trips/{trip}/stops/{stop}/complete | TripStopController | complete | auth:sanctum | P1 |
| DELETE | /trips/{trip}/stops/{stop} | TripStopController | destroy | auth:sanctum | P1 |

### 1.8 Payment Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| POST | /payments | PaymentController | store | auth:sanctum | P0 |
| GET | /payments/{booking_id} | PaymentController | show | auth:sanctum | P1 |

### 1.9 AI Engine Proxy Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| POST | /ai/recommend-truck | AiController | recommendTruck | auth:sanctum | P2 |
| POST | /ai/backhaul-opportunities | AiController | backhaulOpportunities | auth:sanctum | P2 |
| POST | /ai/predict-price | AiController | predictPrice | auth:sanctum | P2 |
| POST | /ai/predict-empty-return | AiController | predictEmptyReturn | auth:sanctum | P2 |
| POST | /ai/optimize-route | AiController | optimizeRoute | auth:sanctum | P2 |

### 1.10 Backhaul Recommendation Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| GET | /trips/{trip}/backhaul-recommendations | BackhaulRecommendationController | index | auth:sanctum | P2 |
| PATCH | /recommendations/{recommendation}/dismiss | BackhaulRecommendationController | dismiss | auth:sanctum | P2 |

### 1.11 Routing/Geocoding Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| POST | /geocode/nearest-city | GeocodingController | nearestCity | auth:sanctum | P1 |
| GET | /routing/route | RoutingController | route | auth:sanctum | P1 |
| GET | /routing/search | RoutingController | search | auth:sanctum | P1 |
| GET | /routing/reverse | RoutingController | reverse | auth:sanctum | P1 |

### 1.12 Document Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| GET | /driver/documents | DocumentController | index | auth:sanctum | P0 |
| POST | /driver/documents | DocumentController | upload | auth:sanctum | P0 |
| GET | /driver/documents/{document}/file | DocumentController | download | auth:sanctum | P0 |
| GET | /admin/driver-documents | DocumentController | adminIndex | auth:sanctum,admin | P0 |
| PATCH | /admin/driver-documents/{document}/review | DocumentController | review | auth:sanctum,admin | P0 |

### 1.13 Rating Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| POST | /ratings | RatingController | store | auth:sanctum | P1 |
| GET | /ratings/{booking_id} | RatingController | show | auth:sanctum | P1 |
| GET | /driver/my-ratings | RatingController | myRatings | auth:sanctum | P1 |

### 1.14 Fleet Management Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| GET | /fleet/dashboard | FleetController | dashboard | auth:sanctum | P1 |
| GET | /fleet/drivers | FleetController | drivers | auth:sanctum | P1 |
| GET | /fleet/vehicles | FleetController | vehicles | auth:sanctum | P1 |
| GET | /fleet/available-cargo | FleetController | availableCargo | auth:sanctum | P1 |
| POST | /fleet/drivers/add | FleetController | addDriver | auth:sanctum | P1 |
| DELETE | /fleet/drivers/{driverId} | FleetController | removeDriver | auth:sanctum | P1 |
| POST | /fleet/vehicles | FleetController | addVehicle | auth:sanctum | P1 |
| PATCH | /fleet/vehicles/{vehicleId}/assign | FleetController | assignVehicle | auth:sanctum | P1 |
| POST | /fleet/bookings | FleetController | createBooking | auth:sanctum | P1 |
| PATCH | /fleet/bookings/{bookingId}/dispatch | FleetController | dispatchBooking | auth:sanctum | P1 |

### 1.15 Notification Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| GET | /notifications | NotificationController | index | auth:sanctum | P1 |
| PATCH | /notifications/read-all | NotificationController | markAllRead | auth:sanctum | P1 |
| PATCH | /notifications/{id}/read | NotificationController | markRead | auth:sanctum | P1 |

### 1.16 Admin API Routes (React Admin Panel)

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| GET | /admin/stats | AdminApiController | stats | auth:sanctum,admin | P0 |
| GET | /admin/users | AdminApiController | users | auth:sanctum,admin | P0 |
| GET | /admin/drivers | AdminApiController | drivers | auth:sanctum,admin | P0 |
| GET | /admin/payments | AdminApiController | payments | auth:sanctum,admin | P0 |
| GET | /admin/analytics/revenue | AdminApiController | analyticsRevenue | auth:sanctum,admin | P2 |
| GET | /admin/analytics/routes | AdminApiController | analyticsRoutes | auth:sanctum,admin | P2 |
| GET | /admin/analytics/cargo | AdminApiController | analyticsCargo | auth:sanctum,admin | P2 |
| GET | /admin/bookings/unpaid | AdminApiController | unpaidBookings | auth:sanctum,admin | P1 |
| POST | /admin/bookings/{id}/mark-cash-paid | AdminApiController | markCashPaid | auth:sanctum,admin | P1 |
| GET | /admin/fleet-owners | AdminApiController | fleetOwners | auth:sanctum,admin | P1 |
| POST | /admin/users | AdminApiController | createUser | auth:sanctum,admin | P0 |
| PUT | /admin/users/{id} | AdminApiController | updateUser | auth:sanctum,admin | P0 |
| DELETE | /admin/users/{id} | AdminApiController | deleteUser | auth:sanctum,admin | P0 |
| POST | /admin/drivers | AdminApiController | createDriver | auth:sanctum,admin | P0 |
| PATCH | /drivers/{id}/status | AdminApiController | updateDriverStatus | auth:sanctum,admin | P0 |
| GET | /admin/settings/pricing | AdminSettingsController | pricingShow | auth:sanctum,admin | P1 |
| PATCH | /admin/settings/pricing | AdminSettingsController | pricingUpdate | auth:sanctum,admin | P1 |

### 1.17 User Management Routes

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| GET | /users | AdminApiController | users | auth:sanctum,admin | P0 |
| GET | /drivers | AdminApiController | drivers | auth:sanctum,admin | P0 |
| GET | /trips | AdminApiController | trips | auth:sanctum,admin | P1 |

---

## 2. CONTROLLER INVENTORY

### 2.1 API Controllers

| Controller | Responsibilities | Key Methods | Dependencies | Spring Equivalent |
|------------|------------------|--------------|--------------|------------------|
| AuthController | Authentication, registration, profile management | register, login, logout, me, changePassword, updateProfile | RegisterRequest, UserResource | AuthController with JWT |
| VehicleController | Vehicle management, GPS tracking, nearby search | index, register, updateLocation, nearby, nearbyTrucks | VehicleRegisterRequest, VehicleResource | VehicleController |
| CargoRequestController | Cargo lifecycle, bidding, nearby drivers | index, store, bookDirect, acceptPrice, nearbyDrivers | CargoCreateRequest, BidService, CargoResource | CargoRequestController |
| BookingController | Booking lifecycle | index, store, cancel | BookingCreateRequest, BookingService | BookingController |
| PaymentController | Payment processing | store, show | PaymentProcessRequest, PaymentService | PaymentController |
| TripController | Trip management, location tracking | store, updateStatus, updateLocation, getLocation | TripCreateRequest, TripService | TripController |
| AiController | AI engine proxy with fallback | recommendTruck, predictPrice, backhaulOpportunities | AiEngineService, RoutingService | AiController |
| BidController | Bid lifecycle management | store, accept, reject, counter, acceptCounter | BidStoreRequest, BidService | BidController |
| AdminApiController | React admin panel API | stats, users, drivers, payments, analytics | None | AdminApiController |
| FleetController | Fleet owner management | dashboard, addDriver, addVehicle, dispatchBooking | None | FleetController |
| TripStopController | Multi-stop trip management | store, arrive, load, complete | TripStopResource, TripService | TripStopController |
| RoutingController | Routing/geocoding proxy | route, search, reverse | RoutingService | RoutingController |
| DocumentController | Driver document management | upload, download, review | DocumentUploadRequest, DocumentResource | DocumentController |
| NotificationController | Notification management | index, markRead, markAllRead | None | NotificationController |
| RatingController | Rating management | store, myRatings | None | RatingController |
| BackhaulRecommendationController | Backhaul display | index, dismiss | None | BackhaulRecommendationController |
| GeocodingController | Nearest city lookup | nearestCity | None | GeocodingController |
| UserController | User CRUD (admin) | index, store, update, destroy | UserStoreRequest, UserResource | UserController |
| AdminSettingsController | Platform settings | pricingShow, pricingUpdate | PlatformSetting | AdminSettingsController |

### 2.2 Admin Controllers

| Controller | Responsibilities | Key Methods | Spring Equivalent |
|------------|------------------|--------------|------------------|
| AdminController | Admin panel CRUD | dashboard, storeUser, users, updateUser, deleteUser | AdminController |

---

## 3. MODEL INVENTORY

### 3.1 Core Models

| Model | Table | Key Attributes | Relationships | Spring Entity |
|-------|-------|----------------|----------------|--------------|
| User | users | id, full_name, phone, email, password, role, verification_status, is_active, fleet_owner_id | hasMany: Vehicle, CargoRequest, Booking, DriverDocument, drivers; belongsTo: fleetOwner | UserEntity |
| Vehicle | vehicles | id, user_id, fleet_owner_id, truck_type, vehicle_category, plate_number, capacity, current_city, latitude, longitude, availability_status, rating | belongsTo: User, fleetOwner; hasMany: Booking | VehicleEntity |
| CargoRequest | cargo_requests | id, user_id, pickup_location, destination, material_type, weight, urgency_level, budget, price_type, bid_deadline, status, service_type | belongsTo: User; hasOne: Booking; hasMany: Bid | CargoRequestEntity |
| Booking | bookings | id, cargo_id, vehicle_id, driver_id, bid_id, booking_status, estimated_price, commission_fee | belongsTo: CargoRequest, Vehicle, User (driver), Bid; hasOne: Trip, Payment, Rating | BookingEntity |
| Bid | bids | id, cargo_request_id, driver_id, vehicle_id, amount, status, counter_amount, counter_by, counter_at | belongsTo: CargoRequest, User (driver), Vehicle; hasOne: Booking | BidEntity |
| Trip | trips | id, booking_id, start_location, destination, route_data, trip_status, trip_type, total_stops, completed_stops | belongsTo: Booking; hasMany: TripStop | TripEntity |
| TripStop | trip_stops | id, trip_id, cargo_request_id, stop_order, location_name, agreed_price, status | belongsTo: Trip, CargoRequest | TripStopEntity |
| Payment | payments | id, booking_id, amount, commission_amount, driver_net_amount, payment_method, payment_status | belongsTo: Booking | PaymentEntity |
| Rating | ratings | id, booking_id, shipper_id, driver_id, rater_id, rating, feedback | belongsTo: Booking, User (shipper), User (driver) | RatingEntity |

### 3.2 Supporting Models

| Model | Table | Key Attributes | Relationships | Spring Entity |
|-------|-------|----------------|----------------|--------------|
| DriverDocument | driver_documents | id, user_id, document_type, file_path, status, rejection_reason, reviewed_by | belongsTo: User, reviewer | DriverDocumentEntity |
| BackhaulRecommendation | backhaul_recommendations | id, trip_id, driver_id, cargo_request_id, score, status, metadata | belongsTo: Trip, User (driver), CargoRequest | BackhaulRecommendationEntity |
| PlatformSetting | platform_settings | id, key, value | None | PlatformSettingEntity |

### 3.3 Model Attributes Summary

#### User Entity
```
- id: Long (PK)
- fullName: String
- phone: String (unique)
- email: String (unique, nullable)
- password: String (hashed)
- role: Enum (SHIPPER, DRIVER, ADMIN, FLEET_OWNER)
- fleetOwnerId: Long (FK to User, nullable)
- location: String (nullable)
- latitude: BigDecimal (nullable)
- longitude: BigDecimal (nullable)
- verificationStatus: Boolean
- isActive: Boolean
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

#### Vehicle Entity
```
- id: Long (PK)
- userId: Long (FK to User)
- fleetOwnerId: Long (FK to User, nullable)
- truckType: String
- vehicleCategory: Enum (HEAVY, LIGHT, nullable)
- plateNumber: String (unique)
- capacity: Double
- currentCity: String (nullable)
- latitude: Double (nullable)
- longitude: Double (nullable)
- lastLocationAt: LocalDateTime (nullable)
- availabilityStatus: String
- rating: BigDecimal
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

#### CargoRequest Entity
```
- id: Long (PK)
- userId: Long (FK to User)
- pickupLocation: String
- pickupLat: BigDecimal (nullable)
- pickupLng: BigDecimal (nullable)
- destination: String
- materialType: String
- weight: Double
- urgencyLevel: String
- budget: BigDecimal (nullable)
- priceType: Enum (FIXED, NEGOTIABLE, nullable)
- bidDeadline: LocalDateTime (nullable)
- status: Enum (PENDING, MATCHED, COMPLETED)
- serviceType: Enum (INTERCITY, INTRACITY, nullable)
- city: String (nullable)
- pickupArea: String (nullable)
- dropoffArea: String (nullable)
- preferredDate: LocalDate (nullable)
- itemsDescription: String (nullable)
- vehicleTypeNeeded: String (nullable)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

#### Booking Entity
```
- id: Long (PK)
- cargoId: Long (FK to CargoRequest)
- vehicleId: Long (FK to Vehicle)
- driverId: Long (FK to User)
- bidId: Long (FK to Bid, nullable)
- bookingStatus: Enum (PENDING, ACCEPTED, COMPLETED, CONFIRMED, DELIVERED, CANCELLED)
- estimatedPrice: BigDecimal (nullable)
- commissionFee: BigDecimal
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

#### Bid Entity
```
- id: Long (PK)
- cargoRequestId: Long (FK to CargoRequest)
- driverId: Long (FK to User)
- vehicleId: Long (FK to Vehicle)
- amount: BigDecimal
- note: String (nullable)
- availableDatetime: LocalDateTime (nullable)
- status: Enum (PENDING, ACCEPTED, REJECTED, EXPIRED, COUNTERED)
- aiScore: Double (nullable)
- isRecommended: Boolean
- distanceKm: Double (nullable)
- counterAmount: BigDecimal (nullable)
- counterNote: String (nullable)
- counterBy: Enum (SHIPPER, DRIVER, nullable)
- counterAt: LocalDateTime (nullable)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

#### Trip Entity
```
- id: Long (PK)
- bookingId: Long (FK to Booking)
- startLocation: String
- destination: String
- routeData: Json (nullable)
- tripStatus: Enum (ONGOING, COMPLETED)
- tripType: Enum (SINGLE, MULTI_STOP, nullable)
- totalStops: Integer
- completedStops: Integer
- startTime: LocalDateTime (nullable)
- endTime: LocalDateTime (nullable)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

#### TripStop Entity
```
- id: Long (PK)
- tripId: Long (FK to Trip)
- cargoRequestId: Long (FK to CargoRequest, nullable)
- stopOrder: Integer
- locationName: String
- pickupLat: BigDecimal (nullable)
- pickupLng: BigDecimal (nullable)
- agreedPrice: BigDecimal
- status: Enum (PENDING, ARRIVED, LOADED, COMPLETED)
- notes: String (nullable)
- arrivedAt: LocalDateTime (nullable)
- completedAt: LocalDateTime (nullable)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

#### Payment Entity
```
- id: Long (PK)
- bookingId: Long (FK to Booking)
- amount: BigDecimal
- commissionAmount: BigDecimal (nullable)
- driverNetAmount: BigDecimal (nullable)
- paidBy: Long (FK to User, nullable)
- paymentMethod: String
- paymentStatus: String
- transactionRef: String (nullable)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

#### Rating Entity
```
- id: Long (PK)
- bookingId: Long (FK to Booking)
- shipperId: Long (FK to User)
- driverId: Long (FK to User)
- raterId: Long (FK to User)
- rating: Integer
- feedback: String (nullable)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

#### DriverDocument Entity
```
- id: Long (PK)
- userId: Long (FK to User)
- documentType: Enum (LICENSE, NATIONAL_ID, VEHICLE_REGISTRATION, INSURANCE, TIN)
- filePath: String
- originalName: String
- status: Enum (PENDING, APPROVED, REJECTED)
- rejectionReason: String (nullable)
- reviewedBy: Long (FK to User, nullable)
- reviewedAt: LocalDateTime (nullable)
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

#### BackhaulRecommendation Entity
```
- id: Long (PK)
- tripId: Long (FK to Trip)
- driverId: Long (FK to User)
- cargoRequestId: Long (FK to CargoRequest)
- score: BigDecimal
- status: Enum (PENDING, VIEWED, BID_PLACED, DISMISSED)
- metadata: Json
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

#### PlatformSetting Entity
```
- id: Long (PK)
- key: String (unique)
- value: String
- createdAt: LocalDateTime
- updatedAt: LocalDateTime
```

---

## 4. SERVICE INVENTORY

### 4.1 Service Classes

| Service | Responsibilities | Methods | External APIs | Spring Service |
|---------|------------------|---------|----------------|---------------|
| AiEngineService | Proxy to FastAPI AI engine | recommendTruck, backhaulOpportunities, predictPrice, predictEmptyReturn, optimizeRoute | FastAPI (localhost:8000) | AiEngineService |
| BackhaulService | Generate backhaul recommendations | recommendForTrip, resolveCity, haversine | None | BackhaulService |
| BidService | Bid lifecycle management | placeBid, acceptFixedPrice, rankBids, counterBid, acceptCounter, acceptBid | None | BidService |
| BookingService | Booking management | createBooking, updateBooking, deleteBooking | None | BookingService |
| PaymentService | Payment processing | processPayment | None | PaymentService |
| RoutingService | Routing and geocoding | getRoute, nearestRoad, searchPlace, reverseGeocode | OSRM, Nominatim | RoutingService |
| TripService | Trip lifecycle management | startTrip, updateLocation, completeTrip | None | TripService |

### 4.2 Service Dependencies

```
AiEngineService
├── HTTP Client (RestTemplate/WebClient)
└── Configuration (AI_ENGINE_URL)

BackhaulService
├── CargoRequestRepository
├── BackhaulRecommendationRepository
└── Ethiopian City Data (62 cities)

BidService
├── BidRepository
├── BookingRepository
├── CargoRequestRepository
├── RatingRepository
├── VehicleRepository
└── UserRepository

BookingService
├── BookingRepository
├── CargoRequestRepository
└── NotificationService

PaymentService
├── PaymentRepository
├── BookingRepository
└── Commission Calculation (10%)

RoutingService
├── HTTP Client (RestTemplate/WebClient)
├── Cache (Redis/Caffeine)
├── OSRM API
└── Nominatim API

TripService
├── TripRepository
├── BookingRepository
├── CargoRequestRepository
├── NotificationService
└── Event Publisher (WebSocket)
```

---

## 5. MIDDLEWARE INVENTORY

### 5.1 Laravel Middleware → Spring Security Filter Mapping

| Laravel Middleware | Purpose | Spring Security Equivalent | Priority |
|--------------------|---------|---------------------------|----------|
| auth:sanctum | API token authentication | JwtAuthenticationFilter | P0 |
| AdminMiddleware | Admin role check | RoleAuthorizationFilter (ROLE_ADMIN) | P0 |
| throttle:login | Rate limiting for login | RateLimitFilter | P1 |
| throttle:api | Rate limiting for API | RateLimitFilter | P1 |
| auth | Session authentication | SessionAuthenticationFilter | P2 |
| guest | Redirect if authenticated | AnonymousAuthenticationFilter | P2 |

### 5.2 Spring Security Filter Chain

```
Security Filter Chain:
1. CorsFilter (CORS configuration)
2. RateLimitFilter (API rate limiting)
3. JwtAuthenticationFilter (JWT token validation)
4. RoleAuthorizationFilter (Role-based access)
5. SecurityContextHolderFilter (Set security context)
6. ExceptionTranslationFilter (Handle auth exceptions)
7. FilterSecurityInterceptor (Authorization check)
```

---

## 6. VALIDATION INVENTORY

### 6.1 Form Requests → Spring Validators

| Laravel Request | Purpose | Spring Validator | Priority |
|-----------------|---------|------------------|----------|
| RegisterRequest | User registration | UserRegistrationValidator | P0 |
| LoginRequest | User login | UserLoginValidator | P0 |
| VehicleRegisterRequest | Vehicle registration | VehicleRegistrationValidator | P0 |
| VehicleUpdateRequest | Vehicle update | VehicleUpdateValidator | P1 |
| VehicleLocationUpdateRequest | GPS location update | VehicleLocationValidator | P0 |
| CargoCreateRequest | Cargo request creation | CargoCreateValidator | P0 |
| CargoUpdateRequest | Cargo request update | CargoUpdateValidator | P1 |
| BookingCreateRequest | Booking creation | BookingCreateValidator | P0 |
| BookingUpdateRequest | Booking update | BookingUpdateValidator | P1 |
| BidStoreRequest | Bid placement | BidStoreValidator | P0 |
| PaymentProcessRequest | Payment processing | PaymentProcessValidator | P0 |
| TripCreateRequest | Trip creation | TripCreateValidator | P0 |
| TripUpdateStatusRequest | Trip status update | TripUpdateStatusValidator | P0 |
| TripLocationUpdateRequest | Trip location update | TripLocationValidator | P0 |
| DocumentUploadRequest | Document upload | DocumentUploadValidator | P0 |
| UserStoreRequest | User creation (admin) | UserStoreValidator | P0 |
| UserUpdateRequest | User update (admin) | UserUpdateValidator | P1 |

### 6.2 Validation Rules Summary

#### User Registration
- full_name: required, string
- phone: required, string, unique
- email: optional, string, unique
- password: required, string, min:8
- role: required, enum (shipper, driver, fleet_owner)

#### Vehicle Registration
- truck_type: required, string
- vehicle_category: optional, enum (heavy, light)
- plate_number: required, string, unique
- capacity: required, numeric, min:0
- current_city: optional, string
- latitude: optional, numeric, between -90,90
- longitude: optional, numeric, between -180,180

#### Cargo Request (Intercity)
- pickup_location: required, string
- destination: required, string
- material_type: required, string
- weight: required, numeric, min:0
- urgency_level: required, string
- budget: optional, numeric, min:0
- price_type: optional, enum (fixed, negotiable)
- bid_deadline: optional, datetime, after:now
- pickup_lat: optional, numeric, between -90,90
- pickup_lng: optional, numeric, between -180,180

#### Cargo Request (Intracity)
- city: required, string
- pickup_area: required, string
- dropoff_area: required, string
- preferred_date: required, date, after_or_equal:today
- items_description: required, string
- vehicle_type_needed: optional, string

#### Bid Placement
- vehicle_id: required, exists in vehicles
- amount: required, numeric, min:1, max:9999999.99
- note: optional, string, max:500
- available_datetime: optional, datetime, after:now (required for intracity)

#### Payment Processing
- booking_id: required, exists in bookings
- payment_method: required, string
- paid_by: optional, exists in users

---

## 7. DATABASE RELATIONSHIP DIAGRAM

### 7.1 Entity Relationship Diagram (Text-Based)

```
┌─────────────────┐
│     User        │
├─────────────────┤
│ id (PK)         │
│ full_name       │
│ phone (UNIQUE)  │
│ email (UNIQUE)  │
│ password        │
│ role (ENUM)     │
│ fleet_owner_id  │───┐
│ verification_   │   │
│   status        │   │
│ is_active       │   │
└────────┬────────┘   │
         │            │
         │────────────┘
         │
         ├──────────────────┐
         │                  │
         ▼                  ▼
┌─────────────────┐  ┌─────────────────┐
│    Vehicle      │  │  CargoRequest   │
├─────────────────┤  ├─────────────────┤
│ id (PK)         │  │ id (PK)         │
│ user_id (FK)    │◄─┤ user_id (FK)    │
│ fleet_owner_id  │  │ pickup_location │
│ truck_type      │  │ destination     │
│ vehicle_category│  │ material_type   │
│ plate_number    │  │ weight          │
│ capacity        │  │ urgency_level   │
│ current_city    │  │ budget          │
│ latitude        │  │ price_type      │
│ longitude       │  │ bid_deadline    │
│ availability_   │  │ status (ENUM)   │
│   status        │  │ service_type    │
│ rating          │  └────────┬────────┘
└────────┬────────┘           │
         │                    │
         │                    ├──────────────────┐
         │                    │                  │
         ▼                    ▼                  ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│    Booking      │  │      Bid        │  │ DriverDocument  │
├─────────────────┤  ├─────────────────┤  ├─────────────────┤
│ id (PK)         │  │ id (PK)         │  │ id (PK)         │
│ cargo_id (FK)   │◄─┤ cargo_request_  │  │ user_id (FK)    │◄─┐
│ vehicle_id (FK) │  │   id (FK)       │  │ document_type  │  │
│ driver_id (FK)  │  │ driver_id (FK)  │  │ file_path      │  │
│ bid_id (FK)     │◄─┤ vehicle_id (FK) │  │ status (ENUM)   │  │
│ booking_status  │  │ amount          │  │ rejection_      │  │
│ estimated_price│  │ status (ENUM)   │  │   reason        │  │
│ commission_fee  │  │ counter_amount  │  │ reviewed_by (FK)│  │
└────────┬────────┘  │ counter_by      │  └─────────────────┘  │
         │           │ counter_at      │                       │
         │           └────────┬────────┘                       │
         │                    │                                │
         ▼                    ▼                                │
┌─────────────────┐  ┌─────────────────┐                       │
│      Trip       │  │  BackhaulRec    │                       │
├─────────────────┤  ├─────────────────┤                       │
│ id (PK)         │  │ id (PK)         │                       │
│ booking_id (FK) │◄─┤ trip_id (FK)    │                       │
│ start_location  │  │ driver_id (FK)  │                       │
│ destination     │  │ cargo_request_  │                       │
│ route_data      │  │   id (FK)       │                       │
│ trip_status     │  │ score           │                       │
│ trip_type       │  │ status (ENUM)   │                       │
│ total_stops     │  │ metadata        │                       │
│ completed_stops │  └─────────────────┘                       │
└────────┬────────┘                                             │
         │                                                      │
         ▼                                                      │
┌──────────────────────────────────────────────────────────────┘
│    TripStop
├──────────────────────────────────────────────────────────────┤
│ id (PK)
│ trip_id (FK)
│ cargo_request_id (FK, nullable)
│ stop_order
│ location_name
│ agreed_price
│ status (ENUM)
└──────────────────────────────────────────────────────────────┘

┌─────────────────┐  ┌─────────────────┐
│    Payment      │  │     Rating      │
├─────────────────┤  ├─────────────────┤
│ id (PK)         │  │ id (PK)         │
│ booking_id (FK) │◄─┤ booking_id (FK) │
│ amount          │  │ shipper_id (FK) │
│ commission_     │  │ driver_id (FK)  │
│   amount        │  │ rater_id (FK)   │
│ driver_net_     │  │ rating          │
│   amount        │  │ feedback        │
│ payment_method  │  └─────────────────┘
│ payment_status  │
└─────────────────┘

┌─────────────────┐
│ PlatformSetting │
├─────────────────┤
│ id (PK)
│ key (UNIQUE)
│ value
└─────────────────┘
```

### 7.2 Relationship Types

| Relationship | Type | Cascade | Notes |
|--------------|------|---------|-------|
| User → Vehicle | One-to-Many | CASCADE DELETE | User owns vehicles |
| User → CargoRequest | One-to-Many | CASCADE DELETE | User creates cargo requests |
| User → Booking | One-to-Many | CASCADE DELETE | User as driver has bookings |
| User → DriverDocument | One-to-Many | CASCADE DELETE | Driver documents |
| User → User (fleet) | Self-referential | SET NULL | Fleet owner has drivers |
| Vehicle → Booking | One-to-Many | CASCADE DELETE | Vehicle used in bookings |
| CargoRequest → Booking | One-to-One | CASCADE DELETE | Cargo has one booking |
| CargoRequest → Bid | One-to-Many | CASCADE DELETE | Cargo receives bids |
| Booking → Trip | One-to-One | CASCADE DELETE | Booking has one trip |
| Booking → Payment | One-to-One | None | Payment for booking |
| Booking → Rating | One-to-One | None | Rating for booking |
| Booking → Bid | Many-to-One | None | Booking from bid |
| Trip → TripStop | One-to-Many | CASCADE DELETE | Trip has multiple stops |
| TripStop → CargoRequest | Many-to-One | SET NULL | Stop may have cargo |
| BackhaulRec → Trip | Many-to-One | CASCADE DELETE | Recommendation for trip |
| BackhaulRec → CargoRequest | Many-to-One | CASCADE DELETE | Recommendation for cargo |

---

## 8. EXTERNAL SERVICE INVENTORY

### 8.1 External APIs

| Service | Purpose | Base URL | Endpoints | Timeout | Caching | Spring Integration |
|---------|---------|----------|-----------|---------|---------|-------------------|
| FastAPI AI | Truck recommendation, price prediction, backhaul, routing | http://localhost:8000 | /ai/recommend-truck, /ai/backhaul-opportunities, /ai/predict-price, /ai/predict-empty-return, /ai/optimize-route | 5s | None | RestTemplate/WebClient |
| OSRM | Route calculation, nearest road | http://router.project-osrm.org | /route/v1/driving, /nearest/v1/driving | 5s | 1h (route), 30min (nearest) | RestTemplate/WebClient + Cache |
| Nominatim | Place search, reverse geocoding | https://nominatim.openstreetmap.org | /search, /reverse | 5s | 24h (search), 6h (reverse) | RestTemplate/WebClient + Cache |

### 8.2 Service Configuration

#### FastAPI AI Service
```yaml
spring:
  ai-engine:
    url: ${AI_ENGINE_URL:http://localhost:8000}
    timeout: 5000
```

#### OSRM Service
```yaml
spring:
  osrm:
    base-url: http://router.project-osrm.org
    timeout: 5000
    cache:
      route-ttl: 3600
      nearest-ttl: 1800
```

#### Nominatim Service
```yaml
spring:
  nominatim:
    base-url: https://nominatim.openstreetmap.org
    user-agent: EthioLoadAI/1.0
    timeout: 5000
    country-codes: et
    cache:
      search-ttl: 86400
      reverse-ttl: 21600
```

### 8.3 Fallback Logic

| Service | Fallback Strategy | Implementation |
|---------|------------------|----------------|
| FastAPI AI | Local calculation using Haversine + city distance table | Service layer fallback |
| OSRM | Haversine distance calculation | Service layer fallback |
| Nominatim | Return null/empty | Service layer fallback |

---

## 9. AUTHENTICATION FLOW

### 9.1 Laravel Sanctum Flow

```
1. Registration
   POST /register
   → Validate input
   → Create user (hash password)
   → Generate API token (Sanctum)
   → Return user + token

2. Login
   POST /login
   → Validate credentials
   → Generate API token
   → Return user + token

3. Authenticated Request
   GET /me
   Header: Authorization: Bearer {token}
   → Validate token (Sanctum)
   → Return user data

4. Logout
   POST /logout
   → Revoke current token
   → Return success
```

### 9.2 Spring Security JWT Flow

```
1. Registration
   POST /api/auth/register
   → Validate input
   → Create user (BCrypt password)
   → Generate JWT token
   → Return user + token

2. Login
   POST /api/auth/login
   → Validate credentials
   → Generate JWT token
   → Return user + token

3. Authenticated Request
   GET /api/auth/me
   Header: Authorization: Bearer {jwt}
   → JwtAuthenticationFilter validates token
   → SecurityContext set
   → Return user data

4. Logout
   POST /api/auth/logout
   → Add token to blacklist (optional)
   → Return success
```

### 9.3 JWT Token Structure

```json
{
  "sub": "user_id",
  "role": "DRIVER",
  "iat": 1234567890,
  "exp": 1234571490,
  "iss": "ethioloadai"
}
```

### 9.4 Security Configuration

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
            .csrf().disable()
            .cors().and()
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            .and()
            .addFilterBefore(jwtAuthenticationFilter, 
                UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

### 9.5 Role-Based Access Control

| Role | Permissions | Spring Security Role |
|------|-------------|---------------------|
| ADMIN | Full system access | ROLE_ADMIN |
| SHIPPER | Create cargo, view bookings, rate drivers | ROLE_SHIPPER |
| DRIVER | Place bids, manage vehicles, update trips | ROLE_DRIVER |
| FLEET_OWNER | Manage fleet, dispatch bookings | ROLE_FLEET_OWNER |

---

## 10. API CONTRACT INVENTORY

### 10.1 Authentication Endpoints

#### POST /api/auth/register
**Request:**
```json
{
  "full_name": "John Doe",
  "phone": "+251911234567",
  "email": "john@example.com",
  "password": "password123",
  "role": "driver"
}
```
**Response (201):**
```json
{
  "user": {
    "id": 1,
    "full_name": "John Doe",
    "phone": "+251911234567",
    "email": "john@example.com",
    "role": "driver",
    "verification_status": false,
    "is_active": false
  },
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

#### POST /api/auth/login
**Request:**
```json
{
  "phone": "+251911234567",
  "password": "password123"
}
```
**Response (200):**
```json
{
  "user": { /* user object */ },
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 10.2 Vehicle Endpoints

#### GET /api/vehicles
**Response (200):**
```json
{
  "data": [
    {
      "id": 1,
      "truck_type": "Flatbed",
      "vehicle_category": "heavy",
      "plate_number": "AA-1234",
      "capacity": 10.5,
      "current_city": "Addis Ababa",
      "availability_status": "available",
      "rating": 4.5
    }
  ]
}
```

#### POST /api/vehicles
**Request:**
```json
{
  "truck_type": "Flatbed",
  "vehicle_category": "heavy",
  "plate_number": "AA-1234",
  "capacity": 10.5,
  "current_city": "Addis Ababa"
}
```

### 10.3 Cargo Request Endpoints

#### GET /api/cargo-requests
**Response (200):**
```json
{
  "data": [
    {
      "id": 1,
      "pickup_location": "Addis Ababa",
      "destination": "Bahir Dar",
      "material_type": "Cement",
      "weight": 5.0,
      "urgency_level": "normal",
      "budget": 5000,
      "price_type": "negotiable",
      "status": "pending"
    }
  ]
}
```

#### POST /api/cargo-requests
**Request (Intercity):**
```json
{
  "pickup_location": "Addis Ababa",
  "destination": "Bahir Dar",
  "material_type": "Cement",
  "weight": 5.0,
  "urgency_level": "normal",
  "budget": 5000,
  "price_type": "negotiable"
}
```

**Request (Intracity):**
```json
{
  "service_type": "intracity",
  "city": "Addis Ababa",
  "pickup_area": "Bole",
  "dropoff_area": "Kazanchis",
  "preferred_date": "2026-07-15",
  "items_description": "Office furniture",
  "vehicle_type_needed": "light"
}
```

### 10.4 Bid Endpoints

#### POST /api/cargo-requests/{id}/bids
**Request:**
```json
{
  "vehicle_id": 1,
  "amount": 4500,
  "note": "Can deliver within 24 hours",
  "available_datetime": "2026-07-15T10:00:00Z"
}
```
**Response (201):**
```json
{
  "id": 1,
  "cargo_request_id": 1,
  "driver_id": 2,
  "vehicle_id": 1,
  "amount": 4500,
  "status": "pending",
  "distance_km": 503.2
}
```

#### PATCH /api/bids/{id}/accept
**Response (200):**
```json
{
  "id": 1,
  "cargo_id": 1,
  "vehicle_id": 1,
  "driver_id": 2,
  "bid_id": 1,
  "booking_status": "accepted",
  "estimated_price": 4500,
  "commission_fee": 450
}
```

### 10.5 Trip Endpoints

#### POST /api/trips
**Request:**
```json
{
  "booking_id": 1
}
```
**Response (201):**
```json
{
  "id": 1,
  "booking_id": 1,
  "start_location": "Addis Ababa",
  "destination": "Bahir Dar",
  "trip_status": "ongoing",
  "start_time": "2026-07-15T08:00:00Z"
}
```

#### PATCH /api/trips/{id}/location
**Request:**
```json
{
  "lat": 9.1450,
  "lng": 38.7400,
  "speed": 60.5,
  "heading": 45
}
```

### 10.6 Error Response Format

**Standard Error Response:**
```json
{
  "message": "Error description",
  "errors": {
    "field": ["Error message"]
  }
}
```

**HTTP Status Codes:**
- 200: Success
- 201: Created
- 400: Bad Request (validation error)
- 401: Unauthorized
- 403: Forbidden
- 404: Not Found
- 422: Validation Error
- 500: Internal Server Error

---

## 11. MIGRATION PRIORITY

### 11.1 Phase 1: Core Infrastructure (P0 - Weeks 1-2)

**Priority: Critical - Must be completed first**

| Component | Tasks | Estimated Effort |
|-----------|-------|------------------|
| Project Setup | Spring Boot project, dependencies, build config | 2 days |
| Database Setup | Flyway migrations, entity mapping, repositories | 3 days |
| Authentication | JWT implementation, security config, user management | 3 days |
| Basic API Structure | Controller base classes, DTOs, response wrappers | 2 days |
| Validation | Validation annotations, custom validators, error handling | 2 days |

**Total Phase 1:** 12 days

### 11.2 Phase 2: Core Business Logic (P0 - Weeks 3-4)

**Priority: Critical - Core freight functionality**

| Component | Tasks | Estimated Effort |
|-----------|-------|------------------|
| User Management | CRUD operations, role management, verification | 2 days |
| Vehicle Management | CRUD operations, GPS updates, nearby search | 3 days |
| Cargo Requests | CRUD operations, intracity/intercity logic | 3 days |
| Bidding System | Bid placement, ranking, acceptance, counter-offers | 4 days |
| Booking System | Booking creation, cancellation, status updates | 3 days |

**Total Phase 2:** 15 days

### 11.3 Phase 3: Trip & Payment (P0 - Weeks 5-6)

**Priority: Critical - Trip execution**

| Component | Tasks | Estimated Effort |
|-----------|-------|------------------|
| Trip Management | Trip creation, status updates, location tracking | 3 days |
| Payment Processing | Payment creation, commission calculation | 2 days |
| Multi-stop Trips | Trip stops, sequential enforcement | 3 days |
| Notifications | Database notifications, notification controller | 2 days |

**Total Phase 3:** 10 days

### 11.4 Phase 4: Admin & Fleet (P1 - Weeks 7-8)

**Priority: High - Admin and fleet features**

| Component | Tasks | Estimated Effort |
|-----------|-------|------------------|
| Admin Panel API | Statistics, user management, analytics | 3 days |
| Fleet Management | Fleet dashboard, driver/vehicle management | 3 days |
| Document Management | Document upload, review, verification | 2 days |
| Rating System | Rating submission, driver ratings | 2 days |

**Total Phase 4:** 10 days

### 11.5 Phase 5: Advanced Features (P2 - Weeks 9-10)

**Priority: Medium - AI and advanced routing**

| Component | Tasks | Estimated Effort |
|-----------|-------|------------------|
| AI Integration | FastAPI proxy, fallback logic | 3 days |
| Routing Service | OSRM integration, Nominatim, caching | 3 days |
| Backhaul Recommendations | Recommendation engine, job queue | 3 days |
| Real-time Tracking | WebSocket, TripLocationUpdated event | 3 days |

**Total Phase 5:** 12 days

### 11.6 Phase 6: Testing & Deployment (P0 - Weeks 11-12)

**Priority: Critical - Quality assurance**

| Component | Tasks | Estimated Effort |
|-----------|-------|------------------|
| Unit Testing | Service layer tests, repository tests | 3 days |
| Integration Testing | API tests, end-to-end tests | 3 days |
| Performance Testing | Load testing, optimization | 2 days |
| Deployment | CI/CD setup, staging deployment | 2 days |

**Total Phase 6:** 10 days

### 11.7 Total Estimated Timeline

**Total:** 69 days (~14 weeks)

**Critical Path:** Phase 1 → Phase 2 → Phase 3 → Phase 6

**Parallel Work:** Phase 4 and Phase 5 can be partially parallelized with Phase 3

---

## 12. PROGRESS CHECKLIST

### 12.1 Phase 1: Core Infrastructure

- [ ] Spring Boot project initialized
- [ ] Dependencies configured (Spring Web, Security, Data JPA, Validation, Cache)
- [ ] Build configuration (Maven/Gradle)
- [ ] Database connection configured
- [ ] Flyway migrations created for all tables
- [ ] Entity classes created for all models
- [ ] Repository interfaces created
- [ ] JWT authentication filter implemented
- [ ] Security configuration completed
- [ ] User registration endpoint working
- [ ] User login endpoint working
- [ ] Token validation working
- [ ] Controller base class created
- [ ] DTO structure defined
- [ ] Response wrapper implemented
- [ ] Global exception handler created
- [ ] Validation annotations configured
- [ ] Custom validators implemented

### 12.2 Phase 2: Core Business Logic

- [ ] User CRUD operations
- [ ] Role-based access control
- [ ] Driver verification workflow
- [ ] Vehicle CRUD operations
- [ ] Vehicle GPS update endpoint
- [ ] Nearby vehicle search
- [ ] Vehicle category filtering
- [ ] Cargo request CRUD operations
- [ ] Intercity cargo logic
- [ ] Intracity cargo logic
- [ ] Cargo request filtering by role
- [ ] Bid placement endpoint
- [ ] Bid ranking algorithm
- [ ] Bid acceptance logic
- [ ] Bid rejection logic
- [ ] Counter-offer functionality
- [ ] Booking creation
- [ ] Booking cancellation
- [ ] Commission calculation (10%)

### 12.3 Phase 3: Trip & Payment

- [ ] Trip creation
- [ ] Trip status updates
- [ ] Trip location tracking
- [ ] Route data storage
- [ ] Payment creation
- [ ] Payment status updates
- [ ] Driver net amount calculation
- [ ] Multi-stop trip creation
- [ ] Trip stop management
- [ ] Sequential stop enforcement
- [ ] Notification database table
- [ ] Notification controller
- [ ] Notification marking read/unread

### 12.4 Phase 4: Admin & Fleet

- [ ] Admin statistics endpoint
- [ ] Admin user management
- [ ] Admin driver management
- [ ] Admin payment listing
- [ ] Admin analytics (revenue, routes, cargo)
- [ ] Fleet dashboard endpoint
- [ ] Fleet driver management
- [ ] Fleet vehicle management
- [ ] Fleet dispatch functionality
- [ ] Document upload endpoint
- [ ] Document download endpoint
- [ ] Document review workflow
- [ ] Auto-verification on all docs approved
- [ ] Rating submission
- [ ] Driver rating retrieval
- [ ] Average rating calculation

### 12.5 Phase 5: Advanced Features

- [ ] FastAPI service integration
- [ ] AI proxy endpoints
- [ ] Fallback logic for AI failures
- [ ] OSRM service integration
- [ ] Nominatim service integration
- [ ] Route caching
- [ ] Place search caching
- [ ] Reverse geocoding caching
- [ ] Backhaul recommendation engine
- [ ] Ethiopian city database
- [ ] Scoring algorithm
- [ ] Async job queue
- [ ] WebSocket configuration
- [ ] TripLocationUpdated event
- [ ] Real-time location broadcast

### 12.6 Phase 6: Testing & Deployment

- [ ] Unit tests for services
- [ ] Unit tests for repositories
- [ ] Integration tests for controllers
- [ ] Integration tests for authentication
- [ ] End-to-end test scenarios
- [ ] Load testing
- [ ] Performance optimization
- [ ] CI/CD pipeline setup
- [ ] Staging environment deployment
- [ ] Production deployment preparation

### 12.7 Documentation

- [ ] API documentation (OpenAPI/Swagger)
- [ ] Architecture documentation
- [ ] Deployment guide
- [ ] Configuration guide
- [ ] Troubleshooting guide

---

## APPENDICES

### Appendix A: Technology Stack

**Laravel (Source):**
- PHP 8.x
- Laravel 11.x
- MySQL
- Laravel Sanctum
- Laravel Queue
- Laravel Broadcasting

**Spring Boot (Target):**
- Java 21
- Spring Boot 3.x
- PostgreSQL or MySQL
- Spring Security + JWT
- Spring Data JPA
- Spring Cache
- WebSocket (STOMP)

### Appendix B: Key Libraries

**Spring Boot Dependencies:**
- spring-boot-starter-web
- spring-boot-starter-security
- spring-boot-starter-data-jpa
- spring-boot-starter-validation
- spring-boot-starter-cache
- spring-boot-starter-websocket
- jjwt (Java JWT)
- flyway-core
- postgresql or mysql-connector-java
- lombok

### Appendix C: Configuration Files

**Required Configuration:**
- application.yml (database, security, cache)
- application-prod.yml (production overrides)
- logback-spring.xml (logging)
- flyway.conf (database migrations)

### Appendix D: Environment Variables

**Required Environment Variables:**
- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SECRET`
- `JWT_EXPIRATION`
- `AI_ENGINE_URL`
- `REDIS_URL` (for caching)

---

**End of Migration Workbook**
