# EthioLoad AI - Complete Integration & API Guide

## All 12 Repositories & 50+ Endpoints

### 1. **AuthRepository** (6 endpoints)
```
POST   /register              → Register user with role
POST   /login                 → Login (returns token)
POST   /logout                → Logout (deletes token)
GET    /me                    → Get current user
PATCH  /me/password           → Change password
```
**Storage**: Token saved to secure storage
**File**: `repositories.dart:8-72`

---

### 2. **CargoRepository** (13 endpoints)
```
GET    /cargo-requests              → List all cargo
GET    /cargo-requests/{id}         → Get cargo details
POST   /cargo/create                → Create cargo (intercity)
POST   /cargo/create                → Create intracity variant
PATCH  /cargo-requests/{id}         → Update status
POST   /cargo-requests/{id}/book-direct    → Direct book
POST   /cargo-requests/{id}/accept-price   → Register interest
DELETE /cargo-requests/{id}         → Delete cargo
POST   /ai/predict-price            → AI price estimate
POST   /geocode/nearest-city        → GPS → nearest city
GET    /cargo-requests (with meta)  → Get location_unset flag
GET    /driver/return-cargo         → Get return cargo opportunities
GET    /cargo-requests/{id}/nearby-drivers → Get nearby drivers
```
**File**: `repositories.dart:80-333`

---

### 3. **VehicleRepository** (5 endpoints)
```
GET    /vehicles               → List vehicles
POST   /vehicle/register       → Register vehicle
PATCH  /driver/current-city    → Update driver city
PATCH  /vehicles/{id}/location → Update vehicle GPS
GET    /vehicle/nearby         → Get nearby vehicles
```
**File**: `repositories.dart:341-414`

---

### 4. **BookingRepository** (3 endpoints)
```
GET    /bookings         → List bookings
GET    /bookings/{id}    → Get booking
POST   /booking/create   → Create booking
```
**File**: `repositories.dart:418-465`

---

### 5. **TripRepository** (11 endpoints)
```
POST   /trips                           → Start trip
GET    /trips/{id}                      → Get trip
PATCH  /trips/{id}/status               → Update status
PATCH  /trips/{id}/location             → Update trip GPS
GET    /trips/{id}/stops                → List stops
POST   /trips/{id}/stops                → Add stop
PATCH  /trips/{id}/stops/{stop}/arrive  → Mark arrived
PATCH  /trips/{id}/stops/{stop}/load    → Mark loaded
PATCH  /trips/{id}/stops/{stop}/complete → Complete stop
DELETE /trips/{id}/stops/{stop}         → Remove stop
GET    /trips/{id}/location             → Get live location
GET    /trips/{id}/backhaul-recommendations → Get recommendations
PATCH  /recommendations/{id}/dismiss    → Dismiss recommendation
```
**File**: `repositories.dart:469-634`

---

### 6. **PaymentRepository** (2 endpoints)
```
POST   /payments               → Create payment
GET    /payments/{booking_id}  → Get payment
```
**Methods**: telebirr, cbe_birr, chapa, cash

---

### 7. **AiRepository** (5 AI endpoints)
```
POST   /ai/recommend-truck            → Find best trucks
POST   /ai/backhaul-opportunities     → Find return cargo
POST   /ai/predict-price              → Predict cargo price
POST   /ai/predict-empty-return       → Empty return risk
POST   /ai/optimize-route             → Optimize multi-stop route
```
**File**: `repositories.dart:674-770`

---

### 8. **DocumentRepository** (2 endpoints)
```
GET    /driver/documents    → List documents
POST   /driver/documents    → Upload document (multipart)
```

---

### 9. **BidRepository** (8 endpoints)
```
GET    /cargo-requests/{id}/bids      → Get bids for cargo
POST   /cargo-requests/{id}/bids      → Place bid
PATCH  /bids/{id}/accept              → Accept bid → Booking
PATCH  /bids/{id}/reject              → Reject bid
PATCH  /bids/{id}/counter             → Counter-offer
PATCH  /bids/{id}/accept-counter      → Accept counter → Booking
PATCH  /bids/{id}                     → Update own bid
GET    /driver/bids                   → Get driver's bids
```

---

