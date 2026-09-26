# Flutter API Configuration - Diagnostic Report

## 🚨 CRITICAL ISSUES FOUND

---

## 1. API Base URL Configuration

### **Current Configuration** ❌
**File**: `lib/src/data/api/api_client.dart` (lines 7-16)

```dart
// ── Base URL selection ─────────────────────────────────────────────────────
// Override at build time with: --dart-define=API_BASE_URL=http://your-server/api
// Falls back to the Herd local server when no override is provided.
const String _kApiBaseUrlOverride = String.fromEnvironment('API_BASE_URL');

String get kBaseUrl {
  if (_kApiBaseUrlOverride.isNotEmpty) return _kApiBaseUrlOverride;
  if (kIsWeb) return 'http://backend.test/api';
  return 'http://backend.test/api';
}
```

### **The Problem** 🔴

The app is **hardcoded to use `http://backend.test/api`** for ALL platforms:
- ✗ Android emulator
- ✗ Android physical device
- ✗ iOS simulator
- ✗ iOS physical device
- ✗ Windows desktop
- ✗ Web browser

**BUT** `backend.test` is **not a real domain**. It only exists in a local network or through DNS configuration.

---

## 2. Dio Client Creation

### **Location & Code** 📍
**File**: `lib/src/data/api/api_client.dart` (lines 58-77)

```dart
final dioProvider = Provider<Dio>((ref) {
  final dio = Dio(
    BaseOptions(
      baseUrl: kBaseUrl,                    // ← Gets 'http://backend.test/api'
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
      sendTimeout: const Duration(seconds: 30),
      validateStatus: (status) => status != null && status < 500,
    ),
  );

  const secureStorage = FlutterSecureStorage();
  final logger = Logger();
  dio.interceptors.add(ApiInterceptor(logger: logger, secureStorage: secureStorage));

  // Dispose the Dio instance when the provider is disposed
  ref.onDispose(() => dio.close());

  return dio;
});
```

### **How It Works**

1. **Initialization**: Dio provider created as a Riverpod singleton
2. **Base URL**: Set to whatever `kBaseUrl` returns
3. **Timeouts**: 30 seconds for all operations
4. **Interceptor**: Adds Bearer token to every request
5. **Error Handling**: Accepts all responses < 500

---

## 3. API_BASE_URL Environment Variable

### **How to Override** ✅

The code DOES support `--dart-define` override:

```dart
const String _kApiBaseUrlOverride = String.fromEnvironment('API_BASE_URL');
```

But it's **not being used** because:
- Documentation mentions it (line 8)
- No build command uses it
- No `.env` file exists
- App launches without the flag

### **How to Override (When You Run)**

```bash
# For Android/iOS/Windows
flutter run --dart-define=API_BASE_URL=http://your-actual-server/api

# For Web
flutter run -d web --dart-define=API_BASE_URL=http://your-actual-server/api

# For release build
flutter build apk --dart-define=API_BASE_URL=http://your-actual-server/api
```

---

## 4. URL Being Used by Platform

### **ALL Platforms Use Same URL** 🚨

| Platform | URL Used | Status |
|----------|----------|--------|
| **Android Emulator** | `http://backend.test/api` | ❌ Cannot resolve |
| **Android Device** | `http://backend.test/api` | ❌ Cannot resolve |
| **iOS Simulator** | `http://backend.test/api` | ❌ Cannot resolve |
| **iOS Device** | `http://backend.test/api` | ❌ Cannot resolve |
| **Windows Desktop** | `http://backend.test/api` | ❌ Cannot resolve |
| **Web (localhost:8080)** | `http://backend.test/api` | ❌ Cannot resolve |

### **The code intentionally does NOT differentiate:**

```dart
String get kBaseUrl {
  if (_kApiBaseUrlOverride.isNotEmpty) return _kApiBaseUrlOverride;
  if (kIsWeb) return 'http://backend.test/api';    // ← Same for web
  return 'http://backend.test/api';                // ← Same for native
}
```

---

## 5. Why Requests Fail - ROOT CAUSE ANALYSIS

### **Failure Sequence** 🔴

