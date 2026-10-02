# API Migration Report: Laravel to Spring Boot

**Generated:** July 9, 2026  
**Purpose:** Identify gaps between React frontend API calls and Spring Boot backend endpoints

---

## Executive Summary

The React frontend currently calls **45+ API endpoints** across 8 functional areas. The Spring Boot backend currently implements **only 6 authentication endpoints**. 

**Migration Status:**
- ✅ **Fully Implemented:** 6 endpoints (13%)
- ⚠️ **Partially Implemented:** 0 endpoints (0%)
- ❌ **Missing Completely:** 39+ endpoints (87%)

**Critical Path:** The frontend cannot switch to the Spring Boot backend until freight management, vehicles, tracking, payments, messaging, drivers, AI integration, and admin features are implemented.

---

## ✅ Fully Implemented in Spring Boot

### Authentication (6/6 endpoints)

| HTTP Method | Endpoint | Purpose | Status |
|-------------|-----------|---------|--------|
| POST | `/api/auth/register` | User registration | ✅ Match |
| POST | `/api/auth/login` | User login | ✅ Match |
| POST | `/api/auth/logout` | User logout | ✅ Match |
| GET | `/api/auth/me` | Get current user | ✅ Match |
| PATCH | `/api/auth/me` | Update profile | ✅ Match |
| PATCH | `/api/auth/me/password` | Change password | ✅ Match |

**Notes:**
- Frontend calls `/register` but Spring Boot expects `/api/auth/register`
- Frontend calls `/auth/login` but Spring Boot expects `/api/auth/login`
- Frontend calls `/me` but Spring Boot expects `/api/auth/me`
- **Action Required:** Either update frontend paths or add path aliases in Spring Boot

---

## ❌ Missing Completely

### Freight/Cargo Management (9 endpoints)

| HTTP Method | Endpoint | Purpose | Request Body | Response | Auth |
|-------------|-----------|---------|--------------|----------|------|
| GET | `/freight` | List freight with filters | Query params: status, cargoType, limit | `{ freight: [], total: number }` | Optional |
| GET | `/freight/{id}` | Get freight details | - | Freight object | Optional |
| POST | `/freight` | Create freight | `{ pickup_location, destination, material_type, weight, budget, urgency_level }` | Created freight | Required |
| POST | `/freight/{id}/deliver` | Mark delivery | `{}` | Success | Required |
| POST | `/freight/{id}/confirm-delivery` | Confirm delivery | `{}` | Success | Required |
| GET | `/cargo-requests` | List all cargo requests | - | Cargo requests array | Admin |
| GET | `/cargo-requests/{id}/bids` | Get bids for cargo | - | `{ data: Bid[] }` | Admin/Owner |
| POST | `/cargo-requests/{id}/bids` | Submit bid | `{ vehicle_id, amount, note }` | Created bid | Driver |
| PATCH | `/bids/{id}/accept` | Accept bid | `{}` | Updated bid | Owner |

**Impact:** Critical - core freight functionality completely missing

---

### Vehicles (2 endpoints)

| HTTP Method | Endpoint | Purpose | Request Body | Response | Auth |
|-------------|-----------|---------|--------------|----------|------|
| GET | `/my-vehicles` | Get user's vehicles | - | `{ vehicles: [] }` | Driver |
| POST | `/vehicles` | Create vehicle | `{ truck_type, plate_number, capacity, current_city }` | Created vehicle | Driver |

**Impact:** High - drivers cannot register vehicles

---

### Tracking (3 endpoints)

| HTTP Method | Endpoint | Purpose | Request Body | Response | Auth |
|-------------|-----------|---------|--------------|----------|------|
| GET | `/tracking/{freightId}/latest` | Get latest location | - | Location object | Driver/Owner |
| GET | `/tracking/{freightId}` | Get route history | - | Location[] | Driver/Owner |
| POST | `/tracking` | Update location | `{ freightId, latitude, longitude }` | Success | Driver |

**Impact:** High - live tracking feature unavailable

---

### Payments (4 endpoints)

| HTTP Method | Endpoint | Purpose | Request Body | Response | Auth |
|-------------|-----------|---------|--------------|----------|------|
| GET | `/payments/{freightId}` | Get payment status | - | Payment object with escrowStatus | Owner/Driver |
| POST | `/payments/initialize` | Initialize payment | `{ freightId, amount, provider }` | Payment intent | Owner |
| POST | `/payments/{freightId}/verify` | Verify payment | `{}` | Success | Owner |
| POST | `/payments/{freightId}/release` | Release payment | `{}` | Success | Owner |

