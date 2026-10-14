# API Transition Guide
## EthioloadAI Laravel to Spring Boot Migration

**Document Version:** 1.0  
**Created:** July 9, 2026  
**Purpose:** Guide for migrating from Laravel API to Spring Boot API  
**Status:** Ready for Implementation

---

## EXECUTIVE SUMMARY

This document provides a comprehensive guide for migrating the authentication API from Laravel to Spring Boot. The Spring Boot API will become the new source of truth, with standardized endpoint paths and JWT authentication replacing Laravel Sanctum.

**Total Endpoints:** 6  
**Breaking Changes:** 0  
**Minor Changes:** 4  
**Frontend Files to Update:** 4 (React) + 2 (Flutter)  
**Migration Effort:** Medium (mostly URL path updates and token format handling)

---

## 1. ENDPOINT MAPPING

### 1.1 POST /api/auth/register

**Laravel Endpoint:** `POST /api/register`  
**Spring Boot Endpoint:** `POST /api/auth/register`  
**HTTP Method:** POST (unchanged)

#### Request Changes
- **Laravel Request:**
  ```json
  {
    "full_name": "John Doe",
    "phone": "+251911234567",
    "email": "john@example.com",
    "password": "password123",
    "role": "shipper"
  }
  ```
- **Spring Boot Request:** Same format
- **Changes:** None

#### Response Changes
- **Laravel Response:**
  ```json
  {
    "user": { ... },
    "token": "1|sanctum_token_here"
  }
  ```
- **Spring Boot Response:**
  ```json
  {
    "user": { ... },
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
  ```
- **Changes:** Token format changed from Sanctum to JWT

#### Authentication Changes
- **Laravel:** No authentication required
- **Spring Boot:** No authentication required
- **Changes:** None

#### Breaking Change: No  
#### Required Frontend Updates:
- Update endpoint URL from `/api/register` to `/api/auth/register`
- Update token handling to use JWT format (Phase 2)

---

### 1.2 POST /api/auth/login

**Laravel Endpoint:** `POST /api/login`  
**Spring Boot Endpoint:** `POST /api/auth/login`  
**HTTP Method:** POST (unchanged)

#### Request Changes
- **Laravel Request:**
  ```json
  {
    "identifier": "john@example.com",
    "password": "password123"
  }
  ```
- **Spring Boot Request:** Same format
- **Changes:** None

#### Response Changes
- **Laravel Response:**
  ```json
  {
    "user": { ... },
    "token": "1|sanctum_token_here"
  }
  ```
- **Spring Boot Response:**
  ```json
  {
    "user": { ... },
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
  ```
- **Changes:** Token format changed from Sanctum to JWT

#### Authentication Changes
- **Laravel:** No authentication required
- **Spring Boot:** No authentication required
- **Changes:** None

#### Breaking Change: No  
#### Required Frontend Updates:
- Update endpoint URL from `/api/login` to `/api/auth/login`
- Update token handling to use JWT format (Phase 2)

---

### 1.3 POST /api/auth/logout

**Laravel Endpoint:** `POST /api/logout`  
**Spring Boot Endpoint:** `POST /api/auth/logout`  
**HTTP Method:** POST (unchanged)

#### Request Changes
- **Laravel Request:** None (token from middleware)
- **Spring Boot Request:** None (token from Authorization header)
- **Changes:** None

#### Response Changes
- **Laravel Response:**
  ```json
  {
    "message": "Logged out successfully"
  }
  ```
- **Spring Boot Response:**
  ```json
  {
    "success": true,
    "message": "Logged out successfully"
  }
  ```
- **Changes:** Added `success` field for consistency

#### Authentication Changes
- **Laravel:** auth:sanctum middleware
- **Spring Boot:** JWT authentication required
- **Changes:** Implementation detail (both require authentication)

#### Breaking Change: No  
#### Required Frontend Updates:
- Update endpoint URL from `/api/logout` to `/api/auth/logout`
- Handle `success` field in response (optional)

---

