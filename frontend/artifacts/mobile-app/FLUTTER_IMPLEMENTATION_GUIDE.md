# Flutter Mobile App - Complete Implementation Guide

## Project Status: ✅ Architecture Complete

This document provides the complete roadmap for implementing the Freight Platform Flutter mobile application.

---

## 1. Project Structure

```
lib/
├── main.dart                          # App entry point with Riverpod + localization
├── src/
│   ├── config/
│   │   ├── theme/
│   │   │   ├── app_theme.dart        # ✅ COMPLETE - Material 3 theme
│   │   │   └── colors.dart           # Color constants
│   │   └── routes/
│   │       ├── app_router.dart       # ✅ COMPLETE - Go Router configuration
│   │       └── route_guards.dart     # Authentication guards
│   │
│   ├── core/
│   │   ├── constants/
│   │   │   ├── api_endpoints.dart    # API endpoint constants
│   │   │   ├── strings.dart          # UI strings
│   │   │   └── assets.dart           # Asset paths
│   │   ├── extensions/
│   │   │   ├── context_extensions.dart
│   │   │   ├── string_extensions.dart
│   │   │   └── date_extensions.dart
│   │   └── utils/
│   │       ├── validators.dart       # Form validators
│   │       ├── formatters.dart       # Date/number formatters
│   │       ├── logger.dart           # Logging utility
│   │       └── permission_handler.dart
│   │
│   ├── data/
│   │   ├── api/
│   │   │   ├── api_client.dart       # ✅ COMPLETE - Dio setup + interceptors
│   │   │   ├── api_endpoints.dart    # API endpoint definitions
│   │   │   └── error_handler.dart    # Error handling
│   │   │
│   │   ├── models/
│   │   │   ├── models.dart           # ✅ COMPLETE - All data models
│   │   │   ├── request_models.dart   # Request/form models
│   │   │   └── response_models.dart  # Response models
│   │   │
│   │   ├── repositories/
│   │   │   ├── repositories.dart     # ✅ COMPLETE - All repos
│   │   │   ├── auth_repository.dart  # Auth business logic
│   │   │   ├── freight_repository.dart
│   │   │   ├── driver_repository.dart
│   │   │   ├── payment_repository.dart
│   │   │   ├── tracking_repository.dart
│   │   │   └── message_repository.dart
│   │   │
│   │   ├── providers/
│   │   │   ├── data_providers.dart   # ✅ COMPLETE - Riverpod providers
│   │   │   ├── auth_provider.dart    # Auth state
│   │   │   ├── freight_provider.dart # Freight state
│   │   │   └── driver_provider.dart  # Driver state
│   │   │
│   │   └── services/
│   │       ├── local_storage.dart    # Hive storage
│   │       ├── push_notification_service.dart
│   │       ├── location_service.dart # GPS tracking
│   │       ├── socket_service.dart   # WebSocket
│   │       └── payment_service.dart  # Payment integration
│   │
│   ├── features/
│   │   ├── auth/
│   │   │   ├── auth_screens.dart     # ✅ COMPLETE - Login/Register
│   │   │   ├── forgot_password_screen.dart
│   │   │   ├── otp_screen.dart
│   │   │   └── biometric_setup_screen.dart
│   │   │
│   │   ├── splash/
│   │   │   └── splash_screen.dart    # ✅ Implemented
│   │   │
│   │   ├── home/
│   │   │   ├── home_screen.dart      # Main dashboard
│   │   │   └── role_selector_screen.dart
│   │   │
│   │   ├── shipper/
│   │   │   ├── freight_list_screen.dart       # Browse loads
│   │   │   ├── create_freight_screen.dart     # Multi-step wizard
│   │   │   ├── freight_detail_screen.dart     # View freight details
│   │   │   ├── freight_applications_screen.dart
│   │   │   ├── ai_recommendations_screen.dart
│   │   │   ├── shipper_dashboard_screen.dart
│   │   │   └── shipper_profile_screen.dart
│   │   │
│   │   ├── driver/
│   │   │   ├── driver_dashboard_screen.dart
│   │   │   ├── available_loads_screen.dart
│   │   │   ├── load_detail_screen.dart
│   │   │   ├── counter_offer_screen.dart
│   │   │   ├── earnings_screen.dart
│   │   │   ├── ratings_screen.dart
│   │   │   ├── vehicle_info_screen.dart
│   │   │   └── return_cargo_screen.dart
│   │   │
│   │   ├── tracking/
│   │   │   ├── tracking_screen.dart           # Live GPS tracking
│   │   │   ├── tracking_map_widget.dart
│   │   │   ├── trip_timeline_widget.dart
│   │   │   └── tracking_history_screen.dart
│   │   │
│   │   ├── payments/
│   │   │   ├── payment_screen.dart            # Escrow payment
│   │   │   ├── chapa_payment_screen.dart
│   │   │   ├── cbe_birr_screen.dart
│   │   │   ├── payment_history_screen.dart
│   │   │   └── payment_confirmation_screen.dart
│   │   │
│   │   ├── agreements/
│   │   │   ├── agreement_screen.dart          # Digital contract
│   │   │   ├── contract_pdf_viewer_screen.dart
│   │   │   ├── e_signature_screen.dart
│   │   │   └── agreement_history_screen.dart
│   │   │
│   │   ├── chat/
│   │   │   ├── chat_list_screen.dart
│   │   │   ├── chat_screen.dart               # Real-time messaging
│   │   │   ├── chat_bubble_widget.dart
│   │   │   └── file_sharing_widget.dart
│   │   │
│   │   ├── ai_assistant/
│   │   │   ├── ai_chat_screen.dart            # AI Assistant
│   │   │   ├── price_prediction_screen.dart
│   │   │   ├── vehicle_recommendation_screen.dart
│   │   │   └── driver_recommendation_screen.dart
│   │   │
│   │   ├── admin/
│   │   │   ├── admin_dashboard_screen.dart
│   │   │   ├── user_management_screen.dart
│   │   │   ├── fraud_monitoring_screen.dart
│   │   │   └── payment_reports_screen.dart
│   │   │
│   │   ├── profile/
│   │   │   ├── profile_screen.dart
│   │   │   ├── edit_profile_screen.dart
│   │   │   ├── settings_screen.dart
│   │   │   └── about_screen.dart
│   │   │
│   │   └── onboarding/
│   │       └── onboarding_screen.dart         # 4-screen onboarding
│   │
│   └── shared/
│       ├── widgets/
│       │   ├── custom_app_bar.dart
│       │   ├── custom_button.dart
│       │   ├── loading_indicator.dart
│       │   ├── error_widget.dart
│       │   ├── empty_state_widget.dart
│       │   ├── freight_card_widget.dart
│       │   ├── driver_card_widget.dart
│       │   ├── rating_widget.dart
│       │   ├── map_widget.dart
│       │   └── bottom_navigation_bar.dart
│       │
│       ├── dialogs/
│       │   ├── confirmation_dialog.dart
│       │   ├── error_dialog.dart
│       │   ├── loading_dialog.dart
│       │   └── success_dialog.dart
│       │
│       └── animations/
│           ├── slide_transition.dart
│           ├── fade_transition.dart
│           └── scale_transition.dart

assets/
├── images/                           # PNG/JPG images
├── icons/                            # Icon assets
├── animations/                       # Lottie JSON files
│   ├── truck_loading.json
│   ├── delivery_success.json
│   ├── ai_animation.json
│   └── network_error.json
└── languages/                        # Localization files
    ├── en_US.json
    ├── am_ET.json
    ├── om_ET.json
    └── ti_ET.json
```

