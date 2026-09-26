# Freight Platform - Flutter Mobile Application

## 🚀 Project Overview

A complete, production-ready Flutter mobile application for the Freight Platform - an AI-powered logistics system with real-time tracking, escrow payments, and multi-role support.

**Version**: 1.0.0  
**Status**: Architecture Complete ✅ - Ready for Feature Implementation  
**Target**: Flutter Stable | Dart 3.0+

---

## 📋 Features

### ✅ Implemented
- Complete project structure with Clean Architecture
- Riverpod state management
- Dio API client with interceptors and error handling
- Comprehensive data models and repositories
- Go Router navigation with role-based access
- Material 3 theme system
- Authentication (login/register) screens
- Secure token storage
- Multi-language localization setup

### 🔄 In Progress
- Freight creation multi-step wizard
- Home screen / Dashboard
- Freight list and details
- Driver profile and management

### 📝 Planned
- Real-time GPS tracking with Google Maps
- WebSocket integration (Socket.IO)
- Escrow payment system (Chapa, CBE Birr, Telebirr)
- Digital contracts with E-signature
- AI assistant chat interface
- Real-time messaging (Socket.IO)
- Firebase push notifications
- Multi-language support (English, Amharic, Oromo, Tigrinya)
- Offline-first caching strategy
- Admin dashboard

---

## 📁 Architecture

### Clean Architecture Layers

```
Presentation Layer (Screens & Widgets)
         ↓
State Management Layer (Riverpod Providers)
         ↓
Domain Layer (Repositories)
         ↓
Data Layer (API Client, Local Storage)
         ↓
External Services (Firebase, Maps, Payment APIs)
```

---

## 🛠️ Technology Stack

| Component | Technology |
|-----------|-----------|
| State Management | Riverpod |
| Networking | Dio |
| Navigation | Go Router |
| Local Storage | Hive, SharedPreferences |
| Secure Storage | flutter_secure_storage |
| Maps & Location | google_maps_flutter, geolocator |
| Real-time | socket_io_client |
| Notifications | firebase_messaging |
| Localization | easy_localization |
| UI Framework | Flutter Material 3 |

---

## 🚀 Quick Start

```bash
flutter pub get
flutter pub run build_runner build --delete-conflicting-outputs
flutter run
```

---

## 📚 Documentation

- [API Integration Plan](./MOBILE_INTEGRATION_PLAN.md) - Backend API documentation
- [Flutter Implementation Guide](./FLUTTER_IMPLEMENTATION_GUIDE.md) - Complete feature roadmap

---

**Status**: Architecture Complete - Ready for Feature Implementation
- Estimated delivery time
- Location history timeline
- Driver location tracking

### Driver Operations
- Driver profile management
- Vehicle information
- Ratings and reviews
- Document management

### Notifications
- Push notifications for shipment updates
- Order status alerts
- Delivery confirmations

## Tech Stack

```
┌─────────────────────────────────────────┐
│    Flutter 3.10+                        │
│    (Cross-platform Mobile Framework)    │
└─────────────────────────────────────────┘
         ↓
┌─────────────────────────────────────────┐
│    Dart 3.0+                            │
│    (Programming Language)               │
└─────────────────────────────────────────┘
         ↓
    ┌──────────────────┬────────────────┐
    ↓                  ↓                 ↓
 UI Layer          State Mgmt       Data Layer
 ────────────────────────────────────────
 • Material 3      • Riverpod       • Dio HTTP
 • GoRouter Nav    • Provider       • Hive Local DB
 • Custom Widgets  • Consumer       • SharedPrefs
                                    • SQLite (optional)
```

### Key Dependencies

| Package | Purpose | Version |
|---------|---------|---------|
| flutter_riverpod | State Management | 2.4.0+ |
| go_router | Navigation | 13.0.0+ |
| dio | HTTP Client | 5.3.0+ |
| hive_flutter | Local Storage | 1.1.0+ |
| google_maps_flutter | Maps | 2.5.0+ |
| geolocator | GPS Location | 9.0.0+ |

