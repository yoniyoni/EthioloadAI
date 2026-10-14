# EthioLoad AI - Flutter Mobile App Architecture Guide

## Overview
The EthioLoad AI Flutter mobile app is a comprehensive logistics platform that connects shippers, drivers, and fleet owners. It communicates with a Laravel backend, uses Riverpod for state management, Dio for HTTP requests, and FlutterMap for location rendering.

---

## 1. Flutter-Laravel Communication Flow

### Base URL Configuration
- **File**: `lib/src/data/api/api_client.dart` (lines 7-16)
- **Base URL**: `http://backend.test/api` (configurable via `--dart-define=API_BASE_URL`)
- Supports environment-specific overrides at build time

### HTTP Client Architecture
```
Request Flow:
User Action → Repository Method → ApiClient.get/post/patch/delete
  → Dio Instance → ApiInterceptor → Laravel Backend
```

**Key Points**:
- All requests go through **Dio** (HTTP client)
- Every request is intercepted to automatically attach **Bearer token**
- All responses are unwrapped from Laravel's `{ "data": ... }` envelope
- Error handling extracts first validation error from 422 responses
- Timeouts: 30 seconds for connect/send/receive

---

## 2. All Repositories (API Integration Points)

### **AuthRepository** - Authentication & Profile
**Location**: `lib/src/data/repositories/repositories.dart` (lines 8-72)
**Endpoints**:
- `POST /register` - Register new user
- `POST /login` - Login with email/phone & password
- `POST /logout` - Logout (clears token)
- `GET /me` - Get current user profile
- `PATCH /me/password` - Change password

**Storage**:
```dart
final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepository(ref.read(apiClientProvider)),
);
```

---

### **CargoRepository** - Freight/Cargo Management
**Location**: `lib/src/data/repositories/repositories.dart` (lines 80-333)
**Endpoints**:
- `GET /cargo-requests` - List all cargo requests
- `GET /cargo-requests/{id}` - Get single cargo details
- `POST /cargo/create` - Create new cargo request
- `PUT/PATCH /cargo-requests/{id}` - Update cargo status
- `POST /cargo-requests/{cargoId}/book-direct` - Direct booking
- `POST /cargo-requests/{cargoId}/accept-price` - Driver registers interest
- `DELETE /cargo-requests/{id}` - Delete cargo
- `POST /ai/predict-price` - **AI endpoint** for price estimation
- `POST /geocode/nearest-city` - Reverse geocode GPS to nearest city
- `GET /cargo-requests` (with metadata) - Get location_unset flag
- `GET /driver/return-cargo` - Get available return cargo
- `GET /cargo-requests/{id}/nearby-drivers` - Get nearby drivers for cargo

**Special Features**:
- Supports both intercity and intracity cargo
- AI price prediction with distance calculation
- GPS-based city detection
- Return cargo optimization

---

### **VehicleRepository** - Vehicle/Truck Management
**Location**: `lib/src/data/repositories/repositories.dart` (lines 341-414)
**Endpoints**:
- `GET /vehicles` - List all vehicles
- `POST /vehicle/register` - Register new vehicle
- `PATCH /driver/current-city` - Update driver's current city
- `PATCH /vehicles/{vehicle}/location` - Update vehicle GPS location
- `GET /vehicle/nearby` - Get nearby vehicles

---

### **BookingRepository** - Booking Management
**Location**: `lib/src/data/repositories/repositories.dart` (lines 418-465)
**Endpoints**:
- `GET /bookings` - List all bookings
- `GET /bookings/{id}` - Get booking details
- `POST /booking/create` - Create booking (includes estimated price)

---

### **TripRepository** - Trip Execution & Tracking
**Location**: `lib/src/data/repositories/repositories.dart` (lines 469-634)
**Endpoints**:
- `POST /trips` - Start a new trip
- `GET /trips/{id}` - Get trip details
- `PATCH /trips/{id}/status` - Update trip status (ongoing/completed)
- `PATCH /trips/{id}/location` - Update trip GPS location
- `GET /trips/{trip}/stops` - Get all stops for a trip
- `POST /trips/{trip}/stops` - Add new stop
- `PATCH /trips/{trip}/stops/{stop}/arrive` - Mark arrived at stop
- `PATCH /trips/{trip}/stops/{stop}/load` - Mark cargo loaded
- `PATCH /trips/{trip}/stops/{stop}/complete` - Mark stop completed
- `DELETE /trips/{trip}/stops/{stop}` - Remove stop
- `GET /trips/{tripId}/backhaul-recommendations` - Get backhaul opportunities
- `PATCH /recommendations/{id}/dismiss` - Dismiss recommendation
- `GET /trips/{trip}/location` - Get live trip location with route history

---

### **PaymentRepository** - Payment Processing
**Location**: `lib/src/data/repositories/repositories.dart` (lines 642-670)
**Endpoints**:
- `POST /payments` - Create payment
- `GET /payments/{booking_id}` - Get booking payment

**Supported Methods**: telebirr, cbe_birr, chapa, cash

---

### **AiRepository** - AI Engine Integration
**Location**: `lib/src/data/repositories/repositories.dart` (lines 674-770)
**AI Endpoints**:
- `POST /ai/recommend-truck` - **Recommend best trucks** for cargo
- `POST /ai/backhaul-opportunities` - **Find backhaul cargo** opportunities
- `POST /ai/predict-price` - **Predict cargo price** based on parameters
- `POST /ai/predict-empty-return` - **Predict empty truck return risk**
- `POST /ai/optimize-route` - **Optimize route** with waypoints

**AI Features**:
- Truck matching based on weight, urgency, material type
- Backhaul optimization for empty return trips
- Price estimation considering distance, urgency, cargo type
- Route optimization for multi-stop trips

---

### **DocumentRepository** - Driver Document Management
**Location**: `lib/src/data/repositories/repositories.dart` (lines 774-821)
**Endpoints**:
- `GET /driver/documents` - List driver documents
- `POST /driver/documents` - Upload document (multipart form-data)

**Supported Document Types**: license, insurance, inspection, registration, etc.

---

### **BidRepository** - Bidding System
**Location**: `lib/src/data/repositories/repositories.dart` (lines 825-954)
**Endpoints**:
- `GET /cargo-requests/{cargoId}/bids` - Get all bids for cargo
- `POST /cargo-requests/{cargoId}/bids` - **Place bid** on cargo
- `PATCH /bids/{bidId}/accept` - **Shipper accepts bid** → creates Booking
- `PATCH /bids/{bidId}/reject` - Reject bid
- `PATCH /bids/{bidId}/counter` - Send counter-offer
- `PATCH /bids/{bidId}/accept-counter` - Accept counter-offer → creates Booking
- `PATCH /bids/{bidId}` - Update own pending bid
- `GET /driver/bids` - Get driver's all bids