---

## 2. Implementation Roadmap

### Phase 1: Foundation ✅
- [x] Project setup with Riverpod + Go Router
- [x] Theme configuration (Material 3)
- [x] API client with Dio + interceptors
- [x] Models and data structures
- [x] Repositories (data abstraction layer)
- [x] State management providers
- [x] Authentication screens (login/register)
- [x] Routing configuration

### Phase 2: Core Shipper Features (In Progress)
- [ ] Home/Dashboard screen
- [ ] Freight list screen (browse available loads)
- [ ] Create freight multi-step wizard
- [ ] Freight detail screen
- [ ] AI recommendations screen
- [ ] Shipper dashboard

### Phase 3: Core Driver Features
- [ ] Driver profile creation
- [ ] Driver dashboard
- [ ] Available loads screen
- [ ] Load detail screen
- [ ] Accept/Reject loads
- [ ] Counter offer negotiation
- [ ] Earnings dashboard
- [ ] Ratings screen

### Phase 4: Real-time Features
- [ ] Live GPS tracking (location service + Google Maps)
- [ ] WebSocket integration (Socket.IO)
- [ ] Real-time notifications (Firebase FCM)
- [ ] Chat messaging
- [ ] Trip timeline visualization

### Phase 5: Payment Integration
- [ ] Escrow payment system
- [ ] Chapa payment integration
- [ ] CBE Birr integration (when API available)
- [ ] Payment confirmation
- [ ] Payment history
- [ ] Receipt generation