### 10. **RatingRepository** (1 endpoint)
```
POST   /ratings    → Submit rating + feedback
```

---

### 11. **NotificationRepository** (3 endpoints)
```
GET    /notifications           → List notifications
PATCH  /notifications/{id}/read → Mark read
PATCH  /notifications/read-all  → Mark all read
```

---

### 12. **RoutingRepository** (4 endpoints - proxy to OSRM/Nominatim)
```
GET    /routing/route              → Get OSRM route
GET    /routing/search?q=...       → Search place
GET    /routing/reverse?lat=&lng=  → Reverse geocode
GET    /nearby-trucks?lat=&lng=    → Find nearby trucks
```

---

## Authentication Flow - Complete

### Step 1: Login
```dart
// User enters email/phone & password
final response = await repository.login(
  identifier: "user@example.com",
  password: "password123",
);
// Response: { token: "...", user: { id, name, role, ... } }
```

### Step 2: Token Storage
```dart
// Token automatically saved to encrypted storage
await apiClient.saveToken(response.token);
```

### Step 3: Token Attachment
```dart
// ApiInterceptor automatically adds Bearer token to every request
onRequest(RequestOptions options, handler) {
  final token = await secureStorage.read(key: 'auth_token');
  if (token != null) {
    options.headers['Authorization'] = 'Bearer $token';  // ← Automatic!
  }
  handler.next(options);
}
```

### Step 4: Initial Auth Check (Cold Start)
```dart
// On app startup
final token = await repository.getStoredToken();
if (token != null) {
  try {
    final user = await repository.me();  // Validate token
    // Success: logged in
  } catch {
    // Token expired: delete and redirect to login
    await repository.deleteToken();
  }
}
```

### Step 5: Logout
```dart
await repository.logout();
// Sends POST /logout, then deletes token
```

---

## Token Storage - Platform-Specific

| Platform | Storage | Security |
|----------|---------|----------|
| **iOS** | Keychain | Encrypted by default |
| **Android** | KeyStore | AES-256 encryption |
| **Web** | Browser secure context | HTTPS-only |

**Key Name**: `'auth_token'`
**Operations**:
- `saveToken(String)` - Save encrypted
- `getToken()` - Retrieve decrypted
- `deleteToken()` - Secure delete

---

## Navigation Architecture

### Public Routes (No Auth Required)
```
/splash              → SplashScreen
/landing             → LandingScreen
/login               → LoginScreen
/register            → RegisterScreen
```

### Shipper Dashboard
```
/shipper                           → Home (job summary, recent bookings)
/freight                           → List all cargo
/freight/:id                       → Cargo detail
/create-freight                    → Create new cargo request
/cargo-bids/:cargoId              → View & select bids
/tracking/:freightId              → Live driver tracking
/my-bookings                       → Booking history (shipper view)
```

### Driver Dashboard
```
/driver                            → Dashboard (available jobs)
/driver/bids                       → My bids on cargo
/driver/active-trip/:tripId       → Active trip management
/navigate                          → Turn-by-turn navigation
/nearby-trucks                     → Map of nearby trucks
/driver-documents                  → Document upload/verification
/my-bookings                       → Booking history (driver view)
```

### Fleet Owner Dashboard
```
/fleet                            → Fleet dashboard
/fleet/drivers                    → Manage drivers
/fleet/vehicles                   → Manage vehicles
/fleet/dispatch                   → Dispatch management
```

### Shared
```
/ai-tools          → AI recommendation tools
/profile           → User profile management
```

### Navigation Guards
```dart
// Unauthenticated users redirected to /landing
if (!isLoggedIn && !isPublic) return '/landing';

// Authenticated users on public page redirected to dashboard
if (isLoggedIn && isPublic) {
  if (role == 'driver') return '/driver';
  if (role == 'fleet_owner') return '/fleet';
  return '/shipper';
}

// Unverified drivers forced to /driver-documents
if (role == 'driver' && !isVerified && path != '/driver-documents') {
  return '/driver-documents';
}
```

---

## Riverpod Providers (Complete List)

### Authentication
```dart
authNotifierProvider
  → StateNotifierProvider<AuthNotifier, AuthState>
  → Manages: user, token, isLoading, error
  → Methods: login(), register(), logout(), checkAuthStatus()
```