**Bidding States**: pending, countered, accepted, rejected

---

### **RatingRepository** - Post-Trip Rating
**Location**: `lib/src/data/repositories/repositories.dart` (lines 958-980)
**Endpoints**:
- `POST /ratings` - Submit rating and feedback for booking

---

### **NotificationRepository** - Push Notifications
**Location**: `lib/src/data/repositories/repositories.dart` (lines 984-1011)
**Endpoints**:
- `GET /notifications` - List notifications with unread count
- `PATCH /notifications/{id}/read` - Mark single notification as read
- `PATCH /notifications/read-all` - Mark all as read

---

### **RoutingRepository** - Maps & Routing (Proxy to OSRM/Nominatim)
**Location**: `lib/src/data/repositories/repositories.dart` (lines 1015-1107)
**Endpoints** (Laravel proxies these):
- `GET /routing/route` - Get OSRM route with fallback
- `GET /routing/search?q=` - Search place via Nominatim
- `GET /routing/reverse?lat=&lng=` - Reverse geocode via Nominatim
- `GET /nearby-trucks?lat=&lng=&radius_km=` - Find nearby trucks

**Why Proxy Through Laravel**:
- Security: API keys not exposed to client
- Rate limiting: Backend can throttle requests
- Caching: Common routes cached server-side
- Fallback handling: Seamless OSRM → haversine distance fallback

---

## 3. Complete Dio API Call Reference

### HTTP Methods Used

#### GET Requests (Read Operations)
```dart
// Simple GET
GET /cargo-requests
GET /me
GET /vehicles
GET /bookings
GET /trips/{id}
GET /driver/documents
GET /notifications
GET /routing/route?from_lat=...&from_lng=...&to_lat=...&to_lng=...
GET /routing/search?q=...
GET /routing/reverse?lat=...&lng=...
```

#### POST Requests (Create/Action Operations)
```dart
// Authentication
POST /register
POST /login

// Cargo Management
POST /cargo/create              // Create freight request
POST /ai/predict-price           // AI price estimation
POST /geocode/nearest-city       // Reverse geocode GPS

// Vehicle Management
POST /vehicle/register

// Trips
POST /trips                      // Start trip
POST /trips/{trip}/stops         // Add stop to trip

// Bookings
POST /booking/create

// Payments
POST /payments

// Bids
POST /cargo-requests/{cargoId}/bids  // Place bid

// AI Features
POST /ai/recommend-truck         // AI truck recommendation
POST /ai/backhaul-opportunities  // AI backhaul search
POST /ai/predict-empty-return    // AI empty return prediction
POST /ai/optimize-route          // AI route optimization

// Driver Tracking
POST /driver/location            // Send GPS location update

// Documents
POST /driver/documents           // Upload document (multipart)
```

#### PATCH Requests (Update Operations)
```dart
// Profile
PATCH /me/password

// Cargo
PATCH /cargo-requests/{id}

// Vehicles
PATCH /driver/current-city
PATCH /vehicles/{vehicle}/location

// Trips
PATCH /trips/{id}/status
PATCH /trips/{id}/location
PATCH /trips/{trip}/stops/{stop}/arrive
PATCH /trips/{trip}/stops/{stop}/load
PATCH /trips/{trip}/stops/{stop}/complete

// Bidding
PATCH /bids/{bidId}/accept
PATCH /bids/{bidId}/counter
PATCH /bids/{bidId}/accept-counter
PATCH /bids/{bidId}
PATCH /recommendations/{id}/dismiss

// Notifications
PATCH /notifications/{id}/read
PATCH /notifications/read-all
```

#### DELETE Requests
```dart
DELETE /cargo-requests/{id}
DELETE /trips/{trip}/stops/{stop}
```

---

## 4. Authentication Flow

### Step-by-Step Authentication Process

**Flow Diagram**:
```
User enters credentials
        ↓
POST /login (email/phone + password)
        ↓
Backend validates & returns { token, user }
        ↓
Flutter saves token to secure storage (FlutterSecureStorage)
        ↓
Update AuthNotifier state with user & token
        ↓
Router redirects to role-based dashboard
        ↓
Every subsequent request includes token in Authorization header
```

### Code Implementation

**File**: `lib/src/data/repositories/repositories.dart` (lines 34-45)
```dart
Future<AuthResponse> login({
  required String identifier,  // email or phone
  required String password,
}) async {
  final response = await _api.post<AuthResponse>(
    '/login',
    data: {'identifier': identifier, 'password': password},
    fromJson: (json) => AuthResponse.fromJson(json as Map<String, dynamic>),
  );
  await _api.saveToken(response.token);  // ← Save to secure storage
  return response;
}
```

**File**: `lib/src/data/providers/data_providers.dart` (lines 83-96)
```dart
Future<void> login({
  required String identifier,
  required String password,
}) async {
  state = state.copyWith(isLoading: true, clearError: true);
  try {
    final res = await _repo.login(identifier: identifier, password: password);
    state = state.copyWith(
      user: res.user,
      token: res.token,
      isLoading: false,
      clearError: true,
    );
  } catch (e) {
    state = state.copyWith(isLoading: false, error: e.toString());
  }
}
```

### Initial Auth Check (Cold Start)

**File**: `lib/src/data/providers/data_providers.dart` (lines 43-54)
```dart
Future<void> checkAuthStatus() async {
  final token = await _repo.getStoredToken();  // ← Retrieve from secure storage
  if (token == null) return;
  state = state.copyWith(token: token, isLoading: true);
  try {
    final user = await _repo.me();              // ← Validate token with backend
    state = state.copyWith(user: user, isLoading: false, clearError: true);
  } catch (_) {
    await _repo.logout();                       // ← Token expired, clear it
    state = const AuthState();
  }
}
```

### Token Attachment to Requests

**File**: `lib/src/data/api/api_client.dart` (lines 24-37)
```dart
@override
Future<void> onRequest(
  RequestOptions options,
  RequestInterceptorHandler handler,
) async {
  logger.i('→ ${options.method} ${options.path}');
  final token = await secureStorage.read(key: 'auth_token');
  if (token != null) {
    options.headers['Authorization'] = 'Bearer $token';  // ← Token added here
  }
  options.headers['Accept'] = 'application/json';
  options.headers['Content-Type'] = 'application/json';
  handler.next(options);
}
```

