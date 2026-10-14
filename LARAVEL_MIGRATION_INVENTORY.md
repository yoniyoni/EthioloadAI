# Laravel to Spring Boot Migration Inventory
## EthioloadAI Logistics Platform

**Generated:** July 8, 2026  
**Laravel Version:** 11.x  
**Target:** Spring Boot 3 (Java 21)

---

## 1. ROUTES

### 1.1 API Routes (`routes/api.php`)

#### Authentication Routes
- **POST** `/register` - AuthController@register
  - Middleware: `throttle:login`
  - Response: UserResource + token (201)
- **POST** `/login` - AuthController@login
  - Middleware: `throttle:login`
  - Response: UserResource + token
- **POST** `/auth/login` - AdminApiController@login
  - Middleware: `throttle:login`
  - Admin panel login
- **POST** `/logout` - AuthController@logout
  - Middleware: `auth:sanctum`
- **GET** `/me` - AuthController@me
  - Middleware: `auth:sanctum`
- **PATCH** `/me` - AuthController@updateProfile
  - Middleware: `auth:sanctum`
- **PATCH** `/me/password` - AuthController@changePassword
  - Middleware: `auth:sanctum`

#### Freight/Cargo Routes
- **GET** `/freight` - CargoRequestController@freightIndex
  - Middleware: `auth:sanctum`
  - CamelCase response for React frontend
- **GET** `/freight/{id}` - CargoRequestController@freightShow
  - Middleware: `auth:sanctum`
- **POST** `/freight` - CargoRequestController@store
  - Middleware: `auth:sanctum`
- **GET** `/cargo-requests` - CargoRequestController@index
  - Middleware: `auth:sanctum`
  - Role-based filtering (shipper sees own, driver sees available)
- **POST** `/cargo-requests` - CargoRequestController@store
  - Middleware: `auth:sanctum`
- **GET** `/cargo-requests/{id}` - CargoRequestController@show
  - Middleware: `auth:sanctum`
- **PATCH** `/cargo-requests/{id}` - CargoRequestController@update
  - Middleware: `auth:sanctum`
- **DELETE** `/cargo-requests/{id}` - CargoRequestController@destroy
  - Middleware: `auth:sanctum`
- **POST** `/cargo-requests/{cargo}/book-direct` - CargoRequestController@bookDirect
  - Middleware: `auth:sanctum`
  - Direct booking for fixed-price cargo
- **POST** `/cargo-requests/{cargo}/accept-price` - CargoRequestController@acceptPrice
  - Middleware: `auth:sanctum`
  - Driver registers interest in fixed-price cargo
- **GET** `/cargo-requests/{cargo}/nearby-drivers` - CargoRequestController@nearbyDrivers
  - Middleware: `auth:sanctum`
  - Shipper only
- **GET** `/driver/return-cargo` - CargoRequestController@returnCargo
  - Middleware: `auth:sanctum`
  - Backhaul cargo suggestions

#### Vehicle Routes
- **GET** `/vehicles` - VehicleController@index
  - Middleware: `auth:sanctum`
  - Role-based filtering
- **POST** `/vehicles` - VehicleController@store
  - Middleware: `auth:sanctum`
- **GET** `/vehicles/{id}` - VehicleController@show
  - Middleware: `auth:sanctum`
- **PATCH** `/vehicles/{id}` - VehicleController@update
  - Middleware: `auth:sanctum`
- **DELETE** `/vehicles/{id}` - VehicleController@destroy
  - Middleware: `auth:sanctum`
- **POST** `/vehicle/register` - VehicleController@register
  - Middleware: `auth:sanctum`
- **GET** `/my-vehicles` - VehicleController@myVehicles
  - Middleware: `auth:sanctum`
  - CamelCase response
- **PATCH** `/vehicles/{vehicle}/location` - VehicleController@updateLocation
  - Middleware: `auth:sanctum`
  - Auto-detects nearest city
- **GET** `/vehicle/nearby` - VehicleController@nearby
  - Middleware: `auth:sanctum`
- **GET** `/nearby-trucks` - VehicleController@nearbyTrucks
  - Middleware: `auth:sanctum`
  - Map view with driver info
- **POST** `/driver/location` - VehicleController@driverLocation
  - Middleware: `auth:sanctum`
  - GPS push from mobile app (~25 min intervals)
- **PATCH** `/driver/current-city` - VehicleController@updateCurrentCity
  - Middleware: `auth:sanctum`
  - Manual city setting

#### Booking Routes
- **GET** `/bookings` - BookingController@index
  - Middleware: `auth:sanctum`
  - Role-based filtering
- **POST** `/bookings` - BookingController@store
  - Middleware: `auth:sanctum`
- **GET** `/bookings/{id}` - BookingController@show
  - Middleware: `auth:sanctum`
- **PATCH** `/bookings/{id}` - BookingController@update
  - Middleware: `auth:sanctum`
- **DELETE** `/bookings/{id}` - BookingController@destroy
  - Middleware: `auth:sanctum`
- **PATCH** `/bookings/{booking}/cancel` - BookingController@cancel
  - Middleware: `auth:sanctum`
- **POST** `/booking/create` - BookingController@store
  - Middleware: `auth:sanctum`

#### Bid Routes
- **GET** `/cargo-requests/{cargo}/bids` - BidController@index
  - Middleware: `auth:sanctum`
  - Shipper only
- **POST** `/cargo-requests/{cargo}/bids` - BidController@store
  - Middleware: `auth:sanctum`
  - Driver/fleet owner only
- **PATCH** `/bids/{bid}` - BidController@update
  - Middleware: `auth:sanctum`
  - Driver only
- **PATCH** `/bids/{bid}/accept` - BidController@accept
  - Middleware: `auth:sanctum`
  - Shipper only
- **PATCH** `/bids/{bid}/reject` - BidController@reject
  - Middleware: `auth:sanctum`
  - Shipper only
- **PATCH** `/bids/{bid}/withdraw` - BidController@withdraw
  - Middleware: `auth:sanctum`
  - Driver only
- **PATCH** `/bids/{bid}/counter` - BidController@counter
  - Middleware: `auth:sanctum`
  - Shipper or driver
- **PATCH** `/bids/{bid}/accept-counter` - BidController@acceptCounter
  - Middleware: `auth:sanctum`
  - Shipper or driver
- **GET** `/driver/bids` - BidController@myBids
  - Middleware: `auth:sanctum`
  - Driver's own bids

#### Trip Routes
- **GET** `/trips` - TripController@index
  - Middleware: `auth:sanctum`
  - Admin only (handled by AdminApiController)
- **POST** `/trips` - TripController@store
  - Middleware: `auth:sanctum`
  - Driver only
- **GET** `/trips/{id}` - TripController@show
  - Middleware: `auth:sanctum`
- **PATCH** `/trips/{id}/status` - TripController@updateStatus
  - Middleware: `auth:sanctum`
  - Driver only
- **PATCH** `/trips/{id}/location` - TripController@updateLocation
  - Middleware: `auth:sanctum`
  - Driver only
- **GET** `/trips/{trip}/location` - TripController@getLocation
  - Middleware: `auth:sanctum`
  - Live tracking endpoint

#### Trip Stop Routes (Multi-stop trips)
- **GET** `/trips/{trip}/stops` - TripStopController@index
  - Middleware: `auth:sanctum`
- **POST** `/trips/{trip}/stops` - TripStopController@store
  - Middleware: `auth:sanctum`
  - Driver only
- **PATCH** `/trips/{trip}/stops/{stop}/arrive` - TripStopController@arrive
  - Middleware: `auth:sanctum`
  - Driver only
- **PATCH** `/trips/{trip}/stops/{stop}/load` - TripStopController@load
  - Middleware: `auth:sanctum`
  - Driver only
- **PATCH** `/trips/{trip}/stops/{stop}/complete` - TripStopController@complete
  - Middleware: `auth:sanctum`
  - Driver only
- **DELETE** `/trips/{trip}/stops/{stop}` - TripStopController@destroy
  - Middleware: `auth:sanctum`
  - Driver only

#### Payment Routes
- **POST** `/payments` - PaymentController@store
  - Middleware: `auth:sanctum`
- **GET** `/payments/{booking_id}` - PaymentController@show
  - Middleware: `auth:sanctum`

#### AI Engine Proxy Routes
- **GET** `/ai/price-prediction` - AiController@predictPrice
  - Middleware: `auth:sanctum`
  - GET proxy for POST endpoint
- **GET** `/ai/vehicle-recommendation` - AiController@recommendTruck
  - Middleware: `auth:sanctum`
  - GET proxy for POST endpoint
- **GET** `/ai/driver-recommendations` - AiController@recommendTruck
  - Middleware: `auth:sanctum`
  - GET proxy for POST endpoint
- **POST** `/ai/recommend-truck` - AiController@recommendTruck
  - Middleware: `auth:sanctum`
- **POST** `/ai/backhaul-opportunities` - AiController@backhaulOpportunities
  - Middleware: `auth:sanctum`
- **POST** `/ai/predict-price` - AiController@predictPrice
  - Middleware: `auth:sanctum`
- **POST** `/ai/predict-empty-return` - AiController@predictEmptyReturn
  - Middleware: `auth:sanctum`