```
1. App starts
   ↓
2. Dio initialized with baseUrl: 'http://backend.test/api'
   ↓
3. User tries to login
   ↓
4. POST http://backend.test/api/login sent
   ↓
5. Device tries to resolve 'backend.test' domain
   ↓
6. ❌ FAILS: DNS cannot resolve 'backend.test'
   ↓
7. DioException thrown: "Failed host lookup: 'backend.test'"
   ↓
8. User sees: "Network error" or "Request failed"
```

### **Network Error Handling** (Lines 137-139)

```dart
} on DioException catch (e) {
  throw ApiException(message: e.message ?? 'Network error', 
                     statusCode: e.response?.statusCode);
}
```

The error message will be something like:
- **"Failed host lookup: 'backend.test'"** (DNS resolution failure)
- **"Connection refused: 'backend.test'"** (if port unreachable)
- **"Connection timed out"** (if host unreachable)

---

## 6. Network Security Configuration

### **Android Network Security** ✅
**File**: `android/app/src/main/res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </base-config>
</network-security-config>
```

**Analysis**:
- ✅ Cleartext (HTTP) traffic IS permitted
- ✅ System certificates trusted
- ❌ But won't help if domain can't be resolved

### **AndroidManifest.xml** ✅
**File**: `android/app/src/main/AndroidManifest.xml` (line 2)

```xml
<uses-permission android:name="android.permission.INTERNET"/>
```

**Analysis**:
- ✅ Internet permission granted
- ❌ But won't help if domain can't be resolved

---

## 7. What "backend.test" Actually Is

### **NOT a Real Domain** 🔴

- ❌ Not registered on the public internet
- ❌ Not a valid TLD (.test is reserved but not functional)
- ❌ Not accessible from outside a local network
- ❌ DNS servers worldwide have no record for it

### **Possible Origins**

The "backend.test" URL suggests:
1. Local development network setup with `/etc/hosts` override
2. Mocked domain for testing
3. Placeholder that was never updated
4. Developer assumed local server running with custom DNS

### **Would Only Work If:**

```bash
# On developer machine or test server:
# In /etc/hosts (Linux/Mac) or C:\Windows\System32\drivers\etc\hosts (Windows):
127.0.0.1 backend.test
```

OR

```bash
# Running local backend server on subdomain via hosts file:
192.168.1.100 backend.test  # If backend runs on this IP
```

---

## 8. DOCUMENTATION MISMATCH ⚠️

### **What Documentation Says vs What Code Does**

| Document | Says | Actual | Status |
|----------|------|--------|--------|
| `SETUP.md` (line 157) | Use `http://localhost:5000/api` | Uses `http://backend.test/api` | ❌ Mismatch |
| `QUICK_START_PHASE_2.5.md` (line 178) | Backend at `http://localhost:5000/api` | Uses `http://backend.test/api` | ❌ Mismatch |
| `api_client.dart` (line 8) | "Override with --dart-define" | Never used | ❌ Missing |
| Express backend mentioned | Expects port 5000 | Not configured | ❌ Missing |

---

## ✅ FIX #1: For Immediate Testing (Android Emulator)

### **If backend runs on localhost:5000**

Android emulator special IP: **10.0.2.2**

```bash
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:5000/api
```

### **How it works:**
- `10.0.2.2` is the Android emulator's way to access the host machine (localhost)
- You'll connect to `http://localhost:5000/api` running on your dev machine

---

## ✅ FIX #2: For Physical Device

### **If backend runs on your laptop on LAN**

1. Find your laptop's IP address:
```bash
# Windows (PowerShell)
ipconfig | findstr "IPv4"
# Output might show: 192.168.1.50

# Mac/Linux
ifconfig | grep "inet "
# Output might show: 192.168.1.50
```

2. Run app with that IP:
```bash
flutter run --dart-define=API_BASE_URL=http://192.168.1.50:5000/api
```

---

## ✅ FIX #3: Update Code to Be Smarter

**File to modify**: `lib/src/data/api/api_client.dart`

Replace lines 12-16:

```dart
// OLD (BROKEN):
String get kBaseUrl {
  if (_kApiBaseUrlOverride.isNotEmpty) return _kApiBaseUrlOverride;
  if (kIsWeb) return 'http://backend.test/api';
  return 'http://backend.test/api';
}

// NEW (SMART):
String get kBaseUrl {
  // 1. Check for --dart-define override first
  if (_kApiBaseUrlOverride.isNotEmpty) {
    return _kApiBaseUrlOverride;
  }
  
  // 2. Platform-specific defaults
  if (kIsWeb) {
    // Web: connect to same host that served the app
    return '/api';  // Relative URL (same origin)
  }
  
  // 3. Check for environment - dev vs. prod
  const bool isProduction = bool.fromEnvironment('PRODUCTION', defaultValue: false);
  
  if (isProduction) {
    // Production: Use your actual backend server
    return 'https://api.ethioload.com/api';
  }
  
  // 4. Development: Try common localhost addresses
  // For Android emulator → 10.0.2.2 (special IP)
  // For physical devices → set via --dart-define
  // For iOS → 127.0.0.1 works
  return 'http://localhost:5000/api';  // Fallback for local dev
}
```

---

## 🔍 How to Verify Current Setup

### **Check What URL the App Is Using**

1. Launch the app
2. Open DevTools console (if available)
3. Look for log line in console:
   ```
   → POST /login
   ```
   This comes from `ApiInterceptor.onRequest()` (line 29)

4. Check logcat (Android) or Xcode logs (iOS)
5. Search for the actual base URL being used

### **Manual Test with Postman**

Try to hit the endpoint manually:

```bash
# Test if backend.test is reachable
curl -X POST http://backend.test/api/login \
  -H "Content-Type: application/json" \
  -d '{"identifier":"test@test.com","password":"test123"}'

# Response will be:
# curl: (6) Could not resolve host: backend.test
```

---

## 📋 Summary: Why Your App Fails

| Step | Issue | Reason |
|------|-------|--------|
| 1 | App launches | ✅ Works |
| 2 | Dio initialized | ✅ Works |
| 3 | API base URL set to `http://backend.test/api` | ✅ Works (but wrong URL) |
| 4 | User taps "Login" | ✅ Works |
| 5 | `POST /login` request sent | ✅ Works |
| 6 | Dio tries to resolve domain name `backend.test` | ❌ **FAILS** |
| 7 | DNS lookup times out or fails | ❌ **FAILS** |
| 8 | `DioException` thrown | ✅ Works (but catches error) |
| 9 | Error message shown to user | ✅ Works |
| 10 | **App appears broken** | ❌ **USER SEES: "Network error"** |

---

## 🎯 Action Items

### **IMMEDIATE (To Get App Working)**

- [ ] Identify where your backend API actually is:
  - Running locally on `http://localhost:5000`? 
  - Running on LAN server at `http://192.168.x.x:5000`?
  - Running remotely at `https://api.example.com`?
  
- [ ] Use the appropriate launch command:
  ```bash
  flutter run --dart-define=API_BASE_URL=http://YOUR_ACTUAL_SERVER/api
  ```

### **SHORT-TERM (Improve Configuration)**

- [ ] Update `lib/src/data/api/api_client.dart` `kBaseUrl` function with platform/environment detection
- [ ] Create `.env` file or launch configuration with proper URLs
- [ ] Update documentation to match actual backend URLs

### **LONG-TERM (Best Practice)**

- [ ] Use Firebase Remote Config or similar for runtime URL configuration
- [ ] Add app initialization screen to detect backend connectivity
- [ ] Support multiple backend environments (dev/staging/prod)
- [ ] Add health check endpoint `/health` before making API calls

---

## 📝 Code Files Responsible

| File | Purpose | Lines |
|------|---------|-------|
| `lib/src/data/api/api_client.dart` | Dio setup + base URL configuration | 7-16, 58-77 |
| `lib/main.dart` | App entry point (no URL config) | All |
| `android/app/src/main/AndroidManifest.xml` | Network permissions | 2, 10 |
| `android/app/src/main/res/xml/network_security_config.xml` | HTTP allowed | All |
| `pubspec.yaml` | Dependencies (Dio v5.3.0) | Line 22 |

---

## 🚀 Next Steps

**Choose one:**

1. **I have a backend running on my machine** → Use Android emulator IP `10.0.2.2:5000`
2. **I need to connect to a backend** → Find its actual URL and use `--dart-define`
3. **I need to set up a test backend** → Check the backend repository setup
4. **I want to fix the code permanently** → Implement the smart `kBaseUrl` function above