### Role-Based Verification Gate

**File**: `lib/src/config/routes/app_router.dart` (lines 93-101)
```dart
// Driver verification gate — fires on every navigation attempt.
// Keeps unverified drivers on the documents screen.
if (isLoggedIn &&
    role == 'driver' &&
    !notifier.isVerified &&
    path != '/driver-documents') {
  return '/driver-documents';  // ← Redirect unverified drivers
}
```

---

## 5. Token Storage Strategy

### Secure Storage Implementation

**Technology**: `flutter_secure_storage` (platform-native encryption)
- **iOS**: Uses Keychain (encrypted by default)
- **Android**: Uses Keystore (AES-256 encryption)
- **Web**: Uses browser's secure context storage

**File**: `lib/src/data/api/api_client.dart` (lines 92-99)

**Token Operations**:
```dart
// Save token after login/register
Future<void> saveToken(String token) =>
    secureStorage.write(key: 'auth_token', value: token);

// Retrieve token on app startup
Future<String?> getToken() => secureStorage.read(key: 'auth_token');

// Clear token on logout
Future<void> deleteToken() => secureStorage.delete(key: 'auth_token');
```

**Token Lifecycle**:
1. **Creation**: Returned from `POST /login` or `POST /register`
2. **Storage**: Saved to secure storage immediately after auth response
3. **Usage**: Automatically attached to every request via interceptor
4. **Validation**: Checked at app startup via `GET /me`
5. **Expiration**: If 401 received or `/me` fails, token is deleted
6. **Logout**: Token deleted on `POST /logout`

---

## 6. Navigation Architecture

### Go Router Configuration

**File**: `lib/src/config/routes/app_router.dart`

### Route Structure

```
Public Routes (no auth required):
  /splash          → SplashScreen
  /landing         → LandingScreen
  /login           → LoginScreen
  /register        → RegisterScreen

Shipper Routes:
  /shipper         → ShipperHomeScreen
  /freight         → FreightListScreen
  /freight/:id     → FreightDetailScreen (parametrized)
  /create-freight  → CreateFreightScreen
  /cargo-bids/:cargoId → ShipperBidSelectionScreen
  /tracking/:freightId → TrackingScreen (live tracking)

Driver Routes:
  /driver          → DriverDashboardScreen
  /driver/bids     → DriverBidsScreen
  /driver/active-trip/:tripId → ActiveTripScreen
  /navigate        → NavigationScreen (turn-by-turn)
  /nearby-trucks   → NearbyTrucksScreen

Fleet Owner Routes:
  /fleet           → FleetDashboardScreen
  /fleet/drivers   → FleetDriversScreen
  /fleet/vehicles  → FleetVehiclesScreen
  /fleet/dispatch  → FleetDispatchScreen

Shared Routes (role-aware):
  /my-bookings     → MyBookingsScreen (shows different content per role)
  /ai-tools        → AiToolsScreen
  /profile         → ProfileScreen
  /driver-documents → DriverDocumentsScreen (verification gate)
```

### Navigation Guards & Redirects

**Authentication Check**:
```dart
// Lines 73-82: Unauthenticated users cannot access protected routes
if (!isLoggedIn && !isPublic) return '/landing';

// Lines 85-91: Already authenticated on public page → redirect to role dashboard
if (isLoggedIn && isPublic) {
  if (role == 'driver') {
    return notifier.isVerified ? '/driver' : '/driver-documents';
  }
  if (role == 'fleet_owner') return '/fleet';
  return '/shipper';
}
```

**Driver Verification Gate**:
```dart
// Lines 93-101: Unverified drivers cannot navigate away from documents screen
if (isLoggedIn && role == 'driver' && !notifier.isVerified && 
    path != '/driver-documents') {
  return '/driver-documents';
}
```

### Router Listener Pattern

**File**: `lib/src/config/routes/app_router.dart` (lines 31-63)
```dart
class _RouterNotifier extends ChangeNotifier {
  _RouterNotifier(this._ref) {
    _ref.listen<AuthState>(authNotifierProvider, (previous, next) {
      final authChanged =
          previous?.isAuthenticated != next.isAuthenticated ||
          previous?.user?.role != next.user?.role ||
          previous?.user?.verificationStatus != next.user?.verificationStatus;
      if (authChanged) {
        notifyListeners();  // ← Triggers GoRouter to re-evaluate redirects
      }
    });
  }
}
```

### Parametrized Routes

```dart
// Cargo detail page with ID parameter
GoRoute(
  path: ':id',
  builder: (_, state) {
    final id = int.parse(state.pathParameters['id']!);
    return FreightDetailScreen(freightId: id);
  },
)

// Navigation with query parameters
GoRoute(
  path: '/navigate',
  builder: (_, state) {
    final q = state.uri.queryParameters;
    final destLat = double.tryParse(q['dest_lat'] ?? '');
    final destLng = double.tryParse(q['dest_lng'] ?? '');
    return NavigationScreen(destLat: destLat, destLng: destLng, ...);
  },
)
```

### Bottom Navigation

**File**: `lib/src/config/routes/app_router.dart` (lines 268-307)

Each role has role-specific navigation tabs:

**Driver Navigation Tabs**:
- Dashboard (`/driver`)
- Bookings (`/my-bookings`)
- Documents (`/driver-documents`)
- AI Tools (`/ai-tools`)
- Profile (`/profile`)

**Shipper Navigation Tabs**:
- Home (`/shipper`)
- Cargo (`/freight`)
- Bookings (`/my-bookings`)
- AI Tools (`/ai-tools`)
- Profile (`/profile`)

**Fleet Owner Navigation Tabs**:
- Dashboard (`/fleet`)
- Drivers (`/fleet/drivers`)
- Vehicles (`/fleet/vehicles`)
- Dispatch (`/fleet/dispatch`)
- Profile (`/profile`)

---

## 7. Riverpod Providers Deep Dive

### Provider Architecture

All providers are defined in: `lib/src/data/providers/data_providers.dart`

### State Providers

#### **Authentication Provider**
```dart
// Lines 105-108
final authNotifierProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(ref.read(authRepositoryProvider));
});
```