### 1.4 GET /api/auth/me

**Laravel Endpoint:** `GET /api/me`  
**Spring Boot Endpoint:** `GET /api/auth/me`  
**HTTP Method:** GET (unchanged)

#### Request Changes
- **Laravel Request:** None
- **Spring Boot Request:** None
- **Changes:** None

#### Response Changes
- **Laravel Response:**
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
- **Spring Boot Response:** Same format
- **Changes:** None

#### Authentication Changes
- **Laravel:** auth:sanctum middleware
- **Spring Boot:** JWT authentication required
- **Changes:** Implementation detail (both require authentication)

#### Breaking Change: No  
#### Required Frontend Updates:
- Update endpoint URL from `/api/me` to `/api/auth/me`

---

### 1.5 PATCH /api/auth/me

**Laravel Endpoint:** `PATCH /api/me`  
**Spring Boot Endpoint:** `PATCH /api/auth/me`  
**HTTP Method:** PATCH (unchanged)

#### Request Changes
- **Laravel Request:**
  ```json
  {
    "name": "John Updated",
    "full_name": "John Updated",
    "phone": "+251911234568",
    "address": "New Address",
    "business_name": "New Business"
  }
  ```
- **Spring Boot Request:** Same format
- **Changes:** None

#### Response Changes
- **Laravel Response:**
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
- **Spring Boot Response:** Same format
- **Changes:** None

#### Authentication Changes
- **Laravel:** auth:sanctum middleware
- **Spring Boot:** JWT authentication required
- **Changes:** Implementation detail (both require authentication)

#### Breaking Change: No  
#### Required Frontend Updates:
- Update endpoint URL from `/api/me` to `/api/auth/me`

---

### 1.6 PATCH /api/auth/me/password

**Laravel Endpoint:** `PATCH /api/me/password`  
**Spring Boot Endpoint:** `PATCH /api/auth/me/password`  
**HTTP Method:** PATCH (unchanged)

#### Request Changes
- **Laravel Request:**
  ```json
  {
    "current_password": "oldpassword",
    "new_password": "newpassword"
  }
  ```
- **Spring Boot Request:** Same format
- **Changes:** None

#### Response Changes
- **Laravel Response:**
  ```json
  {
    "success": true,
    "message": "Password changed successfully."
  }
  ```
- **Spring Boot Response:**
  ```json
  {
    "success": true,
    "message": "Password changed successfully."
  }
  ```
- **Changes:** None

#### Authentication Changes
- **Laravel:** auth:sanctum middleware
- **Spring Boot:** JWT authentication required
- **Changes:** Implementation detail (both require authentication)

#### Breaking Change: No  
#### Required Frontend Updates:
- Update endpoint URL from `/api/me/password` to `/api/auth/me/password`
- Handle new validation error: "New password must be different from current password"

---

## 2. REACT MIGRATION

### 2.1 Files Requiring Updates

#### File 1: `frontend/artifacts/freight-link/src/lib/api.ts`

**Current Endpoint Usage:**
- BASE_URL: `/api`
- Token extraction: `localStorage.getItem("freightlink_auth")`
- Authorization header: `Bearer ${token}`

**Required Changes:**
1. **Update BASE_URL:** Keep as `/api` (no change needed)
2. **Update token handling:** No change needed (JWT uses same Bearer format)
3. **Add error handling:** Handle new `code` field in error responses (optional)

**Estimated Impact:** Low (5 minutes)

**Code Changes:**
```typescript
// No changes required for BASE_URL
// Token handling remains the same
// Optional: Handle code field in error responses
```

---

#### File 2: `frontend/artifacts/freight-link/src/contexts/auth-context.tsx`

**Current Endpoint Usage:**
- Token storage: `localStorage.getItem("freightlink_auth")`
- User normalization: Maps `full_name` to `name`, `verification_status` to `isVerified`

**Required Changes:**
1. **Update token handling:** No change needed (JWT uses same storage)
2. **Update user normalization:** No change needed (Spring Boot returns same format)
3. **Add success field handling:** Handle `success` field in logout response (optional)