### Cargo Management
```dart
cargoListProvider                    → FutureProvider.autoDispose
cargoListWithMetaProvider            → ({locationUnset, cargo})
singleCargoProvider.family({id})     → CargoRequest
```

### Vehicles
```dart
vehicleListProvider                  → FutureProvider.autoDispose
nearbyVehiclesProvider               → List<Vehicle>
```

### Bookings & Trips
```dart
bookingListProvider                  → List<Booking>
singleBookingProvider.family({id})   → Booking
tripProvider.family({tripId})        → Trip
tripStopsProvider.family({tripId})   → List<TripStop>
tripLocationProvider.family({tripId})→ TripLocation (live!)
```

### Bids & Backhaul
```dart
bidsForCargoProvider.family({id})    → List<Bid>
myBidsProvider                       → Driver's bids
backhaulRecommendationsProvider      → Trip backhaul opportunities
returnCargoProvider                  → Return cargo at destination
```

### AI Features
```dart
truckRecommendationProvider.family   → List<TruckRecommendation>
emptyReturnRiskProvider.family       → Map<String, dynamic>
```

### Other
```dart
paymentByBookingProvider.family      → Payment
driverDocumentsProvider              → List<DriverDocument>
nearbyDriversProvider.family         → List<NearbyDriver>
notificationsProvider                → ({items, unreadCount})
```

---

## Dio API Calls - Complete Inventory

### GET Requests (Read Operations)
```
/cargo-requests
/cargo-requests/{id}
/me
/vehicles
/vehicle/nearby
/bookings
/bookings/{id}
/trips/{id}
/trips/{id}/stops
/trips/{id}/location
/trips/{id}/backhaul-recommendations
/driver/documents
/driver/bids
/cargo-requests/{cargoId}/bids
/notifications
/routing/route?from_lat=...&to_lat=...
/routing/search?q=...
/routing/reverse?lat=...&lng=...
/nearby-trucks?lat=...&lng=...
/driver/return-cargo
/cargo-requests/{id}/nearby-drivers
```

### POST Requests (Create/Action)
```
/register
/login
/cargo/create
/cargo-requests/{id}/accept-price
/cargo-requests/{id}/book-direct
/ai/predict-price
/geocode/nearest-city
/vehicle/register
/trips
/trips/{id}/stops
/booking/create
/payments
/cargo-requests/{cargoId}/bids
/ai/recommend-truck
/ai/backhaul-opportunities
/ai/optimize-route
/driver/location
/driver/documents
```

### PATCH Requests (Update)
```
/me/password
/cargo-requests/{id}
/driver/current-city
/vehicles/{id}/location
/trips/{id}/status
/trips/{id}/location
/trips/{id}/stops/{stop}/arrive
/trips/{id}/stops/{stop}/load
/trips/{id}/stops/{stop}/complete
/bids/{id}/accept
/bids/{id}/reject
/bids/{id}/counter
/bids/{id}/accept-counter
/bids/{id}
/recommendations/{id}/dismiss
/notifications/{id}/read
/notifications/read-all
```

### DELETE Requests
```
/cargo-requests/{id}
/trips/{id}/stops/{stop}
```

---

## OpenStreetMap Integration

### Rendering Locations

**TrackingScreen** - Shipper tracking driver in real-time
- Current position marker
- Traveled route (green polyline)
- Planned route overlay (grey)
- Reverse geocoding label ("Currently near...")
- ETA from OSRM
- Truck popup with driver info

**NavigationScreen** - Driver turn-by-turn navigation
- OSRM route polyline (green)
- Origin and destination markers
- Turn-by-turn steps (toggleable list)
- Route summary (distance, ETA)
- Recenter button

**CreateFreightScreen** - Shipper picking locations
- Map with pickup/delivery pins
- Interactive location selection
- Place search suggestions

**NearbyTrucksScreen** - Shipper finding nearby trucks
- Truck markers on map
- Search radius circle
- Clickable for truck details

### Tile Source
```
https://tile.openstreetmap.org/{z}/{x}/{y}.png
```

### Why OpenStreetMap
- ✅ Free (no API key)
- ✅ Open source
- ✅ Good Ethiopia coverage
- ✅ No rate limiting
- ✅ Works offline with caching