### Phase 6: Advanced Features
- [ ] AI chat assistant
- [ ] AI price prediction
- [ ] AI vehicle recommendation
- [ ] AI driver matching
- [ ] Digital contracts + E-signature
- [ ] Multi-language support (English, Amharic, Oromo, Tigrinya)
- [ ] Offline support (local caching)
- [ ] Biometric authentication

### Phase 7: Admin Module
- [ ] Admin dashboard
- [ ] User management
- [ ] Driver approval/rejection
- [ ] Fraud monitoring
- [ ] Payment analytics
- [ ] Dispute resolution

### Phase 8: Polish & Optimization
- [ ] UI/UX refinement
- [ ] Performance optimization
- [ ] Security hardening
- [ ] Unit tests
- [ ] Integration tests
- [ ] E2E tests
- [ ] App store optimization

---

## 3. Technology Stack Breakdown

### State Management: Riverpod
```dart
// Example provider usage
final authNotifierProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final authRepository = ref.watch(authRepositoryProvider);
  return AuthNotifier(authRepository: authRepository);
});

// Usage in widgets
final authState = ref.watch(authNotifierProvider);
final authNotifier = ref.read(authNotifierProvider.notifier);
```

### Networking: Dio + API Client
```dart
// All API calls go through ApiClient
final response = await apiClient.post(
  '/freight',
  data: freightData,
  fromJson: (json) => FreightRequest.fromJson(json),
);
```

### Local Storage: Hive + SharedPreferences
```dart
// Hive for complex objects
final box = Hive.box('freight_box');
box.put('freight_1', freight);

// SharedPreferences for simple data
final prefs = SharedPreferences.getInstance();
prefs.setString('user_role', role);
```

### Secure Storage: flutter_secure_storage
```dart
// Store sensitive data (tokens, passwords)
await secureStorage.write(key: 'auth_token', value: token);
```

### Location & Tracking: Geolocator + Google Maps
```dart
// Get current location
final position = await Geolocator.getCurrentPosition();

// Display on map
GoogleMap(
  initialCameraPosition: initialPosition,
  markers: {userMarker},
  polylines: {routeLine},
)
```

### Real-time Communication: Socket.IO
```dart
// Connect to backend
final socket = io(
  'http://localhost:5000',
  OptionBuilder().setTransports(['websocket']).build(),
);

// Listen for events
socket.on('driver_location_update', (data) {
  // Update map
});
```

### Notifications: Firebase FCM
```dart
// Get device token
final token = await FirebaseMessaging.instance.getToken();

// Listen for messages
FirebaseMessaging.onMessage.listen((RemoteMessage message) {
  // Show notification
});
```

### Localization: easy_localization
```dart
// Use translations
Text('hello'.tr()); // Translates based on locale
Text('welcome_msg'.tr(args: ['John'])); // With arguments
```

---

## 4. Key Screens - Implementation Details

### 4.1 Freight Creation Wizard (Multi-step)

**Step 1: Location Selection**
- Google Maps with marker placement
- Geocoding for address search
- Current location button

**Step 2: Cargo Details**
- Cargo type dropdown (fuel, livestock, electronics, etc.)
- Weight input (tons)
- Volume input (m³)
- Description text area

**Step 3: Budget & Timeline**
- Budget input field
- Deadline date picker
- AI price prediction display

**Step 4: Review & Submit**
- Display summary of all data
- Edit button for each section
- Submit button with validation

### 4.2 Live Tracking Screen

- Google Maps occupying 70% of screen
- Real-time driver location marker
- Route polyline from pickup to delivery
- Bottom sheet showing:
  - Current location address
  - ETA calculation
  - Distance remaining
  - Current speed
  - Trip timeline (picked up, in transit, delivery)

### 4.3 Payment Flow

1. **Initialize Payment Screen**
   - Show freight details
   - Display escrow breakdown:
     - Shipper pays: X
     - Commission (10%): Y
     - Driver receives: Z

2. **Payment Provider Selection**
   - Chapa (primary)
   - CBE Birr (when available)
   - Telebirr (planned)