**Estimated Impact:** Low (5 minutes)

**Code Changes:**
```typescript
// No changes required for token storage
// User normalization remains the same
// Optional: Handle success field in logout response
```

---

#### File 3: `frontend/artifacts/freight-link/src/pages/login.tsx`

**Current Endpoint:** `/auth/login`  
**New Endpoint:** `/auth/login`

**Required Changes:**
1. **Update endpoint URL:** Change from `/auth/login` to `/auth/login` (no change needed)
2. **Update token handling:** No change needed (JWT uses same format)
3. **Handle new validation errors:** Handle phone pattern validation errors

**Estimated Impact:** Low (10 minutes)

**Code Changes:**
```typescript
// Line 27: Update endpoint
const data = await api.post<{ token: string; user: any }>("/auth/login", form);
// No change needed - endpoint already uses /auth/login

// Optional: Handle phone pattern validation errors
```

---

#### File 4: `frontend/artifacts/freight-link/src/pages/register.tsx`

**Current Endpoint:** `/register`  
**New Endpoint:** `/auth/register`

**Required Changes:**
1. **Update endpoint URL:** Change from `/register` to `/auth/register`
2. **Update token handling:** No change needed (JWT uses same format)
3. **Handle new validation errors:** Handle phone pattern validation errors

**Estimated Impact:** Low (10 minutes)

**Code Changes:**
```typescript
// Line 25: Update endpoint
const data = await api.post<{ token: string; user: any }>("/auth/register", {
  full_name: form.name,
  email: form.email,
  phone: form.phone,
  password: form.password,
  role: "shipper",
});
```

---

### 2.2 React Migration Recommendation

**Centralize Endpoints:**

Currently, endpoints are hardcoded in individual components. Recommend creating a centralized API constants file:

**New File:** `frontend/artifacts/freight-link/src/lib/api-endpoints.ts`

```typescript
export const API_ENDPOINTS = {
  AUTH: {
    REGISTER: "/auth/register",
    LOGIN: "/auth/login",
    LOGOUT: "/auth/logout",
    ME: "/auth/me",
    UPDATE_PROFILE: "/auth/me",
    CHANGE_PASSWORD: "/auth/me/password",
  },
} as const;
```

**Benefits:**
- Single source of truth for endpoint URLs
- Easy to update endpoints in one place
- Type-safe endpoint references
- Better maintainability

**Migration Steps:**
1. Create `api-endpoints.ts` file
2. Update `login.tsx` to use `API_ENDPOINTS.AUTH.LOGIN`
3. Update `register.tsx` to use `API_ENDPOINTS.AUTH.REGISTER`
4. Update other auth components as needed

---

## 3. FLUTTER MIGRATION

### 3.1 Files Requiring Updates

#### File 1: `frontend/artifacts/mobile-app/lib/src/data/api/api_client.dart`

**Current Endpoint Usage:**
- BASE_URL: `http://127.0.0.1:8000/api`
- Token storage: `FlutterSecureStorage` with key `auth_token`
- Authorization header: `Bearer $token`

**Required Changes:**
1. **Update BASE_URL:** Change from `http://127.0.0.1:8000/api` to Spring Boot URL (e.g., `http://127.0.0.1:8080/api`)
2. **Update token handling:** No change needed (JWT uses same storage and Bearer format)
3. **Add error handling:** Handle new `code` field in error responses (optional)

**Estimated Impact:** Low (10 minutes)

**Code Changes:**
```dart
// Line 16: Update BASE_URL
String get kBaseUrl {
  if (_kApiBaseUrlOverride.isNotEmpty) return _kApiBaseUrlOverride;
  
  if (kIsWeb) {
    return 'http://127.0.0.1:8080/api'; // Changed from 8000 to 8080
  }
  
  return 'http://127.0.0.1:8080/api'; // Changed from 8000 to 8080
}

// Token handling remains the same
// Optional: Handle code field in error responses
```