- **POST** `/ai/optimize-route` - AiController@optimizeRoute
  - Middleware: `auth:sanctum`

#### Backhaul Recommendation Routes
- **GET** `/trips/{trip}/backhaul-recommendations` - BackhaulRecommendationController@index
  - Middleware: `auth:sanctum`
  - Driver only
- **PATCH** `/recommendations/{recommendation}/dismiss` - BackhaulRecommendationController@dismiss
  - Middleware: `auth:sanctum`
  - Driver only

#### Routing/Geocoding Routes
- **POST** `/geocode/nearest-city` - GeocodingController@nearestCity
  - Middleware: `auth:sanctum`
- **GET** `/routing/route` - RoutingController@route
  - Middleware: `auth:sanctum`
  - OSRM proxy with haversine fallback
- **GET** `/routing/search` - RoutingController@search
  - Middleware: `auth:sanctum`
  - Nominatim proxy
- **GET** `/routing/reverse` - RoutingController@reverse
  - Middleware: `auth:sanctum`
  - Nominatim reverse geocode

#### Document Routes
- **GET** `/driver/documents` - DocumentController@index
  - Middleware: `auth:sanctum`
  - Driver's own documents
- **POST** `/driver/documents` - DocumentController@upload
  - Middleware: `auth:sanctum`
  - Multipart file upload
- **GET** `/driver/documents/{document}/file` - DocumentController@download
  - Middleware: `auth:sanctum`
  - File download
- **GET** `/admin/driver-documents` - DocumentController@adminIndex
  - Middleware: `auth:sanctum`, AdminMiddleware
  - Admin view all documents
- **PATCH** `/admin/driver-documents/{document}/review` - DocumentController@review
  - Middleware: `auth:sanctum`, AdminMiddleware
  - Admin approve/reject

#### Rating Routes
- **POST** `/ratings` - RatingController@store
  - Middleware: `auth:sanctum`
  - Shipper rates driver
- **GET** `/ratings/{booking_id}` - RatingController@show
  - Middleware: `auth:sanctum`
- **GET** `/driver/my-ratings` - RatingController@myRatings
  - Middleware: `auth:sanctum`
  - Driver's received ratings

#### Fleet Management Routes
- **GET** `/fleet/dashboard` - FleetController@dashboard
  - Middleware: `auth:sanctum`
  - Fleet owner only
- **GET** `/fleet/drivers` - FleetController@drivers
  - Middleware: `auth:sanctum`
  - Fleet owner only
- **GET** `/fleet/vehicles` - FleetController@vehicles
  - Middleware: `auth:sanctum`
  - Fleet owner only
- **GET** `/fleet/available-cargo` - FleetController@availableCargo
  - Middleware: `auth:sanctum`
  - Fleet owner only
- **POST** `/fleet/drivers/add` - FleetController@addDriver
  - Middleware: `auth:sanctum`
  - Fleet owner only
- **DELETE** `/fleet/drivers/{driverId}` - FleetController@removeDriver
  - Middleware: `auth:sanctum`
  - Fleet owner only
- **POST** `/fleet/vehicles` - FleetController@addVehicle
  - Middleware: `auth:sanctum`
  - Fleet owner only
- **PATCH** `/fleet/vehicles/{vehicleId}/assign` - FleetController@assignVehicle
  - Middleware: `auth:sanctum`
  - Fleet owner only
- **POST** `/fleet/bookings` - FleetController@createBooking
  - Middleware: `auth:sanctum`
  - Fleet owner direct dispatch
- **PATCH** `/fleet/bookings/{bookingId}/dispatch` - FleetController@dispatchBooking
  - Middleware: `auth:sanctum`
  - Fleet owner only

#### Notification Routes
- **GET** `/notifications` - NotificationController@index
  - Middleware: `auth:sanctum`
- **PATCH** `/notifications/read-all` - NotificationController@markAllRead
  - Middleware: `auth:sanctum`
- **PATCH** `/notifications/{id}/read` - NotificationController@markRead
  - Middleware: `auth:sanctum`

#### Admin API Routes (React Admin Panel)
- **GET** `/admin/stats` - AdminApiController@stats
  - Middleware: `auth:sanctum`, AdminMiddleware
- **GET** `/admin/users` - AdminApiController@users
  - Middleware: `auth:sanctum`, AdminMiddleware
- **GET** `/admin/payments` - AdminApiController@payments
  - Middleware: `auth:sanctum`, AdminMiddleware
- **GET** `/admin/escrow` - AdminApiController@escrow
  - Middleware: `auth:sanctum`, AdminMiddleware
- **GET** `/admin/analytics/revenue` - AdminApiController@analyticsRevenue
  - Middleware: `auth:sanctum`, AdminMiddleware
- **GET** `/admin/analytics/routes` - AdminApiController@analyticsRoutes
  - Middleware: `auth:sanctum`, AdminMiddleware
- **GET** `/admin/analytics/cargo` - AdminApiController@analyticsCargo
  - Middleware: `auth:sanctum`, AdminMiddleware
- **GET** `/admin/bookings/unpaid` - AdminApiController@unpaidBookings
  - Middleware: `auth:sanctum`, AdminMiddleware
- **POST** `/admin/bookings/{id}/mark-cash-paid` - AdminApiController@markCashPaid
  - Middleware: `auth:sanctum`, AdminMiddleware
- **GET** `/admin/fleet-owners` - AdminApiController@fleetOwners
  - Middleware: `auth:sanctum`, AdminMiddleware
- **POST** `/admin/users` - AdminApiController@createUser
  - Middleware: `auth:sanctum`, AdminMiddleware
- **PUT** `/admin/users/{id}` - AdminApiController@updateUser
  - Middleware: `auth:sanctum`, AdminMiddleware
- **DELETE** `/admin/users/{id}` - AdminApiController@deleteUser
  - Middleware: `auth:sanctum`, AdminMiddleware
- **POST** `/admin/drivers` - AdminApiController@createDriver
  - Middleware: `auth:sanctum`, AdminMiddleware
- **PATCH** `/drivers/{id}/status` - AdminApiController@updateDriverStatus
  - Middleware: `auth:sanctum`, AdminMiddleware
- **GET** `/admin/settings/pricing` - AdminSettingsController@pricingShow
  - Middleware: `auth:sanctum`, AdminMiddleware
- **PATCH** `/admin/settings/pricing` - AdminSettingsController@pricingUpdate
  - Middleware: `auth:sanctum`, AdminMiddleware

#### Dispute Routes (Stub)
- **GET** `/disputes` - AdminApiController@disputes
  - Middleware: `auth:sanctum`
  - Returns empty (not implemented)
- **POST** `/disputes` - AdminApiController@createDispute
  - Middleware: `auth:sanctum`
  - Stub implementation
- **PATCH** `/disputes/{id}/resolve` - AdminApiController@resolveDispute
  - Middleware: `auth:sanctum`, AdminMiddleware
  - No-op

#### User Management (Admin)
- **GET** `/users` - AdminApiController@users
  - Middleware: `auth:sanctum`, AdminMiddleware
- **GET** `/drivers` - AdminApiController@drivers
  - Middleware: `auth:sanctum`, AdminMiddleware
- **GET** `/trips` - AdminApiController@trips
  - Middleware: `auth:sanctum`, AdminMiddleware

### 1.2 Admin Web Routes (`routes/admin.php`)
- **GET** `/admin/dashboard` - AdminController@dashboard
  - Middleware: `auth`, AdminMiddleware
- **POST** `/admin/users` - AdminController@storeUser
  - Middleware: `auth`, AdminMiddleware
- **GET** `/admin/users` - AdminController@users
  - Middleware: `auth`, AdminMiddleware
- **GET** `/admin/users/{id}` - AdminController@showUser
  - Middleware: `auth`, AdminMiddleware
- **PUT** `/admin/users/{id}` - AdminController@updateUser
  - Middleware: `auth`, AdminMiddleware
- **DELETE** `/admin/users/{id}` - AdminController@deleteUser
  - Middleware: `auth`, AdminMiddleware
- Similar CRUD for vehicles, cargo_requests, bookings, payments, trips, ratings

### 1.3 Web Routes (`routes/web.php`)
- **GET** `/` - Welcome view
- **GET** `/admin/login` - Admin login view
  - Middleware: `guest`
  - Route name: `admin.login`
- **POST** `/admin/login` - Admin login handler
  - Middleware: `guest`
- **POST** `/admin/logout` - Admin logout
  - Route name: `admin.logout`
- **GET** `/admin` - Admin panel layout
  - Middleware: `auth`, `admin`
- **GET** `/login` - Redirects to `/admin/login`
  - Route name: `login`

---

## 2. CONTROLLERS

### 2.1 API Controllers (`app/Http/Controllers/Api/`)

#### AuthController
**Responsibilities:** User authentication, registration, profile management
**Methods:**
- `register(RegisterRequest)` - Creates user, issues token
- `login(Request)` - Email/phone login, issues token
- `logout(Request)` - Revokes current token
- `me(Request)` - Returns current user
- `changePassword(Request)` - Updates password with validation
- `updateProfile(Request)` - Updates user profile fields
**Dependencies:** RegisterRequest, UserResource
**Validation:** Inline validation for login, password change
**Response Format:** JSON with UserResource + token