**AuthState Structure** (lines 7-37):
```dart
class AuthState {
  final User? user;
  final String? token;
  final bool isLoading;
  final String? error;
  
  bool get isAuthenticated => token != null && user != null;
}
```

---

### Future Providers (Auto-dispose)

**Auto-dispose** = Provider is disposed when no longer watched, preventing memory leaks

#### **Cargo Providers**
```dart
// Lines 114-116: List all cargo
final cargoListProvider = FutureProvider.autoDispose<List<CargoRequest>>((ref) async {
  return ref.read(cargoRepositoryProvider).list();
});

// Lines 119-122: Cargo with location_unset metadata flag
final cargoListWithMetaProvider = FutureProvider.autoDispose<
    ({bool locationUnset, List<CargoRequest> cargo})>((ref) async {
  return ref.read(cargoRepositoryProvider).listWithMeta();
});

// Lines 124-127: Single cargo by ID (parametrized with .family)
final singleCargoProvider =
    FutureProvider.autoDispose.family<CargoRequest, int>((ref, id) async {
  return ref.read(cargoRepositoryProvider).get(id);
});
```

#### **Vehicle Providers**
```dart
// Lines 131-133
final vehicleListProvider = FutureProvider.autoDispose<List<Vehicle>>((ref) async {
  return ref.read(vehicleRepositoryProvider).list();
});

// Lines 135-137
final nearbyVehiclesProvider = FutureProvider.autoDispose<List<Vehicle>>((ref) async {
  return ref.read(vehicleRepositoryProvider).nearby();
});
```

#### **Booking Providers**
```dart
// Lines 141-143
final bookingListProvider = FutureProvider.autoDispose<List<Booking>>((ref) async {
  return ref.read(bookingRepositoryProvider).list();
});

// Lines 145-148
final singleBookingProvider =
    FutureProvider.autoDispose.family<Booking, int>((ref, id) async {
  return ref.read(bookingRepositoryProvider).get(id);
});
```

#### **Trip Providers**
```dart
// Lines 152-154: Get single trip
final tripProvider = FutureProvider.autoDispose.family<Trip, int>((ref, tripId) async {
  return ref.read(tripRepositoryProvider).get(tripId);
});

// Lines 156-159: Get trip stops
final tripStopsProvider =
    FutureProvider.autoDispose.family<List<TripStop>, int>((ref, tripId) async {
  return ref.read(tripRepositoryProvider).getStops(tripId);
});
```

#### **Location Tracking Provider**
```dart
// Lines 202-205: Live GPS location for trip (polled by shipper tracking screen)
final tripLocationProvider =
    FutureProvider.autoDispose.family<TripLocation, int>((ref, tripId) async {
  return ref.read(tripRepositoryProvider).getLocation(tripId);
});
```

#### **Bid Providers**
```dart
// Lines 177-180: Get bids for specific cargo
final bidsForCargoProvider =
    FutureProvider.autoDispose.family<List<Bid>, int>((ref, cargoId) async {
  return ref.read(bidRepositoryProvider).listForCargo(cargoId);
});

// Lines 183-185: Driver's own bids (sorted: countered first)
final myBidsProvider = FutureProvider.autoDispose<List<Bid>>((ref) async {
  return ref.read(bidRepositoryProvider).listMyBids();
});
```

#### **Return Cargo Provider** (AI-powered)
```dart
// Lines 195-199: Cargo available at driver's current destination
final returnCargoProvider =
    FutureProvider.autoDispose<({String? city, List<CargoRequest> cargo})>(
        (ref) async {
  return ref.read(cargoRepositoryProvider).returnCargo();
});
```

---

### AI Providers

#### **Truck Recommendation Provider**
```dart
// Lines 216-242
class TruckRecommendationParams {
  final String pickup;
  final String destination;
  final double weight;
  final String materialType;
  final String urgencyLevel;
  // ...
}

final truckRecommendationProvider =
    FutureProvider.autoDispose.family<List<TruckRecommendation>, TruckRecommendationParams>(
        (ref, params) async {
  return ref.read(aiRepositoryProvider).recommendTruck(
        pickupLocation: params.pickup,
        destination: params.destination,
        weight: params.weight,
        materialType: params.materialType,
        urgencyLevel: params.urgencyLevel,
      );
});
```

**Usage**:
```dart
// In a Widget
final recommendations = ref.watch(
  truckRecommendationProvider(
    TruckRecommendationParams(
      pickup: 'Addis Ababa',
      destination: 'Dire Dawa',
      weight: 5.0,
      materialType: 'general',
      urgencyLevel: 'normal',
    ),
  ),
);
```

#### **Empty Return Risk Provider**
```dart
// Lines 244-248: Predict empty return risk for destination
final emptyReturnRiskProvider =
    FutureProvider.autoDispose.family<Map<String, dynamic>, String>(
        (ref, destination) async {
  return ref.read(aiRepositoryProvider).predictEmptyReturn(destination);
});
```

---

### Repository Providers

All repositories are exposed as providers for dependency injection:

```dart
final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepository(ref.read(apiClientProvider)),
);

final cargoRepositoryProvider = Provider<CargoRepository>(
  (ref) => CargoRepository(ref.read(apiClientProvider)),
);

final vehicleRepositoryProvider = Provider<VehicleRepository>(
  (ref) => VehicleRepository(ref.read(apiClientProvider)),
);

// ... (all 12 repositories)
```

---

### Notifications Provider

```dart
// Lines 252-255
final notificationsProvider = FutureProvider.autoDispose<
    ({List<AppNotification> items, int unreadCount})>((ref) async {
  return ref.read(notificationRepositoryProvider).list();
});
```

---

## 8. OpenStreetMap Rendering

### Where Maps Are Rendered

#### **Shipper Tracking Screen**
**File**: `lib/src/features/shipper/tracking_screen.dart`

**Map Features**:
- Shows driver's **current live position** (updated every 5-30 min)
- Displays **traveled route** (green polyline from route_data)
- Shows **planned route** overlay (grey polyline from OSRM)
- Includes **truck popup** with driver info
- **Reverse geocoding** shows "currently near" address
- **ETA calculation** from OSRM route duration