3. **Provider-Specific Payment**
   - Chapa: Web view integration
   - CBE Birr: Custom UI
   - Telebirr: Custom UI

4. **Confirmation & Receipt**
   - Show payment confirmation
   - Generate PDF receipt
   - Update freight status to "payment_confirmed"

### 4.4 Chat Screen

- Message list with pagination
- Message bubbles (sender/receiver styling)
- Input field with:
  - Text input
  - Image picker
  - File attachment
  - Send button
- Real-time message updates (Socket.IO)
- Read receipts
- Online/offline status

### 4.5 AI Assistant

- Chat interface
- Multi-language support
- Features:
  - Price estimation
  - Vehicle recommendation
  - Driver matching
  - Route optimization
  - Platform support

---

## 5. API Integration Examples

### 5.1 Create Freight Request
```dart
Future<FreightRequest> createFreight(FreightData data) async {
  return await apiClient.post(
    '/freight',
    data: {
      'pickupLocation': data.pickupLocation,
      'pickupLatitude': data.pickupLat,
      'pickupLongitude': data.pickupLng,
      'deliveryLocation': data.deliveryLocation,
      'deliveryLatitude': data.deliveryLat,
      'deliveryLongitude': data.deliveryLng,
      'cargoType': data.cargoType,
      'weightTons': data.weightTons,
      'volumeM3': data.volumeM3,
      'budget': data.budget,
      'deadline': data.deadline?.toIso8601String(),
      'description': data.description,
    },
    fromJson: (json) => FreightRequest.fromJson(json),
  );
}
```

### 5.2 Get AI Price Prediction
```dart
Future<PricePrediction> getPriceEstimate({
  required String cargoType,
  required double weightTons,
  required double distanceKm,
}) async {
  return await apiClient.get(
    '/ai/price-prediction',
    queryParameters: {
      'cargoType': cargoType,
      'weightTons': weightTons,
      'distanceKm': distanceKm,
    },
    fromJson: (json) => PricePrediction.fromJson(json['prediction']),
  );
}
```

### 5.3 Post Tracking Update
```dart
Future<void> updateLocation(double lat, double lng) async {
  await apiClient.post(
    '/tracking',
    data: {
      'freightId': freightId,
      'latitude': lat,
      'longitude': lng,
      'altitude': position.altitude,
      'accuracy': position.accuracy,
      'speed': position.speed,
    },
  );
}
```

### 5.4 Send Message
```dart
Future<Message> sendMessage({
  required int receiverId,
  required String content,
  int? freightId,
}) async {
  final response = await apiClient.post(
    '/messages',
    data: {
      if (freightId != null) 'freightId': freightId,
      'receiverId': receiverId,
      'content': content,
    },
  );
  return Message.fromJson(response['message']);
}
```

---

## 6. Services Implementation

### 6.1 Location Service (Tracking)
```dart
class LocationService {
  static final LocationService _instance = LocationService._internal();
  
  StreamController<Position>? _positionStream;
  
  Future<void> startTracking(int freightId) async {
    _positionStream = StreamController<Position>();
    
    Geolocator.getPositionStream(
      locationSettings: LocationSettings(
        accuracy: LocationAccuracy.best,
        distanceFilter: 10, // 10 meters
      ),
    ).listen((position) {
      _postTrackingUpdate(freightId, position);
      _positionStream?.add(position);
    });
  }
  
  Future<void> _postTrackingUpdate(int freightId, Position position) async {
    await trackingRepository.postUpdate(
      freightId: freightId,
      latitude: position.latitude,
      longitude: position.longitude,
      accuracy: position.accuracy,
      speed: position.speed,
      altitude: position.altitude,
    );
  }
  
  Stream<Position> get positionStream => _positionStream!.stream;
  
  void stopTracking() {
    _positionStream?.close();
  }
}
```

### 6.2 Socket.IO Service (Real-time)
```dart
class SocketService {
  late IO.Socket socket;
  
  void connect() {
    socket = io(
      'http://localhost:5000',
      OptionBuilder()
        .setTransports(['websocket'])
        .disableAutoConnect()
        .build(),
    );
    
    socket.connect();
    
    // Listen for events
    socket.on('new_freight', (data) {
      // Handle new freight
    });
    
    socket.on('driver_location_update', (data) {
      // Update map
    });
  }
  
  void emit(String event, dynamic data) {
    socket.emit(event, data);
  }
  
  void disconnect() {
    socket.disconnect();
  }
}
```