## Project Structure

```
artifacts/mobile-app/
│
├── lib/                          # Source code
│   ├── main.dart                # App entry point & all screens
│   ├── screens/                 # UI screens (organized by feature)
│   ├── services/                # API & business logic
│   ├── models/                  # Data models
│   ├── providers/               # Riverpod state providers
│   ├── widgets/                 # Reusable UI components
│   └── utils/                   # Utilities & constants
│
├── test/                         # Unit & widget tests
│   ├── models/
│   ├── services/
│   └── widgets/
│
├── android/                      # Android native code
│   ├── app/
│   └── gradle/
│
├── ios/                          # iOS native code
│   ├── Runner/
│   └── Pods/
│
├── web/                          # Web platform (if enabled)
├── windows/                      # Windows desktop (if enabled)
│
├── pubspec.yaml                 # Dependencies & project config
├── pubspec.lock                 # Locked dependency versions
├── analysis_options.yaml        # Linting rules
├── SETUP.md                     # Setup instructions
└── README.md                    # This file
```

## Getting Started

### Prerequisites

- **Flutter SDK**: 3.10 or later
- **Dart SDK**: 3.0 or later (included with Flutter)
- **Android SDK**: For Android development
- **Xcode**: For iOS development (Mac only)
- **Git**: For version control

### Installation

1. **Install Flutter**
   ```bash
   # Visit https://flutter.dev/docs/get-started/install
   # and follow platform-specific instructions
   
   # Verify installation
   flutter doctor
   ```

2. **Clone & Setup Project**
   ```bash
   cd artifacts/mobile-app
   flutter pub get
   ```

3. **Generate Build Files** (for Hive & code generators)
   ```bash
   flutter pub run build_runner build
   ```

### Running the App

**Development Mode:**
```bash
# Run on Android/iOS emulator
flutter run

# Run on web
flutter run -d web

# Run on physical device
flutter run
```

**Release Build:**
```bash
# Android APK
flutter build apk --release

# iOS App
flutter build ios --release

# Web
flutter build web --release
```

## API Integration

The app connects to the Express.js backend API running on `http://localhost:5000/api`.

### Example: Fetching Freight List

```dart
// In a Riverpod provider
final freightProvider = FutureProvider<List<Freight>>((ref) async {
  final dio = Dio();
  final response = await dio.get('http://localhost:5000/api/freight');
  return response.data.map((item) => Freight.fromJson(item)).toList();
});

// In a widget
@override
Widget build(BuildContext context, WidgetRef ref) {
  final freightAsync = ref.watch(freightProvider);
  
  return freightAsync.when(
    data: (freight) => ListView(...),
    loading: () => LoadingWidget(),
    error: (err, st) => ErrorWidget(error: err),
  );
}
```

## Authentication Flow

```
┌─────────────────┐
│  Login Screen   │
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│  Validate Input │
└────────┬────────┘
         │
         ↓
┌────────────────────────────────────┐
│  POST /api/auth/login              │
│  { email, password }               │
└────────┬────────────────────────────┘
         │
         ├─ Success (200)
         │   ├─ Save JWT token
         │   ├─ Save user data
         │   └─ Navigate to Home
         │
         └─ Error (401)
             └─ Show error message
```

## State Management Pattern (Riverpod)

```dart
// 1. Define a provider
final userProvider = StateNotifierProvider<UserNotifier, User?>((ref) {
  return UserNotifier(ref.watch(authServiceProvider));
});

// 2. Create a notifier
class UserNotifier extends StateNotifier<User?> {
  UserNotifier(this._authService) : super(null);
  
  final AuthService _authService;
  
  Future<void> login(String email, String password) async {
    try {
      final user = await _authService.login(email, password);
      state = user;
    } catch (e) {
      rethrow;
    }
  }
}

// 3. Use in widgets
@override
Widget build(BuildContext context, WidgetRef ref) {
  final user = ref.watch(userProvider);
  
  return user == null ? LoginScreen() : HomeScreen();
}
```