**Key Code** (lines 185-220):
```dart
FlutterMap(
  mapController: _mapController,
  options: MapOptions(
    initialCenter: _defaultCenter,
    initialZoom: 12.0,
  ),
  children: [
    // OpenStreetMap tile layer
    TileLayer(
      urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      userAgentPackageName: 'com.ethioloadai.app',
    ),
    
    // Traveled route (green polyline)
    if (traveledPoints.isNotEmpty)
      PolylineLayer(
        polylines: [
          Polyline(
            points: traveledPoints,
            strokeWidth: 4.0,
            color: _green.withValues(alpha: 0.7),
          ),
        ],
      ),
    
    // Planned route overlay (grey polyline)
    if (plannedPoints.isNotEmpty)
      PolylineLayer(
        polylines: [
          Polyline(
            points: plannedPoints,
            strokeWidth: 3.5,
            color: Colors.grey.withValues(alpha: 0.5),
          ),
        ],
      ),
    
    // Markers (truck, origin, destination)
    MarkerLayer(
      markers: [
        Marker(
          point: currentPosition,
          child: TruckMarker(),
        ),
      ],
    ),
  ],
)
```

---

#### **Driver Navigation Screen**
**File**: `lib/src/features/driver/navigation_screen.dart`

**Full-Screen Navigation with**:
- **OSRM route polyline** (turn-by-turn directions)
- **Turn-by-turn steps** displayed as list or map
- **Origin and destination pins**
- **Route summary card** showing distance and ETA
- **Recenter button** to follow driver

**Key Code** (lines 142-189):
```dart
FlutterMap(
  mapController: _mapCtrl,
  options: MapOptions(
    initialCenter: _defaultCenter,
    initialZoom: 9.5,
  ),
  children: [
    TileLayer(
      urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      userAgentPackageName: 'com.ethioloadai.app',
    ),
    
    // Route polyline
    if (_routePoints.length >= 2)
      PolylineLayer(
        polylines: [
          Polyline(
            points: _routePoints,
            strokeWidth: 4.5,
            color: _green.withValues(alpha: 0.85),
          ),
        ],
      ),
    
    // Destination and origin markers
    MarkerLayer(
      markers: [
        Marker(
          point: destination,
          child: _DestPin(),
        ),
        if (originProvided)
          Marker(
            point: origin,
            child: _OriginPin(),
          ),
      ],
    ),
  ],
)
```

---

#### **Create Freight Screen**
**File**: `lib/src/features/shipper/create_freight_screen.dart`

**Map Features**:
- Shows **pickup and delivery locations** as pins
- Allows **interactive map selection**
- Used during freight creation for visual confirmation

---

#### **Nearby Trucks Screen**
**File**: `lib/src/features/shipper/nearby_trucks_screen.dart`

**Map Features**:
- Shows **all nearby trucks** as markers
- Centers on the **pickup location**
- Displays **search radius circle**
- Clickable truck markers for details

---

### OpenStreetMap Integration Details

**Tile Source**:
```dart
TileLayer(
  urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
  userAgentPackageName: 'com.ethioloadai.app',
)
```

**Why OpenStreetMap**:
- ✅ Free (no API key needed)
- ✅ Open source
- ✅ No rate limiting for reasonable use
- ✅ Covers Ethiopia well
- ✅ No dependency on Google Maps

**FlutterMap Library** (`flutter_map: ^7.0.2`):
- Lightweight map rendering
- Polyline overlay support
- Interactive markers
- Zoom/pan controls
- Built on top of Leaflet.js

---

## 9. GPS Updates & Location Service

### Location Service Architecture

**File**: `lib/src/services/location_service.dart`

### GPS Tracking Initialization

**When Started**:
- Driver logs in → dashboard initializes
- Trip starts → calibrates interval based on service type

**File**: `lib/src/features/driver/driver_dashboard_screen.dart` (lines 32-38)
```dart
@override
void initState() {
  super.initState();
  WidgetsBinding.instance.addPostFrameCallback((_) async {
    LocationService.startTracking(ref);              // ← Start 25-min updates
    await _calibrateTrackingInterval();              // ← Check if intracity
  });
}
```

---

### Tracking Intervals

```dart
// Lines 23-29
static Future<void> startTracking(
  WidgetRef ref, {
  String serviceType = 'intercity',
}) async {
  final interval = serviceType == 'intracity'
      ? const Duration(minutes: 5)              // ← City deliveries: 5 min
      : const Duration(minutes: 25);            // ← Long haul: 25 min

  // Get initial position
  final position = await _requestAndGet();
  if (position != null) {
    await _push(ref, position);                 // ← Send immediately
  }

  // Set periodic timer
  _timer?.cancel();
  _timer = Timer.periodic(interval, (_) async {
    final pos = await getCurrentPosition();
    if (pos != null) {
      await _push(ref, pos);
    }
  });
}
```

---

### GPS Permission Handling

**File**: `lib/src/services/location_service.dart` (lines 78-99)

```dart
static Future<Position?> _requestAndGet() async {
  try {
    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) return null;

    LocationPermission perm = await Geolocator.checkPermission();
    if (perm == LocationPermission.denied) {
      perm = await Geolocator.requestPermission();  // ← Request if denied
    }
    if (perm == LocationPermission.denied ||
        perm == LocationPermission.deniedForever) {
      return null;                               // ← Return null if refused
    }

    return await Geolocator.getCurrentPosition(
      desiredAccuracy: LocationAccuracy.medium,
      timeLimit: const Duration(seconds: 15),    // ← 15 sec timeout
    );
  } catch (_) {
    return null;                                  // ← Silent failure
  }
}
```

---

### GPS Location Updates - The `_push` Method

**File**: `lib/src/services/location_service.dart` (lines 67-76)

```dart
static Future<void> _push(WidgetRef ref, Position pos) async {
  try {
    await ref.read(apiClientProvider).post<void>(
      '/driver/location',                        // ← Laravel endpoint
      data: {'lat': pos.latitude, 'lng': pos.longitude},
    );
  } catch (_) {
    // Silent failure — will retry on next tick
  }
}
```

**What Happens**:
1. GPS position captured
2. Latitude & longitude extracted
3. POST request sent to `/driver/location`
4. Laravel stores update to trips table
5. Shipper can poll `/trips/{trip}/location` to see position
6. Interval repeats automatically

---

### Battery Warning

**File**: `lib/src/services/location_service.dart` (lines 14-18)

```dart
static bool consumeBatteryWarning() {
  if (_batteryWarnShown) return false;
  _batteryWarnShown = true;
  return true;                                   // ← True only on first call
}
```

**Usage** (from driver_dashboard_screen.dart, lines 54-65):
```dart
if (LocationService.consumeBatteryWarning() && mounted) {
  ScaffoldMessenger.of(context).showSnackBar(
    const SnackBar(
      content: Text(
        'Location updates every 5 min for this city job. '
        'This uses more battery.',
      ),
      duration: Duration(seconds: 6),
    ),
  );
}
```

