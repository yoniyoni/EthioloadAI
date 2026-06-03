# 📱 Flutter Mobile App Setup Guide

## Prerequisites

1. **Flutter SDK** - Download from [flutter.dev](https://flutter.dev/docs/get-started/install)
   - Flutter 3.10+ required
   - Includes Dart SDK automatically

2. **IDEs/Editors:**
   - **Android Studio** - For Android development
   - **Xcode** - For iOS development (Mac only)
   - **VS Code** - With Flutter extension

3. **Development Environment:**
   - Android SDK (API Level 21+)
   - iOS 11.0+ (for iOS deployment)

## Installation Steps

### 1. Install Flutter

Download and install Flutter for your OS:

**Windows:**
```powershell
# Download from flutter.dev and extract to a location like C:\src\flutter
# Add Flutter to PATH:
$env:Path += ";C:\src\flutter\bin"

# Verify installation:
flutter doctor
```

**Mac:**
```bash
# Using Homebrew
brew install flutter

# Or download and add to PATH
```

**Linux:**
```bash
# Download and extract, then add to PATH
export PATH="$PATH:`pwd`/flutter/bin"
```

### 2. Check Environment Setup

```bash
flutter doctor
```

This will show you what's missing. Install any missing dependencies.

### 3. Get Dependencies

From the `artifacts/mobile-app` directory:

```bash
cd artifacts/mobile-app
flutter pub get
```

### 4. Generate Code (for Hive & other generators)

```bash
flutter pub run build_runner build
```

## Running the App

### Run on Android

**Emulator:**
```bash
# Start Android emulator first, then:
flutter run
```

**Physical Device:**
```bash
# Enable USB debugging on your device
# Then:
flutter run
```

### Run on iOS (Mac only)

**Simulator:**
```bash
# Start iOS simulator first
open -a Simulator

# Then:
flutter run
```

**Physical Device:**
```bash
# For physical device, you need to set up signing certificates
flutter run -v
```

### Run on Web (Development)

```bash
flutter run -d web
```

Opens at `http://localhost:8080`

### Build Release APK (Android)

```bash
flutter build apk --release
```

Output: `build/app/outputs/flutter-app.apk`

### Build Release App (iOS)

```bash
flutter build ios --release
```

## Project Structure

```
artifacts/mobile-app/
├── lib/
│   └── main.dart           # Main app entry point & all screens
├── test/                   # Unit & widget tests
├── pubspec.yaml           # Dependencies & project config
├── android/               # Android native code
├── ios/                   # iOS native code
├── web/                   # Web platform files
└── windows/              # Windows desktop support
```

## Key Features Included

✅ **Authentication** - Login & Register screens
✅ **Navigation** - GoRouter for navigation
✅ **State Management** - Riverpod (Flutter Riverpod)
✅ **HTTP Client** - Dio for API calls
✅ **Local Storage** - Hive & SharedPreferences
✅ **Maps** - Google Maps integration ready
✅ **Location** - Geolocator for tracking

## Connecting to Backend API

Update `lib/main.dart` to connect to your Express API:

```dart
// Add API client setup
const String API_BASE_URL = 'http://localhost:5000/api';

// In your API calls:
final response = await Dio().get('$API_BASE_URL/freight');
```

## Common Commands

```bash
# Clean build
flutter clean
flutter pub get

# Run with debug prints
flutter run -v

# Run tests
flutter test

# Format code
flutter format lib/

# Analyze code
flutter analyze

# Generate APK
flutter build apk --split-per-abi

# Build IPA (iOS)
flutter build ios
```

## Troubleshooting

### "Flutter not found"
- Add Flutter to PATH
- Restart terminal/IDE
- Run `flutter doctor` to verify

### Android build fails
```bash
flutter clean
flutter pub get
flutter pub run build_runner build
flutter run
```

### iOS build fails
```bash
cd ios
pod repo update
pod install
cd ..
flutter run
```

### Port already in use (web)
```bash
flutter run -d web --web-port 8081
```

## Next Steps

1. ✅ Install Flutter SDK
2. ✅ Run `flutter pub get` in mobile-app directory
3. ✅ Connect to your local API (update API_BASE_URL in main.dart)
4. ✅ Run `flutter run` to start development
5. ✅ Update screens to connect to real API endpoints
6. ✅ Add authentication logic in LoginScreen/RegisterScreen
7. ✅ Implement real freight list from API

## Development Workflow

1. **Start Backend:**
   ```bash
   # In another terminal
   $env:PORT = 5000
   pnpm --filter @workspace/api-server run dev
   ```

2. **Start Mobile App:**
   ```bash
   cd artifacts/mobile-app
   flutter run
   ```

3. **Hot Reload:**
   - Press `R` in terminal to hot reload
   - Press `R` twice for full restart

## API Integration Example

```dart
// Example: Fetch freight shipments
import 'package:dio/dio.dart';

class FreightService {
  final Dio _dio = Dio();
  
  FreightService() {
    _dio.options.baseUrl = 'http://localhost:5000/api';
  }
  
  Future<List<Freight>> getFreight() async {
    try {
      final response = await _dio.get('/freight');
      return response.data.map((item) => Freight.fromJson(item)).toList();
    } catch (e) {
      print('Error fetching freight: $e');
      rethrow;
    }
  }
}
```

## Resources

- [Flutter Documentation](https://flutter.dev/docs)
- [Dart Documentation](https://dart.dev/guides)
- [Flutter Riverpod](https://riverpod.dev)
- [GoRouter](https://pub.dev/packages/go_router)
- [Dio HTTP Client](https://pub.dev/packages/dio)

---

**Project:** Freight Management Platform
**Mobile App:** Flutter (Dart)
**Status:** Ready for development 🚀