---

## GPS Tracking - Complete Flow

### Initialization
```dart
// Driver logs in
LocationService.startTracking(ref);

// Or trip starts (and calibrates interval)
LocationService.startTracking(ref, serviceType: 'intracity');
```

### Tracking Intervals
```
Intracity: 5 minutes    (e.g., Addis Ababa delivery)
Intercity: 25 minutes   (e.g., Addis → Dire Dawa)
```

### How It Works
```
1. Timer fires every N minutes
2. Geolocator.getCurrentPosition() called
3. POST /driver/location {lat, lng} sent
4. Laravel stores to drivers table
5. Shipper polls GET /trips/{id}/location to see position
```

### Shipper-Side Polling
```
1. Open tracking screen
2. Get trip_id from booking
3. SET timer: GET /trips/{id}/location every 5/30 min
4. Map shows new driver position
5. Reverse geocode for "currently near" address
6. OSRM route overlay (one-time fetch)
7. Display ETA
```

### GPS Permission Flow
```
1. Check if location enabled
2. Check permission status
3. If denied: request permission
4. If granted: get position (medium accuracy, 15 sec timeout)
5. If denied forever or timeout: return null (silent failure)
6. Retry on next timer tick
```

### Status Indicators
```
INTRACITY:
- < 5 min   → Green (live)
- 5-10 min  → Amber (stale)
- > 10 min  → Red (offline)

INTERCITY:
- < 30 min  → Green (live)
- 30-60 min → Amber (stale)
- > 60 min  → Red (offline)
```

### Battery Warning
```dart
// Shown once per session when intracity tracking starts
if (LocationService.consumeBatteryWarning()) {
  showSnackBar('Updates every 5 min. Uses more battery.');
}
```

---

## Booking Creation - Complete Workflow

### Path 1: Bidding System (Most Common)

**Step 1**: Shipper creates cargo
```dart
POST /cargo/create {
  pickup_location: 'Addis Ababa',
  destination: 'Dire Dawa',
  material_type: 'construction',
  weight: 5.0,
  urgency_level: 'normal',
}
// Status: pending
// cargoId returned
```

**Step 2**: AI price estimation (optional)
```dart
POST /ai/predict-price {
  pickup_location, destination, weight, ...
}
// Returns: {min: 1800, max: 2200, distance_km: 460}
```

**Step 3**: Drivers place bids
```dart
POST /cargo-requests/{cargoId}/bids {
  vehicle_id: 123,
  amount: 1900,
  note: 'I can deliver within 2 days'
}
// Status: pending
// bidId returned
```

**Step 4**: Shipper accepts bid
```dart
PATCH /bids/{bidId}/accept
// Backend automatically creates Booking
// Returns: Booking with status: pending
```

**Step 5**: Trip starts
```dart
POST /trips {
  booking_id: 456
}
// Trip created, status: ongoing
// tripId returned
```

### Path 2: Direct Booking (Fixed Price)

```dart
// Driver directly books fixed-price cargo
POST /cargo-requests/{cargoId}/book-direct
// Booking created immediately
```

### Path 3: Manual Booking (Admin)

```dart
POST /booking/create {
  cargo_id: 123,
  vehicle_id: 456,
  driver_id: 789,
  estimated_price: 1900,
}
// Booking created manually
```

---

## AI Endpoints - Detailed

### 1. Truck Recommendation
```
POST /ai/recommend-truck

Input: {
  pickup_location: string,
  destination: string,
  weight: float,
  material_type: string,
  urgency_level: string,
}

Output: {
  recommended_trucks: [
    {
      truck_id: 123,
      truck_type: '10-ton',
      driver_id: 45,
      distance_km: 12,
      match_score: 0.95,
      reason: 'Perfect capacity and proximity'
    },
    ...
  ]
}

Used in:
  - AI Tools screen (Recommend tab)
  - Driver dashboard suggestions
  - Shipper freight creation
```

### 2. Price Prediction
```
POST /ai/predict-price

Input: {
  pickup_location: string,
  destination: string,
  weight: float,
  material_type: string,
  urgency_level: string,
  from_lat/lng: optional float,
  to_lat/lng: optional float,
}

Output: {
  price_min: int,
  price_max: int,
  distance_km: int,
  estimated_duration_hours: float,
  confidence: float,
}

Used in:
  - Cargo creation (budget estimate)
  - AI Tools screen (Price tab)
  - Shipper budget planning
```