#### VehicleController
**Responsibilities:** Vehicle management, GPS tracking, nearby truck search
**Methods:**
- `index(Request)` - List vehicles (role-filtered)
- `myVehicles()` - Current user's vehicles (camelCase)
- `register(VehicleRegisterRequest)` - Register new vehicle
- `store(VehicleRegisterRequest)` - Alias for register
- `show(string $id)` - Show single vehicle
- `update(VehicleUpdateRequest, string $id)` - Update vehicle
- `updateLocation(VehicleLocationUpdateRequest, string $id)` - GPS update with city detection
- `driverLocation(Request)` - Mobile GPS push (~25 min intervals)
- `updateCurrentCity(Request)` - Manual city setting
- `nearby(Request)` - Find nearby vehicles
- `nearbyTrucks(Request)` - Map view with driver info
- `destroy(string $id)` - Delete vehicle
**Dependencies:** VehicleRegisterRequest, VehicleUpdateRequest, VehicleLocationUpdateRequest, VehicleResource, Trip
**Services Used:** None (direct model operations)
**Validation:** Form requests for most operations
**Response Format:** VehicleResource or custom camelCase arrays
**Special Features:**
- CITY_COORDS constant with 44 Ethiopian cities
- Haversine distance calculations
- Auto-detects nearest city from GPS

#### CargoRequestController
**Responsibilities:** Cargo request management, bidding, nearby driver search
**Methods:**
- `index(Request)` - List cargo (role-filtered, distance-sorted)
- `freightIndex(Request)` - React frontend list (camelCase)
- `freightShow($id)` - React frontend single cargo
- `returnCargo(Request)` - Backhaul cargo suggestions
- `bookDirect(CargoRequest)` - Direct fixed-price booking
- `acceptPrice(CargoRequest)` - Register interest in fixed-price
- `store(CargoCreateRequest)` - Create cargo request
- `nearbyDrivers(CargoRequest)` - Find nearby drivers for cargo
- `show(string $id)` - Show single cargo
- `update(CargoUpdateRequest, string $id)` - Update cargo
- `destroy(string $id)` - Delete cargo with bid notifications
**Dependencies:** CargoCreateRequest, CargoUpdateRequest, BidService, CargoResource, BidResource
**Services Used:** BidService
**Validation:** Form requests
**Response Format:** CargoResource or custom arrays
**Special Features:**
- Distance-based sorting for drivers
- Intracity vs intercity filtering
- GPS coordinate fallback to city lookup

#### BookingController
**Responsibilities:** Booking lifecycle management
**Methods:**
- `index()` - List bookings (role-filtered)
- `store(BookingCreateRequest)` - Create booking via service
- `show(string $id)` - Show single booking
- `update(BookingUpdateRequest, string $id)` - Update booking
- `cancel(Booking)` - Cancel booking, restore cargo/vehicle
- `destroy(string $id)` - Delete booking via service
**Dependencies:** BookingCreateRequest, BookingUpdateRequest, BookingResource, BookingService
**Services Used:** BookingService
**Validation:** Form requests
**Response Format:** BookingResource
**Special Features:** Fleet owner booking support

#### PaymentController
**Responsibilities:** Payment processing
**Methods:**
- `store(PaymentProcessRequest)` - Process payment
- `show(string $booking_id)` - Show payment
**Dependencies:** PaymentProcessRequest, PaymentService
**Services Used:** PaymentService
**Validation:** Form requests
**Response Format:** JSON with payment data
**Special Features:** 10% commission calculation

#### TripController
**Responsibilities:** Trip management, location tracking
**Methods:**
- `index()` - List all trips (admin)
- `store(TripCreateRequest)` - Start trip, dispatch backhaul job
- `updateStatus(TripUpdateStatusRequest, string $id)` - Update trip status
- `updateLocation(TripLocationUpdateRequest, string $id)` - Update trip location
- `show(string $id)` - Show single trip
- `getLocation(string $id)` - Live tracking endpoint
**Dependencies:** TripCreateRequest, TripUpdateStatusRequest, TripLocationUpdateRequest, TripService
**Services Used:** TripService
**Validation:** Form requests
**Response Format:** JSON with trip data
**Special Features:**
- Async backhaul recommendation generation
- Route data breadcrumb tracking
- City coordinate resolution

#### AiController
**Responsibilities:** AI engine proxy with fallback logic
**Methods:**
- `recommendTruck(Request)` - Truck recommendation with fallback
- `backhaulOpportunities(Request)` - Backhaul opportunities
- `predictPrice(Request)` - Price prediction with OSRM fallback
- `predictEmptyReturn(Request)` - Empty return risk prediction
- `optimizeRoute(Request)` - Route optimization
**Dependencies:** AiEngineService, RoutingService
**Services Used:** AiEngineService, RoutingService
**Validation:** Inline validation
**Response Format:** JSON with AI results or fallback estimates
**Special Features:**
- Ethiopian city distance table (54 cities)
- Haversine fallback calculations
- Material type multipliers
- OSRM integration for real road distances

#### BidController
**Responsibilities:** Bid lifecycle management
**Methods:**
- `index(CargoRequest)` - List bids for cargo (shipper)
- `store(BidStoreRequest, CargoRequest)` - Place bid
- `accept(Bid)` - Accept bid, create booking
- `reject(Bid)` - Reject bid
- `counter(Request, Bid)` - Send counter-offer
- `acceptCounter(Bid)` - Accept counter-offer
- `withdraw(Bid)` - Withdraw bid
- `update(Request, Bid)` - Update bid amount
- `myBids()` - Driver's own bids
**Dependencies:** BidStoreRequest, BidResource, BookingResource, BidService
**Services Used:** BidService
**Validation:** Form requests
**Response Format:** BidResource or BookingResource
**Special Features:**
- Bid ranking by price + rating
- Counter-offer negotiation flow
- Notification triggers

#### AdminApiController
**Responsibilities:** React admin panel API endpoints
**Methods:**
- `stats()` - Dashboard statistics
- `users(Request)` - User listing with pagination
- `drivers(Request)` - Driver listing with ratings
- `payments()` - Payment listing
- `disputes()` - Dispute stub
- `createDispute(Request)` - Dispute stub
- `resolveDispute($id)` - Dispute stub
- `escrow()` -Escrow stub
- `analyticsRevenue()` - Monthly revenue analytics
- `analyticsRoutes()` - Route analytics
- `analyticsCargo()` - Cargo type analytics
- `createUser(Request)` - Create user
- `updateUser(Request, $id)` - Update user
- `deleteUser($id)` - Delete user
- `createDriver(Request)` - Create driver
- `updateDriverStatus(Request, $id)` - Update driver verification
- `trips()` - Trip listing
- `login(Request)` - Admin login
- `unpaidBookings()` - Unpaid bookings
- `markCashPaid($id)` - Mark cash payment
- `fleetOwners()` - Fleet owner listing
- `formatUser(User)` - User formatting helper
**Dependencies:** None
**Services Used:** None (direct model operations)
**Validation:** Inline validation
**Response Format:** Custom arrays for React admin panel
**Special Features:** Flat route structure for admin panel

#### FleetController
**Responsibilities:** Fleet owner management
**Methods:**
- `dashboard()` - Fleet dashboard summary
- `addDriver(Request)` - Add driver to fleet
- `removeDriver(Request, string $driverId)` - Remove driver
- `addVehicle(Request)` - Add vehicle to fleet
- `assignVehicle(Request, string $vehicleId)` - Assign vehicle to driver
- `dispatchBooking(Request, string $bookingId)` - Dispatch booking to driver
- `availableCargo()` - Available cargo for dispatch
- `createBooking(Request)` - Direct booking creation
- `drivers()` - List fleet drivers
- `vehicles()` - List fleet vehicles
**Dependencies:** None
**Services Used:** None (direct model operations)
**Validation:** Inline validation
**Response Format:** JSON with fleet data
**Special Features:** Fleet owner authorization checks

#### TripStopController
**Responsibilities:** Multi-stop trip management
**Methods:**
- `index(Trip)` - List trip stops
- `store(Request, Trip)` - Add stop to trip
- `arrive(Trip, TripStop)` - Mark stop arrived
- `load(Trip, TripStop)` - Mark stop loaded
- `complete(Trip, TripStop)` - Mark stop complete
- `destroy(Trip, TripStop)` - Remove stop
**Dependencies:** TripStopResource, TripService
**Services Used:** TripService
**Validation:** Inline validation
**Response Format:** TripStopResource
**Special Features:**
- Sequential stop enforcement
- Auto-complete trip on final stop
- Shipper privacy (hide other cargo)

#### RoutingController
**Responsibilities:** Routing/geocoding proxy
**Methods:**
- `route(Request)` - OSRM route with haversine fallback
- `search(Request)` - Nominatim place search
- `reverse(Request)` - Nominatim reverse geocode
**Dependencies:** RoutingService
**Services Used:** RoutingService
**Validation:** Inline validation
**Response Format:** JSON with route/place data
**Special Features:** Cached external API calls

