# API Configuration - Code Breakdown

## Files Responsible for API Communication

---

## 1. API CLIENT SETUP

### File: `lib/src/data/api/api_client.dart`

#### The Problem Zone (Lines 7-16)

```dart
// ── Base URL selection ─────────────────────────────────────────────────────
// Override at build time with: --dart-define=API_BASE_URL=http://your-server/api
// Falls back to the Herd local server when no override is provided.
const String _kApiBaseUrlOverride = String.fromEnvironment('API_BASE_URL');

String get kBaseUrl {
  if (_kApiBaseUrlOverride.isNotEmpty) return _kApiBaseUrlOverride;
  if (kIsWeb) return 'http://backend.test/api';
  return 'http://backend.test/api';
  // ↑ THIS IS THE PROBLEM: backend.test doesn't exist!
}
```

**What goes wrong**:
1. `_kApiBaseUrlOverride` tries to read from `--dart-define=API_BASE_URL`
2. If not provided (and it never is), it's empty
3. Falls back to hardcoded `http://backend.test/api`
4. App tries to connect to non-existent domain
5. DNS lookup fails → Network error

**The fix**:
```dart
String get kBaseUrl {
  if (_kApiBaseUrlOverride.isNotEmpty) return _kApiBaseUrlOverride;
  if (kIsWeb) return 'http://localhost:5000/api';  // ← Use real URL
  return 'http://localhost:5000/api';               // ← Use real URL
}
```

---

#### Dio Provider Creation (Lines 58-77)

```dart
final dioProvider = Provider<Dio>((ref) {
  final dio = Dio(
    BaseOptions(
      baseUrl: kBaseUrl,  // ← Gets 'http://backend.test/api' HERE
      connectTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
      sendTimeout: const Duration(seconds: 30),
      validateStatus: (status) => status != null && status < 500,
    ),
  );

  const secureStorage = FlutterSecureStorage();
  final logger = Logger();
  dio.interceptors.add(
    ApiInterceptor(logger: logger, secureStorage: secureStorage)
  );

  ref.onDispose(() => dio.close());

  return dio;
});
```

**Execution Flow**:
```
1. App starts → main.dart
2. ProviderScope(child: EthioLoadApp())
3. First API call triggers dioProvider access
4. Dio(BaseOptions(baseUrl: kBaseUrl))
5. kBaseUrl evaluated → returns 'http://backend.test/api'
6. Dio stores this as the base URL for all requests
7. When POST /login called → becomes POST http://backend.test/api/login
8. ❌ Fails: DNS cannot resolve backend.test
```

---

#### ApiInterceptor (Lines 18-56)

```dart
class ApiInterceptor extends Interceptor {
  final Logger logger;
  final FlutterSecureStorage secureStorage;

  ApiInterceptor({required this.logger, required this.secureStorage});

  @override
  Future<void> onRequest(
    RequestOptions options,
    RequestInterceptorHandler handler,
  ) async {
    logger.i('→ ${options.method} ${options.path}');  // ← Logs request
    final token = await secureStorage.read(key: 'auth_token');
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    options.headers['Accept'] = 'application/json';
    options.headers['Content-Type'] = 'application/json';
    handler.next(options);  // ← Sends request
  }

  @override
  Future<void> onResponse(Response response, ResponseInterceptorHandler handler) async {
    logger.i('← ${response.statusCode} ${response.requestOptions.path}');  // ← Logs response
    handler.next(response);
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    logger.e('✗ ${err.response?.statusCode} ${err.requestOptions.path}: ${err.message}');
    // ↑ When network fails, logs error here
    handler.next(err);
  }
}
```

**Error Flow**:
```
API call → Network fails (DNS lookup fails for backend.test)
  ↓
DioException thrown with message
  ↓
onError() called
  ↓
Logs: ✗ null /login: Failed host lookup: 'backend.test'
  ↓
Error propagated up the stack
  ↓
UI shows "Network error"
```

---

#### Error Extraction (Lines 112-123)

```dart
String _extractError(Response? response) {
  final data = response?.data;
  if (data is Map) {
    if (data.containsKey('errors')) {
      final errors = data['errors'] as Map;
      final first = errors.values.first;
      if (first is List && first.isNotEmpty) return first[0].toString();
    }
    if (data.containsKey('message')) return data['message'].toString();
  }
  return 'Request failed (${response?.statusCode})';
}
```

**For network failure**:
```
Network fails before response → response is null
  ↓
response?.data throws exception
  ↓
Falls through to return statement
  ↓
Returns: 'Request failed (null)' or 'Network error'
```