### 3. Backhaul Opportunities
```
POST /ai/backhaul-opportunities

Input: {
  current_location: string,
  destination: string,
  available_capacity: float,
}

Output: {
  opportunities: [
    {
      cargo_id: 456,
      pickup: string,
      destination: string,
      weight: float,
      price_offered: float,
      urgency: string,
      match_score: 0.88,
    },
    ...
  ]
}

Used in:
  - Driver dashboard (Backhaul Opportunities section)
  - Suggest return cargo after delivery
  - Optimize truck utilization
```

### 4. Empty Return Risk
```
POST /ai/predict-empty-return

Input: {
  origin: string,
  destination: string,
  truck_type: string,
}

Output: {
  empty_return_risk: float (0.65 = 65%),
  recommendation: string,
  estimated_cost_impact: int,
  similar_routes: [
    {route: string, success_rate: float},
    ...
  ]
}

Used in:
  - AI Tools screen (Empty Return tab)
  - Driver decision-making for routes
  - Risk assessment
```

### 5. Route Optimization
```
POST /ai/optimize-route

Input: {
  origin: string,
  destination: string,
  waypoints: [string, ...],
}

Output: {
  optimized_route: [string, ...],
  distance_km: float,
  estimated_time_hours: float,
  improvement_percent: float,
}

Used in:
  - Multi-stop trip planning
  - Fleet dispatch optimization
  - Driver navigation suggestions
```

---

## File Organization

```
lib/src/
├── config/
│   ├── routes/
│   │   └── app_router.dart              ← 369 lines: Navigation, guards
│   └── theme/
│       └── app_theme.dart
├── data/
│   ├── api/
│   │   └── api_client.dart              ← 204 lines: Dio, interceptor, token mgmt
│   ├── models/
│   │   └── models.dart                  ← Data classes
│   ├── providers/
│   │   └── data_providers.dart          ← 256 lines: 25+ Riverpod providers
│   └── repositories/
│       └── repositories.dart            ← 1108 lines: 12 repos, 50+ endpoints
├── features/
│   ├── auth/                            ← Login/register
│   ├── shipper/                         ← Cargo, tracking, bids
│   ├── driver/                          ← Dashboard, navigation, trips
│   ├── fleet/                           ← Fleet management
│   ├── ai/                              ← AI tools UI
│   ├── bookings/                        ← Booking history
│   ├── profile/                         ← User profile
│   └── shared/                          ← Reusable widgets
├── services/
│   ├── location_service.dart            ← 131 lines: GPS tracking
│   └── routing_service.dart             ← 64 lines: OSRM/Nominatim
└── main.dart                            ← 49 lines: App entry
```

---

## Key Dependencies

```yaml
# State Management
flutter_riverpod: ^2.4.0
riverpod: ^2.4.0

# Navigation
go_router: ^13.2.0

# HTTP
dio: ^5.3.0

# Security
flutter_secure_storage: ^9.0.0

# Location
geolocator: ^10.1.0

# Maps
flutter_map: ^7.0.2
latlong2: ^0.9.1

# Localization
easy_localization: ^3.0.7

# UI
google_fonts: ^6.1.0
cached_network_image: ^3.3.0

# Logging
logger: ^2.0.0

# Other
image_picker: ^1.0.7
intl: ^0.20.2
```

---

## Quick Stats

- **Total Endpoints**: 50+
- **Repositories**: 12
- **Riverpod Providers**: 25+
- **HTTP Methods**: GET, POST, PATCH, DELETE
- **AI Endpoints**: 5
- **Screens**: 20+
- **Supported Languages**: 4 (English, Amharic, Oromo, Tigrinya)
- **Supported Roles**: 3 (Shipper, Driver, Fleet Owner)
- **Max API Timeout**: 30 seconds
- **GPS Update Intervals**: 5/25 minutes
- **Max Tracking Poll Interval**: 5-30 minutes

---

See `ARCHITECTURE_GUIDE.md` for more detailed code examples and explanations.