**Impact:** Critical - payment escrow system missing

---

### Messages (2 endpoints)

| HTTP Method | Endpoint | Purpose | Request Body | Response | Auth |
|-------------|-----------|---------|--------------|----------|------|
| GET | `/messages/{freightId}` | Get messages | - | `{ messages: [] }` | Driver/Owner |
| POST | `/messages` | Send message | `{ freightId, receiverId, content, type }` | Created message | Driver/Owner |

**Impact:** Medium - communication feature missing

---

### Drivers (3 endpoints)

| HTTP Method | Endpoint | Purpose | Request Body | Response | Auth |
|-------------|-----------|---------|--------------|----------|------|
| GET | `/drivers` | List all drivers | Query params: status, limit | `{ drivers: [], total: number }` | Public |
| GET | `/drivers/{id}/status` | Get driver status | - | Driver status | Public |
| PATCH | `/drivers/{id}/status` | Update driver status | `{ status }` | Success | Admin |

**Impact:** Medium - driver discovery and management missing

---

### AI Engine Integration (3 endpoints)

| HTTP Method | Endpoint | Purpose | Request Body | Response | Auth |
|-------------|-----------|---------|--------------|----------|------|
| GET | `/ai/price-prediction` | Get price prediction | Query: weight, distance_km, cargo_type | `{ prediction: { recommendedPrice, minPrice, maxPrice, confidence, model, pricePerKm, pricePerTon, breakdown } }` | Optional |
| GET | `/ai/vehicle-recommendation` | Get vehicle recommendation | Query: weight, cargo_type, distance_km | `{ recommendation: { truckType, capacityRange, reason, features, riskLevel } }` | Optional |
| POST | `/ai/recommend-truck` | Recommend truck for freight | `{ freight_id, weight, cargo_type, budget }` | `{ matches: [{ driverId, driverName, vehicleTruckType, vehicleCapacity, avgRating, totalDeliveries, matchScore }] }` | Admin/Owner |

**Impact:** High - AI-powered features unavailable (note: AI Engine service exists at separate port 8000)

---

### Admin Dashboard (20+ endpoints)

| HTTP Method | Endpoint | Purpose | Auth |
|-------------|-----------|---------|------|
| GET | `/admin/stats` | Dashboard statistics | Admin |
| GET | `/users` | List all users | Admin |
| POST | `/admin/users` | Create user | Admin |
| PUT | `/admin/users/{id}` | Update user | Admin |
| DELETE | `/admin/users/{id}` | Delete user | Admin |
| GET | `/drivers` | List drivers (admin view) | Admin |
| POST | `/admin/drivers` | Create driver | Admin |
| GET | `/admin/payments` | List payments | Admin |
| GET | `/disputes` | List disputes | Admin |
| PATCH | `/disputes/{id}/resolve` | Resolve dispute | Admin |
| GET | `/admin/escrow` | Escrow overview | Admin |
| GET | `/admin/driver-documents` | List driver documents | Admin |
| PATCH | `/admin/driver-documents/{id}/review` | Approve/reject document | Admin |
| GET | `/driver/documents/{id}/file` | Download document file | Admin |
| GET | `/admin/settings/pricing` | Get pricing settings | Admin |
| PATCH | `/admin/settings/pricing` | Update pricing settings | Admin |
| GET | `/trips` | List trips | Admin |
| GET | `/admin/bookings/unpaid` | List unpaid bookings | Admin |
| POST | `/admin/bookings/{id}/mark-cash-paid` | Mark cash as paid | Admin |
| GET | `/admin/fleet-owners` | List fleet owners | Admin |
| GET | `/admin/analytics/revenue` | Revenue analytics | Admin |
| GET | `/admin/analytics/routes` | Route analytics | Admin |
| GET | `/admin/analytics/cargo` | Cargo analytics | Admin |

**Impact:** Critical - admin panel completely non-functional

---

### Fleet Owner Features (1 endpoint)

| HTTP Method | Endpoint | Purpose | Auth |
|-------------|-----------|---------|------|
| GET | `/admin/fleet-owners` | List fleet owners | Admin |

**Impact:** Low - fleet owner management missing

---

## ⚠️ Path Mismatches

The following endpoints exist in Spring Boot but at different paths:

| Frontend Calls | Spring Boot Expects | Resolution |
|----------------|---------------------|------------|
| `/register` | `/api/auth/register` | Update frontend or add path alias |
| `/auth/login` | `/api/auth/login` | Update frontend or add path alias |
| `/me` | `/api/auth/me` | Update frontend or add path alias |
| `/me` (PATCH) | `/api/auth/me` | Update frontend or add path alias |

