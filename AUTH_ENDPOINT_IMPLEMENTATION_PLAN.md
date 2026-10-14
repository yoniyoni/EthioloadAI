# Authentication Endpoint Implementation Plan
## EthioloadAI Spring Boot Authentication Module

**Document Version:** 1.0  
**Created:** July 8, 2026  
**Purpose:** Implementation checklist for Milestone 5 - Controllers and REST Endpoints  
**Milestone:** 5

---

## TABLE OF CONTENTS

1. [POST /api/auth/register](#1-post-apiauth-register)
2. [POST /api/auth/login](#2-post-apiauth-login)
3. [POST /api/auth/logout](#3-post-apiauth-logout)
4. [GET /api/auth/me](#4-get-apiauthme)
5. [PATCH /api/auth/me](#5-patch-apiauthme)
6. [PATCH /api/auth/me/password](#6-patch-apiauthmepassword)

---

## 1. POST /api/auth/register

### Endpoint Details
- **Path:** `/api/auth/register`
- **HTTP Method:** POST
- **Controller:** AuthController
- **Handler Method:** register

### Request DTO
- **Class:** RegisterRequest
- **Fields:**
  - `full_name` (String, required, max 255)
  - `phone` (String, required, pattern: ^\\+?[0-9]{10,15}$)
  - `email` (String, optional, email format)
  - `password` (String, required, min 6)
  - `role` (String, required, enum: shipper, driver, admin, fleet_owner)

### Response DTO
- **Class:** AuthenticationResponse
- **Fields:**
  - `user` (UserResponse)
  - `token` (String)

### Validation
- **Bean Validation:**
  - `@NotBlank` on full_name, phone, password, role
  - `@Size(max=255)` on full_name
  - `@Pattern(regexp="^\\+?[0-9]{10,15}$")` on phone
  - `@Email` on email (if provided)
  - `@Size(min=6)` on password
  - `@Pattern(regexp="shipper|driver|admin|fleet_owner")` on role
- **Business Validation:**
  - Phone must be unique in database
  - Email must be unique in database (if provided)

### Authentication Required
- **No** - Public endpoint

### Required Roles
- **None** - Public endpoint

### Possible Exceptions
- `ValidationException` (422) - Phone/email already taken, validation errors
- `AuthenticationException` (500) - Unexpected error during registration

### HTTP Status Codes
- **201 Created** - Registration successful
- **422 Unprocessable Entity** - Validation error
- **500 Internal Server Error** - Unexpected error

### Laravel Response Format
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

### Planned Spring Boot Response Format
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
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Difference:** Token format changes from Sanctum to JWT

### React Compatibility Notes
- React expects response format: {user, token}
- React will store token in localStorage/sessionStorage
- React will redirect to dashboard on success
- React will display validation errors on 422
- **Action Required:** React must be updated to handle JWT token format (Phase 2)

### Flutter Compatibility Notes
- Flutter expects response format: {user, token}
- Flutter will store token in secure storage
- Flutter will navigate to dashboard on success
- Flutter will display validation errors on 422
- **Action Required:** Flutter must be updated to handle JWT token format (Phase 2)

### Integration Test Scenarios
1. **Successful registration with all fields**
   - Request: Valid register request with all fields
   - Expected: 201 Created, user with token, verification_status=true, is_active=true (non-driver)
2. **Successful driver registration**
   - Request: Valid register request with role=driver
   - Expected: 201 Created, user with token, verification_status=false, is_active=false
3. **Duplicate phone**
   - Request: Register with existing phone
   - Expected: 422 Unprocessable Entity, error message "The phone has already been taken."
4. **Duplicate email**
   - Request: Register with existing email
   - Expected: 422 Unprocessable Entity, error message "The email has already been taken."
5. **Invalid phone format**
   - Request: Register with invalid phone format
   - Expected: 422 Unprocessable Entity, validation error
6. **Invalid email format**
   - Request: Register with invalid email format
   - Expected: 422 Unprocessable Entity, validation error
7. **Password too short**
   - Request: Register with password < 6 characters
   - Expected: 422 Unprocessable Entity, validation error
8. **Invalid role**
   - Request: Register with invalid role
   - Expected: 422 Unprocessable Entity, validation error

### Security Considerations
- Password must be hashed with BCrypt before storage
- Phone/email uniqueness prevents duplicate accounts
- Driver accounts start inactive and unverified (prevents unauthorized access)
- Rate limiting: 10 requests per hour per IP (configured in RateLimitConfig)
- No sensitive data logged (passwords, tokens)

### Logging Requirements
- **INFO:** Registration initiated - phone, email
- **WARN:** Registration failed - phone already exists
- **WARN:** Registration failed - email already exists
- **INFO:** Registration successful - userId, role, phone
- **ERROR:** Registration failed - unexpected error

### Rate Limiting Requirements
- **Limit:** 10 requests per hour per IP
- **Bucket:** registration bucket
- **Configuration:** rate-limit.registration.capacity=10, refill-duration-seconds=3600
- **Response:** 429 Too Many Requests if exceeded

---

## 2. POST /api/auth/login

### Endpoint Details
- **Path:** `/api/auth/login`
- **HTTP Method:** POST
- **Controller:** AuthController
- **Handler Method:** login

### Request DTO
- **Class:** LoginRequest
- **Fields:**
  - `identifier` (String, required)
  - `password` (String, required)

### Response DTO
- **Class:** AuthenticationResponse
- **Fields:**
  - `user` (UserResponse)
  - `token` (String)

### Validation
- **Bean Validation:**
  - `@NotBlank` on identifier, password
- **Business Validation:**
  - Identifier must be valid email or phone
  - User must exist
  - Password must match stored hash
  - Account must be active (is_active=true)
  - Driver must be verified (verification_status=true) if role=driver

### Authentication Required
- **No** - Public endpoint

### Required Roles
- **None** - Public endpoint

### Possible Exceptions
- `AuthenticationException.InvalidCredentialsException` (401) - Invalid credentials
- `AuthenticationException.AccountInactiveException` (403) - Account inactive
- `AuthenticationException.DriverNotVerifiedException` (403) - Driver not verified
- `AuthenticationException.ResourceNotFoundException` (404) - User not found

### HTTP Status Codes
- **200 OK** - Login successful
- **401 Unauthorized** - Invalid credentials
- **403 Forbidden** - Account inactive or driver not verified
- **500 Internal Server Error** - Unexpected error

### Laravel Response Format
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

### Planned Spring Boot Response Format
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
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Difference:** Token format changes from Sanctum to JWT

### React Compatibility Notes
- React expects response format: {user, token}
- React will store token in localStorage/sessionStorage
- React will redirect to dashboard on success
- React will display error message on 401/403
- **Action Required:** React must be updated to handle JWT token format (Phase 2)

### Flutter Compatibility Notes
- Flutter expects response format: {user, token}
- Flutter will store token in secure storage
- Flutter will navigate to dashboard on success
- Flutter will display error message on 401/403
- **Action Required:** Flutter must be updated to handle JWT token format (Phase 2)

### Integration Test Scenarios
1. **Successful login with email**
   - Request: Valid login with email identifier
   - Expected: 200 OK, user with token
2. **Successful login with phone**
   - Request: Valid login with phone identifier
   - Expected: 200 OK, user with token
3. **Invalid credentials**
   - Request: Valid identifier, wrong password
   - Expected: 401 Unauthorized, error message "Invalid credentials. Check your email/phone and password."
4. **User not found**
   - Request: Non-existent identifier
   - Expected: 401 Unauthorized, error message "Invalid credentials. Check your email/phone and password."
5. **Account inactive**
   - Request: Valid credentials for inactive account
   - Expected: 403 Forbidden, error message "Account is inactive. Please contact support."
6. **Driver not verified**
   - Request: Valid credentials for unverified driver
   - Expected: 403 Forbidden, error message "Driver account is not verified. Please complete document verification."
7. **Successful driver login**
   - Request: Valid credentials for verified driver
   - Expected: 200 OK, user with token

### Security Considerations
- Password verification uses BCrypt
- Account status checks prevent unauthorized access
- Driver verification check ensures only verified drivers can access system
- Rate limiting: 5 requests per minute per IP (configured in RateLimitConfig)
- Failed login attempts logged for security monitoring
- No sensitive data logged (passwords, tokens)

### Logging Requirements
- **INFO:** Login attempt initiated - identifier
- **WARN:** Login failed - user not found for identifier
- **WARN:** Login failed - invalid credentials for userId, identifier
- **WARN:** Login failed - account inactive for userId, identifier
- **WARN:** Login failed - driver not verified for userId, identifier
- **INFO:** Login successful - userId, role, identifier
- **ERROR:** Login failed - unexpected error

### Rate Limiting Requirements
- **Limit:** 5 requests per minute per IP
- **Bucket:** login bucket
- **Configuration:** rate-limit.login.capacity=5, refill-duration-seconds=60
- **Response:** 429 Too Many Requests if exceeded

---

## 3. POST /api/auth/logout

### Endpoint Details
- **Path:** `/api/auth/logout`
- **HTTP Method:** POST
- **Controller:** AuthController
- **Handler Method:** logout

### Request DTO
- **None** - Token extracted from Authorization header

### Response DTO
- **Class:** Simple response
- **Fields:**
  - `message` (String)

### Validation
- **None** - Token validation handled by JWT filter

### Authentication Required
- **Yes** - Protected endpoint

### Required Roles
- **Any** - All authenticated users can logout

### Possible Exceptions
- `AuthenticationException.InvalidTokenException` (401) - Invalid token
- `AuthenticationException.ExpiredTokenException` (401) - Token expired
- `AuthenticationException.MissingTokenException` (401) - Token missing

### HTTP Status Codes
- **200 OK** - Logout successful
- **401 Unauthorized** - Invalid/expired/missing token
- **500 Internal Server Error** - Unexpected error

### Laravel Response Format
```json
{
  "message": "Logged out successfully"
}
```

### Planned Spring Boot Response Format
```json
{
  "message": "Logged out successfully"
}
```

**Difference:** None

### React Compatibility Notes
- React expects response format: {message}
- React will remove token from localStorage/sessionStorage
- React will redirect to login page on success
- React will redirect to login page on 401
- **No changes required**

### Flutter Compatibility Notes
- Flutter expects response format: {message}
- Flutter will remove token from secure storage
- Flutter will navigate to login page on success
- Flutter will navigate to login page on 401
- **No changes required**

### Integration Test Scenarios
1. **Successful logout**
   - Request: Valid token in Authorization header
   - Expected: 200 OK, success message
2. **Invalid token**
   - Request: Invalid token in Authorization header
   - Expected: 401 Unauthorized
3. **Missing token**
   - Request: No Authorization header
   - Expected: 401 Unauthorized
4. **Expired token**
   - Request: Expired token in Authorization header
   - Expected: 401 Unauthorized

### Security Considerations
- Token validation handled by JWT filter
- Token revocation will be implemented with refresh tokens (future)
- Rate limiting: Not required for logout (already authenticated)
- No sensitive data logged

### Logging Requirements
- **INFO:** User logout
- **WARN:** Logout failed - invalid token
- **WARN:** Logout failed - expired token
- **WARN:** Logout failed - missing token
- **ERROR:** Logout failed - unexpected error

### Rate Limiting Requirements
- **None** - Not required for logout endpoint

---

## 4. GET /api/auth/me

### Endpoint Details
- **Path:** `/api/auth/me`
- **HTTP Method:** GET
- **Controller:** AuthController
- **Handler Method:** getCurrentUser

### Request DTO
- **None** - Token extracted from Authorization header

### Response DTO
- **Class:** UserResponse
- **Fields:**
  - `id` (Long)
  - `full_name` (String)
  - `phone` (String)
  - `email` (String)
  - `role` (String)
  - `location` (String)
  - `verification_status` (Boolean)
  - `is_active` (Boolean)
  - `created_at` (LocalDateTime)
  - `updated_at` (LocalDateTime)

### Validation
- **None** - Token validation handled by JWT filter

### Authentication Required
- **Yes** - Protected endpoint

### Required Roles
- **Any** - All authenticated users can access their own data

### Possible Exceptions
- `AuthenticationException.InvalidTokenException` (401) - Invalid token
- `AuthenticationException.ExpiredTokenException` (401) - Token expired
- `AuthenticationException.MissingTokenException` (401) - Token missing
- `AuthenticationException.ResourceNotFoundException` (404) - User not found

### HTTP Status Codes
- **200 OK** - User data retrieved
- **401 Unauthorized** - Invalid/expired/missing token
- **404 Not Found** - User not found
- **500 Internal Server Error** - Unexpected error

### Laravel Response Format
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

### Planned Spring Boot Response Format
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

**Difference:** None

### React Compatibility Notes
- React expects response format: UserResponse (snake_case)
- React will display user data on success
- React will redirect to login page on 401
- **No changes required**

### Flutter Compatibility Notes
- Flutter expects response format: UserResponse (snake_case)
- Flutter will display user data on success
- Flutter will navigate to login page on 401
- **No changes required**

### Integration Test Scenarios
1. **Successful get current user**
   - Request: Valid token in Authorization header
   - Expected: 200 OK, user data
2. **Invalid token**
   - Request: Invalid token in Authorization header
   - Expected: 401 Unauthorized
3. **Missing token**
   - Request: No Authorization header
   - Expected: 401 Unauthorized
4. **Expired token**
   - Request: Expired token in Authorization header
   - Expected: 401 Unauthorized
5. **User not found**
   - Request: Valid token for deleted user
   - Expected: 404 Not Found

### Security Considerations
- Token validation handled by JWT filter
- User ID extracted from JWT token claims
- User can only access their own data
- Rate limiting: 100 requests per minute per user (configured in RateLimitConfig)
- No sensitive data logged

### Logging Requirements
- **INFO:** Fetching current user with userId
- **WARN:** Get current user failed - invalid token
- **WARN:** Get current user failed - expired token
- **WARN:** Get current user failed - missing token
- **WARN:** Get current user failed - user not found
- **ERROR:** Get current user failed - unexpected error

### Rate Limiting Requirements
- **Limit:** 100 requests per minute per user
- **Bucket:** API bucket
- **Configuration:** rate-limit.api.capacity=100, refill-duration-seconds=60
- **Response:** 429 Too Many Requests if exceeded

---

## 5. PATCH /api/auth/me

### Endpoint Details
- **Path:** `/api/auth/me`
- **HTTP Method:** PATCH
- **Controller:** AuthController
- **Handler Method:** updateProfile

### Request DTO
- **Class:** UpdateProfileRequest
- **Fields:**
  - `name` (String, optional, max 255)
  - `full_name` (String, optional, max 255)
  - `phone` (String, optional, max 50)
  - `address` (String, optional, max 255)
  - `business_name` (String, optional, max 255)

### Response DTO
- **Class:** UserResponse
- **Fields:**
  - `id` (Long)
  - `full_name` (String)
  - `phone` (String)
  - `email` (String)
  - `role` (String)
  - `location` (String)
  - `verification_status` (Boolean)
  - `is_active` (Boolean)
  - `created_at` (LocalDateTime)
  - `updated_at` (LocalDateTime)

### Validation
- **Bean Validation:**
  - `@Size(max=255)` on name, full_name, address, business_name
  - `@Size(max=50)` on phone
- **Business Validation:**
  - Phone must be unique if changed
  - Email must be unique if changed
  - name mapped to full_name if provided

### Authentication Required
- **Yes** - Protected endpoint

### Required Roles
- **Any** - All authenticated users can update their own profile

### Possible Exceptions
- `AuthenticationException.InvalidTokenException` (401) - Invalid token
- `AuthenticationException.ExpiredTokenException` (401) - Token expired
- `AuthenticationException.MissingTokenException` (401) - Token missing
- `AuthenticationException.ResourceNotFoundException` (404) - User not found
- `ValidationException` (422) - Phone/email already taken, validation errors

### HTTP Status Codes
- **200 OK** - Profile updated
- **401 Unauthorized** - Invalid/expired/missing token
- **404 Not Found** - User not found
- **422 Unprocessable Entity** - Validation error
- **500 Internal Server Error** - Unexpected error

### Laravel Response Format
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

### Planned Spring Boot Response Format
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

**Difference:** None

### React Compatibility Notes
- React expects response format: UserResponse (snake_case)
- React will display updated user data on success
- React will display validation errors on 422
- React will redirect to login page on 401
- **No changes required**

### Flutter Compatibility Notes
- Flutter expects response format: UserResponse (snake_case)
- Flutter will display updated user data on success
- Flutter will display validation errors on 422
- Flutter will navigate to login page on 401
- **No changes required**

### Integration Test Scenarios
1. **Successful profile update with full_name**
   - Request: Valid update with full_name
   - Expected: 200 OK, updated user data
2. **Successful profile update with name (mapped to full_name)**
   - Request: Valid update with name
   - Expected: 200 OK, updated user data with full_name
3. **Duplicate phone**
   - Request: Update with existing phone (different user)
   - Expected: 422 Unprocessable Entity, error message "The phone has already been taken."
4. **Duplicate email**
   - Request: Update with existing email (different user)
   - Expected: 422 Unprocessable Entity, error message "The email has already been taken."
5. **Invalid token**
   - Request: Invalid token in Authorization header
   - Expected: 401 Unauthorized
6. **User not found**
   - Request: Valid token for deleted user
   - Expected: 404 Not Found

### Security Considerations
- Token validation handled by JWT filter
- User ID extracted from JWT token claims
- User can only update their own profile
- Phone/email uniqueness checks prevent data conflicts
- Rate limiting: 100 requests per minute per user (configured in RateLimitConfig)
- No sensitive data logged

### Logging Requirements
- **INFO:** Updating profile for userId
- **WARN:** Update profile failed - invalid token
- **WARN:** Update profile failed - expired token
- **WARN:** Update profile failed - missing token
- **WARN:** Update profile failed - user not found
- **WARN:** Update profile failed - phone already exists
- **WARN:** Update profile failed - email already exists
- **INFO:** Profile updated successfully for userId
- **ERROR:** Update profile failed - unexpected error

### Rate Limiting Requirements
- **Limit:** 100 requests per minute per user
- **Bucket:** API bucket
- **Configuration:** rate-limit.api.capacity=100, refill-duration-seconds=60
- **Response:** 429 Too Many Requests if exceeded

---

## 6. PATCH /api/auth/me/password

### Endpoint Details
- **Path:** `/api/auth/me/password`
- **HTTP Method:** PATCH
- **Controller:** AuthController
- **Handler Method:** changePassword

### Request DTO
- **Class:** ChangePasswordRequest
- **Fields:**
  - `current_password` (String, required)
  - `new_password` (String, required, min 6)

### Response DTO
- **Class:** Simple response
- **Fields:**
  - `success` (Boolean)
  - `message` (String)

### Validation
- **Bean Validation:**
  - `@NotBlank` on current_password, new_password
  - `@Size(min=6)` on new_password
- **Business Validation:**
  - Current password must match stored hash
  - New password must be different from current password

### Authentication Required
- **Yes** - Protected endpoint

### Required Roles
- **Any** - All authenticated users can change their password

### Possible Exceptions
- `AuthenticationException.InvalidTokenException` (401) - Invalid token
- `AuthenticationException.ExpiredTokenException` (401) - Token expired
- `AuthenticationException.MissingTokenException` (401) - Token missing
- `AuthenticationException.ResourceNotFoundException` (404) - User not found
- `AuthenticationException.CurrentPasswordIncorrectException` (422) - Current password incorrect
- `ValidationException` (422) - New password same as current, validation errors

### HTTP Status Codes
- **200 OK** - Password changed
- **401 Unauthorized** - Invalid/expired/missing token
- **404 Not Found** - User not found
- **422 Unprocessable Entity** - Current password incorrect, new password same as current
- **500 Internal Server Error** - Unexpected error

### Laravel Response Format
```json
{
  "success": true,
  "message": "Password changed successfully."
}
```

### Planned Spring Boot Response Format
```json
{
  "success": true,
  "message": "Password changed successfully."
}
```

**Difference:** None

### React Compatibility Notes
- React expects response format: {success, message}
- React will display success message on success
- React will display error message on 422
- React will redirect to login page on 401
- **No changes required**

### Flutter Compatibility Notes
- Flutter expects response format: {success, message}
- Flutter will display success message on success
- Flutter will display error message on 422
- Flutter will navigate to login page on 401
- **No changes required**

### Integration Test Scenarios
1. **Successful password change**
   - Request: Valid current password, new password
   - Expected: 200 OK, success message
2. **Current password incorrect**
   - Request: Invalid current password
   - Expected: 422 Unprocessable Entity, error message "Current password is incorrect."
3. **New password same as current**
   - Request: New password equals current password
   - Expected: 422 Unprocessable Entity, error message "New password must be different from current password."
4. **New password too short**
   - Request: New password < 6 characters
   - Expected: 422 Unprocessable Entity, validation error
5. **Invalid token**
   - Request: Invalid token in Authorization header
   - Expected: 401 Unauthorized
6. **User not found**
   - Request: Valid token for deleted user
   - Expected: 404 Not Found

### Security Considerations
- Token validation handled by JWT filter
- User ID extracted from JWT token claims
- Current password verification prevents unauthorized changes
- New password validation prevents password reuse
- Password hashed with BCrypt before storage
- Rate limiting: 100 requests per minute per user (configured in RateLimitConfig)
- No passwords logged

### Logging Requirements
- **INFO:** Password change initiated - userId
- **WARN:** Password change failed - invalid token
- **WARN:** Password change failed - expired token
- **WARN:** Password change failed - missing token
- **WARN:** Password change failed - user not found
- **WARN:** Password change failed - current password incorrect
- **WARN:** Password change failed - new password same as current
- **INFO:** Password change successful - userId
- **ERROR:** Password change failed - unexpected error

### Rate Limiting Requirements
- **Limit:** 100 requests per minute per user
- **Bucket:** API bucket
- **Configuration:** rate-limit.api.capacity=100, refill-duration-seconds=60
- **Response:** 429 Too Many Requests if exceeded

---

## CONTROLLER IMPLEMENTATION CHECKLIST

### AuthController
- [ ] Create AuthController class
- [ ] Add @RestController annotation
- [ ] Add @RequestMapping("/api/auth") annotation
- [ ] Inject AuthenticationService
- [ ] Implement register() method
  - [ ] Add @PostMapping("/register")
  - [ ] Add @Validated annotation
  - [ ] Call authenticationService.register()
  - [ ] Return ResponseEntity with 201 status
- [ ] Implement login() method
  - [ ] Add @PostMapping("/login")
  - [ ] Add @Validated annotation
  - [ ] Call authenticationService.login()
  - [ ] Return ResponseEntity with 200 status
- [ ] Implement logout() method
  - [ ] Add @PostMapping("/logout")
  - [ ] Extract token from Authorization header
  - [ ] Call authenticationService.logout()
  - [ ] Return ResponseEntity with 200 status
- [ ] Implement getCurrentUser() method
  - [ ] Add @GetMapping("/me")
  - [ ] Extract userId from SecurityContext
  - [ ] Call authenticationService.getCurrentUser()
  - [ ] Return ResponseEntity with 200 status
- [ ] Implement updateProfile() method
  - [ ] Add @PatchMapping("/me")
  - [ ] Add @Validated annotation
  - [ ] Extract userId from SecurityContext
  - [ ] Call authenticationService.updateProfile()
  - [ ] Return ResponseEntity with 200 status
- [ ] Implement changePassword() method
  - [ ] Add @PatchMapping("/me/password")
  - [ ] Add @Validated annotation
  - [ ] Extract userId from SecurityContext
  - [ ] Call authenticationService.changePassword()
  - [ ] Return ResponseEntity with 200 status

### Security Configuration
- [ ] Update SecurityConfig to include public endpoints
- [ ] Ensure /api/auth/register is public
- [ ] Ensure /api/auth/login is public
- [ ] Ensure /api/auth/logout requires authentication
- [ ] Ensure /api/auth/me requires authentication
- [ ] Ensure /api/auth/me (PATCH) requires authentication
- [ ] Ensure /api/auth/me/password requires authentication

### OpenAPI Documentation
- [ ] Add @Operation annotations to each endpoint
- [ ] Add @ApiResponse annotations for each response type
- [ ] Add @Parameter annotations for request body
- [ ] Add @Tag annotation for controller

---

## INTEGRATION TEST CHECKLIST

### Register Endpoint Tests
- [ ] Test successful registration with all fields
- [ ] Test successful driver registration
- [ ] Test duplicate phone
- [ ] Test duplicate email
- [ ] Test invalid phone format
- [ ] Test invalid email format
- [ ] Test password too short
- [ ] Test invalid role

### Login Endpoint Tests
- [ ] Test successful login with email
- [ ] Test successful login with phone
- [ ] Test invalid credentials
- [ ] Test user not found
- [ ] Test account inactive
- [ ] Test driver not verified
- [ ] Test successful driver login

### Logout Endpoint Tests
- [ ] Test successful logout
- [ ] Test invalid token
- [ ] Test missing token
- [ ] Test expired token

### Get Current User Tests
- [ ] Test successful get current user
- [ ] Test invalid token
- [ ] Test missing token
- [ ] Test expired token
- [ ] Test user not found

### Update Profile Tests
- [ ] Test successful profile update with full_name
- [ ] Test successful profile update with name
- [ ] Test duplicate phone
- [ ] Test duplicate email
- [ ] Test invalid token
- [ ] Test user not found

### Change Password Tests
- [ ] Test successful password change
- [ ] Test current password incorrect
- [ ] Test new password same as current
- [ ] Test new password too short
- [ ] Test invalid token
- [ ] Test user not found

---

## SECURITY CHECKLIST

### Authentication
- [ ] JWT filter configured and active
- [ ] Token extraction from Authorization header
- [ ] Token validation on protected endpoints
- [ ] Token expiration check
- [ ] Token revocation check (future)

### Authorization
- [ ] Public endpoints correctly configured
- [ ] Protected endpoints require authentication
- [ ] User can only access their own data
- [ ] Role-based authorization (future)

### Rate Limiting
- [ ] Rate limiting filter configured
- [ ] Login rate limiting active (5/min)
- [ ] Registration rate limiting active (10/hour)
- [ ] API rate limiting active (100/min)
- [ ] Rate limiting by IP for public endpoints
- [ ] Rate limiting by user for authenticated endpoints

### Data Protection
- [ ] Password field has @JsonIgnore
- [ ] No passwords logged
- [ ] No tokens logged
- [ ] No sensitive personal information logged
- [ ] BCrypt password hashing

### Input Validation
- [ ] Bean validation on all request DTOs
- [ ] Business validation in service layer
- [ ] SQL injection prevention (JPA)
- [ ] XSS prevention (Jackson)

---

## LOGGING CHECKLIST

### Structured Logging
- [ ] All security events logged
- [ ] All authentication failures logged
- [ ] All authorization failures logged
- [ ] All validation failures logged
- [ ] All successful operations logged

### Log Levels
- [ ] INFO for successful operations
- [ ] WARN for failures (authentication, authorization, validation)
- [ ] ERROR for unexpected errors

### Log Content
- [ ] No passwords logged
- [ ] No tokens logged
- [ ] No sensitive personal information logged
- [ ] User IDs logged for audit trail
- [ ] Identifiers logged for security monitoring

---

## PRODUCTION READINESS CHECKLIST

### API Compatibility
- [ ] All response formats match Laravel
- [ ] All error messages match Laravel
- [ ] All HTTP status codes match Laravel
- [ ] All validation rules match Laravel

### Frontend Compatibility
- [ ] React compatibility verified
- [ ] Flutter compatibility verified
- [ ] JWT token format documented
- [ ] Migration path documented

### Security
- [ ] All security considerations addressed
- [ ] Rate limiting configured
- [ ] Password hashing verified
- [ ] Token validation verified

### Monitoring
- [ ] Structured logging implemented
- [ ] Security events logged
- [ ] Error logging implemented
- [ ] Performance metrics (Actuator) configured

---

## CONCLUSION

This document provides a comprehensive implementation plan for Milestone 5 - Controllers and REST Endpoints. All endpoints are documented with:

- Request/response formats
- Validation rules
- Authentication/authorization requirements
- Exception handling
- HTTP status codes
- Laravel compatibility
- React/Flutter compatibility
- Integration test scenarios
- Security considerations
- Logging requirements
- Rate limiting requirements

The implementation checklist ensures all requirements are met before proceeding to the next milestone.

---

**Document Status:** COMPLETE  
**Next Milestone:** Milestone 5 Implementation  
**Approval Status:** READY FOR IMPLEMENTATION