---

## 2. API CLIENT USAGE

### File: `lib/src/data/repositories/repositories.dart` (Lines 19-32)

#### How a Login Request Flows

```dart
Future<AuthResponse> register({
  required String fullName,
  required String email,
  required String phone,
  required String password,
  required String role,
}) async {
  final response = await _api.post<AuthResponse>(
    '/login',  // ← Endpoint path ONLY
    // Full URL becomes: baseUrl + '/login'
    // = 'http://backend.test/api' + '/login'
    // = 'http://backend.test/api/login'
    data: {
      'full_name': fullName,
      'email': email,
      'phone': phone,
      'password': password,
      'role': role,
    },
    fromJson: (json) => AuthResponse.fromJson(json as Map<String, dynamic>),
  );
  await _api.saveToken(response.token);
  return response;
}
```

**Request Lifecycle**:

```
1. User taps "Login" button
   ↓
2. AuthNotifier.login() called
   ↓
3. _repo.login(identifier, password) called
   ↓
4. _api.post<AuthResponse>('/login', data: {...}) called
   ↓
5. ApiClient.post() method (lines 142-157):
   ├─ dio.post('/login', data: data)
   │  (baseUrl prepended automatically by Dio)
   ├─ Full URL: 'http://backend.test/api/login'
   └─ Dio.post() called with this URL
   ↓
6. ApiInterceptor.onRequest() triggered:
   ├─ Logs: '→ POST /login'
   ├─ Adds Bearer token (if exists)
   └─ Calls handler.next(options)
   ↓
7. Network request sent:
   ├─ Attempt to resolve 'backend.test'
   │  ❌ FAILS HERE: "Failed host lookup: 'backend.test'"
   ├─ DioException thrown
   └─ Never reaches backend
   ↓
8. ApiInterceptor.onError() triggered:
   └─ Logs: '✗ null /login: Failed host lookup: backend.test'
   ↓
9. ApiClient.post() catch block (lines 154-156):
   └─ Throws ApiException("Failed host lookup", null)
   ↓
10. AuthNotifier.login() catch block (line 94):
    └─ state = state.copyWith(error: "Failed host lookup")
    ↓
11. LoginScreen watches authNotifierProvider:
    └─ Shows error message to user: "Failed host lookup"
```

---

## 3. REPOSITORY PROVIDERS

### File: `lib/src/data/providers/data_providers.dart` (Lines 105-108)

```dart
final authNotifierProvider =
    StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(ref.read(authRepositoryProvider));
});
```

**Dependency chain**:
```
LoginScreen
  ↓ watches
authNotifierProvider
  ↓ depends on
authRepositoryProvider (from repositories.dart)
  ↓ depends on
apiClientProvider (from api_client.dart)
  ↓ depends on
dioProvider (from api_client.dart)
  ↓ uses
kBaseUrl = 'http://backend.test/api'  ← ROOT PROBLEM
```

---

## 4. ANDROID CONFIGURATION

### File: `android/app/src/main/AndroidManifest.xml`

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET"/>
    <!-- ✅ Internet permission granted -->
    
    <application
        android:name="${applicationName}"
        android:networkSecurityConfig="@xml/network_security_config">
        <!-- ✅ References network security config -->
```

**This allows HTTP requests**, but doesn't fix the domain resolution issue.

---

### File: `android/app/src/main/res/xml/network_security_config.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <!-- ✅ Allows HTTP (not just HTTPS) -->
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </base-config>
</network-security-config>
```

**This is configured correctly**, but doesn't help if domain doesn't exist.

---

## 5. MAIN APP ENTRY

### File: `lib/main.dart`

```dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await EasyLocalization.ensureInitialized();
  // No API configuration here - uses api_client.dart defaults
  
  runApp(
    EasyLocalization(
      // ... localization setup ...
      child: const ProviderScope(
        child: EthioLoadApp(),
      ),
    ),
  );
}

class EthioLoadApp extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.read(appRouterProvider);
    final themes = ref.read(appThemeProvider);

    return MaterialApp.router(
      title: 'EthioLoad AI',
      routerConfig: router,
      // ...
    );
  }
}
```

**Flow**:
```
main()
  ↓
runApp(ProviderScope(...))
  ↓
First screen (SplashScreen or LoginScreen)
  ↓
User action triggers API call
  ↓
dioProvider accessed
  ↓
kBaseUrl evaluated to 'http://backend.test/api'
  ↓
