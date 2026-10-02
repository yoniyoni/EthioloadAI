# Authentication Module Migration
## EthioloadAI Laravel to Spring Boot Migration

**Module Name:** Authentication  
**Laravel Version:** 11.x  
**Spring Boot Version:** 3.x (Java 21)  
**Document Version:** 1.0  
**Created:** July 8, 2026  
**Last Updated:** July 8, 2026

---

## TABLE OF CONTENTS

1. [Module Name](#1-module-name)
2. [Business Purpose](#2-business-purpose)
3. [Laravel Analysis](#3-laravel-analysis)
4. [Database](#4-database)
5. [API Contract](#5-api-contract)
6. [Spring Boot Design](#6-spring-boot-design)
7. [Compatibility Checklist](#7-compatibility-checklist)
8. [Testing](#8-testing)
9. [Migration Status](#9-migration-status)

---

## 1. MODULE NAME

**Module:** Authentication

**Description:** User registration, login, logout, profile management, and authentication using Laravel Sanctum tokens

**Priority:** P0 (Critical - must be migrated first)

**Dependencies:** None (foundational module)

**Dependents:** All other modules (all require authentication)

---

## 2. BUSINESS PURPOSE

### 2.1 Problem Statement

**What does the authentication module do?**
The authentication module provides user registration, login, logout, and profile management functionality. It enables users (shippers, drivers, fleet owners, and admins) to authenticate with the system and access protected API endpoints.

### 2.2 Business Value

**Key business benefits:**
- Enables secure access to the logistics platform
- Supports multiple user roles with different permissions
- Provides token-based authentication for API access
- Allows users to manage their profiles and passwords
- Supports driver verification workflow

### 2.3 Key Use Cases

**Primary use cases:**
1. **User Registration**: New users register with phone/email and password
2. **User Login**: Existing users authenticate with phone/email and password
3. **Token Management**: Users receive and use Sanctum tokens for API access
4. **Profile Management**: Users update their profile information
5. **Password Management**: Users change their passwords
6. **Logout**: Users invalidate their current token
7. **Admin Panel Login**: Admins authenticate for the React admin panel

### 2.4 Business Rules

**Key business rules:**
- **Phone Uniqueness**: Phone numbers must be unique across all users
- **Email Uniqueness**: Email addresses must be unique (if provided)
- **Driver Verification**: Drivers start inactive until all 5 documents are admin-approved
- **Immediate Activation**: Shippers and fleet owners are active immediately upon registration
- **Role-Based Access**: Different roles have different permissions (admin, shipper, driver, fleet_owner)
- **Token Expiration**: Tokens do not expire by default (configurable via Sanctum)
- **Rate Limiting**: Login and registration endpoints are rate-limited to prevent abuse

---

## 3. LARAVEL ANALYSIS

### 3.1 Routes

**API Routes:**

| Method | URI | Controller | Method | Middleware | Priority |
|--------|-----|------------|--------|------------|----------|
| POST | /register | AuthController | register | throttle:login | P0 |
| POST | /login | AuthController | login | throttle:login | P0 |
| POST | /auth/login | AdminApiController | login | throttle:login | P0 |
| POST | /logout | AuthController | logout | auth:sanctum | P0 |
| GET | /me | AuthController | me | auth:sanctum | P0 |
| PATCH | /me | AuthController | updateProfile | auth:sanctum | P1 |
| PATCH | /me/password | AuthController | changePassword | auth:sanctum | P1 |

**Route Groups:**
- **Public endpoints** (throttle:login): /register, /login, /auth/login
- **Authenticated endpoints** (auth:sanctum): /logout, /me, /me/*, all other protected routes
- **Admin endpoints** (admin): /admin/*, /users, /drivers, /trips (with AdminMiddleware)

### 3.2 Controllers

**Controllers:**

| Controller | Responsibilities | Key Methods | Dependencies | Spring Equivalent |
|------------|------------------|--------------|--------------|------------------|
| AuthController | User authentication, registration, profile management | register, login, logout, me, changePassword, updateProfile | RegisterRequest, UserResource, Hash, User model | AuthController |
| AdminApiController | Admin panel authentication and user management | login, createUser, updateUser, deleteUser, createDriver, updateDriverStatus | Hash, User model, Request validation | AdminAuthController |

**Controller Details:**

#### AuthController

**Responsibilities:** User authentication, registration, profile management, password changes

**Methods:**
- `register(RegisterRequest $request)` - Creates new user, issues Sanctum token
- `login(Request $request)` - Authenticates user with phone/email and password, issues token
- `logout(Request $request)` - Deletes current access token
- `me(Request $request)` - Returns current authenticated user
- `changePassword(Request $request)` - Changes user password after validating current password
- `updateProfile(Request $request)` - Updates user profile fields

**Dependencies:** RegisterRequest, UserResource, Illuminate\Support\Facades\Hash, User model

**Validation:** 
- RegisterRequest for registration validation
- Inline validation for login, password change, profile update

**Response Format:** JSON with UserResource and token (for register/login), or UserResource only (for me/updateProfile), or success message (for logout/changePassword)

**Special Features:**
- **Flexible login**: Accepts either email or phone as identifier
- **Driver activation logic**: Drivers start inactive, shippers/fleet owners active immediately
- **Token naming**: Creates tokens with names 'api-token' (regular login) and 'admin-panel' (admin login)
- **Profile field mapping**: Maps 'name' to 'full_name' for backward compatibility

#### AdminApiController (login method only)

**Responsibilities:** Admin panel authentication

**Methods:**
- `login(Request $request)` - Authenticates admin with phone/email and password, issues token for admin panel

**Dependencies:** Hash, User model, Request validation

**Validation:** Inline validation for identifier and password

**Response Format:** JSON with token and formatted user data (formatUser method)

**Special Features:**
- **Flexible login**: Accepts either email or phone as identifier
- **Token naming**: Creates token with name 'admin-panel'
- **User formatting**: Uses formatUser helper to shape response for React admin panel

### 3.3 Models

**Models:**

| Model | Table | Key Attributes | Relationships | Spring Entity |
|-------|-------|----------------|----------------|--------------|
| User | users | id, full_name, phone, email, password, role, verification_status, is_active | hasMany: Vehicle, CargoRequest, Booking, DriverDocument, drivers; belongsTo: fleetOwner | UserEntity |
| PersonalAccessToken | personal_access_tokens | id, tokenable_type, tokenable_id, name, token, abilities, last_used_at, expires_at | belongsTo: tokenable (polymorphic) | PersonalAccessTokenEntity |

**Model Details:**

#### User

**Table:** users

**Attributes:**
- `id` (bigint, primary key, auto-increment)
- `full_name` (string, 255)
- `phone` (string, unique)
- `email` (string, unique, nullable)
- `password` (string, hashed)
- `role` (enum: shipper, driver, admin, fleet_owner)
- `fleet_owner_id` (bigint, foreign key to users, nullable)
- `location` (string, nullable)
- `latitude` (decimal, 10,7, nullable)
- `longitude` (decimal, 10,7, nullable)
- `verification_status` (boolean, default false)
- `is_active` (boolean, nullable)
- `remember_token` (string, nullable)
- `created_at` (timestamp)
- `updated_at` (timestamp)

**Relationships:**
- `vehicles()` - hasMany Vehicle
- `cargoRequests()` - hasMany CargoRequest
- `bookings()` - hasMany Booking (as driver_id)
- `documents()` - hasMany DriverDocument
- `drivers()` - hasMany User (as fleet_owner_id)
- `fleetOwner()` - belongsTo User (as fleet_owner_id)

**Accessors/Mutators:**
- `is_admin` - Returns true if role is admin (case-insensitive)
- `is_fleet_owner` - Returns true if role is fleet_owner (case-insensitive)

**Scopes:** None defined

**Traits:**
- HasApiTokens (Laravel Sanctum)
- HasFactory
- Notifiable

**Casts:**
- `email_verified_at` - datetime
- `password` - hashed
- `verification_status` - boolean
- `is_active` - boolean

#### PersonalAccessToken (Laravel Sanctum)

**Table:** personal_access_tokens

**Attributes:**
- `id` (bigint, primary key, auto-increment)
- `tokenable_type` (string, polymorphic type)
- `tokenable_id` (bigint, polymorphic ID)
- `name` (text, token name)
- `token` (string, 64, unique, hashed token)
- `abilities` (text, nullable, JSON)
- `last_used_at` (timestamp, nullable)
- `expires_at` (timestamp, nullable, indexed)
- `created_at` (timestamp)
- `updated_at` (timestamp)

**Relationships:**
- `tokenable()` - belongsTo (polymorphic to User)

**Note:** This is a Laravel Sanctum model that manages API tokens. Tokens are SHA-256 hashed before storage.

### 3.4 Services

**Service Classes:**

None - Authentication logic is implemented directly in controllers using Laravel's built-in authentication and Sanctum.

### 3.5 Middleware

**Middleware:**

| Laravel Middleware | Purpose | Spring Security Equivalent | Priority |
|--------------------|---------|---------------------------|----------|
| auth:sanctum | Sanctum token authentication | JwtAuthenticationFilter | P0 |
| AdminMiddleware | Admin role check | RoleAuthorizationFilter (ROLE_ADMIN) | P0 |
| throttle:login | Rate limiting for login endpoints | RateLimitFilter | P1 |
| throttle:api | Rate limiting for API endpoints | RateLimitFilter | P1 |

**Middleware Details:**

#### auth:sanctum (Laravel Sanctum)

**Purpose:** Authenticates requests using Laravel Sanctum tokens (Bearer tokens in Authorization header)

**Logic:** 
- Extracts Bearer token from Authorization header
- Validates token against personal_access_tokens table
- Sets authenticated user in request context
- Returns 401 if token is invalid or missing

**Spring Equivalent:** JwtAuthenticationFilter or custom Sanctum-compatible token filter

#### AdminMiddleware

**Purpose:** Ensures only admin users can access protected admin endpoints

**Logic:**
```php
$user = $request->user();
if (!$user || !$user->is_admin) {
    return response()->json(['message' => 'Forbidden'], 403);
}
return $next($request);
```

**Spring Equivalent:** @PreAuthorize("hasRole('ADMIN')") or custom RoleAuthorizationFilter

#### throttle:login

**Purpose:** Rate limits login and registration endpoints to prevent brute force attacks

**Logic:** Uses Laravel's rate limiting middleware with default limits

**Spring Equivalent:** RateLimitFilter with bucket4j or Spring Security's rate limiting

#### throttle:api

**Purpose:** Rate limits API endpoints to prevent abuse

**Logic:** Applied to all API routes via api middleware group

**Spring Equivalent:** RateLimitFilter with bucket4j or Spring Security's rate limiting

### 3.6 Validation

**Form Requests:**

| Laravel Request | Purpose | Spring Validator | Priority |
|-----------------|---------|------------------|----------|
| RegisterRequest | User registration | UserRegistrationValidator | P0 |

**Validation Rules:**

#### RegisterRequest

**Validation Rules:**
- `full_name`: required, string, max:255
- `phone`: required, string, unique:users,phone
- `email`: nullable, email, unique:users,email
- `password`: required, string, min:6
- `role`: required, in:shipper,driver,admin,fleet_owner

**Custom Validation:** None

#### Login (Inline Validation)

**Validation Rules:**
- `identifier`: required, string
- `password`: required, string

**Custom Validation:** 
- Determines if identifier is email or phone using FILTER_VALIDATE_EMAIL
- Validates credentials using Hash::check()

#### Change Password (Inline Validation)

**Validation Rules:**
- `current_password`: required, string
- `new_password`: required, string, min:6

**Custom Validation:**
- Validates current password using Hash::check()
- Returns 422 if current password is incorrect

#### Update Profile (Inline Validation)

**Validation Rules:**
- `name`: sometimes, string, max:255
- `full_name`: sometimes, string, max:255
- `phone`: sometimes, string, max:50
- `address`: sometimes, nullable, string, max:255
- `business_name`: sometimes, nullable, string, max:255

**Custom Validation:**
- Maps 'name' to 'full_name' for backward compatibility
- All fields are optional (sometimes validation)

### 3.7 Events

**Events:** None defined for authentication module

### 3.8 Notifications

**Notifications:** None defined for authentication module

### 3.9 External APIs

**External API Calls:** None - Authentication module does not call external APIs

---

## 4. DATABASE

### 4.1 Tables

**Tables:**

| Table | Purpose | Rows (approx) | Spring Entity |
|-------|---------|---------------|--------------|
| users | User accounts | 1000+ | UserEntity |
| personal_access_tokens | Sanctum API tokens | 2000+ | PersonalAccessTokenEntity |
| password_reset_tokens | Password reset tokens (unused) | 0 | PasswordResetTokenEntity |
| sessions | Laravel sessions (unused for API) | 0 | SessionEntity |

**Table Details:**

#### users

**Purpose:** Stores user account information

**Columns:**

| Column | Type | Constraints | Default | Nullable | Index |
|--------|------|-------------|---------|----------|-------|
| id | bigint | PRIMARY KEY, AUTO_INCREMENT | - | NO | PRIMARY |
| full_name | varchar(255) | - | - | NO | - |
| phone | varchar(255) | UNIQUE | - | NO | UNIQUE |
| email | varchar(255) | UNIQUE | - | NO | UNIQUE |
| password | varchar(255) | - | - | NO | - |
| role | enum('shipper','driver','admin') | - | - | NO | - |
| location | varchar(255) | - | - | YES | - |
| latitude | decimal(10,7) | - | - | YES | - |
| longitude | decimal(10,7) | - | - | YES | - |
| verification_status | boolean | - | false | NO | - |
| remember_token | varchar(100) | - | - | YES | - |
| created_at | timestamp | - | CURRENT_TIMESTAMP | NO | - |
| updated_at | timestamp | - | CURRENT_TIMESTAMP | NO | - |

**Primary Key:** id

**Foreign Keys:** None (fleet_owner_id is referenced but not defined as foreign key in migration)

**Note:** The migration shows role enum only includes 'shipper', 'driver', 'admin' but code also uses 'fleet_owner'. This is a discrepancy that needs investigation.

#### personal_access_tokens

**Purpose:** Stores Laravel Sanctum API tokens

**Columns:**

| Column | Type | Constraints | Default | Nullable | Index |
|--------|------|-------------|---------|----------|-------|
| id | bigint | PRIMARY KEY, AUTO_INCREMENT | - | NO | PRIMARY |
| tokenable_type | varchar(255) | - | - | NO | INDEX (morphs) |
| tokenable_id | bigint | - | - | NO | INDEX (morphs) |
| name | text | - | - | NO | - |
| token | varchar(64) | UNIQUE | - | NO | UNIQUE |
| abilities | text | - | - | YES | - |
| last_used_at | timestamp | - | - | YES | - |
| expires_at | timestamp | - | - | YES | INDEX |
| created_at | timestamp | - | CURRENT_TIMESTAMP | NO | - |
| updated_at | timestamp | - | CURRENT_TIMESTAMP | NO | - |

**Primary Key:** id

**Foreign Keys:** None (polymorphic relationship via tokenable_type/tokenable_id)

**Indexes:**
- tokenable_type, tokenable_id (composite index for morphs)
- token (unique)
- expires_at (index)

#### password_reset_tokens

**Purpose:** Stores password reset tokens (currently unused)

**Columns:**

| Column | Type | Constraints | Default | Nullable | Index |
|--------|------|-------------|---------|----------|-------|
| email | varchar(255) | PRIMARY KEY | - | NO | PRIMARY |
| token | varchar(255) | - | - | NO | - |
| created_at | timestamp | - | - | YES | - |

**Primary Key:** email

**Note:** This table is created but password reset functionality is not implemented in the current codebase.

#### sessions

**Purpose:** Stores Laravel sessions (unused for API authentication)

**Columns:**

| Column | Type | Constraints | Default | Nullable | Index |
|--------|------|-------------|---------|----------|-------|
| id | varchar(255) | PRIMARY KEY | - | NO | PRIMARY |
| user_id | bigint | FOREIGN KEY | - | YES | INDEX |
| ip_address | varchar(45) | - | - | YES | - |
| user_agent | text | - | - | YES | - |
| payload | longtext | - | - | NO | - |
| last_activity | integer | - | - | NO | INDEX |

**Primary Key:** id

**Foreign Keys:** user_id → users.id

**Note:** This table is created but session-based authentication is not used for API endpoints.

### 4.2 Relationships

**Relationship Diagram:**

```
User (1) --< hasMany >-- (N) Vehicle
User (1) --< hasMany >-- (N) CargoRequest
User (1) --< hasMany >-- (N) Booking (as driver_id)
User (1) --< hasMany >-- (N) DriverDocument
User (1) --< hasMany >-- (N) User (as fleet_owner_id)
User (N) --< belongsTo >-- (1) User (as fleet_owner_id)
User (1) --< hasMany >-- (N) PersonalAccessToken (via Sanctum)
```

**Relationship Details:**

| From | To | Type | On Delete | On Update |
|------|-----|------|-----------|-----------|
| users.id | vehicles.user_id | One-to-Many | CASCADE | CASCADE |
| users.id | cargo_requests.user_id | One-to-Many | CASCADE | CASCADE |
| users.id | bookings.driver_id | One-to-Many | CASCADE | CASCADE |
| users.id | driver_documents.user_id | One-to-Many | CASCADE | CASCADE |
| users.id | users.fleet_owner_id | One-to-Many | CASCADE | CASCADE |
| users.fleet_owner_id | users.id | Many-to-One | SET NULL | CASCADE |
| users.id | personal_access_tokens.tokenable_id | One-to-Many | CASCADE | CASCADE |

### 4.3 Constraints

**Unique Constraints:**
- users.phone - UNIQUE
- users.email - UNIQUE
- personal_access_tokens.token - UNIQUE

**Check Constraints:** None defined

**Foreign Key Constraints:**
- sessions.user_id → users.id (CASCADE)

### 4.4 Indexes

**Indexes:**

| Index | Columns | Type | Unique | Purpose |
|-------|---------|------|--------|---------|
| PRIMARY | users.id | B-tree | yes | Primary key |
| users_phone_unique | users.phone | B-tree | yes | Phone uniqueness |
| users_email_unique | users.email | B-tree | yes | Email uniqueness |
| PRIMARY | personal_access_tokens.id | B-tree | yes | Primary key |
| personal_access_tokens_tokenable_type_tokenable_id_index | tokenable_type, tokenable_id | B-tree | no | Polymorphic relationship |
| personal_access_tokens_token_unique | token | B-tree | yes | Token uniqueness |
| personal_access_tokens_expires_at_index | expires_at | B-tree | no | Token expiration queries |
| PRIMARY | password_reset_tokens.email | B-tree | yes | Primary key |
| PRIMARY | sessions.id | B-tree | yes | Primary key |
| sessions_user_id_index | user_id | B-tree | no | User session lookup |
| sessions_last_activity_index | last_activity | B-tree | no | Session cleanup |

**Index Details:**

#### users_phone_unique

**Columns:** phone

**Type:** B-tree

**Unique:** yes

**Purpose:** Enforce phone number uniqueness across all users

**Usage:** Registration validation, login lookup

#### users_email_unique

**Columns:** email

**Type:** B-tree

**Unique:** yes

**Purpose:** Enforce email uniqueness across all users

**Usage:** Registration validation, login lookup

#### personal_access_tokens_token_unique

**Columns:** token

**Type:** B-tree

**Unique:** yes

**Purpose:** Enforce token uniqueness

**Usage:** Token validation during authentication

#### personal_access_tokens_expires_at_index

**Columns:** expires_at

**Type:** B-tree

**Unique:** no

**Purpose:** Enable efficient queries for expired token cleanup

**Usage:** Token expiration checks, cleanup jobs

---

## 5. API CONTRACT

### 5.1 Request Format

#### POST /register

**Method:** POST

**Endpoint:** `/api/register`

**Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "full_name": "John Doe",
  "phone": "+251911234567",
  "email": "john@example.com",
  "password": "password123",
  "role": "shipper"
}
```

**Request Validation:**
- `full_name`: required, string, max:255
- `phone`: required, string, unique:users,phone
- `email`: nullable, email, unique:users,email
- `password`: required, string, min:6
- `role`: required, in:shipper,driver,admin,fleet_owner

#### POST /login

**Method:** POST

**Endpoint:** `/api/login`

**Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "identifier": "+251911234567",
  "password": "password123"
}
```

**Request Validation:**
- `identifier`: required, string (can be email or phone)
- `password`: required, string

**Note:** identifier is automatically detected as email or phone using FILTER_VALIDATE_EMAIL

#### POST /auth/login (Admin Panel)

**Method:** POST

**Endpoint:** `/api/auth/login`

**Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "identifier": "admin@example.com",
  "password": "admin123"
}
```

**Request Validation:**
- `identifier`: required, string (can be email or phone)
- `password`: required, string

**Note:** identifier is automatically detected as email or phone using FILTER_VALIDATE_EMAIL

#### POST /logout

**Method:** POST

**Endpoint:** `/api/logout`

**Headers:**
```
Content-Type: application/json
Authorization: Bearer {token}
```

**Request Body:** None

#### GET /me

**Method:** GET

**Endpoint:** `/api/me`

**Headers:**
```
Authorization: Bearer {token}
```

**Request Body:** None

#### PATCH /me

**Method:** PATCH

**Endpoint:** `/api/me`

**Headers:**
```
Content-Type: application/json
Authorization: Bearer {token}
```

**Request Body:**
```json
{
  "name": "John Updated",
  "full_name": "John Updated",
  "phone": "+251911234568",
  "address": "Addis Ababa, Ethiopia",
  "business_name": "John's Business"
}
```

**Request Validation:**
- `name`: sometimes, string, max:255
- `full_name`: sometimes, string, max:255
- `phone`: sometimes, string, max:50
- `address`: sometimes, nullable, string, max:255
- `business_name`: sometimes, nullable, string, max:255

**Note:** All fields are optional. 'name' is mapped to 'full_name' for backward compatibility.

#### PATCH /me/password

**Method:** PATCH

**Endpoint:** `/api/me/password`

**Headers:**
```
Content-Type: application/json
Authorization: Bearer {token}
```

**Request Body:**
```json
{
  "current_password": "password123",
  "new_password": "newpassword123"
}
```

**Request Validation:**
- `current_password`: required, string
- `new_password`: required, string, min:6

### 5.2 Response Format

#### POST /register - Success Response (201 Created)

```json
{
  "user": {
    "id": 1,
    "full_name": "John Doe",
    "phone": "+251911234567",
    "email": "john@example.com",
    "role": "shipper",
    "location": null,
    "verification_status": true,
    "is_active": true,
    "created_at": "2026-07-08T12:00:00.000000Z",
    "updated_at": "2026-07-08T12:00:00.000000Z"
  },
  "token": "1|abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890ab"
}
```

#### POST /login - Success Response (200 OK)

```json
{
  "user": {
    "id": 1,
    "full_name": "John Doe",
    "phone": "+251911234567",
    "email": "john@example.com",
    "role": "shipper",
    "location": null,
    "verification_status": true,
    "is_active": true,
    "created_at": "2026-07-08T12:00:00.000000Z",
    "updated_at": "2026-07-08T12:00:00.000000Z"
  },
  "token": "1|abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890ab"
}
```

#### POST /auth/login - Success Response (200 OK)

```json
{
  "token": "1|abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890ab",
  "user": {
    "id": 1,
    "name": "John Doe",
    "email": "john@example.com",
    "phone": "+251911234567",
    "role": "admin",
    "isVerified": true,
    "isActive": true,
    "createdAt": "2026-07-08T12:00:00.000000Z",
    "updatedAt": "2026-07-08T12:00:00.000000Z"
  }
}
```

**Note:** Admin login uses formatUser() which returns camelCase fields (isVerified, isActive, createdAt, updatedAt) instead of snake_case (verification_status, is_active, created_at, updated_at).

#### POST /logout - Success Response (200 OK)

```json
{
  "message": "Logged out successfully"
}
```

#### GET /me - Success Response (200 OK)

```json
{
  "id": 1,
  "full_name": "John Doe",
  "phone": "+251911234567",
  "email": "john@example.com",
  "role": "shipper",
  "location": null,
  "verification_status": true,
  "is_active": true,
  "created_at": "2026-07-08T12:00:00.000000Z",
  "updated_at": "2026-07-08T12:00:00.000000Z"
}
```

#### PATCH /me - Success Response (200 OK)

```json
{
  "id": 1,
  "full_name": "John Updated",
  "phone": "+251911234568",
  "email": "john@example.com",
  "role": "shipper",
  "location": null,
  "verification_status": true,
  "is_active": true,
  "created_at": "2026-07-08T12:00:00.000000Z",
  "updated_at": "2026-07-08T12:01:00.000000Z"
}
```

#### PATCH /me/password - Success Response (200 OK)

```json
{
  "success": true,
  "message": "Password changed successfully."
}
```

#### Error Response (401 Unauthorized) - Invalid Credentials

```json
{
  "message": "Invalid credentials. Check your email/phone and password."
}
```

**Note:** Admin login returns different error format:

```json
{
  "error": "Invalid credentials"
}
```

#### Error Response (422 Unprocessable Entity) - Validation Error

```json
{
  "message": "The phone has already been taken.",
  "errors": {
    "phone": [
      "The phone has already been taken."
    ]
  }
}
```

#### Error Response (422 Unprocessable Entity) - Incorrect Current Password

```json
{
  "message": "Current password is incorrect."
}
```

#### Error Response (403 Forbidden) - Admin Access Denied

```json
{
  "message": "Forbidden"
}
```

### 5.3 Status Codes

| Status Code | Meaning | Usage |
|-------------|---------|-------|
| 200 | OK | Successful login, logout, me, updateProfile, changePassword |
| 201 | Created | Successful registration |
| 401 | Unauthorized | Invalid credentials, missing/invalid token |
| 403 | Forbidden | Admin access denied |
| 422 | Unprocessable Entity | Validation errors, incorrect current password |

### 5.4 Validation Rules

**Field Validation:**

| Field | Type | Required | Min | Max | Pattern | Format |
|-------|------|----------|-----|-----|---------|--------|
| full_name | string | yes | - | 255 | - | - |
| phone | string | yes | - | 255 | - | - |
| email | string | no | - | 255 | email | email |
| password | string | yes | 6 | - | - | - |
| role | string | yes | - | - | - | enum:shipper,driver,admin,fleet_owner |
| identifier | string | yes | - | - | - | - |
| current_password | string | yes (for changePassword) | - | - | - | - |
| new_password | string | yes (for changePassword) | 6 | - | - | - |

**Business Validation:**
- **Phone uniqueness**: Phone must be unique across all users
- **Email uniqueness**: Email must be unique if provided
- **Role validation**: Role must be one of: shipper, driver, admin, fleet_owner
- **Driver activation**: Drivers start with verification_status=false and is_active=false
- **Shipper/fleet owner activation**: Shippers and fleet owners start with verification_status=true and is_active=true
- **Password verification**: Current password must match before changing password
- **Identifier detection**: Login identifier is automatically detected as email or phone

---

## 6. SPRING BOOT DESIGN

**Note:** This section is intentionally left blank as per instructions. Spring Boot design will be completed after authentication strategy decision (see MIGRATION_DECISIONS.md).

---

## 7. COMPATIBILITY CHECKLIST

### 7.1 React Frontend

**API Contract Compatibility:**
- [ ] Request format matches React expectations
- [ ] Response format matches React expectations
- [ ] Status codes match React expectations
- [ ] Error responses match React expectations
- [ ] Field names match React expectations (snake_case vs camelCase)
- [ ] Date formats match React expectations
- [ ] Pagination format matches React expectations

**Authentication Compatibility:**
- [ ] Token format matches React expectations
- [ ] Token validation works with React
- [ ] Refresh token flow works with React

**Critical Compatibility Issues:**
- **Field naming inconsistency**: UserResource returns snake_case (verification_status, is_active, created_at, updated_at) but formatUser() returns camelCase (isVerified, isActive, createdAt, updatedAt). React frontend may expect one format consistently.
- **Admin login response format**: Admin login returns different response structure than regular login (formatUser vs UserResource). React admin panel may depend on this specific format.

**Testing:**
- [ ] Manual testing with React frontend
- [ ] Automated API tests with React frontend
- [ ] Regression testing with React frontend

**Notes:**
- React frontend uses camelCase for most fields but may accept snake_case
- Admin panel specifically uses formatUser() response format
- Must verify which format React frontend expects for each endpoint

### 7.2 Flutter Application

**API Contract Compatibility:**
- [ ] Request format matches Flutter expectations
- [ ] Response format matches Flutter expectations
- [ ] Status codes match Flutter expectations
- [ ] Error responses match Flutter expectations
- [ ] Field names match Flutter expectations
- [ ] Date formats match Flutter expectations
- [ ] Pagination format matches Flutter expectations

**Authentication Compatibility:**
- [ ] Token format matches Flutter expectations
- [ ] Token validation works with Flutter
- [ ] Refresh token flow works with Flutter

**Critical Compatibility Issues:**
- **Field naming inconsistency**: Same as React - snake_case vs camelCase inconsistency
- **Token format**: Laravel Sanctum tokens use format "1|{hashed_token}" which may differ from standard JWT format

**Testing:**
- [ ] Manual testing with Flutter app
- [ ] Automated API tests with Flutter app
- [ ] Regression testing with Flutter app

**Notes:**
- Flutter app may expect standard JWT format
- Laravel Sanctum tokens are not JWT-compatible by default
- Must verify Flutter app's token handling expectations

### 7.3 FastAPI AI Service

**API Contract Compatibility:**
- [ ] Request format to AI service matches expectations
- [ ] Response format from AI service matches expectations
- [ ] Error handling from AI service works correctly
- [ ] Timeout configuration is appropriate
- [ ] Retry logic is implemented

**Integration:**
- [ ] AI service client is configured correctly
- [ ] Fallback logic is implemented
- [ ] Caching strategy is appropriate

**Critical Compatibility Issues:**
- None - Authentication module does not integrate with FastAPI AI service

**Testing:**
- [ ] Manual testing with AI service
- [ ] Automated integration tests with AI service
- [ ] Error scenario testing with AI service

**Notes:**
- Authentication module is independent of AI service
- No compatibility issues expected

### 7.4 PostgreSQL

**Schema Compatibility:**
- [ ] Table structure matches Laravel schema
- [ ] Column types match Laravel schema
- [ ] Constraints match Laravel schema
- [ ] Indexes match Laravel schema
- [ ] Relationships match Laravel schema
- [ ] Default values match Laravel schema

**Data Migration:**
- [ ] Existing data is compatible
- [ ] Data migration script is tested
- [ ] Rollback plan is documented

**Flyway Migrations:**
- [ ] Flyway migration script is created
- [ ] Migration script is tested
- [ ] Migration script is reversible

**Critical Compatibility Issues:**
- **Role enum discrepancy**: Migration shows role enum as ('shipper','driver','admin') but code uses 'fleet_owner'. This is a schema inconsistency that must be resolved.
- **Missing foreign key**: fleet_owner_id is referenced in User model but not defined as foreign key in migration. Should be added for data integrity.
- **Missing is_active column**: User model uses is_active but migration does not include this column. This is a schema inconsistency.

**Testing:**
- [ ] Migration testing on development database
- [ ] Migration testing on staging database
- [ ] Data validation after migration

**Notes:**
- Schema inconsistencies must be resolved before migration
- Fleet owner role support appears to be incomplete in schema
- is_active column may have been added via a separate migration

### 7.5 OpenStreetMap / OSRM

**API Contract Compatibility:**
- N/A - Authentication module does not integrate with OSRM or Nominatim

**Integration:**
- N/A

**Critical Compatibility Issues:**
- None

**Testing:**
- N/A

**Notes:**
- Authentication module is independent of OSRM/Nominatim

---

## 8. TESTING

### 8.1 Unit Tests

**Service Layer Tests:**

| Test Case | Description | Status |
|-----------|-------------|--------|
| [Test 1] | [Description] | [ ] |
| [Test 2] | [Description] | [ ] |
| [Test 3] | [Description] | [ ] |

**Repository Tests:**

| Test Case | Description | Status |
|-----------|-------------|--------|
| [Test 1] | [Description] | [ ] |
| [Test 2] | [Description] | [ ] |
| [Test 3] | [Description] | [ ] |

**Mapper Tests:**

| Test Case | Description | Status |
|-----------|-------------|--------|
| [Test 1] | [Description] | [ ] |
| [Test 2] | [Description] | [ ] |
| [Test 3] | [Description] | [ ] |

**Test Coverage Target:** 90%

**Actual Coverage:** [To be measured]

### 8.2 Integration Tests

**Database Integration Tests:**

| Test Case | Description | Status |
|-----------|-------------|--------|
| [Test 1] | [Description] | [ ] |
| [Test 2] | [Description] | [ ] |
| [Test 3] | [Description] | [ ] |

**External API Integration Tests:**

| Test Case | Description | Status |
|-----------|-------------|--------|
| [Test 1] | [Description] | [ ] |
| [Test 2] | [Description] | [ ] |
| [Test 3] | [Description] | [ ] |

**Security Integration Tests:**

| Test Case | Description | Status |
|-----------|-------------|--------|
| [Test 1] | [Description] | [ ] |
| [Test 2] | [Description] | [ ] |
| [Test 3] | [Description] | [ ] |

### 8.3 API Tests

**Endpoint Tests:**

| Endpoint | Method | Test Cases | Status |
|----------|--------|------------|--------|
| /register | POST | [Count] | [ ] |
| /login | POST | [Count] | [ ] |
| /auth/login | POST | [Count] | [ ] |
| /logout | POST | [Count] | [ ] |
| /me | GET | [Count] | [ ] |
| /me | PATCH | [Count] | [ ] |
| /me/password | PATCH | [Count] | [ ] |

**Validation Tests:**

| Test Case | Description | Status |
|-----------|-------------|--------|
| [Test 1] | [Description] | [ ] |
| [Test 2] | [Description] | [ ] |
| [Test 3] | [Description] | [ ] |

**Error Handling Tests:**

| Test Case | Description | Status |
|-----------|-------------|--------|
| [Test 1] | [Description] | [ ] |
| [Test 2] | [Description] | [ ] |
| [Test 3] | [Description] | [ ] |

### 8.4 Manual Verification

**React Frontend Verification:**

| Feature | Test Steps | Expected Result | Status |
|---------|------------|-----------------|--------|
| [Feature 1] | [Steps] | [Result] | [ ] |
| [Feature 2] | [Steps] | [Result] | [ ] |
| [Feature 3] | [Steps] | [Result] | [ ] |

**Flutter Application Verification:**

| Feature | Test Steps | Expected Result | Status |
|---------|------------|-----------------|--------|
| [Feature 1] | [Steps] | [Result] | [ ] |
| [Feature 2] | [Steps] | [Result] | [ ] |
| [Feature 3] | [Steps] | [Result] | [ ] |

**Performance Verification:**

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| [Metric 1] | [Target] | [Actual] | [ ] |
| [Metric 2] | [Target] | [Actual] | [ ] |
| [Metric 3] | [Target] | [Actual] | [ ] |

---

## 9. MIGRATION STATUS

### 9.1 Overall Status

**Current Status:** Analysis Complete

**Completion Percentage:** 30%

**Started Date:** July 8, 2026

**Completed Date:** [Pending]

**Estimated Completion:** [Pending authentication strategy decision]

### 9.2 Phase Status

**Analysis Phase:**
- [x] Laravel analysis complete
- [x] Database analysis complete
- [x] API contract analysis complete
- [x] Dependencies identified
- [x] Risks documented

**Design Phase:**
- [ ] Spring Boot design complete (blocked by authentication strategy decision)
- [ ] Package structure defined
- [ ] DTOs designed
- [ ] Entities designed
- [ ] Repositories designed
- [ ] Services designed
- [ ] Controllers designed
- [ ] Security design complete

**Implementation Phase:**
- [ ] Entities implemented
- [ ] Repositories implemented
- [ ] DTOs implemented
- [ ] Mappers implemented
- [ ] Services implemented
- [ ] Controllers implemented
- [ ] Exception handling implemented
- [ ] Security implemented

**Testing Phase:**
- [ ] Unit tests complete
- [ ] Integration tests complete
- [ ] API tests complete
- [ ] Manual verification complete

**Approval Phase:**
- [ ] Code review complete
- [ ] React frontend verified
- [ ] Flutter application verified
- [ ] Performance verified
- [ ] Migration approved

### 9.3 Blockers

**Current Blockers:**
- **Authentication strategy decision**: JWT vs Laravel Sanctum compatibility must be decided before implementation (see MIGRATION_DECISIONS.md)
- **Schema inconsistencies**: Role enum and is_active column discrepancies must be resolved
- **Frontend compatibility**: Field naming format (snake_case vs camelCase) must be verified with React and Flutter teams

**Resolved Blockers:**
- None

### 9.4 Risks

**Identified Risks:**

| Risk | Impact | Probability | Mitigation | Status |
|------|--------|-------------|------------|--------|
| Schema inconsistency (role enum) | High | High | Add fleet_owner to enum in migration before Spring Boot implementation | Open |
| Schema inconsistency (is_active column) | High | High | Verify if column exists via separate migration, add if missing | Open |
| Field naming format mismatch | Medium | Medium | Verify with React/Flutter teams which format they expect | Open |
| Token format incompatibility | High | High | Evaluate JWT vs Sanctum compatibility with Flutter app | Open |
| Admin panel response format dependency | Medium | Medium | Preserve formatUser() response format for admin login | Open |
| Missing foreign key constraint | Low | Low | Add foreign key constraint for fleet_owner_id | Open |

### 9.5 Dependencies

**Module Dependencies:**
- None (foundational module)

**External Dependencies:**
- None

### 9.6 Notes

**Implementation Notes:**
- Laravel Sanctum uses SHA-256 hashed tokens, not JWT
- Tokens do not expire by default (configurable via Sanctum)
- Admin panel uses different response format (formatUser vs UserResource)
- Rate limiting is applied to login/registration endpoints
- Driver verification workflow requires document approval before activation

**Testing Notes:**
- Must test both regular login and admin login flows
- Must verify token validation with both React and Flutter apps
- Must test driver activation workflow

**Deployment Notes:**
- Authentication is foundational - must be migrated first
- Dual deployment (Laravel + Spring Boot) will require token sharing or migration strategy
- Database schema must be synchronized before dual deployment

**Lessons Learned:**
- Schema inconsistencies exist between migration and code
- Response format is not consistent across endpoints
- Token format may not be compatible with Flutter app expectations

---

## AUTHENTICATION MIGRATION READINESS REPORT

### What Can Be Migrated Safely

**Low Risk Components:**
1. **User model structure** - Core attributes are well-defined
2. **Registration validation rules** - Clear validation requirements
3. **Login logic** - Simple credential validation with phone/email detection
4. **Password change logic** - Straightforward current password verification
5. **Profile update logic** - Simple field updates with optional fields
6. **Logout logic** - Simple token deletion

**Medium Risk Components:**
1. **Token management** - Requires authentication strategy decision (JWT vs Sanctum compatibility)
2. **Rate limiting** - Can be implemented with Spring Security rate limiting
3. **Admin middleware** - Can be implemented with @PreAuthorize annotations

### What Requires Architectural Decisions

**Critical Decisions Required:**

1. **Authentication Strategy** (see MIGRATION_DECISIONS.md)
   - **Option A**: Maintain Laravel Sanctum token compatibility during migration
   - **Option B**: Adopt JWT after evaluating impact on React frontend and Flutter application
   - **Decision Impact**: Affects token format, validation, and frontend integration

2. **Schema Inconsistency Resolution**
   - **Role enum**: Migration shows ('shipper','driver','admin') but code uses 'fleet_owner'
   - **is_active column**: Model uses is_active but migration does not include it
   - **Decision Required**: Add missing enum value and column before Spring Boot implementation

3. **Response Format Standardization**
   - **Field naming**: UserResource uses snake_case, formatUser uses camelCase
   - **Decision Required**: Standardize on one format or maintain both for compatibility

4. **Dual Deployment Strategy**
   - **Token sharing**: How to share tokens between Laravel and Spring Boot during migration
   - **Decision Required**: Implement token validation in both systems or migrate users incrementally

### Open Questions

1. **Flutter Token Format**: Does the Flutter app expect standard JWT format or Laravel Sanctum format?
2. **React Field Naming**: Does React expect snake_case or camelCase for user fields?
3. **is_active Column**: Does the is_active column exist in production via a separate migration?
4. **Fleet Owner Role**: Is fleet_owner role fully implemented in production or still in development?
5. **Admin Panel Dependency**: Does the React admin panel depend specifically on formatUser() response format?
6. **Token Expiration**: Are there any token expiration requirements not reflected in Sanctum config?

### Risks

**High Priority Risks:**

1. **Schema Inconsistency Risk**
   - **Risk**: Role enum missing 'fleet_owner' value
   - **Impact**: Fleet owner registration will fail
   - **Mitigation**: Add 'fleet_owner' to enum before Spring Boot implementation

2. **Token Format Incompatibility Risk**
   - **Risk**: Flutter app may not support Laravel Sanctum token format
   - **Impact**: Flutter app authentication will fail
   - **Mitigation**: Evaluate Flutter app token handling before authentication strategy decision

3. **Field Naming Mismatch Risk**
   - **Risk**: React/Flutter may expect different field naming conventions
   - **Impact**: Frontend may fail to parse user responses
   - **Mitigation**: Verify field naming expectations with frontend teams

**Medium Priority Risks:**

1. **Admin Panel Dependency Risk**
   - **Risk**: React admin panel may depend on specific formatUser() response format
   - **Impact**: Admin panel may break after migration
   - **Mitigation**: Preserve formatUser() response format for admin login endpoint

2. **Dual Deployment Complexity Risk**
   - **Risk**: Managing two authentication systems during migration
   - **Impact**: Increased deployment complexity and potential for user confusion
   - **Mitigation**: Implement clear migration strategy and user communication plan

### Recommendations

**Immediate Actions (Before Implementation):**

1. **Resolve Schema Inconsistencies**
   - Add 'fleet_owner' to role enum in users table
   - Verify and add is_active column if missing
   - Add foreign key constraint for fleet_owner_id
   - Test changes in development environment

2. **Authentication Strategy Decision**
   - Evaluate Flutter app token format requirements
   - Evaluate React frontend token handling
   - Make authentication strategy decision (Option A or B from MIGRATION_DECISIONS.md)
   - Document decision and rationale

3. **Frontend Compatibility Verification**
   - Verify React frontend field naming expectations (snake_case vs camelCase)
   - Verify Flutter app token format expectations
   - Verify admin panel response format dependencies
   - Document any required format preservation

4. **Response Format Standardization**
   - Decide whether to standardize on snake_case or camelCase
   - If standardizing, update all endpoints consistently
   - If preserving both, document which endpoints use which format
   - Update API documentation accordingly

**Implementation Recommendations:**

1. **Preserve Critical Formats**
   - Preserve formatUser() response format for admin login endpoint
   - Preserve UserResource format for regular user endpoints
   - Preserve token format if Option A (Sanctum compatibility) is chosen

2. **Implement Gradual Migration**
   - Start with registration and login endpoints
   - Test thoroughly with both React and Flutter
   - Gradually migrate profile management endpoints
   - Maintain Laravel backend as fallback during migration

3. **Comprehensive Testing**
   - Test all authentication flows with both frontend applications
   - Test token validation and refresh flows
   - Test error handling and validation
   - Test rate limiting
   - Test admin panel authentication

4. **Monitoring and Rollback**
   - Implement comprehensive logging for authentication events
   - Monitor authentication success/failure rates
   - Prepare rollback plan in case of issues
   - Maintain Laravel backend until Spring Boot is fully validated

**Long-term Recommendations:**

1. **Consider JWT Migration**
   - Evaluate JWT adoption after initial migration is complete
   - JWT provides better statelessness and microservices compatibility
   - JWT is industry standard for API authentication

2. **Implement Token Refresh**
   - Add token refresh mechanism for better security
   - Implement token expiration policies
   - Add token revocation support

3. **Enhance Security**
   - Implement multi-factor authentication for admin users
   - Add account lockout after failed login attempts
   - Implement password strength requirements
   - Add audit logging for authentication events

---

## APPENDICES

### Appendix A: References

**Laravel Code References:**
- backend/routes/api.php - Authentication routes
- backend/app/Http/Controllers/Api/AuthController.php - Main authentication controller
- backend/app/Http/Controllers/Api/AdminApiController.php - Admin authentication controller
- backend/app/Models/User.php - User model
- backend/app/Http/Resources/UserResource.php - User resource transformer
- backend/app/Http/Requests/RegisterRequest.php - Registration validation
- backend/app/Http/Middleware/AdminMiddleware.php - Admin role check
- backend/database/migrations/0001_01_01_000000_create_users_table.php - Users table migration
- backend/database/migrations/2026_05_15_082328_create_personal_access_tokens_table.php - Sanctum tokens migration
- backend/config/sanctum.php - Sanctum configuration

**Spring Boot Code References:**
- None - Spring Boot implementation pending authentication strategy decision

### Appendix B: Meeting Notes

**Design Review Meeting:**
- **Date:** [Pending]
- **Attendees:** [Pending]
- **Decisions:** [Pending]
- **Action Items:** [Pending]

**Code Review Meeting:**
- **Date:** [Pending]
- **Attendees:** [Pending]
- **Decisions:** [Pending]
- **Action Items:** [Pending]

### Appendix C: Change Log

| Date | Version | Changes | Author |
|------|---------|---------|--------|
| 2026-07-08 | 1.0 | Initial authentication module analysis | Cascade |

---

**Document Status:** Active  
**Last Reviewed:** July 8, 2026  
**Next Review:** After authentication strategy decision  
**Owner:** [To be assigned]  
**Reviewers:** [To be assigned]