---

#### File 2: `frontend/artifacts/mobile-app/lib/src/features/auth/auth_screens.dart`

**Current Endpoint Usage:**
- Login: Uses authNotifierProvider (endpoint defined in provider)
- Register: Uses authNotifierProvider (endpoint defined in provider)

**Required Changes:**
1. **Update authNotifierProvider:** Update endpoint URLs in auth notifier
2. **Update token handling:** No change needed (JWT uses same format)
3. **Handle new validation errors:** Handle phone pattern validation errors

**Estimated Impact:** Medium (20 minutes)

**Code Changes:**
```dart
// Need to update authNotifierProvider (in data_providers.dart)
// Update login endpoint from /login to /auth/login
// Update register endpoint from /register to /auth/register
// Update logout endpoint from /logout to /auth/logout
// Update me endpoint from /me to /auth/me
```

---

### 3.2 Flutter Migration Recommendation

**Centralize Endpoints:**

Currently, endpoints are likely defined in data providers. Recommend creating a centralized endpoint constants file:

**New File:** `frontend/artifacts/mobile-app/lib/src/data/api/api_endpoints.dart`

```dart
class ApiEndpoints {
  static const String auth = '/auth';
  
  static const String register = '$auth/register';
  static const String login = '$auth/login';
  static const String logout = '$auth/logout';
  static const String me = '$auth/me';
  static const String changePassword = '$auth/me/password';
}
```

**Benefits:**
- Single source of truth for endpoint URLs
- Easy to update endpoints in one place
- Type-safe endpoint references
- Better maintainability

**Migration Steps:**
1. Create `api_endpoints.dart` file
2. Update authNotifierProvider to use `ApiEndpoints`
3. Update other providers as needed

---

## 4. AUTHENTICATION CHANGES

### 4.1 Sanctum → JWT Transition

#### Laravel Sanctum (Current)
- **Token Format:** Plain text token stored in database
- **Token Storage:** `personal_access_tokens` table
- **Token Validation:** Database lookup
- **Token Expiration:** Configurable (default: never)
- **Token Revocation:** Database deletion

#### Spring Boot JWT (New)
- **Token Format:** JWT (JSON Web Token)
- **Token Storage:** Stateless (no database storage)
- **Token Validation:** Cryptographic signature verification
- **Token Expiration:** Configurable (default: 1 hour)
- **Token Revocation:** Redis blacklist (future implementation)

#### Migration Impact
- **Frontend:** Token format changes from plain text to JWT
- **Backend:** Token validation changes from database lookup to signature verification
- **Security:** JWT is more secure (stateless, cryptographic)
- **Performance:** JWT is faster (no database lookup)

---

### 4.2 Token Storage Changes

#### React
- **Current:** `localStorage.getItem("freightlink_auth")`
- **New:** Same (no change needed)
- **Format:** JWT token instead of Sanctum token
- **Impact:** Low (storage mechanism unchanged)

#### Flutter
- **Current:** `FlutterSecureStorage` with key `auth_token`
- **New:** Same (no change needed)
- **Format:** JWT token instead of Sanctum token
- **Impact:** Low (storage mechanism unchanged)

---

### 4.3 Authorization Header Format

#### Current (Laravel)
```
Authorization: Bearer 1|sanctum_token_here
```

#### New (Spring Boot)
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### Changes
- **Format:** Same (`Bearer {token}`)
- **Token Content:** Changed from Sanctum to JWT
- **Impact:** Low (header format unchanged)

---

### 4.4 Logout Behavior

#### Laravel (Current)
- **Action:** Delete token from `personal_access_tokens` table
- **Result:** Token immediately revoked
- **Status:** Implemented

#### Spring Boot (New)
- **Action:** Placeholder (no token revocation yet)
- **Result:** Token remains valid until expiration
- **Status:** Not implemented (deferred to future milestone)

#### Migration Impact
- **Frontend:** No change needed (logout still removes token from storage)
- **Backend:** Token revocation not implemented (security risk)
- **Mitigation:** Token expiration (1 hour) limits exposure
- **Future:** Implement Redis blacklist for token revocation