---

### Geolocator Package Configuration

**pubspec.yaml** (line 28):
```yaml
geolocator: ^10.1.0
```

**Accuracy**: `LocationAccuracy.medium` (typically 10-30 meters)

**Platform Permissions Required**:
- Android: `android.permission.ACCESS_FINE_LOCATION`
- iOS: `NSLocationWhenInUseUsageDescription`

---

## 10. Tracking Updates - How They're Sent

### Complete Tracking Flow

#### **Driver Side - Automatic Updates**

```
1. Driver logs in
   ↓
2. LocationService.startTracking(ref) called
   ↓
3. Timer set (5 min for intracity, 25 min for intercity)
   ↓
4. Every interval:
   a. GPS location captured via Geolocator
   b. POST /driver/location { lat, lng }
   c. Laravel stores to drivers.latitude/longitude
   d. Also updates trips.last_known_location_lat/lng
   e. Silent failure if no internet (retry on next interval)
```

**File**: `lib/src/services/location_service.dart` (lines 23-43)

---

#### **Shipper Side - Polling Live Position**

```
1. Shipper opens tracking screen
2. Lookup trip_id from booking
3. Start polling GET /trips/{trip}/location
4. Polling interval: 5 min (intracity) / 30 min (intercity)
5. Each poll returns:
   - currentLat, currentLng (driver's position)
   - destinationLat, destinationLng
   - routeData (breadcrumb trail)
   - minutesSinceUpdate
   - eta
6. Map updates with new position
7. Reverse geocode to get "currently near" address
8. Fetch OSRM route overlay if first time
```

**File**: `lib/src/features/shipper/tracking_screen.dart` (lines 59-81)

```dart
Future<void> _bootstrap() async {
  // ... get trip_id from booking ...
  
  final serviceType = _booking?.serviceType ?? 'intercity';
  final interval = serviceType == 'intracity'
      ? const Duration(minutes: 5)
      : const Duration(minutes: 30);
  
  _pollTimer = Timer.periodic(interval, (_) => _refresh());
}

Future<void> _refresh() async {
  if (_tripId == null) return;
  try {
    final loc = await ref.read(tripRepositoryProvider).getLocation(_tripId!);
    setState(() {
      _location = loc;
    });
    
    // Update map with new position
    _mapController.move(
      LatLng(loc.currentLat!, loc.currentLng!),
      12.0,
    );
    
    // Fetch reverse geocoding for "currently near"
    _fetchReverseGeocode(loc.currentLat!, loc.currentLng!);
    
    // Fetch route overlay on first load
    if (_plannedRoute == null && loc.hasPosition && loc.hasDestination) {
      _fetchPlannedRoute(loc);
    }
  } catch (_) {}
}
```

---

#### **Data Model - TripLocation**

**Returned by**: `GET /trips/{tripId}/location`

**Fields**:
```dart
class TripLocation {
  final int tripId;
  final double? currentLat;
  final double? currentLng;
  final double? destinationLat;
  final double? destinationLng;
  final List<Map> routeData;           // Breadcrumbs: [{lat, lng, ...}, ...]
  final int? minutesSinceUpdate;       // How old is this data
  final double? eta;                   // ETA in hours
  
  bool get hasPosition => currentLat != null && currentLng != null;
  bool get hasDestination => destinationLat != null && destinationLng != null;
}
```

---

### Status Indicators Based on Freshness

**File**: `lib/src/features/shipper/tracking_screen.dart` (lines 152-173)

```dart
Color _statusColor() {
  final mins = _location?.minutesSinceUpdate;
  if (_isIntracity) {
    if (mins < 5)  return const Color(0xFF059669);   // ← Green (live)
    if (mins < 10) return const Color(0xFFF59E0B);   // ← Amber (stale)
    return Colors.red;                              // ← Red (offline)
  } else {
    if (mins < 30)  return const Color(0xFF059669);
    if (mins < 60)  return const Color(0xFFF59E0B);
    return Colors.red;
  }
}

String _lastUpdatedText() {
  final mins = _location?.minutesSinceUpdate;
  if (mins == null) return '—';
  if (mins < 60) return '$mins minutes ago';
  return '${(mins / 60).floor()} hours ago';
}
```

---

## 11. Booking Creation Workflow

### Step 1: Create Cargo Request

**Endpoint**: `POST /cargo/create`

**File**: `lib/src/features/shipper/create_freight_screen.dart` (lines 300+)

```dart
// User fills in freight details
String pickupLocation = 'Addis Ababa';
String destination = 'Dire Dawa';
double weight = 5.0;  // tons
String materialType = 'construction';
String urgency = 'normal';

// Shipper calls repository
final cargo = await ref.read(cargoRepositoryProvider).create(
  pickupLocation: pickupLocation,
  destination: destination,
  materialType: materialType,
  weight: weight,
  urgencyLevel: urgency,
  budget: 2000,
  priceType: 'negotiable',
);

// Cargo created with status: 'pending'
// cargoId = cargo.id
```

---

### Step 2A: AI Price Prediction (Optional)

**Endpoint**: `POST /ai/predict-price`

```dart
// Get AI price estimate before listing
final priceEst = await ref.read(cargoRepositoryProvider).predictPrice(
  pickup: 'Addis Ababa',
  destination: 'Dire Dawa',
  weight: 5.0,
  materialType: 'construction',
);

// Returns: {min: 1800, max: 2200, distanceKm: 460}
```

---

### Step 2B: Drivers Place Bids

**Endpoint**: `POST /cargo-requests/{cargoId}/bids`

**Drivers see**:
- Cargo details in dashboard
- Can place bid with their vehicle and price

```dart
// Driver submits bid
final bid = await ref.read(bidRepositoryProvider).place(
  cargoId: cargo.id,
  vehicleId: driverVehicle.id,
  amount: 1900,  // Driver's proposed price
  note: 'I can deliver within 2 days',
);

// Bid created with status: 'pending'
```

---

### Step 3: Shipper Reviews & Accepts Bid

**Endpoint**: `PATCH /bids/{bidId}/accept`

**File**: `lib/src/features/shipper/shipper_bid_selection_screen.dart`