## Database Schema

The app syncs with these main entities from the backend:

```
User
├── id: String
├── email: String
├── name: String
├── role: UserRole (admin|shipper|driver|fleet_owner|support)
└── createdAt: DateTime

Freight
├── id: String
├── origin: String
├── destination: String
├── weight: double
├── status: String
├── driver: User?
├── createdAt: DateTime
└── updatedAt: DateTime

Tracking
├── id: String
├── freightId: String
├── latitude: double
├── longitude: double
├── timestamp: DateTime
└── status: String
```

## Testing

```bash
# Run all tests
flutter test

# Run specific test file
flutter test test/models/user_model_test.dart

# Generate coverage report
flutter test --coverage

# Run with verbose output
flutter test -v
```

## Build & Deployment

### Android

```bash
# Build APK (debug)
flutter build apk

# Build APK (release)
flutter build apk --release

# Build AAB (for Google Play)
flutter build appbundle --release

# Get signing key info
keytool -list -v -keystore android/key.jks
```

### iOS

```bash
# Build IPA (for TestFlight/App Store)
flutter build ios --release

# Locate IPA
# build/ios/iphoneos/Runner.app

# Distribute via TestFlight
# Use Xcode or fastlane
```

## Performance Optimization

- **Code Splitting**: Lazy load screens with GoRouter
- **Image Caching**: Use `cached_network_image` for remote images
- **State Optimization**: Use `.select()` in Riverpod for fine-grained rebuilds
- **Build Optimization**: Enable shrinking and minification for release builds

## Troubleshooting

### Common Issues

**Issue:** `flutter: command not found`
```bash
# Add Flutter to PATH
export PATH="$PATH:$HOME/development/flutter/bin"
```

**Issue:** Android build fails
```bash
flutter clean
flutter pub get
flutter pub run build_runner build
flutter run
```

**Issue:** iOS build fails
```bash
cd ios
pod repo update
pod install
cd ..
flutter run
```

**Issue:** Hot reload not working
```bash
# Full restart
flutter run --no-fast-start
```

## Development Workflow

1. **Create Feature Branch**
   ```bash
   git checkout -b feature/shipment-tracking
   ```

2. **Implement Feature**
   - Add models in `lib/models/`
   - Create providers in `lib/providers/`
   - Build UI screens in `lib/screens/`
   - Write tests in `test/`

3. **Format & Lint**
   ```bash
   flutter format lib/ test/
   flutter analyze
   ```

4. **Run Tests**
   ```bash
   flutter test
   ```

5. **Commit & Push**
   ```bash
   git add .
   git commit -m "feat: add shipment tracking"
   git push origin feature/shipment-tracking
   ```

## Contributing

1. Follow Flutter best practices
2. Write tests for new features
3. Keep code formatted with `flutter format`
4. Resolve linting issues with `flutter analyze`
5. Use meaningful commit messages

## Resources

- [Flutter Documentation](https://flutter.dev/docs)
- [Dart Language Guide](https://dart.dev/guides/language/language-tour)
- [Flutter Riverpod](https://riverpod.dev)
- [GoRouter Navigation](https://pub.dev/packages/go_router)
- [Material Design 3](https://m3.material.io)
- [Google Maps Flutter](https://pub.dev/packages/google_maps_flutter)

## License

Proprietary - Freight Management Platform

## Support

For issues or questions:
- Check the [SETUP.md](./SETUP.md) file
- Review Flutter documentation
- Check GitHub issues in the main repository

---

**Project:** Freight Management Platform
**Mobile App:** Flutter (Dart)
**Status:** 🚀 Ready for Development
**Last Updated:** June 2, 2026