### 6.3 Push Notification Service
```dart
class PushNotificationService {
  Future<void> initialize() async {
    // Request permission
    final settings = await FirebaseMessaging.instance.requestPermission();
    
    // Get device token
    final token = await FirebaseMessaging.instance.getToken();
    
    // Listen for messages
    FirebaseMessaging.onMessage.listen((RemoteMessage message) {
      _showNotification(message);
    });
  }
  
  void _showNotification(RemoteMessage message) {
    // Show local notification
    // Use flutter_local_notifications package
  }
}
```

---

## 7. Role-Based Navigation

```dart
class RoleBasedHome extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authNotifierProvider);
    final role = authState.user?.role;
    
    switch (role) {
      case 'shipper':
        return const ShipperDashboard();
      case 'driver':
        return const DriverDashboard();
      case 'fleet_owner':
        return const FleetOwnerDashboard();
      case 'admin':
        return const AdminDashboard();
      case 'support':
        return const SupportDashboard();
      default:
        return const Center(child: Text('Unknown role'));
    }
  }
}
```

---

## 8. Testing Strategy

### 8.1 Unit Tests
```dart
test('AuthRepository.login returns AuthResponse', () async {
  final response = await authRepository.login(
    email: 'test@example.com',
    password: 'password123',
  );
  expect(response.token, isNotEmpty);
  expect(response.user.role, 'shipper');
});
```

### 8.2 Integration Tests
```dart
testWidgets('Login flow works end-to-end', (WidgetTester tester) async {
  await tester.pumpWidget(const FreightApp());
  
  // Enter credentials
  await tester.enterText(find.byType(TextField).first, 'test@example.com');
  await tester.enterText(find.byType(TextField).last, 'password123');
  
  // Tap login
  await tester.tap(find.byType(ElevatedButton));
  await tester.pumpAndSettle();
  
  // Verify navigation
  expect(find.byType(HomeScreen), findsOneWidget);
});
```

---

## 9. Build & Deployment

### Android
```bash
flutter build apk --release
# Or
flutter build aab --release  # Google Play
```

### iOS
```bash
flutter build ios --release
flutter build ios --release --codesign
```

### Web
```bash
flutter build web --release
```

---

## 10. Performance Optimization Checklist

- [ ] Image optimization (WebP format, lazy loading)
- [ ] API response caching (1 hour for user data, 5 min for freight)
- [ ] Pagination/infinite scroll
- [ ] Lazy loading of screens
- [ ] Widget memoization (const constructors)
- [ ] Efficient state management (watch vs read)
- [ ] Background task optimization
- [ ] Memory leak prevention
- [ ] Jank monitoring
- [ ] Battery optimization for GPS tracking

---

## 11. Security Checklist

- [ ] HTTPS only for API
- [ ] Token refresh mechanism
- [ ] Secure token storage (flutter_secure_storage)
- [ ] Password hashing on backend
- [ ] Input validation/sanitization
- [ ] API rate limiting
- [ ] Biometric authentication
- [ ] Encryption for sensitive data
- [ ] Obfuscation (release builds)
- [ ] Certificate pinning

---

## 12. Next Steps

1. **Complete Auth Screens** - Implement login/register with validation
2. **Implement Home Screen** - Dashboard with role-based widgets
3. **Build Freight Creation Wizard** - Multi-step form with Google Maps
4. **Integrate Payment Gateway** - Chapa API integration
5. **Set Up Tracking** - Location service + Google Maps
6. **Implement Chat** - Real-time messaging with Socket.IO
7. **Add Notifications** - Firebase FCM setup
8. **Localization** - Translate to 4 languages
9. **Testing** - Unit, integration, and E2E tests
10. **Deployment** - App Store and Google Play

---

## 13. Resources

- **Flutter Docs**: https://flutter.dev/docs
- **Riverpod Docs**: https://riverpod.dev
- **Go Router Docs**: https://pub.dev/packages/go_router
- **Dio Docs**: https://pub.dev/packages/dio
- **Firebase Docs**: https://firebase.flutter.dev
- **Google Maps Docs**: https://pub.dev/packages/google_maps_flutter

---

**Status**: ✅ Architecture Complete - Ready for Feature Implementation

**Architecture Created By**: GitHub Copilot
**Date**: 2026-06-02
**Version**: 1.0.0