```dart
// Shipper sees all bids for their cargo
final bids = await ref.read(bidRepositoryProvider).listForCargo(cargo.id);

// Shipper clicks "Accept" on a bid
final booking = await ref.read(bidRepositoryProvider).acceptBid(bid.id);

// Magic: Backend automatically creates Booking
// Booking status: 'pending'
// Other bids are auto-rejected
```

---

### Step 4: Create Booking (Alternative - Direct Booking)

For **fixed-price cargo**, driver can directly book:

**Endpoint**: `POST /booking/create`

```dart
// Manual booking creation (rare path)
final booking = await ref.read(bookingRepositoryProvider).create(
  cargoId: cargo.id,
  vehicleId: vehicle.id,
  driverId: driver.id,
  estimatedPrice: 1900,
);

// Booking created with status: 'pending'
```

---

### Step 5: Booking Confirmation & Trip Start

**Endpoint**: `POST /trips`

```dart
// Driver or shipper clicks "Start Trip"
final trip = await ref.read(tripRepositoryProvider).start(booking.id);

// Trip created
// tripId = trip.id
// trip.status = 'ongoing'
```

---

### Complete Booking Lifecycle Data Model

```dart
class Booking {
  int id;
  int cargoId;
  int? vehicleId;
  int? driverId;
  User? driver;
  String bookingStatus;        // pending → accepted → rejected
  String serviceType;          // intercity | intracity
  double estimatedPrice;
  int? tripId;                 // Populated after POST /trips
  bool get isTripOngoing => tripId != null;
  
  DateTime createdAt;
  DateTime? completedAt;
}

class Trip {
  int id;
  int bookingId;
  String tripStatus;           // ongoing → completed
  double? currentLat;
  double? currentLng;
  double? destinationLat;
  double? destinationLng;
  List<TripStop> stops;        // Multi-stop support
}
```

---

### Bidding vs Direct Booking

| Feature | Bidding | Direct |
|---------|---------|--------|
| Initiation | Shipper creates cargo | Driver accepts fixed cargo |
| Price | Negotiated via counter-offers | Fixed by shipper |
| Status | Bids pending until accepted | Booking created immediately |
| Use Case | Complex jobs | Simple fixed-price jobs |
| Endpoint | `/cargo-requests/{id}/bids` | `/cargo-requests/{id}/book-direct` |

---

## 12. AI Endpoints - Complete Reference

### AI Repository
**File**: `lib/src/data/repositories/repositories.dart` (lines 674-770)

All AI endpoints go through **AiRepository** → **apiClientProvider** → **Laravel** → **Python AI Engine**

---

### 1. **Truck Recommendation Engine**

**Endpoint**: `POST /ai/recommend-truck`

**Purpose**: Find best trucks for a cargo job

**Input**:
```dart
{
  'pickup_location': 'Addis Ababa',
  'destination': 'Dire Dawa',
  'weight': 5.0,
  'material_type': 'construction',
  'urgency_level': 'normal',
}
```

**Output**:
```dart
{
  'recommended_trucks': [
    {
      'truck_id': 123,
      'truck_type': '10-ton',
      'driver_id': 45,
      'distance_km': 12,
      'match_score': 0.95,
      'reason': 'Perfect capacity and proximity'
    },
    // ... more trucks ...
  ]
}
```

**Used In**:
- `lib/src/features/ai/ai_tools_screen.dart` - Recommend tab
- Driver dashboard suggestions

**Code Example**:
```dart
final trucks = await ref.read(aiRepositoryProvider).recommendTruck(
  pickupLocation: 'Addis Ababa',
  destination: 'Dire Dawa',
  weight: 5.0,
  materialType: 'construction',
  urgencyLevel: 'normal',
);

// trucks is List<TruckRecommendation>
for (final truck in trucks) {
  print('${truck.truckType}: ${truck.matchScore}% match');
}
```

---

### 2. **Price Prediction**

**Endpoint**: `POST /ai/predict-price`

**Purpose**: Predict cargo pricing based on logistics factors

**Input**:
```dart
{
  'pickup_location': 'Addis Ababa',
  'destination': 'Dire Dawa',
  'weight': 5.0,
  'material_type': 'construction',
  'from': 'Addis Ababa',
  'to': 'Dire Dawa',
  'from_lat': 9.0320,
  'from_lng': 38.7469,
  'to_lat': 9.5500,
  'to_lng': 40.5000,
}
```

**Output**:
```dart
{
  'price_min': 1800,
  'price_max': 2200,
  'distance_km': 460,
  'estimated_duration_hours': 7.5,
  'confidence': 0.92,
}
```

**Used In**:
- `lib/src/features/shipper/create_freight_screen.dart` - Price estimate display
- `lib/src/features/ai/ai_tools_screen.dart` - Price tab

**Code Example**:
```dart
final priceData = await ref.read(cargoRepositoryProvider).predictPrice(
  pickup: 'Addis Ababa',
  destination: 'Dire Dawa',
  weight: 5.0,
  urgencyLevel: 'normal',
  materialType: 'construction',
);

// priceData is ({int? min, int? max, int? distanceKm})
print('Estimated price: ${priceData.min} - ${priceData.max} ETB');
print('Distance: ${priceData.distanceKm} km');
```

---

### 3. **Backhaul Opportunities**

**Endpoint**: `POST /ai/backhaul-opportunities`

**Purpose**: Find return cargo to avoid empty backhaul

**Input**:
```dart
{
  'current_location': 'Dire Dawa',
  'destination': 'Addis Ababa',
  'available_capacity': 3.5,  // remaining tons
}
```

**Output**:
```dart
{
  'opportunities': [
    {
      'cargo_id': 456,
      'pickup': 'Dire Dawa',
      'destination': 'Addis Ababa',
      'weight': 3.0,
      'price_offered': 800,
      'urgency': 'normal',
      'match_score': 0.88,
    },
    // ... more cargo ...
  ]
}
```

**Used In**:
- Driver dashboard - "Backhaul Opportunities" section
- After trip completion, suggests return cargo
- Shown to drivers with empty capacity

**Code Example**:
```dart
final opportunities = await ref.read(aiRepositoryProvider).backhaulOpportunities(
  currentLocation: 'Dire Dawa',
  destination: 'Addis Ababa',
  availableCapacity: 3.5,
);

for (final opp in opportunities) {
  print('${opp.weight}t cargo from ${opp.pickup} to ${opp.destination}');
}
```

---

### 4. **Empty Return Risk Prediction**

**Endpoint**: `POST /ai/predict-empty-return`

**Purpose**: Predict likelihood of returning empty (high cost)