---

### 4.5 Refresh Token Strategy

#### Laravel (Current)
- **Refresh Tokens:** Not implemented
- **Token Expiration:** Never (unless manually revoked)
- **User Experience:** No re-login required

#### Spring Boot (New)
- **Refresh Tokens:** Not implemented (deferred to future milestone)
- **Token Expiration:** 1 hour (configurable)
- **User Experience:** Re-login required after 1 hour

#### Migration Impact
- **Frontend:** Users will need to re-login after 1 hour
- **Backend:** Implement refresh tokens in future milestone
- **Mitigation:** Increase token expiration to 24 hours (temporary)
- **Future:** Implement refresh token mechanism

---

## 5. COMPATIBILITY RISKS

### 5.1 React Risks

#### Risk 1: Endpoint URL Changes
- **Description:** All authentication endpoints have `/auth` prefix
- **Impact:** Medium - All auth endpoints will fail if not updated
- **Mitigation:** Update all endpoint URLs in React components
- **Priority:** High

#### Risk 2: Token Format Changes
- **Description:** Token format changes from Sanctum to JWT
- **Impact:** Medium - Token validation will fail if not updated
- **Mitigation:** No change needed (JWT uses same Bearer format)
- **Priority:** Low

#### Risk 3: New Validation Errors
- **Description:** Phone pattern validation and new password validation
- **Impact:** Low - New validation errors may confuse users
- **Mitigation:** Update error messages to handle new validation errors
- **Priority:** Medium

#### Risk 4: Error Format Changes
- **Description:** Error responses include `code` field
- **Impact:** Low - Error handling may need updates
- **Mitigation:** Handle `code` field in error responses (optional)
- **Priority:** Low

---

### 5.2 Flutter Risks

#### Risk 1: Base URL Changes
- **Description:** Base URL changes from port 8000 to 8080
- **Impact:** High - All API calls will fail if not updated
- **Mitigation:** Update BASE_URL in api_client.dart
- **Priority:** High

#### Risk 2: Endpoint URL Changes
- **Description:** All authentication endpoints have `/auth` prefix
- **Impact:** Medium - All auth endpoints will fail if not updated
- **Mitigation:** Update all endpoint URLs in Flutter providers
- **Priority:** High

#### Risk 3: Token Format Changes
- **Description:** Token format changes from Sanctum to JWT
- **Impact:** Medium - Token validation will fail if not updated
- **Mitigation:** No change needed (JWT uses same Bearer format)
- **Priority:** Low

#### Risk 4: New Validation Errors
- **Description:** Phone pattern validation and new password validation
- **Impact:** Low - New validation errors may confuse users
- **Mitigation:** Update error messages to handle new validation errors
- **Priority:** Medium

---

### 5.3 API Risks

#### Risk 1: Token Revocation Not Implemented
- **Description:** Logout does not revoke tokens
- **Impact:** Medium - Compromised tokens remain valid until expiration
- **Mitigation:** Token expiration (1 hour) limits exposure
- **Priority:** High (implement in future milestone)

#### Risk 2: Refresh Tokens Not Implemented
- **Description:** Users must re-login after token expiration
- **Impact:** Medium - User experience degraded
- **Mitigation:** Increase token expiration to 24 hours (temporary)
- **Priority:** High (implement in future milestone)

#### Risk 3: Account Lockout Not Implemented
- **Description:** No account lockout after failed login attempts
- **Impact:** Medium - Vulnerable to brute force attacks
- **Mitigation:** Rate limiting (5/min) provides some protection
- **Priority:** Medium (implement in future milestone)

---

### 5.4 Deployment Risks

#### Risk 1: Backend Port Conflict
- **Description:** Laravel uses port 8000, Spring Boot uses port 8080
- **Impact:** High - Frontend will fail to connect if port not updated
- **Mitigation:** Update frontend BASE_URL to use port 8080
- **Priority:** High

