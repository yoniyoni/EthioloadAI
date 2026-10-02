# Authentication Design Blueprint
## EthioloadAI Laravel to Spring Boot Migration

**Module:** Authentication  
**Laravel Version:** 11.x  
**Spring Boot Version:** 3.x (Java 21)  
**Document Version:** 1.0  
**Created:** July 8, 2026  
**Purpose:** Implementation blueprint for Authentication module migration

---

## TABLE OF CONTENTS

1. [Authentication Architecture](#1-authentication-architecture)
2. [Authentication Sequence Diagrams](#2-authentication-sequence-diagrams)
3. [JWT Design](#3-jwt-design)
4. [Spring Security Design](#4-spring-security-design)
5. [API Compatibility](#5-api-compatibility)
6. [Validation Design](#6-validation-design)
7. [Exception Design](#7-exception-design)
8. [Security Decisions](#8-security-decisions)
9. [React Compatibility](#9-react-compatibility)
10. [Flutter Compatibility](#10-flutter-compatibility)
11. [Testing Strategy](#11-testing-strategy)
12. [Migration Checklist](#12-migration-checklist)

---

## 1. AUTHENTICATION ARCHITECTURE

### 1.1 Authentication Flow Overview

The authentication system supports four user types with different authentication and authorization requirements:

**User Types:**
- **Shipper**: Can post cargo requests, manage bookings
- **Driver**: Can bid on cargo, manage trips, requires document verification
- **Fleet Owner**: Can manage fleet of drivers and vehicles
- **Admin**: Can manage all users, view analytics, resolve disputes

### 1.2 Authentication Flow

**Phase 1: Initial Migration (Laravel Sanctum Compatibility)**

During the initial migration, Spring Boot will maintain compatibility with Laravel Sanctum tokens to enable dual deployment:

1. **User Registration**
   - Client sends registration request with phone/email, password, role
   - Server validates input (phone uniqueness, email uniqueness, password strength)
   - Server hashes password using BCrypt
   - Server creates user record in database
   - Server sets initial status based on role:
     - Drivers: verification_status=false, is_active=false
     - Shippers/Fleet Owners: verification_status=true, is_active=true
   - Server generates Sanctum-compatible token (format: "id|hashed_token")
   - Server stores token in personal_access_tokens table
   - Server returns user data and token to client

2. **User Login**
   - Client sends login request with identifier (phone or email) and password
   - Server detects identifier type (email vs phone) using validation
   - Server retrieves user by identifier
   - Server verifies password using BCrypt
   - Server generates Sanctum-compatible token
   - Server stores token in personal_access_tokens table
   - Server returns user data and token to client

3. **Token Validation**
   - Client includes Bearer token in Authorization header
   - Server extracts token from header
   - Server validates token against personal_access_tokens table
   - Server loads associated user
   - Server sets authentication context in Spring Security
   - Request proceeds to controller

4. **Logout**
   - Client sends logout request with Bearer token
   - Server extracts token from header
   - Server deletes token from personal_access_tokens table
   - Server returns success response

**Phase 2: JWT Migration (Post-Initial Migration)**

After initial migration is complete and validated, migrate to JWT:

1. **User Registration**
   - Same as Phase 1, but generates JWT instead of Sanctum token
   - JWT includes claims: sub (user ID), role, verification_status, is_active
   - JWT signed with secret key
   - JWT stored in database for revocation capability

2. **User Login**
   - Same as Phase 1, but generates JWT instead of Sanctum token
   - Access token: Short-lived (15 minutes)
   - Refresh token: Long-lived (7 days)
   - Refresh token stored in database

3. **Token Validation**
   - Client includes JWT in Authorization header
   - Server validates JWT signature
   - Server checks token expiration
   - Server checks token revocation status (if stored in database)
   - Server loads user from database
   - Server sets authentication context in Spring Security

4. **Token Refresh**
   - Client sends refresh token request
   - Server validates refresh token
   - Server generates new access token
   - Server optionally rotates refresh token
   - Server returns new access token (and optionally new refresh token)

### 1.3 Authorization Flow

Authorization is role-based with additional status checks:

**Role Hierarchy:**
```
ADMIN (highest)
  ├── Can access all admin endpoints
  ├── Can manage all users
  └── Can view all data

FLEET_OWNER
  ├── Can manage own drivers
  ├── Can manage own vehicles
  └── Can create bookings for fleet

SHIPPER
  ├── Can post cargo requests
  ├── Can manage own bookings
  └── Can view own data

DRIVER (lowest)
  ├── Can bid on cargo
  ├── Can manage own trips
  └── Can view own data
```

**Authorization Rules:**
1. **Authentication Required**: All endpoints except /register, /login, /auth/login
2. **Admin Endpoints**: Require ROLE_ADMIN
3. **Fleet Management**: Require ROLE_FLEET_OWNER or ROLE_ADMIN
4. **Driver Actions**: Require ROLE_DRIVER and verification_status=true and is_active=true
5. **Shipper Actions**: Require ROLE_SHIPPER or ROLE_ADMIN
6. **Self-Access**: Users can only access their own data (unless admin)

**Status-Based Authorization:**
- **Drivers**: Must have verification_status=true and is_active=true to perform actions
- **Shippers/Fleet Owners**: Must have is_active=true to perform actions
- **Admins**: Always active (no status checks)

### 1.4 User Lifecycle

**Shipper Lifecycle:**
1. **Registration**: Create user with role=shipper, verification_status=true, is_active=true
2. **Active**: Can post cargo, manage bookings immediately
3. **Deactivation**: Admin can set is_active=false (prevents access)
4. **Reactivation**: Admin can set is_active=true (restores access)

**Driver Lifecycle:**
1. **Registration**: Create user with role=driver, verification_status=false, is_active=false
2. **Document Upload**: Driver uploads 5 required documents
3. **Document Review**: Admin reviews documents
4. **Verification**: Admin sets verification_status=true
5. **Activation**: Admin sets is_active=true (or auto-activate after verification)
6. **Active**: Can bid on cargo, manage trips
7. **Deactivation**: Admin can set is_active=false (prevents access)
8. **Reactivation**: Admin can set is_active=true (restores access)

**Fleet Owner Lifecycle:**
1. **Registration**: Create user with role=fleet_owner, verification_status=true, is_active=true
2. **Active**: Can manage fleet immediately
3. **Deactivation**: Admin can set is_active=false (prevents access)
4. **Reactivation**: Admin can set is_active=true (restores access)

**Admin Lifecycle:**
1. **Creation**: Admin creates user with role=admin, verification_status=true, is_active=true
2. **Active**: Full admin access
3. **Deactivation**: Another admin can set is_active=false (prevents access)
4. **Reactivation**: Another admin can set is_active=true (restores access)

### 1.5 Admin Authentication

**Admin Login Flow:**
1. Admin sends login request to /api/auth/login
2. Server validates credentials (same as regular login)
3. Server generates token with name='admin-panel'
4. Server returns response in formatUser format (camelCase fields)
5. Admin panel uses token for subsequent requests

**Admin Authorization:**
- All /admin/* endpoints require ROLE_ADMIN
- AdminMiddleware checks user.is_admin accessor
- Admin can access all user data
- Admin can modify user status
- Admin can resolve disputes
- Admin can view analytics

### 1.6 Fleet Owner Authentication

**Fleet Owner Login Flow:**
1. Fleet owner sends login request to /api/login
2. Server validates credentials
3. Server generates token with name='api-token'
4. Server returns response in UserResource format (snake_case fields)
5. Fleet owner uses token for subsequent requests

**Fleet Owner Authorization:**
- Can access /fleet/* endpoints
- Can manage own drivers (users where fleet_owner_id = own user ID)
- Can manage own vehicles (vehicles where fleet_owner_id = own user ID)
- Can create bookings for fleet
- Cannot access other fleet owners' data

### 1.7 Driver Authentication

**Driver Login Flow:**
1. Driver sends login request to /api/login
2. Server validates credentials
3. Server generates token with name='api-token'
4. Server returns response in UserResource format (snake_case fields)
5. Driver uses token for subsequent requests

**Driver Authorization:**
- Can access driver-specific endpoints
- Must have verification_status=true and is_active=true to perform actions
- Can bid on cargo requests
- Can manage own trips
- Can update own location
- Cannot access other drivers' data

---

## 2. AUTHENTICATION SEQUENCE DIAGRAMS

### 2.1 Register Sequence

**Phase 1 (Sanctum Compatibility):**
```
Client                    Spring Boot              Database
  |                            |                        |
  |--POST /api/register------>|                        |
  |  {full_name, phone,       |                        |
  |   email, password, role}  |                        |
  |                            |                        |
  |                            |--Validate Request----->|
  |                            |  (phone uniqueness,    |
  |                            |   email uniqueness,     |
  |                            |   password strength)   |
  |                            |<---Validation Result---|
  |                            |                        |
  |                            |--Hash Password-------->|
  |                            |<---Hashed Password----|
  |                            |                        |
  |                            |--INSERT User---------->|
  |                            |  (set status based on  |
  |                            |   role)                |
  |                            |<---User ID------------|
  |                            |                        |
  |                            |--Generate Token------->|
  |                            |  (Sanctum format)      |
  |                            |<---Token--------------|
  |                            |                        |
  |                            |--INSERT Token-------->|
  |                            |  (personal_access_     |
  |                            |   tokens)              |
  |                            |<---Success------------|
  |                            |                        |
  |<--201 Created-------------|                        |
  |  {user, token}            |                        |
```

**Phase 2 (JWT):**
```
Client                    Spring Boot              Database
  |                            |                        |
  |--POST /api/register------>|                        |
  |  {full_name, phone,       |                        |
  |   email, password, role}  |                        |
  |                            |                        |
  |                            |--Validate Request----->|
  |                            |<---Validation Result---|
  |                            |                        |
  |                            |--Hash Password-------->|
  |                            |<---Hashed Password----|
  |                            |                        |
  |                            |--INSERT User---------->|
  |                            |<---User ID------------|
  |                            |                        |
  |                            |--Generate JWT---------|
  |                            |  (access + refresh)    |
  |                            |                        |
  |                            |--INSERT Refresh Token>|
  |                            |<---Success------------|
  |                            |                        |
  |<--201 Created-------------|                        |
  |  {user, accessToken,      |                        |
  |   refreshToken}           |                        |
```

### 2.2 Login Sequence

**Phase 1 (Sanctum Compatibility):**
```
Client                    Spring Boot              Database
  |                            |                        |
  |--POST /api/login--------->|                        |
  |  {identifier, password}    |                        |
  |                            |                        |
  |                            |--Detect Identifier Type|
  |                            |  (email vs phone)       |
  |                            |                        |
  |                            |--SELECT User---------->|
  |                            |  WHERE email/phone = ?  |
  |                            |<---User Data-----------|
  |                            |                        |
  |                            |--Verify Password------>|
  |                            |  (BCrypt check)         |
  |                            |<---Verification Result-|
  |                            |                        |
  |                            |--Generate Token------->|
  |                            |  (Sanctum format)      |
  |                            |<---Token--------------|
  |                            |                        |
  |                            |--INSERT Token-------->|
  |                            |<---Success------------|
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {user, token}            |                        |
```

**Phase 2 (JWT):**
```
Client                    Spring Boot              Database
  |                            |                        |
  |--POST /api/login--------->|                        |
  |  {identifier, password}    |                        |
  |                            |                        |
  |                            |--Detect Identifier Type|
  |                            |                        |
  |                            |--SELECT User---------->|
  |                            |<---User Data-----------|
  |                            |                        |
  |                            |--Verify Password------>|
  |                            |<---Verification Result-|
  |                            |                        |
  |                            |--Generate JWT---------|
  |                            |  (access + refresh)    |
  |                            |                        |
  |                            |--INSERT Refresh Token>|
  |                            |<---Success------------|
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {user, accessToken,      |                        |
  |   refreshToken}           |                        |
```

### 2.3 Logout Sequence

**Phase 1 (Sanctum Compatibility):**
```
Client                    Spring Boot              Database
  |                            |                        |
  |--POST /api/logout-------->|                        |
  |  Authorization: Bearer {token}                     |
  |                            |                        |
  |                            |--Extract Token--------|
  |                            |                        |
  |                            |--DELETE Token-------->|
  |                            |  FROM personal_access_ |
  |                            |   tokens               |
  |                            |<---Success------------|
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {message: "Logged out successfully"}             |
```

**Phase 2 (JWT):**
```
Client                    Spring Boot              Database
  |                            |                        |
  |--POST /api/logout-------->|                        |
  |  Authorization: Bearer {accessToken}              |
  |                            |                        |
  |                            |--Extract Token--------|
  |                            |                        |
  |                            |--Delete Refresh Token>|
  |                            |  (if stored in DB)     |
  |                            |<---Success------------|
  |                            |                        |
  |                            |--Add Token to Blacklist|
  |                            |  (optional)            |
  |                            |<---Success------------|
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {message: "Logged out successfully"}             |
```

### 2.4 Get Current User Sequence

**Phase 1 (Sanctum Compatibility):**
```
Client                    Spring Boot              Database
  |                            |                        |
  |--GET /api/me-------------->|                        |
  |  Authorization: Bearer {token}                     |
  |                            |                        |
  |                            |--Extract Token--------|
  |                            |                        |
  |                            |--Validate Token------>|
  |                            |  (check personal_access_|
  |                            |   tokens table)        |
  |                            |<---Token Valid--------|
  |                            |                        |
  |                            |--Load User------------>|
  |                            |  WHERE id = token.user_id|
  |                            |<---User Data-----------|
  |                            |                        |
  |                            |--Format Response------|
  |                            |  (UserResource)        |
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {user data}              |                        |
```

**Phase 2 (JWT):**
```
Client                    Spring Boot              Database
  |                            |                        |
  |--GET /api/me-------------->|                        |
  |  Authorization: Bearer {accessToken}              |
  |                            |                        |
  |                            |--Extract Token--------|
  |                            |                        |
  |                            |--Validate JWT---------|
  |                            |  (signature, expiration)|
  |                            |                        |
  |                            |--Check Revocation----->|
  |                            |  (if stored in DB)     |
  |                            |<---Not Revoked--------|
  |                            |                        |
  |                            |--Load User------------>|
  |                            |  WHERE id = jwt.sub    |
  |                            |<---User Data-----------|
  |                            |                        |
  |                            |--Format Response------|
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {user data}              |                        |
```

### 2.5 Refresh Token Sequence (Phase 2 Only)

```
Client                    Spring Boot              Database
  |                            |                        |
  |--POST /api/auth/refresh-->|                        |
  |  {refreshToken}            |                        |
  |                            |                        |
  |                            |--Validate Refresh Token|
  |                            |                        |
  |                            |--SELECT Refresh Token->|
  |                            |<---Token Data----------|
  |                            |                        |
  |                            |--Check Expiration-----|
  |                            |                        |
  |                            |--Generate New Access Token|
  |                            |                        |
  |                            |--Rotate Refresh Token->|
  |                            |  (optional)            |
  |                            |                        |
  |                            |--UPDATE Refresh Token>|
  |                            |<---Success------------|
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {accessToken,            |                        |
  |   refreshToken}           |                        |
```

### 2.6 Change Password Sequence

```
Client                    Spring Boot              Database
  |                            |                        |
  |--PATCH /api/me/password-->|                        |
  |  Authorization: Bearer {token}                     |
  |  {current_password, new_password}                  |
  |                            |                        |
  |                            |--Extract Token--------|
  |                            |                        |
  |                            |--Validate Token------>|
  |                            |                        |
  |                            |--Load User------------>|
  |                            |<---User Data-----------|
  |                            |                        |
  |                            |--Verify Current Password|
  |                            |  (BCrypt check)         |
  |                            |<---Verification Result-|
  |                            |                        |
  |                            |--Hash New Password---->|
  |                            |<---Hashed Password----|
  |                            |                        |
  |                            |--UPDATE User Password->|
  |                            |<---Success------------|
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {success: true, message}  |                        |
```

### 2.7 Reset Password Sequence

**Note:** Password reset is not implemented in the current Laravel codebase. This sequence is for future implementation.

```
Client                    Spring Boot              Database
  |                            |                        |
  |--POST /api/auth/forgot-password---------------------|
  |  {email}                   |                        |
  |                            |                        |
  |                            |--Validate Email--------|
  |                            |                        |
  |                            |--SELECT User---------->|
  |                            |  WHERE email = ?        |
  |                            |<---User Data-----------|
  |                            |                        |
  |                            |--Generate Reset Token--|
  |                            |                        |
  |                            |--INSERT Reset Token---->|
  |                            |<---Success------------|
  |                            |                        |
  |                            |--Send Email----------->|
  |                            |  (via notification)     |
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {message: "Reset email sent"}                     |
```

### 2.8 Email Verification Sequence

**Note:** Email verification is not implemented in the current Laravel codebase. This sequence is for future implementation.

```
Client                    Spring Boot              Database
  |                            |                        |
  |--GET /api/auth/verify-email------------------------|
  |  ?token={verification_token}                        |
  |                            |                        |
  |                            |--Validate Token--------|
  |                            |                        |
  |                            |--SELECT User---------->|
  |                            |<---User Data-----------|
  |                            |                        |
  |                            |--UPDATE User---------->|
  |                            |  SET email_verified_at  |
  |                            |<---Success------------|
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {message: "Email verified"}                       |
```

### 2.9 Driver Verification Sequence

```
Client (Driver)           Spring Boot              Database
  |                            |                        |
  |--POST /api/documents/upload-----------------------|
  |  Authorization: Bearer {token}                     |
  |  {document_type, file}     |                        |
  |                            |                        |
  |                            |--Validate Token------>|
  |                            |                        |
  |                            |--Check Driver Status---|
  |                            |  (must be driver)      |
  |                            |                        |
  |                            |--Upload File---------->|
  |                            |  (to storage)          |
  |                            |<---File Path----------|
  |                            |                        |
  |                            |--INSERT Document------>|
  |                            |<---Success------------|
  |                            |                        |
  |<--201 Created-------------|                        |
  |  {document data}           |                        |

Client (Admin)             Spring Boot              Database
  |                            |                        |
  |--PATCH /api/admin/driver-documents/{id}/review---|
  |  Authorization: Bearer {token}                     |
  |  {status: "approved"}       |                        |
  |                            |                        |
  |                            |--Validate Token------>|
  |                            |                        |
  |                            |--Check Admin Role-----|
  |                            |                        |
  |                            |--UPDATE Document------>|
  |                            |<---Success------------|
  |                            |                        |
  |                            |--Check All Documents--|
  |                            |  (for driver)          |
  |                            |                        |
  |                            |--If All Approved------|
  |                            |--UPDATE Driver-------->|
  |                            |  SET verification_status|
  |                            |    = true              |
  |                            |  SET is_active = true   |
  |                            |<---Success------------|
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {document data}           |                        |
```

### 2.10 Admin Login Sequence

**Phase 1 (Sanctum Compatibility):**
```
Client (Admin Panel)       Spring Boot              Database
  |                            |                        |
  |--POST /api/auth/login---->|                        |
  |  {identifier, password}    |                        |
  |                            |                        |
  |                            |--Detect Identifier Type|
  |                            |                        |
  |                            |--SELECT User---------->|
  |                            |  WHERE email/phone = ?  |
  |                            |<---User Data-----------|
  |                            |                        |
  |                            |--Verify Password------>|
  |                            |<---Verification Result-|
  |                            |                        |
  |                            |--Check Admin Role-----|
  |                            |                        |
  |                            |--Generate Token------->|
  |                            |  (name='admin-panel')   |
  |                            |<---Token--------------|
  |                            |                        |
  |                            |--INSERT Token-------->|
  |                            |<---Success------------|
  |                            |                        |
  |                            |--Format Response------|
  |                            |  (formatUser - camelCase)|
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {token, user}            |  (token first, user second)|
```

**Phase 2 (JWT):**
```
Client (Admin Panel)       Spring Boot              Database
  |                            |                        |
  |--POST /api/auth/login---->|                        |
  |  {identifier, password}    |                        |
  |                            |                        |
  |                            |--Detect Identifier Type|
  |                            |                        |
- |                            |--SELECT User---------->|
  |                            |<---User Data-----------|
  |                            |                        |
  |                            |--Verify Password------>|
  |                            |<---Verification Result-|
  |                            |                        |
  |                            |--Check Admin Role-----|
  |                            |                        |
  |                            |--Generate JWT---------|
  |                            |  (access + refresh)    |
  |                            |                        |
  |                            |--INSERT Refresh Token>|
  |                            |<---Success------------|
  |                            |                        |
  |                            |--Format Response------|
  |                            |  (formatUser - camelCase)|
  |                            |                        |
  |<--200 OK-------------------|                        |
  |  {token, user}            |  (token first, user second)|
```

---

## 3. JWT DESIGN

### 3.1 Migration Strategy from Laravel Sanctum to JWT

**Phase 1: Sanctum Compatibility (Initial Migration)**

During initial migration, Spring Boot will implement Sanctum-compatible token validation to enable dual deployment:

**Token Format:**
- Format: "id|hashed_token"
- Example: "1|abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890ab"
- Token ID: Integer (from personal_access_tokens.id)
- Hashed Token: SHA-256 hash of plain token

**Token Storage:**
- Stored in personal_access_tokens table
- Table columns: id, tokenable_type, tokenable_id, name, token, abilities, last_used_at, expires_at, created_at, updated_at
- Token is SHA-256 hashed before storage
- Plain token is returned to client and never stored

**Token Validation:**
- Extract token from Authorization header
- Split by "|" to get token ID and hashed token
- Query personal_access_tokens table by ID
- Compare hashed token with stored hash
- Check expiration if expires_at is set
- Load user from tokenable_id

**Rationale:**
- Enables dual deployment (Laravel + Spring Boot)
- Preserves existing tokens during migration
- No frontend changes required initially
- Gradual migration path to JWT

**Phase 2: JWT Migration (Post-Initial Migration)**

After initial migration is validated, migrate to JWT:

**Migration Steps:**
1. Implement JWT token generation alongside Sanctum tokens
2. Update frontend applications to use JWT tokens
3. Gradually phase out Sanctum token generation
4. Maintain Sanctum token validation for existing tokens
5. Deprecate Sanctum tokens after all clients migrated

**Token Format:**
- Format: "header.payload.signature"
- Example: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

### 3.2 Access Token Design

**Purpose:** Short-lived token for API authentication

**Claims:**
```json
{
  "sub": "123",              // User ID
  "email": "user@example.com",
  "phone": "+251911234567",
  "role": "shipper",         // shipper, driver, admin, fleet_owner
  "verification_status": true,
  "is_active": true,
  "iat": 1625097600,         // Issued at
  "exp": 1625098500          // Expiration (15 minutes)
}
```

**Expiration:** 15 minutes

**Rationale:**
- Short expiration reduces risk of token theft
- Forces regular token refresh
- Industry standard for access tokens
- Balances security and user experience

### 3.3 Refresh Token Design

**Purpose:** Long-lived token for obtaining new access tokens

**Claims:**
```json
{
  "sub": "123",              // User ID
  "jti": "unique-id",        // Token ID for revocation
  "type": "refresh",
  "iat": 1625097600,         // Issued at
  "exp": 1625702400          // Expiration (7 days)
}
```

**Expiration:** 7 days

**Storage:** Stored in database for revocation capability

**Rationale:**
- Long expiration improves user experience
- Database storage enables revocation
- Token ID enables per-token revocation
- Industry standard for refresh tokens

### 3.4 Claims Design

**Standard Claims:**
- **sub** (Subject): User ID
- **iat** (Issued At): Token issuance timestamp
- **exp** (Expiration): Token expiration timestamp
- **jti** (JWT ID): Unique token identifier (for refresh tokens)

**Custom Claims:**
- **email**: User email (for display)
- **phone**: User phone (for display)
- **role**: User role (for authorization)
- **verification_status**: Driver verification status (for authorization)
- **is_active**: Account active status (for authorization)

**Rationale:**
- Include authorization data in token to reduce database queries
- Include display data for UI rendering
- Exclude sensitive data (password, tokens)
- Keep token size reasonable (< 1KB)

### 3.5 Expiration Strategy

**Access Token:** 15 minutes
- Short expiration limits damage from token theft
- Forces regular token refresh
- Industry standard for access tokens

**Refresh Token:** 7 days
- Long expiration improves user experience
- Users don't need to re-login frequently
- Can be revoked if compromised
- Industry standard for refresh tokens

**Rationale:**
- Balances security and user experience
- Follows OAuth 2.0 best practices
- Enables token revocation without forcing re-login

### 3.6 Rotation Strategy

**Refresh Token Rotation:**
- When refresh token is used, generate new refresh token
- Invalidate old refresh token
- Store new refresh token in database
- Return both new access token and new refresh token

**Rationale:**
- Limits damage from refresh token theft
- Each refresh token can only be used once
- Detects token theft (reuse of old token)
- Industry best practice for security

**Implementation:**
- Store refresh token hash in database
- On refresh, validate hash and generate new hash
- Delete old refresh token
- Insert new refresh token
- If old refresh token is reused, invalidate all tokens for user

### 3.7 Revocation Strategy

**Token Revocation Methods:**

1. **Logout:**
   - Delete refresh token from database
   - Add access token to blacklist (optional)
   - Immediate revocation

2. **Password Change:**
   - Delete all refresh tokens for user
   - Add all access tokens to blacklist (optional)
   - Forces re-login

3. **Admin Action:**
   - Delete all refresh tokens for user
   - Add all access tokens to blacklist (optional)
   - Forces re-login

4. **Token Theft Detection:**
   - If refresh token is reused, invalidate all tokens
   - Alert user of potential compromise

**Blacklist Strategy:**
- Store revoked access token IDs in Redis
- Set TTL equal to token expiration
- Check blacklist during token validation
- Automatic cleanup after expiration

**Rationale:**
- Enables immediate token revocation
- Supports security events (password change, admin action)
- Detects token theft
- Redis provides fast lookup and automatic cleanup

---

## 4. SPRING SECURITY DESIGN

### 4.1 Security Filter Chain

**Filter Chain Order:**

1. **CorsFilter**
   - Handles CORS preflight requests
   - Adds CORS headers to responses
   - Configured for React frontend and Flutter app origins

2. **RateLimitFilter**
   - Applies rate limiting to login/registration endpoints
   - Uses token bucket algorithm
   - Configured limits: 5 requests per minute for login, 10 per hour for registration

3. **JwtAuthenticationFilter** (Phase 2) or SanctumAuthenticationFilter (Phase 1)
   - Extracts token from Authorization header
   - Validates token (JWT or Sanctum)
   - Loads user from database
   - Sets authentication in SecurityContext
   - Skips authentication for public endpoints

4. **UsernamePasswordAuthenticationFilter**
   - Not used (authentication handled by controllers)
   - Kept for potential form-based login

5. **SecurityContextHolderFilter**
   - Sets SecurityContext from request
   - Enables @PreAuthorize annotations

6. **ExceptionTranslationFilter**
   - Handles authentication/authorization exceptions
   - Returns appropriate HTTP status codes

7. **FilterSecurityInterceptor**
   - Enforces authorization rules
   - Checks @PreAuthorize annotations
   - Checks role-based access

**Rationale:**
- Follows Spring Security filter chain order
- Custom filters for Sanctum/JWT authentication
- Rate limiting before authentication to prevent abuse
- CORS handling first for preflight requests

### 4.2 Authentication Provider

**Custom Authentication Provider:**

**Responsibilities:**
- Load user by identifier (email or phone)
- Verify password using BCrypt
- Create Authentication object
- Handle user status checks (is_active, verification_status)

**User Loading:**
- Query users table by email or phone
- Load user roles and authorities
- Check account status (is_active)
- Check driver verification status (if role=driver)

**Password Verification:**
- Use BCryptPasswordEncoder
- Compare provided password with stored hash
- Handle password mismatch

**Status Checks:**
- If is_active=false, throw DisabledException
- If role=driver and verification_status=false, throw DisabledException
- If role=driver and is_active=false, throw DisabledException

**Rationale:**
- Custom provider needed for email/phone login
- Status checks integrated into authentication flow
- Consistent error handling for disabled accounts

### 4.3 Password Encoder

**BCryptPasswordEncoder**

**Algorithm:** BCrypt

**Strength Factor:** 10 (default)

**Rationale:**
- BCrypt is industry standard for password hashing
- Built-in salt generation
- Adaptive strength factor
- Compatible with Laravel's BCrypt implementation
- Spring Security default

**Migration from Laravel:**
- Laravel uses BCrypt by default
- Spring Boot BCryptPasswordEncoder is compatible
- No password re-hashing required
- Existing passwords will validate correctly

### 4.4 Authentication Manager

**ProviderManager**

**Configuration:**
- Uses CustomAuthenticationProvider
- Supports multiple authentication methods (email, phone)
- Handles authentication exceptions

**Authentication Flow:**
1. Controller receives login request
2. Controller creates UsernamePasswordAuthenticationToken
3. AuthenticationManager authenticates token
4. CustomAuthenticationProvider loads user and verifies password
5. AuthenticationManager returns authenticated Authentication
6. Controller generates token (Sanctum or JWT)

**Rationale:**
- Leverages Spring Security authentication framework
- Custom provider for business logic
- Consistent authentication flow

### 4.5 JWT Filter (Phase 2)

**JwtAuthenticationFilter**

**Responsibilities:**
- Extract JWT from Authorization header
- Validate JWT signature
- Check token expiration
- Check token revocation (blacklist)
- Load user from database
- Set authentication in SecurityContext

**Token Extraction:**
- Extract from Authorization: Bearer {token}
- Header format: "Bearer " + token
- Handle missing/invalid header

**Token Validation:**
- Validate signature using secret key
- Check expiration (exp claim)
- Check not-before (nbf claim, if used)
- Check issuer (iss claim, if used)
- Check audience (aud claim, if used)

**Revocation Check:**
- Check Redis blacklist for token ID
- If blacklisted, throw AuthenticationException
- Skip blacklist check if not configured

**User Loading:**
- Extract user ID from sub claim
- Load user from database
- Check account status (is_active, verification_status)
- Create Authentication object with authorities

**Rationale:**
- Stateless authentication (no session)
- Standard JWT validation
- Optional revocation support
- Integration with Spring Security

### 4.6 Sanctum Filter (Phase 1)

**SanctumAuthenticationFilter**

**Responsibilities:**
- Extract Sanctum token from Authorization header
- Validate token against database
- Load user from database
- Set authentication in SecurityContext

**Token Extraction:**
- Extract from Authorization: Bearer {token}
- Split by "|" to get token ID and hashed token
- Handle missing/invalid format

**Token Validation:**
- Query personal_access_tokens table by token ID
- Compare hashed token with stored hash
- Check expiration if expires_at is set
- Update last_used_at timestamp

**User Loading:**
- Load user from tokenable_id
- Check account status (is_active, verification_status)
- Create Authentication object with authorities

**Rationale:**
- Maintains Laravel Sanctum compatibility
- Enables dual deployment
- Gradual migration path to JWT

### 4.7 Authorization Rules

**Role-Based Authorization:**

**@PreAuthorize Annotations:**

```java
// Admin endpoints
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> adminStats() { }

// Fleet owner endpoints
@PreAuthorize("hasRole('FLEET_OWNER') or hasRole('ADMIN')")
public ResponseEntity<?> fleetDashboard() { }

// Shipper endpoints
@PreAuthorize("hasRole('SHIPPER') or hasRole('ADMIN')")
public ResponseEntity<?> createCargoRequest() { }

// Driver endpoints
@PreAuthorize("hasRole('DRIVER') or hasRole('ADMIN')")
public ResponseEntity<?> bidOnCargo() { }

// Self-access
@PreAuthorize("#userId == #authentication.principal.id or hasRole('ADMIN')")
public ResponseEntity<?> getUserProfile(Long userId) { }
```

**Status-Based Authorization:**

**Custom Security Expressions:**

```java
public class CustomSecurityExpression {
    public boolean isDriverVerified(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return user.getVerificationStatus() && user.getIsActive();
    }
    
    public boolean isActive(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return user.getIsActive();
    }
}
```

**Usage:**

```java
@PreAuthorize("@customSecurity.isDriverVerified(authentication)")
public ResponseEntity<?> driverAction() { }
```

**Rationale:**
- Declarative authorization with annotations
- Role-based access control
- Custom expressions for business rules
- Integration with Spring Security

### 4.8 Role Hierarchy

**Role Hierarchy:**

```
ADMIN > FLEET_OWNER > SHIPPER
ADMIN > FLEET_OWNER > DRIVER
ADMIN > SHIPPER
ADMIN > DRIVER
FLEET_OWNER > SHIPPER (for fleet management)
FLEET_OWNER > DRIVER (for fleet management)
```

**Configuration:**

```yaml
security:
  role-hierarchy:
    - ADMIN > FLEET_OWNER
    - ADMIN > SHIPPER
    - ADMIN > DRIVER
    - FLEET_OWNER > SHIPPER
    - FLEET_OWNER > DRIVER
```

**Rationale:**
- Admin has highest privileges
- Fleet owners can manage shippers and drivers
- Clear privilege escalation
- Spring Security built-in support

### 4.9 Endpoint Security Matrix

| Endpoint | Method | Public | Auth Required | Roles | Status Check |
|----------|--------|--------|---------------|-------|--------------|
| /api/register | POST | Yes | No | None | No |
| /api/login | POST | Yes | No | None | No |
| /api/auth/login | POST | Yes | No | None | No |
| /api/logout | POST | No | Yes | Any | No |
| /api/me | GET | No | Yes | Any | No |
| /api/me | PATCH | No | Yes | Any | No |
| /api/me/password | PATCH | No | Yes | Any | No |
| /api/admin/* | * | No | Yes | ADMIN | No |
| /api/fleet/* | * | No | Yes | FLEET_OWNER, ADMIN | No |
| /api/cargo-requests | POST | No | Yes | SHIPPER, ADMIN | No |
| /api/cargo-requests/{id}/bids | POST | No | Yes | DRIVER, ADMIN | verification_status, is_active |
| /api/bookings | POST | No | Yes | SHIPPER, FLEET_OWNER, ADMIN | No |

**Rationale:**
- Public endpoints for registration and login
- All other endpoints require authentication
- Admin endpoints restricted to ADMIN role
- Fleet endpoints restricted to FLEET_OWNER and ADMIN
- Driver actions require verification and active status
- Shipper actions require SHIPPER or ADMIN role

---

## 5. API COMPATIBILITY

### 5.1 POST /api/register

**Laravel Request:**
```json
{
  "full_name": "John Doe",
  "phone": "+251911234567",
  "email": "john@example.com",
  "password": "password123",
  "role": "shipper"
}
```

**Spring Boot Request:**
```json
{
  "full_name": "John Doe",
  "phone": "+251911234567",
  "email": "john@example.com",
  "password": "password123",
  "role": "shipper"
}
```

**Intentional Differences:** None

**Laravel Response (201 Created):**
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

**Spring Boot Response (Phase 1 - Sanctum):**
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

**Spring Boot Response (Phase 2 - JWT):**
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
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Intentional Differences:**
- Phase 2: Token field renamed to accessToken and refreshToken
- Phase 2: Token format changes from Sanctum to JWT

### 5.2 POST /api/login

**Laravel Request:**
```json
{
  "identifier": "+251911234567",
  "password": "password123"
}
```

**Spring Boot Request:**
```json
{
  "identifier": "+251911234567",
  "password": "password123"
}
```

**Intentional Differences:** None

**Laravel Response (200 OK):**
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

**Spring Boot Response (Phase 1 - Sanctum):**
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

**Spring Boot Response (Phase 2 - JWT):**
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
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Intentional Differences:**
- Phase 2: Token field renamed to accessToken and refreshToken
- Phase 2: Token format changes from Sanctum to JWT

### 5.3 POST /api/auth/login (Admin Panel)

**Laravel Request:**
```json
{
  "identifier": "admin@example.com",
  "password": "admin123"
}
```

**Spring Boot Request:**
```json
{
  "identifier": "admin@example.com",
  "password": "admin123"
}
```

**Intentional Differences:** None

**Laravel Response (200 OK):**
```json
{
  "token": "1|abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890ab",
  "user": {
    "id": 1,
    "name": "Admin User",
    "email": "admin@example.com",
    "phone": "+251911234567",
    "role": "admin",
    "isVerified": true,
    "isActive": true,
    "createdAt": "2026-07-08T12:00:00.000000Z",
    "updatedAt": "2026-07-08T12:00:00.000000Z"
  }
}
```

**Spring Boot Response (Phase 1 - Sanctum):**
```json
{
  "token": "1|abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmnopqrstuvwxyz1234567890ab",
  "user": {
    "id": 1,
    "name": "Admin User",
    "email": "admin@example.com",
    "phone": "+251911234567",
    "role": "admin",
    "isVerified": true,
    "isActive": true,
    "createdAt": "2026-07-08T12:00:00.000000Z",
    "updatedAt": "2026-07-08T12:00:00.000000Z"
  }
}
```

**Spring Boot Response (Phase 2 - JWT):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "id": 1,
    "name": "Admin User",
    "email": "admin@example.com",
    "phone": "+251911234567",
    "role": "admin",
    "isVerified": true,
    "isActive": true,
    "createdAt": "2026-07-08T12:00:00.000000Z",
    "updatedAt": "2026-07-08T12:00:00.000000Z"
  }
}
```

**Intentional Differences:**
- Phase 2: Token format changes from Sanctum to JWT
- Preserved: camelCase field names (isVerified, isActive, createdAt, updatedAt)
- Preserved: field order (token first, user second)
- Preserved: 'name' instead of 'full_name'

**Rationale:** Admin panel depends on this specific format

### 5.4 POST /api/logout

**Laravel Request:**
```
Authorization: Bearer {token}
```

**Spring Boot Request:**
```
Authorization: Bearer {token}
```

**Intentional Differences:** None

**Laravel Response (200 OK):**
```json
{
  "message": "Logged out successfully"
}
```

**Spring Boot Response:**
```json
{
  "message": "Logged out successfully"
}
```

**Intentional Differences:** None

### 5.5 GET /api/me

**Laravel Request:**
```
Authorization: Bearer {token}
```

**Spring Boot Request:**
```
Authorization: Bearer {token}
```

**Intentional Differences:** None

**Laravel Response (200 OK):**
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

**Spring Boot Response:**
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

**Intentional Differences:** None

### 5.6 PATCH /api/me

**Laravel Request:**
```json
{
  "name": "John Updated",
  "full_name": "John Updated",
  "phone": "+251911234568",
  "address": "Addis Ababa, Ethiopia",
  "business_name": "John's Business"
}
```

**Spring Boot Request:**
```json
{
  "name": "John Updated",
  "full_name": "John Updated",
  "phone": "+251911234568",
  "address": "Addis Ababa, Ethiopia",
  "business_name": "John's Business"
}
```

**Intentional Differences:** None

**Laravel Response (200 OK):**
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

**Spring Boot Response:**
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

**Intentional Differences:** None

### 5.7 PATCH /api/me/password

**Laravel Request:**
```json
{
  "current_password": "password123",
  "new_password": "newpassword123"
}
```

**Spring Boot Request:**
```json
{
  "current_password": "password123",
  "new_password": "newpassword123"
}
```

**Intentional Differences:** None

**Laravel Response (200 OK):**
```json
{
  "success": true,
  "message": "Password changed successfully."
}
```

**Spring Boot Response:**
```json
{
  "success": true,
  "message": "Password changed successfully."
}
```

**Intentional Differences:** None

### 5.8 Error Responses

**Laravel Error (401 Unauthorized - Invalid Credentials):**
```json
{
  "message": "Invalid credentials. Check your email/phone and password."
}
```

**Spring Boot Error:**
```json
{
  "message": "Invalid credentials. Check your email/phone and password."
}
```

**Intentional Differences:** None

**Laravel Error (401 Unauthorized - Admin Login):**
```json
{
  "error": "Invalid credentials"
}
```

**Spring Boot Error:**
```json
{
  "error": "Invalid credentials"
}
```

**Intentional Differences:** None (preserve admin login error format)

**Laravel Error (422 Unprocessable Entity - Validation):**
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

**Spring Boot Error:**
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

**Intentional Differences:** None

**Laravel Error (422 Unprocessable Entity - Incorrect Password):**
```json
{
  "message": "Current password is incorrect."
}
```

**Spring Boot Error:**
```json
{
  "message": "Current password is incorrect."
}
```

**Intentional Differences:** None

**Laravel Error (403 Forbidden):**
```json
{
  "message": "Forbidden"
}
```

**Spring Boot Error:**
```json
{
  "message": "Forbidden"
}
```

**Intentional Differences:** None

---

## 6. VALIDATION DESIGN

### 6.1 Registration Validation

**Validation Rules:**

| Field | Type | Required | Min | Max | Pattern | Additional Rules |
|-------|------|----------|-----|-----|---------|-----------------|
| full_name | String | Yes | - | 255 | - | - |
| phone | String | Yes | - | 255 | - | Unique:users,phone |
| email | String | No | - | 255 | Email format | Unique:users,email (if provided) |
| password | String | Yes | 6 | - | - | - |
| role | String | Yes | - | - | - | In:shipper,driver,admin,fleet_owner |

**Business Validation:**
- Phone must be unique across all users
- Email must be unique if provided
- Role must be one of: shipper, driver, admin, fleet_owner
- Password must be at least 6 characters

**Implementation:**
- Use Bean Validation annotations (@NotNull, @Size, @Pattern, @Email)
- Custom validator for phone uniqueness
- Custom validator for email uniqueness
- Enum validator for role

### 6.2 Login Validation

**Validation Rules:**

| Field | Type | Required | Min | Max | Pattern | Additional Rules |
|-------|------|----------|-----|-----|---------|-----------------|
| identifier | String | Yes | - | - | - | - |
| password | String | Yes | - | - | - | - |

**Business Validation:**
- Identifier can be email or phone
- Password must match stored hash
- User must exist
- User must be active (is_active=true)
- If driver, must be verified (verification_status=true)

**Implementation:**
- Bean Validation for required fields
- Custom validation for identifier format (email or phone)
- Password verification in authentication provider
- Status checks in authentication provider

### 6.3 Password Validation

**Password Strength Requirements:**
- Minimum length: 6 characters
- No special character requirement (Laravel doesn't enforce)
- No uppercase/lowercase requirement (Laravel doesn't enforce)
- No digit requirement (Laravel doesn't enforce)

**Password Change Validation:**
- Current password must match stored hash
- New password must be at least 6 characters
- New password must be different from current password

**Implementation:**
- Bean Validation for minimum length
- Custom validator for password difference
- Password verification in service layer

### 6.4 Profile Update Validation

**Validation Rules:**

| Field | Type | Required | Min | Max | Pattern | Additional Rules |
|-------|------|----------|-----|-----|---------|-----------------|
| name | String | No | - | 255 | - | Mapped to full_name |
| full_name | String | No | - | 255 | - | - |
| phone | String | No | - | 50 | - | - |
| address | String | No | - | 255 | - | Nullable |
| business_name | String | No | - | 255 | - | Nullable |

**Business Validation:**
- All fields are optional
- If name is provided, map to full_name
- Phone uniqueness check (if changed)
- Email uniqueness check (if changed)

**Implementation:**
- Bean Validation for optional fields
- Custom validator for phone uniqueness (if changed)
- Custom validator for email uniqueness (if changed)
- Field mapping logic in service layer

### 6.5 Driver Verification Validation

**Document Upload Validation:**

| Field | Type | Required | Min | Max | Pattern | Additional Rules |
|-------|------|----------|-----|-----|---------|-----------------|
| document_type | String | Yes | - | - | - | In:license,insurance,vehicle_registration,id_card,other |
| file | File | Yes | - | 10MB | - | Allowed types: pdf, jpg, jpeg, png |

**Business Validation:**
- User must be driver
- Document type must be one of 5 required types
- File size must not exceed 10MB
- File type must be allowed

**Document Review Validation:**

| Field | Type | Required | Min | Max | Pattern | Additional Rules |
|-------|------|----------|-----|-----|---------|-----------------|
| status | String | Yes | - | - | - | In:approved,rejected |
| rejection_reason | String | No | - | 500 | - | Required if status=rejected |

**Business Validation:**
- User must be admin
- Document must exist
- Status must be approved or rejected
- Rejection reason required if rejected

**Implementation:**
- Bean Validation for required fields
- Enum validator for document type and status
- File size and type validation
- Role-based authorization

### 6.6 Admin Login Validation

**Validation Rules:**

| Field | Type | Required | Min | Max | Pattern | Additional Rules |
|-------|------|----------|-----|-----|---------|-----------------|
| identifier | String | Yes | - | - | - | - |
| password | String | Yes | - | - | - | - |

**Business Validation:**
- Same as regular login validation
- User must have admin role
- User must be active

**Implementation:**
- Same as regular login validation
- Role check in authentication provider
- Status check in authentication provider

---

## 7. EXCEPTION DESIGN

### 7.1 Authentication Exceptions

**Exception Types:**

| Exception | HTTP Status | Message | Use Case |
|-----------|--------------|---------|----------|
| AuthenticationException | 401 | "Invalid credentials. Check your email/phone and password." | Invalid username or password |
| DisabledException | 403 | "Account is disabled. Please contact support." | Account is inactive |
| LockedException | 403 | "Account is locked. Please contact support." | Account is locked (future) |
| CredentialsExpiredException | 403 | "Password has expired. Please reset your password." | Password expired (future) |
| AccountExpiredException | 403 | "Account has expired. Please contact support." | Account expired (future) |

**Implementation:**
- Custom exception classes extending Spring Security exceptions
- Global exception handler to map to HTTP status codes
- Consistent error message format

### 7.2 Authorization Exceptions

**Exception Types:**

| Exception | HTTP Status | Message | Use Case |
|-----------|--------------|---------|----------|
| AccessDeniedException | 403 | "Forbidden" | User lacks required role |
| ResourceNotFoundException | 404 | "Resource not found" | Resource not found |
| OwnershipException | 403 | "You do not have permission to access this resource" | User does not own resource |

**Implementation:**
- Custom exception classes
- Global exception handler
- Role-based authorization with @PreAuthorize
- Custom security expressions for ownership checks

### 7.3 Validation Exceptions

**Exception Types:**

| Exception | HTTP Status | Message | Use Case |
|-----------|--------------|---------|----------|
| MethodArgumentNotValidException | 422 | Validation error message | Bean validation failure |
| ConstraintViolationException | 422 | Validation error message | Custom validation failure |

**Error Response Format:**
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

**Implementation:**
- Bean Validation annotations
- Custom validators
- Global exception handler for validation exceptions
- Consistent error response format

### 7.4 Token Exceptions

**Exception Types:**

| Exception | HTTP Status | Message | Use Case |
|-----------|--------------|---------|----------|
| InvalidTokenException | 401 | "Invalid token" | Token is invalid or malformed |
| ExpiredTokenException | 401 | "Token has expired" | Token has expired |
| RevokedTokenException | 401 | "Token has been revoked" | Token has been revoked |
| MissingTokenException | 401 | "Authorization header is missing" | Authorization header is missing |

**Implementation:**
- Custom exception classes
- JWT filter throws appropriate exceptions
- Sanctum filter throws appropriate exceptions
- Global exception handler maps to HTTP status codes

### 7.5 Account Status Exceptions

**Exception Types:**

| Exception | HTTP Status | Message | Use Case |
|-----------|--------------|---------|----------|
| DriverNotVerifiedException | 403 | "Driver account is not verified. Please complete document verification." | Driver not verified |
| AccountInactiveException | 403 | "Account is inactive. Please contact support." | Account is inactive |
| CurrentPasswordIncorrectException | 422 | "Current password is incorrect." | Current password mismatch |

**Implementation:**
- Custom exception classes
- Thrown in authentication provider or service layer
- Global exception handler maps to HTTP status codes
- Specific messages for each status

### 7.6 Exception to HTTP Status Mapping

| Exception | HTTP Status | Response Format |
|-----------|--------------|-----------------|
| AuthenticationException | 401 | {"message": "Invalid credentials. Check your email/phone and password."} |
| AccessDeniedException | 403 | {"message": "Forbidden"} |
| ResourceNotFoundException | 404 | {"message": "Resource not found"} |
| MethodArgumentNotValidException | 422 | {"message": "...", "errors": {...}} |
| InvalidTokenException | 401 | {"message": "Invalid token"} |
| ExpiredTokenException | 401 | {"message": "Token has expired"} |
| DriverNotVerifiedException | 403 | {"message": "Driver account is not verified. Please complete document verification."} |
| AccountInactiveException | 403 | {"message": "Account is inactive. Please contact support."} |
| CurrentPasswordIncorrectException | 422 | {"message": "Current password is incorrect."} |

**Implementation:**
- @ControllerAdvice with @ExceptionHandler methods
- Consistent error response format
- Preserve Laravel error messages for compatibility

---

## 8. SECURITY DECISIONS

### 8.1 Password Hashing

**Algorithm:** BCrypt

**Strength Factor:** 10

**Rationale:**
- BCrypt is industry standard for password hashing
- Built-in salt generation (no separate salt storage)
- Adaptive strength factor (can increase over time)
- Compatible with Laravel's BCrypt implementation
- Spring Security default
- No password re-hashing required during migration

**Implementation:**
- BCryptPasswordEncoder in Spring Security
- Same algorithm as Laravel
- Existing passwords will validate correctly

### 8.2 Token Storage

**Phase 1 (Sanctum Compatibility):**
- Stored in personal_access_tokens table
- SHA-256 hashed before storage
- Plain token never stored
- Token includes ID and hash

**Phase 2 (JWT):**
- Access tokens: Not stored (stateless)
- Refresh tokens: Stored in database for revocation
- Refresh token hash stored (not plain token)
- Optional: Access token blacklist in Redis

**Rationale:**
- Phase 1: Maintain Laravel compatibility for dual deployment
- Phase 2: Stateless access tokens reduce database load
- Phase 2: Refresh token storage enables revocation
- Phase 2: Redis blacklist enables immediate revocation

### 8.3 CORS Policy

**Allowed Origins:**
- http://localhost:3000 (React development)
- http://localhost:8080 (React production)
- React frontend production URL
- Flutter app (no CORS needed for mobile)

**Allowed Methods:**
- GET, POST, PUT, PATCH, DELETE, OPTIONS

**Allowed Headers:**
- Authorization
- Content-Type
- Accept
- Origin

**Allow Credentials:**
- Yes (for cookies if needed in future)

**Max Age:**
- 3600 seconds (1 hour)

**Rationale:**
- Support React frontend development and production
- Support all HTTP methods needed by API
- Support Authorization header for token authentication
- Cache preflight requests for performance

### 8.4 CSRF Policy

**Decision:** Disabled for API endpoints

**Rationale:**
- API is stateless (token-based authentication)
- CSRF is for session-based authentication
- Not needed for token-based authentication
- Reduces complexity
- Laravel Sanctum also disables CSRF for API

**Implementation:**
- Disable CSRF in Spring Security configuration
- Only enable CSRF for any future web endpoints

### 8.5 Session Policy

**Decision:** No sessions for API authentication

**Rationale:**
- Token-based authentication is stateless
- Sessions add complexity and server load
- Not needed for API endpoints
- Better for horizontal scaling
- Laravel Sanctum also uses stateless tokens

**Implementation:**
- Disable session creation in Spring Security
- Use token-based authentication only
- Sessions only for future web endpoints (if any)

### 8.6 Rate Limiting

**Login Endpoint:**
- Limit: 5 requests per minute per IP
- Window: Sliding window
- Block duration: 15 minutes

**Registration Endpoint:**
- Limit: 10 requests per hour per IP
- Window: Fixed window
- Block duration: 1 hour

**General API Endpoints:**
- Limit: 100 requests per minute per user
- Window: Sliding window
- Block duration: 5 minutes

**Rationale:**
- Prevent brute force attacks on login
- Prevent spam registration
- Prevent API abuse
- Sliding window for better user experience
- IP-based limits for public endpoints
- User-based limits for authenticated endpoints

**Implementation:**
- Spring Boot Starter for Rate Limiting or bucket4j
- Redis for distributed rate limiting
- Custom rate limit filter
- Different limits per endpoint type

### 8.7 Brute-Force Protection

**Strategy:**
- Rate limiting (primary protection)
- Account lockout after 5 failed login attempts
- Lockout duration: 15 minutes
- Progressive lockout (longer for repeated offenses)

**Implementation:**
- Track failed login attempts per IP
- Track failed login attempts per user
- Lock account after threshold
- Send email notification on lockout
- Admin can unlock account

**Rationale:**
- Rate limiting is first line of defense
- Account lockout prevents credential stuffing
- Progressive lockout deters persistent attackers
- Email notification alerts user to compromise
- Admin unlock provides recovery path

### 8.8 Account Lockout Strategy

**Lockout Conditions:**
- 5 failed login attempts in 15 minutes
- 10 failed login attempts in 1 hour
- Admin manual lockout

**Lockout Duration:**
- First lockout: 15 minutes
- Second lockout: 1 hour
- Third lockout: 24 hours
- Subsequent lockouts: 7 days

**Unlock Methods:**
- Automatic unlock after duration
- Admin manual unlock
- Password reset (if implemented)

**Implementation:**
- Store failed attempt count in database
- Store lockout timestamp in database
- Check lockout status before authentication
- Admin endpoint to unlock accounts
- Email notification on lockout

**Rationale:**
- Progressive lockout deters persistent attackers
- Automatic unlock prevents permanent lockout
- Admin unlock provides recovery path
- Email notification alerts user to compromise

---

## 9. REACT COMPATIBILITY

### 9.1 Login Flow

**Current React Flow:**
1. User enters email/phone and password
2. React sends POST /api/login
3. React receives token and user data
4. React stores token in localStorage/sessionStorage
5. React redirects to dashboard
6. React includes token in Authorization header for subsequent requests

**Spring Boot Compatibility:**
- Phase 1: No changes required (Sanctum token format)
- Phase 2: React must be updated to handle JWT tokens
- Phase 2: React must implement token refresh logic

**Must Remain Unchanged:**
- Login endpoint URL: /api/login
- Request format: {identifier, password}
- Response format: {user, token} (Phase 1)或 {user, accessToken, refreshToken} (Phase 2)
- User data format: UserResource (snake_case)
- Token storage location (localStorage/sessionStorage)
- Authorization header format: Bearer {token}

### 9.2 Logout Flow

**Current React Flow:**
1. User clicks logout
2. React sends POST /api/logout
3. React removes token from storage
4. React redirects to login page

**Spring Boot Compatibility:**
- No changes required

**Must Remain Unchanged:**
- Logout endpoint URL: /api/logout
- Authorization header format: Bearer {token}
- Response format: {message: "Logged out successfully"}
- Token removal from storage

### 9.3 Token Handling

**Current React Token Handling:**
- Store token in localStorage or sessionStorage
- Include token in Authorization header
- No token refresh (Laravel Sanctum tokens don't expire)

**Spring Boot Compatibility:**
- Phase 1: No changes required
- Phase 2: React must implement token refresh logic
- Phase 2: React must handle token expiration

**Phase 2 Requirements:**
- Store both accessToken and refreshToken
- Implement token refresh before expiration
- Handle token refresh failure (redirect to login)
- Implement automatic token refresh on 401 responses

### 9.4 Protected Routes

**Current React Protected Routes:**
- Check if token exists in storage
- If no token, redirect to login
- If token exists, allow access
- Include token in API requests

**Spring Boot Compatibility:**
- No changes required

**Must Remain Unchanged:**
- Token existence check
- Redirect to login if no token
- Token inclusion in API requests

### 9.5 Refresh Behavior

**Current React Refresh Behavior:**
- No token refresh (Laravel Sanctum tokens don't expire)
- Token persists until logout

**Spring Boot Compatibility:**
- Phase 1: No changes required
- Phase 2: React must implement token refresh

**Phase 2 Requirements:**
- Refresh token before expiration (e.g., 5 minutes before)
- Refresh token on 401 response
- Handle refresh failure (redirect to login)
- Update stored tokens after refresh

### 9.6 Error Handling

**Current React Error Handling:**
- 401: Redirect to login
- 403: Show "Forbidden" message
- 422: Show validation errors
- 500: Show "Server error" message

**Spring Boot Compatibility:**
- No changes required

**Must Remain Unchanged:**
- HTTP status codes
- Error message format
- Error handling logic

### 9.7 Admin Panel Compatibility

**Current Admin Panel Flow:**
- Admin logs in via /api/auth/login
- Receives token and user data in formatUser format (camelCase)
- Stores token
- Accesses admin endpoints

**Must Remain Unchanged:**
- Admin login endpoint URL: /api/auth/login
- Request format: {identifier, password}
- Response format: {token, user} (token first, user second)
- User data format: formatUser (camelCase: isVerified, isActive, createdAt, updatedAt)
- Field name: 'name' instead of 'full_name'

**Rationale:** Admin panel depends on this specific format

---

## 10. FLUTTER COMPATIBILITY

### 10.1 Login Flow

**Current Flutter Flow:**
1. User enters email/phone and password
2. Flutter sends POST /api/login
3. Flutter receives token and user data
4. Flutter stores token in secure storage
5. Flutter navigates to dashboard
6. Flutter includes token in Authorization header for subsequent requests

**Spring Boot Compatibility:**
- Phase 1: No changes required if Flutter supports Sanctum token format
- Phase 2: Flutter must be updated to handle JWT tokens
- Phase 2: Flutter must implement token refresh logic

**Must Remain Unchanged:**
- Login endpoint URL: /api/login
- Request format: {identifier, password}
- Response format: {user, token} (Phase 1)或 {user, accessToken, refreshToken} (Phase 2)
- User data format: UserResource (snake_case)
- Token storage in secure storage
- Authorization header format: Bearer {token}

**Open Question:** Does Flutter expect JWT format or Sanctum format?

### 10.2 Token Handling

**Current Flutter Token Handling:**
- Store token in secure storage (flutter_secure_storage)
- Include token in Authorization header
- No token refresh (Laravel Sanctum tokens don't expire)

**Spring Boot Compatibility:**
- Phase 1: No changes required if Flutter supports Sanctum token format
- Phase 2: Flutter must implement token refresh logic
- Phase 2: Flutter must handle token expiration

**Phase 2 Requirements:**
- Store both accessToken and refreshToken
- Implement token refresh before expiration
- Handle token refresh failure (redirect to login)
- Implement automatic token refresh on 401 responses

### 10.3 Refresh Strategy

**Current Flutter Refresh Strategy:**
- No token refresh (Laravel Sanctum tokens don't expire)

**Spring Boot Compatibility:**
- Phase 1: No changes required
- Phase 2: Flutter must implement token refresh

**Phase 2 Requirements:**
- Refresh token before expiration (e.g., 5 minutes before)
- Refresh token on 401 response
- Handle refresh failure (redirect to login)
- Update stored tokens after refresh
- Background refresh for better UX

### 10.4 Offline Behavior

**Current Flutter Offline Behavior:**
- May cache data locally
- May queue requests for when online
- Token validation requires network

**Spring Boot Compatibility:**
- No changes required

**Considerations:**
- Token validation requires network
- Offline mode may need cached token validation
- Consider offline token validation strategy

### 10.5 Error Handling

**Current Flutter Error Handling:**
- 401: Redirect to login
- 403: Show "Forbidden" message
- 422: Show validation errors
- 500: Show "Server error" message

**Spring Boot Compatibility:**
- No changes required

**Must Remain Unchanged:**
- HTTP status codes
- Error message format
- Error handling logic

---

## 11. TESTING STRATEGY

### 11.1 Unit Tests

**Service Layer Tests:**

| Test Case | Description | Priority |
|-----------|-------------|----------|
| Register with valid data | Successful registration | P0 |
| Register with duplicate phone | Should fail validation | P0 |
| Register with duplicate email | Should fail validation | P0 |
| Register with invalid role | Should fail validation | P0 |
| Login with valid credentials | Successful login | P0 |
| Login with invalid credentials | Should fail authentication | P0 |
| Login with inactive account | Should fail authentication | P0 |
| Login with unverified driver | Should fail authentication | P0 |
| Logout with valid token | Successful logout | P0 |
| Logout with invalid token | Should fail authentication | P0 |
| Change password with valid current password | Successful password change | P1 |
| Change password with invalid current password | Should fail validation | P1 |
| Update profile with valid data | Successful update | P1 |
| Update profile with duplicate phone | Should fail validation | P1 |
| Admin login with valid credentials | Successful admin login | P0 |
| Admin login with non-admin credentials | Should fail authorization | P0 |

**Repository Layer Tests:**

| Test Case | Description | Priority |
|-----------|-------------|----------|
| Find user by email | Should return user | P0 |
| Find user by phone | Should return user | P0 |
| Find user by non-existent email | Should return empty | P0 |
| Check phone uniqueness | Should return true if unique | P0 |
| Check email uniqueness | Should return true if unique | P0 |
| Create user with valid data | Should create user | P0 |
| Update user password | Should update password | P0 |
| Activate user | Should set is_active=true | P1 |
| Deactivate user | Should set is_active=false | P1 |
| Verify driver | Should set verification_status=true | P1 |

**Mapper Tests:**

| Test Case | Description | Priority |
|-----------|-------------|----------|
| Map registration request to entity | Should map correctly | P1 |
| Map entity to user response | Should map correctly | P1 |
| Map entity to formatUser response | Should map correctly (camelCase) | P1 |

### 11.2 Integration Tests

**Database Integration Tests:**

| Test Case | Description | Priority |
|-----------|-------------|----------|
| Register and login flow | End-to-end test | P0 |
| Token validation | Test token validation against database | P0 |
| Token revocation | Test token deletion on logout | P0 |
| Password change flow | End-to-end test | P1 |
| Profile update flow | End-to-end test | P1 |
| Driver verification flow | End-to-end test | P1 |

**Security Integration Tests:**

| Test Case | Description | Priority |
|-----------|-------------|----------|
| Access protected endpoint without token | Should return 401 | P0 |
| Access protected endpoint with invalid token | Should return 401 | P0 |
| Access admin endpoint without admin role | Should return 403 | P0 |
| Access driver endpoint without verification | Should return 403 | P0 |
| Access user's own data | Should succeed | P0 |
| Access another user's data | Should return 403 | P0 |

**Rate Limiting Tests:**

| Test Case | Description | Priority |
|-----------|-------------|----------|
| Login rate limiting | Should block after 5 attempts | P1 |
| Registration rate limiting | Should block after 10 attempts | P1 |
| API rate limiting | Should block after 100 requests | P2 |

### 11.3 Security Tests

| Test Case | Description | Priority |
|-----------|-------------|----------|
| SQL injection attempt | Should be prevented | P0 |
| XSS attempt | Should be prevented | P0 |
| CSRF attempt | Should be prevented (if enabled) | P2 |
| Token theft detection | Should detect token reuse | P1 |
| Brute force attack | Should be prevented by rate limiting | P0 |
| Password hashing verification | Should use BCrypt | P0 |
| Token expiration | Should expire after configured time | P1 |
| Token revocation | Should revoke token on logout | P0 |

### 11.4 API Tests

**Endpoint Tests:**

| Endpoint | Method | Test Cases | Priority |
|----------|--------|------------|----------|
| /api/register | POST | Valid data, duplicate phone, duplicate email, invalid role | P0 |
| /api/login | POST | Valid credentials, invalid credentials, inactive account | P0 |
| /api/auth/login | POST | Valid admin credentials, non-admin credentials | P0 |
| /api/logout | POST | Valid token, invalid token | P0 |
| /api/me | GET | Valid token, invalid token | P0 |
| /api/me | PATCH | Valid data, duplicate phone, invalid data | P1 |
| /api/me/password | PATCH | Valid current password, invalid current password | P1 |

**Validation Tests:**

| Test Case | Description | Priority |
|-----------|-------------|----------|
| Phone uniqueness validation | Should enforce uniqueness | P0 |
| Email uniqueness validation | Should enforce uniqueness | P0 |
| Password minimum length | Should enforce minimum length | P0 |
| Role enum validation | Should enforce valid roles | P0 |
| Required field validation | Should enforce required fields | P0 |

**Error Handling Tests:**

| Test Case | Description | Priority |
|-----------|-------------|----------|
| 401 response format | Should match Laravel format | P0 |
| 403 response format | Should match Laravel format | P0 |
| 422 response format | Should match Laravel format | P0 |
| Error message consistency | Should match Laravel messages | P0 |

### 11.5 Manual Tests

**React Frontend Tests:**

| Feature | Test Steps | Expected Result | Priority |
|---------|------------|-----------------|----------|
| Registration | Register new user via React | User created, token received, redirect to dashboard | P0 |
| Login | Login via React | Token received, redirect to dashboard | P0 |
| Logout | Logout via React | Token removed, redirect to login | P0 |
| Profile update | Update profile via React | Profile updated, data refreshed | P1 |
| Password change | Change password via React | Password changed, re-login required | P1 |
| Token expiration | Wait for token expiration (Phase 2) | Auto-refresh or redirect to login | P1 |
| Admin login | Login via admin panel | Token received, redirect to admin dashboard | P0 |

**Flutter App Tests:**

| Feature | Test Steps | Expected Result | Priority |
|---------|------------|-----------------|----------|
| Registration | Register new user via Flutter | User created, token received, navigate to dashboard | P0 |
| Login | Login via Flutter | Token received, navigate to dashboard | P0 |
| Logout | Logout via Flutter | Token removed, navigate to login | P0 |
| Token storage | Verify token in secure storage | Token stored securely | P0 |
| Token refresh | Wait for token expiration (Phase 2) | Auto-refresh or navigate to login | P1 |

**Performance Tests:**

| Metric | Target | Priority |
|--------|--------|----------|
| Login response time | < 500ms | P1 |
| Registration response time | < 500ms | P1 |
| Token validation time | < 100ms | P1 |
| Logout response time | < 200ms | P2 |

---

## 12. MIGRATION CHECKLIST

### 12.1 Phase 1: Sanctum Compatibility (Initial Migration)

**Database Setup:**
- [ ] Verify personal_access_tokens table exists
- [ ] Verify users table has all required columns
- [ ] Verify role enum includes all values
- [ ] Verify foreign key constraints are correct
- [ ] Test database connectivity

**Configuration Setup:**
- [ ] Configure BCryptPasswordEncoder
- [ ] Configure security filter chain
- [ ] Configure CORS policy
- [ ] Configure rate limiting
- [ ] Configure Sanctum authentication filter
- [ ] Configure custom authentication provider

**Entity Implementation:**
- [ ] Create User entity
- [ ] Create PersonalAccessToken entity
- [ ] Define relationships
- [ ] Implement password hashing
- [ ] Implement field casts

**Repository Implementation:**
- [ ] Create UserRepository
- [ ] Create PersonalAccessTokenRepository
- [ ] Implement findByEmail
- [ ] Implement findByPhone
- [ ] Implement findByToken
- [ ] Implement uniqueness checks

**DTO Implementation:**
- [ ] Create RegisterRequest DTO
- [ ] Create LoginRequest DTO
- [ ] Create ChangePasswordRequest DTO
- [ ] Create UpdateProfileRequest DTO
- [ ] Create UserResponse DTO (UserResource format)
- [ ] Create AdminUserResponse DTO (formatUser format)
- [ ] Add validation annotations

**Mapper Implementation:**
- [ ] Create UserMapper (MapStruct)
- [ ] Implement toEntity (RegisterRequest)
- [ ] Implement toResponse (User)
- [ ] Implement toAdminResponse (User)
- [ ] Test field mapping (snake_case vs camelCase)

**Service Implementation:**
- [ ] Create AuthService interface
- [ ] Create AuthServiceImpl
- [ ] Implement register method
- [ ] Implement login method
- [ ] Implement logout method
- [ ] Implement getCurrentUser method
- [ ] Implement changePassword method
- [ ] Implement updateProfile method
- [ ] Implement admin login method
- [ ] Implement driver activation logic
- [ ] Implement status checks

**Controller Implementation:**
- [ ] Create AuthController
- [ ] Implement POST /api/register
- [ ] Implement POST /api/login
- [ ] Implement POST /api/logout
- [ ] Implement GET /api/me
- [ ] Implement PATCH /api/me
- [ ] Implement MATCH /api/me/password
- [ ] Create AdminAuthController
- [ ] Implement POST /api/auth/login
- [ ] Add security annotations
- [ ] Add rate limiting annotations

**Security Implementation:**
- [ ] Implement SanctumAuthenticationFilter
- [ ] Implement CustomAuthenticationProvider
- [ ] Implement AdminMiddleware (role check)
- [ ] Implement rate limiting filter
- [ ] Configure endpoint security
- [ ] Test authentication flow
- [ ] Test authorization flow

**Exception Handling:**
- [ ] Create custom exception classes
- [ ] Implement global exception handler
- [ ] Map exceptions to HTTP status codes
- [ ] Preserve Laravel error messages
- [ ] Test error responses

**Testing:**
- [ ] Write unit tests for services
- [ ] Write unit tests for repositories
- [ ] Write unit tests for mappers
- [ ] Write integration tests for authentication flow
- [ ] Write integration tests for authorization
- [ ] Write API tests for all endpoints
- [ ] Test with React frontend
- [ ] Test with Flutter app
- [ ] Test with admin panel

**Documentation:**
- [ ] Update API documentation
- [ ] Document authentication flow
- [ ] Document token format
- [ ] Document error responses
- [ ] Document rate limiting rules

### 12.2 Phase 2: JWT Migration (Post-Initial Migration)

**JWT Configuration:**
- [ ] Configure JWT secret key
- [ ] Configure JWT expiration times
- [ ] Configure JWT signing algorithm
- [ ] Configure JWT claims
- [ ] Configure refresh token storage

**JWT Filter Implementation:**
- [ ] Implement JwtAuthenticationFilter
- [ ] Implement JWT validation logic
- [ ] Implement token extraction
- [ ] Implement expiration check
- [ ] Implement revocation check (optional)

**Token Service Implementation:**
- [ ] Create TokenService
- [ ] Implement access token generation
- [ ] Implement refresh token generation
- [ ] Implement token validation
- [ ] Implement token refresh
- [ ] Implement token revocation
- [ ] Implement token rotation

**Refresh Token Storage:**
- [ ] Create refresh_tokens table
- [ ] Create RefreshToken entity
- [ ] Create RefreshTokenRepository
- [ ] Implement refresh token CRUD
- [ ] Implement refresh token cleanup

**Frontend Updates:**
- [ ] Update React to handle JWT tokens
- [ ] Implement token refresh logic in React
- [ ] Update Flutter to handle JWT tokens
- [ ] Implement token refresh logic in Flutter
- [ ] Test token refresh flow
- [ ] Test token expiration handling

**Deprecation:**
- [ ] Deprecate Sanctum token generation
- [ ] Maintain Sanctum token validation
- [ ] Monitor Sanctum token usage
- [ ] Plan Sanctum token removal
- [ ] Remove Sanctum dependencies

**Testing:**
- [ ] Write unit tests for JWT filter
- [ ] Write unit tests for token service
- [ ] Write integration tests for JWT flow
- [ ] Write integration tests for token refresh
- [ ] Write integration tests for token revocation
- [ ] Test with React frontend
- [ ] Test with Flutter app
- [ ] Test token expiration
- [ ] Test token refresh

**Documentation:**
- [ ] Update API documentation
- [ ] Document JWT token format
- [ ] Document token refresh flow
- [ ] Document token revocation
- [ ] Update frontend documentation

### 12.3 Verification Steps

**Pre-Deployment Verification:**
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] All API tests pass
- [ ] React frontend works correctly
- [ ] Flutter app works correctly
- [ ] Admin panel works correctly
- [ ] Rate limiting works correctly
- [ ] Error responses match Laravel format
- [ ] Token validation works correctly
- [ ] Authorization works correctly

**Deployment Verification:**
- [ ] Deploy to staging environment
- [ ] Test all authentication flows
- [ ] Test with React frontend
- [ ] Test with Flutter app
- [ ] Test with admin panel
- [ ] Monitor error logs
- [ ] Monitor performance metrics
- [ ] Verify rate limiting
- [ ] Verify security headers

**Post-Deployment Verification:**
- [ ] Monitor authentication success rate
- [ ] Monitor authentication failure rate
- [ ] Monitor error rates
- [ ] Monitor performance metrics
- [ ] Collect user feedback
- [ ] Address any issues
- [ ] Prepare rollback plan

---

## CONCLUSION

This design blueprint provides a comprehensive plan for migrating the Authentication module from Laravel to Spring Boot. The key decisions are:

1. **Two-Phase Migration**: Maintain Laravel Sanctum compatibility initially, then migrate to JWT
2. **API Compatibility**: Preserve Laravel API contracts to avoid breaking changes
3. **Response Format Preservation**: Maintain UserResource (snake_case) for regular endpoints and formatUser (camelCase) for admin endpoints
4. **Security**: Use BCrypt for password hashing, implement rate limiting, and account lockout
5. **Testing**: Comprehensive testing strategy including unit, integration, security, and manual tests

The implementation checklist provides an ordered, independently reviewable set of tasks for both phases of the migration.

---

**Document Status:** Active  
**Last Updated:** July 8, 2026  
**Next Review:** Before implementation begins  
**Owner:** [To be assigned]  
**Reviewers:** [To be assigned]