#### DocumentController
**Responsibilities:** Driver document management
**Methods:**
- `index()` - List driver's documents
- `upload(DocumentUploadRequest)` - Upload document
- `download(DriverDocument)` - Download document file
- `adminIndex(Request)` - Admin view all documents
- `review(Request, DriverDocument)` - Approve/reject document
- `maybeVerifyDriver(int $userId)` - Auto-verify driver on all docs approved
**Dependencies:** DocumentUploadRequest, DocumentResource
**Services Used:** None (direct model operations)
**Validation:** Form requests
**Response Format:** DocumentResource
**Special Features:**
- Private file storage
- Auto-verification on 5 approved docs
- Document type labels

#### NotificationController
**Responsibilities:** Notification management
**Methods:**
- `index(Request)` - List notifications
- `markRead(Request, string $id)` - Mark single read
- `markAllRead(Request)` - Mark all read
**Dependencies:** None
**Services Used:** None (direct model operations)
**Validation:** None
**Response Format:** JSON with notification data
**Special Features:** Unread count tracking

#### RatingController
**Responsibilities:** Rating management
**Methods:**
- `store(Request)` - Submit rating
- `show(string $booking_id)` - Show booking ratings
- `myRatings()` - Driver's received ratings
**Dependencies:** None
**Services Used:** None (direct model operations)
**Validation:** Inline validation
**Response Format:** JSON with rating data
**Special Features:** Average rating calculation

#### BackhaulRecommendationController
**Responsibilities:** Backhaul recommendation display
**Methods:**
- `index(Trip)` - List trip recommendations
- `dismiss(BackhaulRecommendation)` - Dismiss recommendation
**Dependencies:** None
**Services Used:** None (direct model operations)
**Validation:** None
**Response Format:** JSON with recommendation data
**Special Features:** Score-based ordering

#### GeocodingController
**Responsibilities:** Nearest city lookup
**Methods:**
- `nearestCity(Request)` - Find nearest Ethiopian city
**Dependencies:** None
**Services Used:** None
**Validation:** Inline validation
**Response Format:** JSON with city data
**Special Features:** Uses VehicleController CITY_COORDS

#### UserController
**Responsibilities:** User CRUD (admin)
**Methods:**
- `index()` - List all users
- `store(UserStoreRequest)` - Create user
- `show(string $id)` - Show user
- `update(UserUpdateRequest, string $id)` - Update user
- `destroy(string $id)` - Delete user
**Dependencies:** UserStoreRequest, UserUpdateRequest, UserResource
**Services Used:** None (direct model operations)
**Validation:** Form requests
**Response Format:** UserResource

#### AdminSettingsController
**Responsibilities:** Platform settings management
**Methods:**
- `pricingShow()` - Show pricing settings
- `pricingUpdate(Request)` - Update pricing settings
**Dependencies:** PlatformSetting model
**Services Used:** None (direct model operations)
**Validation:** Inline validation
**Response Format:** JSON with pricing data
**Special Features:** PlatformSetting key-value storage

### 2.2 Admin Controllers (`app/Http/Controllers/Admin/`)

#### AdminController
**Responsibilities:** Admin panel CRUD operations
**Methods:**
- `dashboard()` - Dashboard with trends
- `storeUser(Request)` - Create user
- `users()` - List users
- `showUser($id)` - Show user
- `updateUser(Request, $id)` - Update user
- `deleteUser($id)` - Delete user
- Similar methods for vehicles, cargo_requests, bookings, payments, trips, ratings
**Dependencies:** None
**Services Used:** None (direct model operations)
**Validation:** Inline validation
**Response Format:** JSON with model data

---

## 3. MODELS

### 3.1 User Model
**Attributes:**
- `id` (bigint, primary key)
- `full_name` (string)
- `phone` (string, unique)
- `email` (string, unique, nullable)
- `password` (string, hashed)
- `role` (enum: shipper, driver, admin, fleet_owner)
- `fleet_owner_id` (foreign key to users, nullable)
- `location` (string, nullable)
- `latitude` (decimal, nullable)
- `longitude` (decimal, nullable)
- `verification_status` (boolean, default false)
- `is_active` (boolean, nullable)
- `remember_token` (string, nullable)
- `timestamps`

**Relationships:**
- `vehicles()` - hasMany Vehicle
- `cargoRequests()` - hasMany CargoRequest
- `bookings()` - hasMany Booking (as driver)
- `documents()` - hasMany DriverDocument
- `drivers()` - hasMany User (as fleet owner)
- `fleetOwner()` - belongsTo User

**Accessors:**
- `is_admin` - Returns true if role is admin
- `is_fleet_owner` - Returns true if role is fleet_owner

**Traits:**
- HasApiTokens (Laravel Sanctum)
- HasFactory
- Notifiable

**Scopes:** None defined

### 3.2 Vehicle Model
**Attributes:**
- `id` (bigint, primary key)
- `user_id` (foreign key to users, cascade delete)
- `fleet_owner_id` (foreign key to users, nullable)
- `truck_type` (string)
- `vehicle_category` (enum: heavy, light, nullable)
- `plate_number` (string, unique)
- `capacity` (float)
- `current_city` (string, nullable)
- `latitude` (float, nullable)
- `longitude` (float, nullable)
- `last_location_at` (datetime, nullable)
- `availability_status` (string, default available)
- `rating` (decimal, default 0)
- `timestamps`

**Relationships:**
- `user()` - belongsTo User
- `fleetOwner()` - belongsTo User
- `bookings()` - hasMany Booking

**Casts:**
- `last_location_at` - datetime
- `latitude` - float
- `longitude` - float

**Traits:** None

**Scopes:** None defined

### 3.3 CargoRequest Model
**Attributes:**
- `id` (bigint, primary key)
- `user_id` (foreign key to users, cascade delete)
- `pickup_location` (string)
- `pickup_lat` (decimal, nullable)
- `pickup_lng` (decimal, nullable)
- `pickup_latitude` (decimal, nullable)
- `pickup_longitude` (decimal, nullable)
- `destination` (string)
- `material_type` (string)
- `weight` (float)
- `urgency_level` (string)
- `budget` (float, nullable)
- `price_type` (string, nullable: fixed, negotiable)
- `bid_deadline` (datetime, nullable)
- `status` (enum: pending, matched, completed, default pending)
- `service_type` (string, nullable: intercity, intracity)
- `city` (string, nullable)
- `pickup_area` (string, nullable)
- `dropoff_area` (string, nullable)
- `preferred_date` (date, nullable)
- `items_description` (string, nullable)
- `vehicle_type_needed` (string, nullable)
- `timestamps`

**Relationships:**
- `user()` - belongsTo User
- `bookings()` - hasOne Booking
- `bids()` - hasMany Bid

**Casts:**
- `bid_deadline` - datetime
- `preferred_date` - date

**Traits:** None

**Scopes:** None defined

### 3.4 Booking Model
**Attributes:**
- `id` (bigint, primary key)
- `cargo_id` (foreign key to cargo_requests, cascade delete)
- `vehicle_id` (foreign key to vehicles, cascade delete)
- `driver_id` (foreign key to users, cascade delete)
- `bid_id` (foreign key to bids, nullable)
- `booking_status` (enum: pending, accepted, completed, confirmed, delivered, cancelled)
- `estimated_price` (decimal, nullable)
- `commission_fee` (decimal)
- `timestamps`

**Relationships:**
- `cargoRequest()` - belongsTo CargoRequest (via cargo_id)
- `vehicle()` - belongsTo Vehicle
- `driver()` - belongsTo User (via driver_id)
- `trip()` - hasOne Trip
- `payment()` - hasOne Payment
- `rating()` - hasOne Rating
- `bid()` - belongsTo Bid

**Traits:** HasFactory

**Scopes:** None defined

### 3.5 Bid Model
**Attributes:**
- `id` (bigint, primary key)
- `cargo_request_id` (foreign key to cargo_requests, cascade delete)
- `driver_id` (foreign key to users, cascade delete)
- `vehicle_id` (foreign key to vehicles, cascade delete)
- `amount` (decimal)
- `note` (text, nullable)
- `available_datetime` (datetime, nullable)
- `status` (enum: pending, accepted, rejected, expired, countered)
- `ai_score` (float, nullable)
- `is_recommended` (boolean, default false)
- `distance_km` (float, nullable)
- `counter_amount` (decimal, nullable)
- `counter_note` (string, nullable)
- `counter_by` (string, nullable: shipper, driver)
- `counter_at` (datetime, nullable)
- `timestamps`

**Relationships:**
- `cargoRequest()` - belongsTo CargoRequest
- `driver()` - belongsTo User (via driver_id)
- `vehicle()` - belongsTo Vehicle
- `booking()` - hasOne Booking

**Casts:**
- `amount` - decimal:2
- `counter_amount` - decimal:2
- `ai_score` - float
- `is_recommended` - boolean
- `counter_at` - datetime
- `available_datetime` - datetime

**Traits:** None

**Scopes:** None defined

### 3.6 Trip Model
**Attributes:**
- `id` (bigint, primary key)
- `booking_id` (foreign key to bookings, cascade delete)
- `start_location` (string)
- `destination` (string)
- `route_data` (json, nullable)
- `trip_status` (enum: ongoing, completed)
- `trip_type` (string, nullable: single, multi_stop)
- `total_stops` (integer, default 1)
- `completed_stops` (integer, default 0)
- `start_time` (datetime, nullable)
- `end_time` (datetime, nullable)
- `timestamps`

**Relationships:**
- `booking()` - belongsTo Booking
- `tripStops()` - hasMany TripStop (ordered by stop_order)