#### Risk 2: Database Migration
- **Description:** Spring Boot uses Flyway, Laravel uses migrations
- **Impact:** High - Database schema may be incompatible
- **Mitigation:** Verify Flyway migrations match Laravel schema
- **Priority:** High

#### Risk 3: Environment Variables
- **Description:** Spring Boot requires JWT_SECRET environment variable
- **Impact:** High - Application will fail to start if not set
- **Mitigation:** Set JWT_SECRET in environment configuration
- **Priority:** High

#### Risk 4: CORS Configuration
- **Description:** Spring Boot CORS may block frontend requests
- **Impact:** High - Frontend will fail to connect
- **Mitigation:** Configure CORS to allow frontend origin
- **Priority:** High

---

## 6. MIGRATION CHECKLIST

### 6.1 Backend Preparation

#### Phase 1: Configuration
- [ ] Set JWT_SECRET environment variable (minimum 32 characters)
- [ ] Configure CORS to allow frontend origin
- [ ] Verify Flyway migrations match Laravel schema
- [ ] Configure rate limiting (login: 5/min, registration: 10/hour, API: 100/min)
- [ ] Configure JWT expiration (1 hour or 24 hours)
- [ ] Test backend startup and health check

#### Phase 2: Database
- [ ] Run Flyway migrations
- [ ] Verify users table schema matches Laravel
- [ ] Verify data migration (if needed)
- [ ] Test database connectivity

#### Phase 3: Security
- [ ] Verify JWT secret is set
- [ ] Verify password encoder is configured
- [ ] Verify rate limiting is active
- [ ] Verify CORS is configured
- [ ] Test authentication flow

---

### 6.2 React Migration

#### Phase 1: API Client
- [ ] Update BASE_URL if needed (keep as `/api`)
- [ ] Test token extraction from localStorage
- [ ] Test Authorization header format
- [ ] Test error handling with new format

#### Phase 2: Endpoint Updates
- [ ] Create `api-endpoints.ts` file
- [ ] Update `login.tsx` to use `/auth/login`
- [ ] Update `register.tsx` to use `/auth/register`
- [ ] Update other auth components to use new endpoints
- [ ] Update logout endpoint to `/auth/logout`
- [ ] Update me endpoint to `/auth/me`
- [ ] Update password change endpoint to `/auth/me/password`

#### Phase 3: Error Handling
- [ ] Handle phone pattern validation errors
- [ ] Handle new password validation errors
- [ ] Handle `code` field in error responses (optional)
- [ ] Test error messages display correctly

#### Phase 4: Testing
- [ ] Test registration with new endpoint
- [ ] Test login with new endpoint
- [ ] Test logout with new endpoint
- [ ] Test get current user with new endpoint
- [ ] Test update profile with new endpoint
- [ ] Test change password with new endpoint
- [ ] Test token storage and retrieval
- [ ] Test error handling

---

### 6.3 Flutter Migration

#### Phase 1: API Client
- [ ] Update BASE_URL from port 8000 to 8080
- [ ] Test token extraction from secure storage
- [ ] Test Authorization header format
- [ ] Test error handling with new format

#### Phase 2: Endpoint Updates
- [ ] Create `api_endpoints.dart` file
- [ ] Update authNotifierProvider to use `/auth/login`
- [ ] Update authNotifierProvider to use `/auth/register`
- [ ] Update authNotifierProvider to use `/auth/logout`
- [ ] Update authNotifierProvider to use `/auth/me`
- [ ] Update authNotifierProvider to use `/auth/me/password`

#### Phase 3: Error Handling
- [ ] Handle phone pattern validation errors
- [ ] Handle new password validation errors
- [ ] Handle `code` field in error responses (optional)
- [ ] Test error messages display correctly

#### Phase 4: Testing
- [ ] Test registration with new endpoint
- [ ] Test login with new endpoint
- [ ] Test logout with new endpoint
- [ ] Test get current user with new endpoint
- [ ] Test update profile with new endpoint
- [ ] Test change password with new endpoint
- [ ] Test token storage and retrieval
- [ ] Test error handling