---

## Request/Response Format Differences

### Registration

**Frontend sends:**
```json
{
  "full_name": "string",
  "email": "string",
  "phone": "string",
  "password": "string",
  "role": "shipper"
}
```

**Spring Boot RegisterRequest expects:**
```java
- fullName (matches full_name)
- email (matches)
- phone (matches)
- password (matches)
- role (matches)
```

**Status:** ⚠️ Field name mismatch (`full_name` vs `fullName`)

### Login

**Frontend sends:**
```json
{
  "identifier": "string",  // email or phone
  "password": "string"
}
```

**Spring Boot LoginRequest expects:**
```java
- emailOrPhone (matches identifier)
- password (matches)
```

**Status:** ⚠️ Field name mismatch (`identifier` vs `emailOrPhone`)

### Profile Update

**Frontend sends:**
```json
{
  "name": "string",
  "phone": "string",
  "address": "string",
  "business_name": "string"
}
```

**Spring Boot UpdateProfileRequest expects:**
```java
- fullName (not name)
- phone (matches)
- location (not address)
- businessName (matches business_name)
```

**Status:** ⚠️ Multiple field name mismatches

---

## Authentication Requirements

| Endpoint | Frontend Auth | Spring Boot Auth |
|----------|---------------|------------------|
| `/register` | None | None |
| `/auth/login` | None | None |
| `/me` | Bearer token | Bearer token |
| `/me` (PATCH) | Bearer token | Bearer token |
| `/freight/*` | Optional for GET, Required for POST/PUT/DELETE | Not implemented |
| `/vehicles/*` | Required | Not implemented |
| `/tracking/*` | Required | Not implemented |
| `/payments/*` | Required | Not implemented |
| `/messages/*` | Required | Not implemented |
| `/admin/*` | Admin role | Not implemented |

---

## Data Model Gaps

### Missing Entities in Spring Boot

1. **Freight/Cargo** - Core business entity
2. **Vehicle** - Driver's truck registration
3. **Location/Tracking** - GPS coordinates and route history
4. **Payment/Escrow** - Payment processing and escrow
5. **Message** - In-app messaging
6. **Bid/Application** - Driver bids on freight
7. **DriverProfile** - Extended driver information
8. **FleetOwner** - Fleet owner management
9. **Document** - Driver verification documents
10. **Dispute** - Dispute resolution
11. **Trip** - Trip management
12. **Booking** - Booking management
13. **Analytics** - Revenue/route/cargo analytics

### Current Entities in Spring Boot

1. **User** - Basic user entity with roles (SHIPPER, DRIVER, ADMIN, FLEET_OWNER)

---

## Recommended Implementation Priority

### Phase 1 - Critical (Blocker)
1. Freight/Cargo CRUD operations
2. Vehicle management
3. Payment/Escrow system
4. Bidding system

### Phase 2 - High Priority
5. Tracking/location updates
6. Messaging system
7. Driver discovery
8. AI Engine integration (proxy endpoints)

### Phase 3 - Medium Priority
9. Admin dashboard core features
10. Document management
11. Dispute resolution

### Phase 4 - Low Priority
12. Analytics endpoints
13. Fleet owner features
14. Advanced admin features

---

## Technical Notes

### AI Engine Integration
The AI Engine runs as a separate service on port 8000. Spring Boot needs to:
1. Create proxy endpoints to forward requests to AI Engine
2. Handle AI Engine service failures gracefully
3. Add configuration for AI Engine URL

### File Uploads
Document uploads require:
1. Multipart file upload support
2. File storage configuration (local/S3)
3. File serving endpoints with authentication

### Database Schema
Spring Boot uses a new database `ethioloadai_spring`. Required migrations:
1. Freight table
2. Vehicles table
3. Tracking/locations table
4. Payments/escrow table
5. Messages table
6. Bids table
7. Documents table
8. Disputes table
9. Trips table
10. Bookings table
11. Analytics tables

---

## Conclusion

The Spring Boot backend is currently at **13% completion** for frontend API compatibility. The authentication system is functional but requires path alignment. All business logic endpoints (freight, vehicles, payments, tracking, messaging, admin) are missing.

**Estimated Development Effort:** 4-6 weeks for full implementation assuming 1 senior developer.

**Risk Level:** High - Frontend cannot switch until Phase 1 is complete.