**Casts:**
- `route_data` - array
- `start_time` - datetime
- `end_time` - datetime
- `total_stops` - integer
- `completed_stops` - integer

**Appends:**
- `total_amount` - Sum of all stop agreed prices

**Accessors:**
- `getTotalAmountAttribute()` - Calculates sum of trip stop prices

**Methods:**
- `isMultiStop()` - Returns true if trip_type is multi_stop

**Traits:** None

**Scopes:** None defined

### 3.7 TripStop Model
**Attributes:**
- `id` (bigint, primary key)
- `trip_id` (foreign key to trips, cascade delete)
- `cargo_request_id` (foreign key to cargo_requests, nullable, null on delete)
- `stop_order` (unsigned integer)
- `location_name` (string)
- `pickup_lat` (decimal, nullable)
- `pickup_lng` (decimal, nullable)
- `agreed_price` (decimal)
- `status` (enum: pending, arrived, loaded, completed, default pending)
- `notes` (text, nullable)
- `arrived_at` (datetime, nullable)
- `completed_at` (datetime, nullable)
- `timestamps`

**Relationships:**
- `trip()` - belongsTo Trip
- `cargoRequest()` - belongsTo CargoRequest

**Casts:**
- `agreed_price` - decimal:2
- `pickup_lat` - float
- `pickup_lng` - float
- `arrived_at` - datetime
- `completed_at` - datetime

**Scopes:**
- `inOrder()` - Orders by stop_order ascending

**Traits:** None

### 3.8 Payment Model
**Attributes:**
- `id` (bigint, primary key)
- `booking_id` (foreign key to bookings)
- `amount` (decimal)
- `commission_amount` (decimal, nullable)
- `driver_net_amount` (decimal, nullable)
- `paid_by` (foreign key to users, nullable)
- `payment_method` (string)
- `payment_status` (string)
- `transaction_ref` (string, nullable)
- `timestamps`

**Relationships:**
- `booking()` - belongsTo Booking

**Traits:** None

**Scopes:** None defined

### 3.9 Rating Model
**Attributes:**
- `id` (bigint, primary key)
- `booking_id` (foreign key to bookings)
- `shipper_id` (foreign key to users)
- `driver_id` (foreign key to users)
- `rater_id` (foreign key to users)
- `rating` (integer)
- `feedback` (string, nullable)
- `timestamps`

**Relationships:**
- `booking()` - belongsTo Booking
- `shipper()` - belongsTo User (via shipper_id)
- `driver()` - belongsTo User (via driver_id)

**Traits:** None

**Scopes:** None defined

### 3.10 DriverDocument Model
**Attributes:**
- `id` (bigint, primary key)
- `user_id` (foreign key to users, cascade delete)
- `document_type` (enum: license, national_id, vehicle_registration, insurance, tin)
- `file_path` (string)
- `original_name` (string)
- `status` (enum: pending, approved, rejected, default pending)
- `rejection_reason` (text, nullable)
- `reviewed_by` (foreign key to users, nullable, set null on delete)
- `reviewed_at` (datetime, nullable)
- `timestamps`

**Relationships:**
- `user()` - belongsTo User
- `reviewer()` - belongsTo User (via reviewed_by)

**Casts:**
- `reviewed_at` - datetime

**Static Methods:**
- `labelFor(string $type)` - Returns human-readable document type label

**Traits:** None

**Scopes:** None defined

### 3.11 BackhaulRecommendation Model
**Attributes:**
- `id` (bigint, primary key)
- `trip_id` (foreign key to trips, cascade delete)
- `driver_id` (foreign key to users, cascade delete)
- `cargo_request_id` (foreign key to cargo_requests, cascade delete)
- `score` (decimal, default 0)
- `status` (enum: pending, viewed, bid_placed, dismissed, default pending)
- `metadata` (jsonb, default '{}')
- `timestamps`

**Relationships:**
- `trip()` - belongsTo Trip
- `driver()` - belongsTo User (via driver_id)
- `cargoRequest()` - belongsTo CargoRequest

**Casts:**
- `score` - float
- `metadata` - array

**Traits:** None

**Scopes:** None defined

### 3.12 PlatformSetting Model
**Attributes:**
- `id` (bigint, primary key)
- `key` (string)
- `value` (string)
- `timestamps`

**Static Methods:**
- `get(string $key, mixed $default)` - Get setting value
- `set(string $key, mixed $value)` - Set setting value

**Traits:** None

**Scopes:** None defined

### 3.13 AIRecommendation Model
**Attributes:** Minimal model (119 bytes) - appears to be stub

**Relationships:** None defined

**Traits:** None

**Scopes:** None defined

---

## 4. SERVICES

### 4.1 AiEngineService
**Responsibilities:** Proxy to FastAPI AI engine
**Methods:**
- `recommendTruck(array $payload)` - POST to /ai/recommend-truck
- `backhaulOpportunities(array $payload)` - POST to /ai/backhaul-opportunities
- `predictPrice(array $payload)` - POST to /ai/predict-price
- `predictEmptyReturn(array $payload)` - POST to /ai/predict-empty-return
- `optimizeRoute(array $payload)` - POST to /ai/optimize-route
- `post($endpoint, $payload)` - Generic POST method with error handling
**External API Calls:** FastAPI AI engine (configurable URL, default localhost:8000)
**Database Interactions:** None
**Configuration:** `AI_ENGINE_URL` env variable or config

### 4.2 BackhaulService
**Responsibilities:** Generate backhaul cargo recommendations
**Methods:**
- `recommendForTrip(Trip $trip)` - Generate and persist recommendations
- `resolveCity(string $location)` - Resolve city name to coordinates
- `haversine(float $lat1, float $lng1, float $lat2, float $lng2)` - Distance calculation
**External API Calls:** None
**Database Interactions:**
- Reads CargoRequest (pending status)
- Creates/updates BackhaulRecommendation
**Special Features:**
- Ethiopian corridor city database (62 cities)
- Scoring algorithm (distance 40%, urgency 30%, weight 30%)
- 100km radius filter

### 4.3 BidService
**Responsibilities:** Bid lifecycle management
**Methods:**
- `placeBid(array $validated, User $bidder, CargoRequest $cargo)` - Place bid
- `acceptFixedPrice(CargoRequest $cargo, User $driver, Vehicle $vehicle)` - Accept fixed price
- `rankBids(Collection $bids)` - Sort bids by price + rating
- `counterBid(Bid $bid, User $actor, float $counterAmount, ?string $counterNote)` - Counter offer
- `acceptCounter(Bid $bid, User $actor)` - Accept counter offer
- `acceptBid(Bid $bid)` - Accept bid, create booking
- `haversine(float $lat1, float $lon1, float $lat2, float $lon2)` - Distance calculation
**External API Calls:** None
**Database Interactions:**
- Creates/updates Bid
- Creates Booking
- Updates CargoRequest status
- Reads Rating for ranking
**Special Features:**
- Transaction-based booking creation
- One bid per vehicle per cargo
- Intracity vs intercity bid rules
- 10% commission calculation

### 4.4 BookingService
**Responsibilities:** Booking management
**Methods:**
- `createBooking(array $data)` - Create booking with commission
- `updateBooking(Booking $booking, array $data)` - Update booking
- `deleteBooking(Booking $booking)` - Delete booking
**External API Calls:** None
**Database Interactions:**
- Creates/updates/deletes Booking
- Updates CargoRequest status
- Triggers BookingCreatedNotification
**Special Features:** 10% commission calculation

### 4.5 PaymentService
**Responsibilities:** Payment processing
**Methods:**
- `processPayment(Booking $booking, array $paymentDetails)` - Process payment
**External API Calls:** None
**Database Interactions:**
- Creates Payment
- Updates Booking status
**Special Features:**
- 10% commission calculation
- Driver net amount calculation