---

### 6.4 Manual Verification

#### Phase 1: Backend Verification
- [ ] Verify backend is running on port 8080
- [ ] Verify health endpoint is accessible
- [ ] Verify OpenAPI documentation is accessible
- [ ] Verify rate limiting is active
- [ ] Verify CORS is configured

#### Phase 2: React Verification
- [ ] Verify React app connects to backend
- [ ] Verify registration works
- [ ] Verify login works
- [ ] Verify logout works
- [ ] Verify token is stored correctly
- [ ] Verify user data is displayed correctly
- [ ] Verify error messages display correctly

#### Phase 3: Flutter Verification
- [ ] Verify Flutter app connects to backend
- [ ] Verify registration works
- [ ] Verify login works
- [ ] Verify logout works
- [ ] Verify token is stored correctly
- [ ] Verify user data is displayed correctly
- [ ] Verify error messages display correctly

#### Phase 4: Integration Testing
- [ ] Test end-to-end registration flow
- [ ] Test end-to-end login flow
- [ ] Test end-to-end logout flow
- [ ] Test end-to-end profile update flow
- [ ] Test end-to-end password change flow
- [ ] Test error scenarios
- [ ] Test rate limiting
- [ ] Test token expiration

---

### 6.5 Rollback Plan

#### React Rollback
- [ ] Revert endpoint URL changes
- [ ] Revert BASE_URL changes
- [ ] Revert error handling changes
- [ ] Test connection to Laravel backend

#### Flutter Rollback
- [ ] Revert endpoint URL changes
- [ ] Revert BASE_URL changes
- [ ] Revert error handling changes
- [ ] Test connection to Laravel backend

#### Backend Rollback
- [ ] Stop Spring Boot backend
- [ ] Start Laravel backend
- [ ] Verify Laravel backend is running
- [ ] Test frontend connection to Laravel

---

## 7. POST-MIGRATION TASKS

### 7.1 High Priority

#### Task 1: Implement Token Revocation
- **Description:** Implement Redis-based token blacklist
- **Milestone:** Future
- **Impact:** High - Security improvement
- **Effort:** Medium

#### Task 2: Implement Refresh Tokens
- **Description:** Implement refresh token mechanism
- **Milestone:** Future
- **Impact:** High - User experience improvement
- **Effort:** High

#### Task 3: Implement Account Lockout
- **Description:** Implement account lockout after failed login attempts
- **Milestone:** Future
- **Impact:** Medium - Security improvement
- **Effort:** Medium

### 7.2 Medium Priority

#### Task 4: Centralize Endpoints
- **Description:** Create centralized endpoint constants files
- **Milestone:** Current
- **Impact:** Low - Maintainability improvement
- **Effort:** Low

#### Task 5: Add Request ID Logging
- **Description:** Add request ID to all log statements
- **Milestone:** Future
- **Impact:** Low - Traceability improvement
- **Effort:** Low

#### Task 6: Add Audit Logging
- **Description:** Add detailed audit logging (user agent, IP address)
- **Milestone:** Future
- **Impact:** Low - Security monitoring improvement
- **Effort:** Low

---

## CONCLUSION

This migration guide provides a comprehensive roadmap for transitioning from Laravel to Spring Boot API. The migration is straightforward with no breaking changes, only minor updates to endpoint URLs and token format handling.

**Key Points:**
- All endpoints have `/auth` prefix in Spring Boot
- Token format changes from Sanctum to JWT (approved architectural change)
- Frontend migration is low to medium effort
- Backend migration is high priority (configuration and database)
- Rollback plan is straightforward

**Recommendation:** Proceed with migration following the ordered checklist, starting with backend preparation, then frontend updates, followed by manual verification and integration testing.

---

**Document Status:** COMPLETE  
**Migration Status:** READY FOR IMPLEMENTATION  
**Next Step:** Backend Configuration (Phase 1)