❌ Network fails
```

---

## 6. PUBSPEC DEPENDENCIES

### File: `pubspec.yaml` (Line 22)

```yaml
dependencies:
  flutter:
    sdk: flutter
  
  # Networking
  dio: ^5.3.0  # ← HTTP client
  
  # State Management
  flutter_riverpod: ^2.4.0  # ← Dependency injection
```

**Version**: Dio 5.3.0
**Features**: 
- ✅ HTTP/HTTPS support
- ✅ Interceptors support
- ✅ Timeout configuration
- ❌ Cannot create domain names

---

## Complete Request Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ main.dart: main() → runApp(ProviderScope(...))             │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ lib/src/features/auth/login_screen.dart                    │
│ User enters email/password, taps LOGIN                      │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ data_providers.dart: authNotifierProvider.login()           │
│ AuthNotifier state changes to isLoading: true              │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ repositories.dart: AuthRepository.login()                   │
│ Calls _api.post('/login', ...)                              │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ api_client.dart: ApiClient.post()                           │
│ Calls dio.post(endpoint, data: data)                        │
│ (baseUrl prepended automatically)                           │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ api_client.dart: Dio baseUrl = kBaseUrl                     │
│ kBaseUrl getter returns 'http://backend.test/api'           │
│ Full URL: http://backend.test/api/login                     │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ api_client.dart: ApiInterceptor.onRequest()                 │
│ Logs: '→ POST /login'                                       │
│ Adds Authorization header                                   │
│ Calls handler.next(options)                                 │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ Dio Network Layer                                           │
│ Attempts to POST http://backend.test/api/login              │
│                                                              │
│ Step 1: DNS lookup for 'backend.test'                       │
│         ❌ FAILS: Domain not found                           │
│                                                              │
│ DioException thrown:                                        │
│   type: DioExceptionType.connectionTimeout                 │
│   message: "Failed host lookup: 'backend.test'"            │
│   requestOptions.path: '/login'                            │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ api_client.dart: ApiInterceptor.onError()                   │
│ Logs: '✗ null /login: Failed host lookup: backend.test'     │
│ Calls handler.next(error)                                   │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ api_client.dart: ApiClient.post() catch block               │
│ Throws: ApiException(                                       │
│   message: "Failed host lookup: 'backend.test'",            │
│   statusCode: null                                          │
│ )                                                            │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ data_providers.dart: AuthNotifier.login() catch block        │
│ state = state.copyWith(                                     │
│   isLoading: false,                                         │
│   error: "Failed host lookup: 'backend.test'"               │
│ )                                                            │
└─────────────────────┬───────────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────────┐
│ login_screen.dart: Consumer widget rebuilds                 │
│ Watches authNotifierProvider                                │
│ Sees error state                                            │
│ Shows SnackBar: "Failed host lookup: 'backend.test'"        │
│                                                              │
│ USER SEES: "Network error"                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## The Fix Applied

### Change This (api_client.dart, lines 12-16):

```dart
String get kBaseUrl {
  if (_kApiBaseUrlOverride.isNotEmpty) return _kApiBaseUrlOverride;
  if (kIsWeb) return 'http://backend.test/api';
  return 'http://backend.test/api';
}
```

### To This:

```dart
String get kBaseUrl {
  if (_kApiBaseUrlOverride.isNotEmpty) return _kApiBaseUrlOverride;
  if (kIsWeb) return 'http://localhost:5000/api';
  return 'http://localhost:5000/api';
}
```

### Or Better, Use This:

```dart
String get kBaseUrl {
  if (_kApiBaseUrlOverride.isNotEmpty) return _kApiBaseUrlOverride;
  
  const bool kIsProduction = bool.fromEnvironment('PRODUCTION', defaultValue: false);
  
  if (kIsProduction) {
    return 'https://api.ethioload.com/api';
  }
  
  // Development: Use localhost
  return 'http://localhost:5000/api';
}
```

---

## Summary

| Component | File | Issue | Fix |
|-----------|------|-------|-----|
| **Base URL** | `api_client.dart` line 15 | `backend.test` doesn't exist | Use `localhost:5000` |
| **Dio Provider** | `api_client.dart` line 61 | Uses hardcoded URL | Use corrected kBaseUrl |
| **Repository** | `repositories.dart` line 39 | Depends on wrong URL | No change needed (upstream) |
| **Interceptor** | `api_client.dart` line 29 | Works fine, logs the wrong URL | No change needed |
| **Permissions** | `AndroidManifest.xml` line 2 | Configured correctly | No change needed |
| **Network Config** | `network_security_config.xml` | Allows HTTP correctly | No change needed |