**Input**:
```dart
{
  'origin': 'Addis Ababa',  // Current driver location
  'destination': 'Dire Dawa',
  'truck_type': 'general',
}
```

**Output**:
```dart
{
  'empty_return_risk': 0.65,  // 65% chance of returning empty
  'recommendation': 'Search for backhaul cargo in Dire Dawa',
  'estimated_cost_impact': 1200,  // ETB cost if returning empty
  'similar_routes': [
    {
      'route': 'Addis → Dire Dawa → Adama → Addis',
      'success_rate': 0.72,
    },
  ],
}
```

**Used In**:
- `lib/src/features/ai/ai_tools_screen.dart` - Empty Return tab
- Driver decision-making for route planning

**Code Example**:
```dart
final risk = await ref.read(aiRepositoryProvider).predictEmptyReturn('Dire Dawa');

if (risk['empty_return_risk'] > 0.6) {
  print('High risk of empty return: ${risk['recommendation']}');
}
```

---

### 5. **Route Optimization**

**Endpoint**: `POST /ai/optimize-route`

**Purpose**: Find optimal multi-stop route (TSP problem)

**Input**:
```dart
{
  'origin': 'Addis Ababa',
  'destination': 'Dire Dawa',
  'waypoints': ['Adama', 'Asella', 'Awash'],
}
```

**Output**:
```dart
{
  'optimized_route': [
    'Addis Ababa',
    'Adama',
    'Awash',
    'Asella',
    'Dire Dawa',
  ],
  'distance_km': 520,
  'estimated_time_hours': 8.5,
  'improvement_percent': 12,  // vs original order
}
```

**Used In**:
- Complex multi-stop freight planning
- Fleet dispatch optimization

**Code Example**:
```dart
final optimized = await ref.read(aiRepositoryProvider).optimizeRoute(
  origin: 'Addis Ababa',
  destination: 'Dire Dawa',
  waypoints: ['Adama', 'Asella', 'Awash'],
);

print('Optimized stops: ${optimized['optimized_route'].join(' → ')}');
```

---

### AI Feature Integration in UI

**File**: `lib/src/features/ai/ai_tools_screen.dart`

Exposes all 5 AI endpoints in tabs:
1. **Recommend Tab** - Truck recommendations
2. **Price Tab** - Price predictions
3. **Empty Return Tab** - Empty return risk
4. Backhaul section in driver dashboard
5. Route optimization in trip planning

---

### AI Data Models

```dart
class TruckRecommendation {
  int truckId;
  String truckType;
  int driverId;
  double distanceKm;
  double matchScore;
  String reason;
}

class BackhaulRecommendation {
  int id;
  int cargoId;
  String pickup;
  String destination;
  double weight;
  double priceOffered;
  String urgency;
  double matchScore;
}

class BackhaulOpportunity {
  int cargoId;
  String pickup;
  String destination;
  double weight;
  double priceOffered;
  String urgency;
  double matchScore;
}
```

---

## Summary Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     Flutter Mobile App                          │
│  (AuthScreen, FreightListScreen, TrackingScreen, etc.)         │
└────────────────┬────────────────────────────────────────────────┘
                 │
         ┌───────v────────────────────┐
         │  Go Router Navigation      │
         │  (Role-based routing)      │
         └───────┬────────────────────┘
                 │
         ┌───────v────────────────────────────────────────────┐
         │     Riverpod State Management                     │
         │  (authNotifierProvider, cargoListProvider, etc.)  │
         └───────┬────────────────────────────────────────────┘
                 │
    ┌────────────v────────────────────┐
    │   Repositories Layer            │
    │ (AuthRepo, CargoRepo, TripsRepo│
    │  BidRepo, AiRepo, etc.)        │
    └────────────┬────────────────────┘
                 │
    ┌────────────v────────────────────────────────┐
    │      Dio HTTP Client + Interceptor          │
    │  (Adds Bearer token, unwraps envelopes)     │
    └────────────┬────────────────────────────────┘
                 │ HTTPS
    ┌────────────v─────────────────────────┐
    │   Laravel Backend + Python AI Engine │
    │  http://backend.test/api             │
    └──────────────────────────────────────┘
```

---

## Key Technologies

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **UI/Navigation** | Flutter, Go Router | Mobile interface & routing |
| **State Mgmt** | Riverpod | Reactive state & providers |
| **HTTP** | Dio | REST API client |
| **Storage** | FlutterSecureStorage | Token & credential storage |
| **Maps** | FlutterMap, OpenStreetMap | Location rendering |
| **GPS** | Geolocator | Location tracking |
| **Localization** | Easy Localization | Multi-language support |
| **Backend** | Laravel | REST API server |
| **AI** | Python | Recommendation engine |
| **Routing** | OSRM, Nominatim | Route & geocoding service |

---

## File Organization

```
lib/src/
├── config/
│   ├── routes/
│   │   └── app_router.dart              ← Navigation configuration
│   └── theme/
│       └── app_theme.dart
├── data/
│   ├── api/
│   │   └── api_client.dart              ← Dio setup, interceptor, token mgmt
│   ├── models/
│   │   └── models.dart                  ← All data classes
│   ├── providers/
│   │   └── data_providers.dart          ← Riverpod providers
│   └── repositories/
│       └── repositories.dart            ← 12 repositories, 30+ API endpoints
├── features/
│   ├── auth/                            ← Login/register screens
│   ├── shipper/                         ← Freight, tracking, bid selection
│   ├── driver/                          ← Dashboard, navigation, active trip
│   ├── fleet/                           ← Fleet management screens
│   ├── ai/                              ← AI tools screen
│   ├── bookings/                        ← Booking history
│   ├── profile/                         ← User profile
│   └── shared/                          ← Reusable widgets
└── services/
    ├── location_service.dart            ← GPS tracking (5/25 min intervals)
    └── routing_service.dart             ← OSRM/Nominatim wrapper
```

---

This architecture ensures:
- ✅ **Clean separation of concerns** (repos, providers, UI)
- ✅ **Secure token management** (encrypted storage)
- ✅ **Efficient state management** (Riverpod auto-dispose)
- ✅ **Offline-first GPS tracking** (automatic retries)
- ✅ **Real-time live tracking** (polling with configurable intervals)
- ✅ **AI-powered recommendations** (5 ML endpoints)
- ✅ **Multi-language support** (Amharic, English, Oromo, Tigrinya)
- ✅ **Role-based access** (Shipper, Driver, Fleet Owner)