### 4.6 RoutingService
**Responsibilities:** Routing and geocoding via external APIs
**Methods:**
- `getRoute(float $lat1, float $lng1, float $lat2, float $lng2)` - OSRM route
- `nearestRoad(float $lat, float $lng)` - OSRM nearest road
- `searchPlace(string $query)` - Nominatim place search
- `reverseGeocode(float $lat, float $lng)` - Nominatim reverse geocode
**External API Calls:**
- OSRM (http://router.project-osrm.org)
- Nominatim (https://nominatim.openstreetmap.org)
**Database Interactions:** None (uses Cache)
**Special Features:**
- Caching (OSRM: 1h, nearest: 30min, search: 24h, reverse: 6h)
- Haversine fallback
- Ethiopia-specific filtering (countrycodes=et)

### 4.7 TripService
**Responsibilities:** Trip lifecycle management
**Methods:**
- `startTrip(Booking $booking)` - Start trip, dispatch backhaul job
- `updateLocation(Trip $trip, array $gpsData)` - Update location, broadcast event
- `completeTrip(Trip $trip)` - Complete trip, update statuses
**External API Calls:** None
**Database Interactions:**
- Creates/updates Trip
- Updates Booking status
- Updates CargoRequest status
- Triggers TripStatusUpdatedNotification
- Dispatches TripLocationUpdated event
**Special Features:**
- Multi-stop commission recalculation
- Route data breadcrumb tracking

---

## 5. MIDDLEWARE

### 5.1 AdminMiddleware
**Location:** `app/Http/Middleware/AdminMiddleware.php`
**Responsibilities:** Admin authorization check
**Logic:**
- Checks if user is authenticated
- Checks if user->is_admin is true
- Returns 403 Forbidden if not admin
**Usage:** Applied to admin routes via `auth:sanctum` + AdminMiddleware

### 5.2 Kernel Middleware Groups
**Location:** `app/Http/Kernel.php`
**API Group:**
- `throttle:api` - Rate limiting
- `SubstituteBindings` - Route model binding

**Web Group:**
- Empty (not used in this application)

### 5.3 Route Middleware
- `auth` - Authentication (session-based)
- `auth.basic` - Basic authentication
- `cache.headers` - Cache headers
- `can` - Authorization (policies)
- `guest` - Redirect if authenticated
- `password.confirm` - Password confirmation
- `signed` - Signed URL validation
- `throttle` - Rate limiting
- `verified` - Email verification
- `admin` - AdminMiddleware (custom)

### 5.4 Rate Limiting
- `throttle:login` - Applied to register/login routes
- `throttle:api` - Applied to all API routes

### 5.5 Logging
- No custom logging middleware
- Services use Log facade for error logging

---

## 6. VALIDATION

### 6.1 Form Requests (`app/Http/Requests/`)

#### BidStoreRequest
**Authorization:** Driver or fleet owner only
**Rules:**
- `vehicle_id` - required, integer, exists:vehicles,id
- `amount` - required, numeric, min:1, max:9999999.99
- `note` - nullable, string, max:500
- `available_datetime` - nullable, date, after:now
**Custom Messages:**
- amount.min: "Bid amount must be at least 1 ETB."
- available_datetime.after: "Available datetime must be in the future."

#### BookingCreateRequest
**Rules:** (Not fully analyzed - 1274 bytes)

#### BookingUpdateRequest
**Rules:** (Not fully analyzed - 618 bytes)

#### CargoCreateRequest
**Authorization:** Always true
**Rules (Intracity):**
- `service_type` - nullable, in:intercity,intracity
- `city` - required, string, max:255
- `pickup_area` - required, string, max:500
- `dropoff_area` - required, string, max:500
- `preferred_date` - required, date, after_or_equal:today
- `items_description` - required, string, max:2000
- `vehicle_type_needed` - nullable, string, max:100
- `bid_deadline` - nullable, date, after:now
- `budget` - nullable, numeric
- `price_type` - nullable, in:fixed,negotiable
- `status` - sometimes, in:pending,matched,completed
- `pickup_lat` - nullable, numeric, between:-90,90
- `pickup_lng` - nullable, numeric, between:-180,180

**Rules (Intercity - default):**
- `service_type` - nullable, in:intercity,intracity
- `pickup_location` - required, string, max:255
- `destination` - required, string, max:255
- `material_type` - required, string, max:255
- `weight` - required, numeric
- `urgency_level` - required, string, max:255
- `budget` - nullable, numeric
- `price_type` - nullable, in:fixed,negotiable
- `bid_deadline` - nullable, date, after:now
- `status` - sometimes, in:pending,matched,completed
- `pickup_lat` - nullable, numeric, between:-90,90
- `pickup_lng` - nullable, numeric, between:-180,180

#### CargoUpdateRequest
**Rules:** (Not fully analyzed - 722 bytes)

#### DocumentUploadRequest
**Rules:** (Not fully analyzed - 625 bytes)

#### LoginRequest
**Rules:** (Not fully analyzed - 568 bytes)

#### PaymentProcessRequest
**Rules:** (Not fully analyzed - 818 bytes)

#### RegisterRequest
**Rules:** (Not fully analyzed - 851 bytes)

#### TripCreateRequest
**Rules:** (Not fully analyzed - 360 bytes)

#### TripLocationUpdateRequest
**Rules:** (Not fully analyzed - 367 bytes)

#### TripUpdateStatusRequest
**Rules:** (Not fully analyzed - 352 bytes)

#### UserStoreRequest
**Rules:** (Not fully analyzed - 619 bytes)

#### UserUpdateRequest
**Rules:** (Not fully analyzed - 687 bytes)

#### VehicleLocationUpdateRequest
**Rules:** (Not fully analyzed - 740 bytes)

#### VehicleRegisterRequest
**Authorization:** Always true
**Rules:**
- `truck_type` - required, string, max:255
- `vehicle_category` - nullable, in:heavy,light
- `plate_number` - required, string, max:255, unique:vehicles,plate_number
- `capacity` - required, numeric
- `current_city` - nullable, string, max:255
- `latitude` - nullable, numeric, between:-90,90
- `longitude` - nullable, numeric, between:-180,180
- `availability_status` - sometimes, string
- `rating` - sometimes, numeric, min:0, max:5

#### VehicleUpdateRequest
**Rules:** (Not fully analyzed - 881 bytes)

---

## 7. DATABASE

### 7.1 Tables

#### users
**Columns:**
- id (bigint, primary key)
- full_name (string)
- phone (string, unique)
- email (string, unique, nullable - made nullable in migration)
- password (string, hashed)
- role (enum: shipper, driver, admin)
- location (string, nullable)
- latitude (decimal 10,7, nullable)
- longitude (decimal 10,7, nullable)
- verification_status (boolean, default false)
- is_active (boolean, nullable - added in migration)
- fleet_owner_id (foreign key to users, nullable - added in migration)
- remember_token (string, nullable)
- timestamps

**Indexes:**
- Primary key on id
- Unique on phone
- Unique on email
- Foreign key on fleet_owner_id

#### vehicles
**Columns:**
- id (bigint, primary key)
- user_id (foreign key to users, cascade delete)
- fleet_owner_id (foreign key to users, nullable - added in migration)
- truck_type (string)
- vehicle_category (string, nullable: heavy,light - added in migration)
- plate_number (string, unique)
- capacity (float)
- current_city (string, nullable)
- latitude (decimal 10,7, nullable)
- longitude (decimal 10,7, nullable)
- last_location_at (timestamp, nullable - added in migration)
- availability_status (string, default available)
- rating (decimal 3,2, default 0)
- timestamps

**Indexes:**
- Primary key on id
- Unique on plate_number
- Foreign key on user_id
- Foreign key on fleet_owner_id

#### cargo_requests
**Columns:**
- id (bigint, primary key)
- user_id (foreign key to users, cascade delete)
- pickup_location (string)
- pickup_lat (decimal 10,8, nullable - added in migration)
- pickup_lng (decimal 11,8, nullable - added in migration)
- pickup_latitude (decimal, nullable)
- pickup_longitude (decimal, nullable)
- destination (string)
- material_type (string)
- weight (float)
- urgency_level (string)
- budget (float, nullable)
- price_type (string, nullable: fixed,negotiable - added in migration)
- bid_deadline (datetime, nullable - added in migration)
- status (enum: pending, matched, completed, default pending)
- service_type (string, nullable: intercity,intracity - added in migration)
- city (string, nullable - added in migration)
- pickup_area (string, nullable - added in migration)
- dropoff_area (string, nullable - added in migration)
- preferred_date (date, nullable - added in migration)
- items_description (string, nullable - added in migration)
- vehicle_type_needed (string, nullable - added in migration)
- timestamps

**Indexes:**
- Primary key on id
- Foreign key on user_id

#### bookings
**Columns:**
- id (bigint, primary key)
- cargo_id (foreign key to cargo_requests, cascade delete)
- vehicle_id (foreign key to vehicles, cascade delete)
- driver_id (foreign key to users, cascade delete)
- bid_id (foreign key to bids, nullable - added in migration)
- booking_status (enum: pending, accepted, completed, confirmed, delivered - added in migration)
- estimated_price (decimal 10,2, nullable)
- commission_fee (decimal 10,2)
- timestamps

**Indexes:**
- Primary key on id
- Foreign key on cargo_id
- Foreign key on vehicle_id
- Foreign key on driver_id
- Foreign key on bid_id

#### bids
**Columns:**
- id (bigint, primary key)
- cargo_request_id (foreign key to cargo_requests, cascade delete)
- driver_id (foreign key to users, cascade delete)
- vehicle_id (foreign key to vehicles, cascade delete)
- amount (decimal 10,2)
- note (text, nullable)
- available_datetime (datetime, nullable - added in migration)
- status (enum: pending, accepted, rejected, expired, countered - added in migration)
- ai_score (float, nullable)
- is_recommended (boolean, default false)
- distance_km (float, nullable - added in migration)
- counter_amount (decimal 10,2, nullable - added in migration)
- counter_note (string, nullable - added in migration)
- counter_by (string, nullable - added in migration)
- counter_at (datetime, nullable - added in migration)
- timestamps

**Indexes:**
- Primary key on id
- Foreign key on cargo_request_id
- Foreign key on driver_id
- Foreign key on vehicle_id
- Unique on (cargo_request_id, driver_id) - removed in migration, changed to vehicle-based uniqueness

#### trips
**Columns:**
- id (bigint, primary key)
- booking_id (foreign key to bookings, cascade delete)
- start_location (string)
- destination (string)
- route_data (json, nullable)
- trip_status (enum: ongoing, completed)
- trip_type (string, nullable: single, multi_stop - added in migration)
- total_stops (integer, default 1 - added in migration)
- completed_stops (integer, default 0 - added in migration)
- start_time (timestamp, nullable)
- end_time (timestamp, nullable)
- timestamps

**Indexes:**
- Primary key on id
- Foreign key on booking_id

#### trip_stops
**Columns:**
- id (bigint, primary key)
- trip_id (foreign key to trips, cascade delete)
- cargo_request_id (foreign key to cargo_requests, nullable, null on delete)
- stop_order (unsigned integer)
- location_name (string)
- pickup_lat (decimal 10,8, nullable)
- pickup_lng (decimal 11,8, nullable)
- agreed_price (decimal 10,2)
- status (enum: pending, arrived, loaded, completed, default pending)
- notes (text, nullable)
- arrived_at (timestamp, nullable)
- completed_at (timestamp, nullable)
- timestamps

**Indexes:**
- Primary key on id
- Foreign key on trip_id
- Foreign key on cargo_request_id
- Index on (trip_id, stop_order)

#### payments
**Columns:**
- id (bigint, primary key)
- booking_id (foreign key to bookings)
- amount (decimal)
- commission_amount (decimal, nullable - added in migration)
- driver_net_amount (decimal, nullable - added in migration)
- paid_by (foreign key to users, nullable)
- payment_method (string)
- payment_status (string)
- transaction_ref (string, nullable)
- timestamps

**Indexes:**
- Primary key on id
- Foreign key on booking_id
- Foreign key on paid_by

#### ratings
**Columns:**
- id (bigint, primary key)
- booking_id (foreign key to bookings)
- shipper_id (foreign key to users)
- driver_id (foreign key to users)
- rater_id (foreign key to users - added in migration)
- rating (integer)
- feedback (string, nullable)
- timestamps

**Indexes:**
- Primary key on id
- Foreign key on booking_id
- Foreign key on shipper_id
- Foreign key on driver_id
- Foreign key on rater_id

#### driver_documents
**Columns:**
- id (bigint, primary key)
- user_id (foreign key to users, cascade delete)
- document_type (enum: license, national_id, vehicle_registration, insurance, tin)
- file_path (string)
- original_name (string)
- status (enum: pending, approved, rejected, default pending)
- rejection_reason (text, nullable)
- reviewed_by (foreign key to users, nullable, set null on delete)
- reviewed_at (timestamp, nullable)
- timestamps

**Indexes:**
- Primary key on id
- Foreign key on user_id
- Foreign key on reviewed_by
- Unique on (user_id, document_type)

#### backhaul_recommendations
**Columns:**
- id (bigint, primary key)
- trip_id (foreign key to trips, cascade delete)
- driver_id (foreign key to users, cascade delete)
- cargo_request_id (foreign key to cargo_requests, cascade delete)
- score (decimal 4,3, default 0)
- status (enum: pending, viewed, bid_placed, dismissed, default pending)
- metadata (jsonb, default '{}')
- timestamps

**Indexes:**
- Primary key on id
- Foreign key on trip_id
- Foreign key on driver_id
- Foreign key on cargo_request_id
- Unique on (trip_id, driver_id, cargo_request_id) - named backhaul_unique

#### platform_settings
**Columns:**
- id (bigint, primary key)
- key (string)
- value (string)
- timestamps

**Indexes:**
- Primary key on id
- Unique on key (implied by updateOrCreate logic)

#### ai_recommendations
**Columns:** (Not fully analyzed - stub model)

#### personal_access_tokens
**Columns:** Laravel Sanctum default table
- id (bigint, primary key)
- tokenable_type (string)
- tokenable_id (bigint)
- name (string)
- token (string, unique)
- abilities (text, nullable)
- last_used_at (timestamp, nullable)
- expires_at (timestamp, nullable)
- created_at (timestamp)
- updated_at (timestamp)

**Indexes:**
- Primary key on id
- Unique on token

#### notifications
**Columns:** Laravel notifications default table
- id (bigint, primary key)
- type (string)
- notifiable_type (string)
- notifiable_id (bigint)
- data (json)
- read_at (timestamp, nullable)
- created_at (timestamp)
- updated_at (timestamp)

**Indexes:**
- Primary key on id
- Index on (notifiable_type, notifiable_id)

#### jobs
**Columns:** Laravel queue default table
- id (bigint, primary key)
- queue (string)
- payload (longtext)
- attempts (unsigned integer)
- reserved_at (timestamp, nullable)
- available_at (timestamp)
- created_at (timestamp)

**Indexes:**
- Primary key on id

#### cache
**Columns:** Laravel cache default table
- key (string, primary key)
- value (text)
- expiration (integer)

#### password_reset_tokens
**Columns:** Laravel auth default table
- email (string, primary key)
- token (string)
- created_at (timestamp, nullable)

#### sessions
**Columns:** Laravel session default table
- id (string, primary key)
- user_id (bigint, nullable, indexed)
- ip_address (string 45, nullable)
- user_agent (text, nullable)
- payload (longtext)
- last_activity (integer, indexed)

### 7.2 Relationships Summary
- User → Vehicle (one-to-many)
- User → CargoRequest (one-to-many)
- User → Booking (one-to-many as driver)
- User → DriverDocument (one-to-many)
- User → User (self-referential for fleet owner → drivers)
- Vehicle → Booking (one-to-many)
- CargoRequest → Booking (one-to-one)
- CargoRequest → Bid (one-to-many)
- Booking → Trip (one-to-one)
- Booking → Payment (one-to-one)
- Booking → Rating (one-to-one)
- Booking → Bid (belongs-to)
- Trip → TripStop (one-to-many)
- TripStop → CargoRequest (belongs-to, nullable)
- BackhaulRecommendation → Trip, Driver, CargoRequest (belongs-to)

### 7.3 Constraints
- Foreign keys with CASCADE DELETE on most relationships
- Unique constraints on phone, email, plate_number
- Composite unique on (user_id, document_type)
- Composite unique on (trip_id, driver_id, cargo_request_id)
- Composite unique on (cargo_request_id, driver_id) in bids (later removed)

### 7.4 Indexes
- Primary keys on all tables
- Foreign key indexes
- Unique indexes as noted above
- Composite index on (trip_id, stop_order) for trip_stops

---

## 8. AUTHENTICATION

### 8.1 Laravel Sanctum Configuration
**Location:** `config/sanctum.php`
**Stateful Domains:** localhost, localhost:3000, 127.0.0.1, 127.0.0.1:8000, ::1
**Guard:** web (session-based for SPA)
**Expiration:** null (tokens don't expire)
**Token Prefix:** Configurable via SANCTUM_TOKEN_PREFIX env
**Middleware:**
- authenticate_session
- encrypt_cookies
- validate_csrf_token

### 8.2 Guards
**Location:** `config/auth.php`
**Default Guard:** web
**Password Broker:** users
**Guards Defined:**
- `web` - Session-based driver, users provider

**Providers:**
- `users` - Eloquent driver, User model

### 8.3 Policies
**No explicit policies defined** - Authorization handled via inline checks in controllers and middleware

### 8.4 Roles
**User Roles:**
- `shipper` - Creates cargo requests
- `driver` - Places bids, drives trips
- `admin` - Full system access
- `fleet_owner` - Manages fleet of drivers and vehicles

**Role-Based Access Control:**
- Implemented via `$user->role` checks in controllers
- AdminMiddleware for admin-only routes
- Inline authorization in controllers (e.g., driver can only update own vehicles)

### 8.5 Permissions
**No granular permission system** - Role-based only

### 8.6 Verification Status
**Driver Verification:**
- `verification_status` boolean on User model
- Drivers start inactive (false)
- Requires 5 approved documents: license, national_id, vehicle_registration, insurance, tin
- Auto-verified when all 5 documents approved
- Controlled via DocumentController@review

---

## 9. EXTERNAL INTEGRATIONS

### 9.1 FastAPI AI Service
**Service:** AiEngineService
**Base URL:** Configurable via `AI_ENGINE_URL` env (default: http://localhost:8000)
**Endpoints:**
- POST /ai/recommend-truck
- POST /ai/backhaul-opportunities
- POST /ai/predict-price
- POST /ai/predict-empty-return
- POST /ai/optimize-route
**Timeout:** 5 seconds
**Error Handling:** Returns error structure with fallback logic in controllers
**Fallback:** Controllers implement local calculation when AI service unavailable

### 9.2 OpenStreetMap / Nominatim
**Service:** RoutingService
**Base URL:** https://nominatim.openstreetmap.org
**Endpoints:**
- GET /search (place search)
- GET /reverse (reverse geocoding)
**User-Agent:** EthioLoadAI/1.0 (contact@ethioloadai.et)
**Timeout:** 5 seconds
**Caching:**
- Search results: 24 hours
- Reverse geocode: 6 hours
**Country Filter:** Ethiopia only (countrycodes=et)
**Error Handling:** Returns empty array or null on failure

### 9.3 OSRM (Open Source Routing Machine)
**Service:** RoutingService
**Base URL:** http://router.project-osrm.org
**Endpoints:**
- GET /route/v1/driving/{coords} (route calculation)
- GET /nearest/v1/driving/{coords} (nearest road snap)
**Parameters:**
- overview=full
- geometries=geojson
- alternatives=true
- steps=true
**Timeout:** 5 seconds
**Caching:**
- Route results: 1 hour
- Nearest road: 30 minutes
**Error Handling:** Returns null on failure, controllers use haversine fallback
**Coordinate Order:** OSRM expects lng,lat order

### 9.4 Email
**Status:** Not implemented in current codebase
**Infrastructure:** Laravel's notification system supports email channels
**Future:** Email notifications could be added to notification classes

### 9.5 SMS
**Status:** Not implemented in current codebase
**Infrastructure:** Would require SMS gateway integration
**Future:** SMS notifications could be added to notification classes

### 9.6 File Storage
**Service:** Laravel Storage (private disk)
**Location:** `storage/app/driver-documents/{user_id}/`
**Access:** Private disk (not publicly accessible)
**Controller:** DocumentController
**Operations:**
- Upload: `Storage::disk('private')->store()`
- Download: `Storage::disk('private')->download()`
- Delete: `Storage::disk('private')->delete()`
**File Types:** Driver documents (license, national_id, vehicle_registration, insurance, tin)
**Security:** Only document owner or admin can download

---

## 10. EVENTS

### 10.1 TripLocationUpdated
**Location:** `app/Events/TripLocationUpdated.php`
**Purpose:** Broadcast real-time trip location updates
**Implements:** ShouldBroadcastNow (synchronous broadcast)
**Channel:** PrivateChannel('trip.{trip_id}')
**Broadcast Name:** TripLocationUpdated
**Payload:**
- trip_id
- lat
- lng
- timestamp
**Triggered By:** TripService@updateLocation
**Usage:** Real-time tracking for shippers

---

## 11. QUEUES

### 11.1 Queue Configuration
**Default Queue:** Laravel's default queue (database driver)
**Jobs Table:** jobs table created in migration
**Queue Worker:** Not configured in analyzed code (assumes standard Laravel queue worker)

### 11.2 Jobs

#### GenerateBackhaulRecommendations
**Location:** `app/Jobs/GenerateBackhaulRecommendations.php`
**Purpose:** Async generation of backhaul cargo recommendations
**Implements:** ShouldQueue
**Tries:** 3
**Timeout:** 30 seconds
**Parameters:** Trip model
**Service Used:** BackhaulService
**Triggered By:** TripController@store (after trip creation)
**Error Handling:** Logs error, rethrows for retry
**Success:** Logs count of recommendations generated

---

## 12. NOTIFICATIONS

### 12.1 Notification Classes (`app/Notifications/`)

#### BidPlacedNotification
**Purpose:** Notify shipper when driver places bid
**Channels:** database
**Payload:**
- bid_id
- cargo_id
- route (pickup → destination)
- amount
- driver_name
- message
- type: bid_placed
**Triggered By:** BidController@store

#### BookingCreatedNotification
**Purpose:** Notify driver/shipper when booking is confirmed
**Channels:** database
**Payload:**
- booking_id
- title (bilingual: Amharic/English)
- message (bilingual)
- estimated_price
- type: booking_created
**Triggered By:** BidController@accept, BidController@acceptCounter, CargoRequestController@bookDirect

#### BidRejectedNotification
**Purpose:** Notify driver when bid is rejected
**Channels:** database
**Payload:** (Not fully analyzed)
**Triggered By:** BidController@reject, cargo deletion

#### BidCounteredNotification
**Purpose:** Notify counter-offer recipient
**Channels:** database
**Payload:** (Not fully analyzed)
**Triggered By:** BidController@counter

#### FixedPriceBookedNotification
**Purpose:** Notify shipper when fixed-price cargo is booked
**Channels:** database
**Payload:** (Not fully analyzed)
**Triggered By:** CargoRequestController@bookDirect

#### DocumentReviewedNotification
**Purpose:** Notify driver of document review result
**Channels:** database
**Payload:** (Not fully analyzed)
**Triggered By:** DocumentController@review

#### AllDocumentsApprovedNotification
**Purpose:** Notify driver when all documents approved (verification complete)
**Channels:** database
**Payload:** (Not fully analyzed)
**Triggered By:** DocumentController@review (when all 5 docs approved)

#### TripStatusUpdatedNotification
**Purpose:** Notify shipper of trip status changes
**Channels:** database
**Payload:** (Not fully analyzed)
**Triggered By:** TripService@startTrip, TripService@completeTrip

### 12.2 Notification Storage
**Table:** notifications
**Access:** Via NotificationController
**Endpoints:**
- GET /notifications - List user's notifications
- PATCH /notifications/{id}/read - Mark single read
- PATCH /notifications/read-all - Mark all read

---

## 13. SCHEDULED TASKS

**Status:** No scheduled tasks configured in analyzed code
**Infrastructure:** Laravel's scheduler (app/Console/Kernel.php) available but not used
**Future:** Could add scheduled tasks for:
- Cleanup expired bids
- Archive old data
- Generate analytics reports

---

## 14. ADDITIONAL FEATURES

### 14.1 Ethiopian Geography Data
**VehicleController::CITY_COORDS** - 44 cities with coordinates
**BackhaulService::$CITIES** - 62 corridor cities with name variants
**Usage:**
- Nearest city detection from GPS
- Distance calculations
- Route planning

### 14.2 Distance Calculations
**Haversine Formula:** Used throughout for great-circle distance
**Implementations:**
- VehicleController::haversineKm()
- CargoRequestController::haversineKm()
- AiController::haversineKm()
- BidService::haversine()
- BackhaulService::haversine()
- RoutingController::haversineKm()
- GeocodingController::haversineKm()

### 14.3 Price Calculation Logic
**Base Formula:** distance_km × rate × weight × urgency_multiplier × material_multiplier
**Rate Range:** PlatformSetting pricing.rate_min to pricing.rate_max (default 18-28 ETB/km)
**Urgency Multipliers:**
- express: 1.4
- high: 1.2
- normal: 1.0
**Material Multipliers:**
- Fragile (glass, electronic, ceramic): 1.25
- Perishable (vegetable, fruit, dairy, teff, grain, coffee): 1.15
- Bulk (cement, sand, gravel, construction): 0.9
- General: 1.0
**Commission:** 10% of final price

### 14.4 Commission Structure
**Standard:** 10% of booking amount
**Calculation Points:**
- BidService@acceptBid
- BidService@acceptCounter
- BookingService@createBooking
- PaymentService@processPayment
- TripService@completeTrip (multi-stop recalculation)

### 14.5 Multi-stop Trip Support
**Trip Types:** single, multi_stop
**Features:**
- Sequential stop enforcement
- Per-stop pricing
- Route data breadcrumb tracking
- Auto-complete on final stop
- Shipper privacy (hide other cargo details)

### 14.6 Intracity vs Intercity
**Service Types:**
- intercity - Long-distance between cities
- intracity - Within same city
**Differences:**
- Intracity requires available_datetime
- Intracity filtered by current city
- Different bid ranking logic
- Different pricing (platform settings)

---

## 15. TECHNICAL DEBT & NOTES

### 15.1 Stub Implementations
- Disputes system (AdminApiController@disputes, @createDispute, @resolveDispute)
- AIRecommendation model (minimal)
- Escrow system (returns zeros)

### 15.2 Data Quality Issues
- Duplicate coordinate fields (pickup_lat/pickup_latitude, pickup_lng/pickup_longitude)
- Some nullable fields that should be required
- Email made nullable after initial migration

### 15.3 Hard-coded Values
- Commission rate (10%) - should be configurable
- City coordinate tables (should be in database)
- Material type multipliers (should be in database)
- Urgency multipliers (should be in database)

### 15.4 Missing Features
- Email notifications
- SMS notifications
- Scheduled tasks
- Comprehensive error logging
- API rate limiting per user
- Request throttling configuration

---

## 16. MIGRATION CONSIDERATIONS FOR SPRING BOOT

### 16.1 Authentication
- Laravel Sanctum → Spring Security with JWT
- Token management → JWT token service
- Middleware → Spring Security filters

### 16.2 Database
- Laravel Eloquent → Spring Data JPA
- Migrations → Flyway or Liquibase
- Relationships → JPA annotations (@OneToMany, @ManyToOne, etc.)
- Casts → JPA converters

### 16.3 Validation
- Form Requests → Spring Validation (@Valid, @NotNull, etc.)
- Custom rules → Custom validators
- Error responses → @ControllerAdvice

### 16.4 Services
- Laravel Services → Spring @Service classes
- Dependency Injection → Spring DI container
- HTTP Client → RestTemplate or WebClient

### 16.5 Events
- Laravel Events → Spring ApplicationEventPublisher
- Broadcasting → WebSocket (STOMP over WebSocket)

### 16.6 Queues
- Laravel Jobs → Spring @Async with @EnableAsync
- Queue configuration → Spring TaskExecutor

### 16.7 Notifications
- Laravel Notifications → Spring EmailService + SMS gateway
- Database notifications → Custom notification table

### 16.8 File Storage
- Laravel Storage → Spring Resource (local, S3, etc.)
- Private disk → Spring Security on file endpoints

### 16.9 Caching
- Laravel Cache → Spring Cache abstraction (Redis, Caffeine, etc.)

### 16.10 Configuration
- Laravel config → Spring @ConfigurationProperties
- Environment variables → Spring @Value

---

**End of Migration Inventory**
