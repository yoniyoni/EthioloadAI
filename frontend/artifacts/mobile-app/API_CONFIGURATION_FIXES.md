# API Configuration - Practical Fixes

## Quick Diagnosis

**Your app fails because**: It's trying to reach `http://backend.test/api` which doesn't exist.

**The fix depends on**: Where your backend actually is running.

---

## OPTION A: Android Emulator + Local Backend

**Use when**: You have a backend running on your laptop at `http://localhost:5000`

### Step 1: Launch App with Correct API URL

```bash
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:5000/api
```

**Why 10.0.2.2?** - Android emulator's special IP to access the host machine

**For Pixel emulator specifically**:
```bash
# Make sure emulator is running
flutter emulators --launch Pixel_5_API_34

# Then run with the URL
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:5000/api
```

### Step 2: Verify Backend is Running

Before launching the app, check:

```bash
# Test if backend is reachable
curl http://localhost:5000/api/health

# Should return 200 OK or similar
```

### Step 3: Check Logs

When you run the app, you'll see in console:
```
→ POST /login
← 200 http://10.0.2.2:5000/api/login
```

If you see different status codes:
- `500` - Backend error
- `404` - Endpoint doesn't exist
- Connection timeout - Backend not running

---

## OPTION B: Physical Android Device + Local Backend (WiFi)

**Use when**: You have a physical Android phone and backend on your laptop

### Step 1: Find Your Laptop's IP Address

```powershell
# Windows PowerShell
ipconfig

# Look for output like:
# IPv4 Address. . . . . . . . . . : 192.168.1.50
```

Or on Mac:
```bash
ifconfig | grep "inet " | grep -v 127.0.0.1
# Output: inet 192.168.1.50 netmask 0xffffff00 broadcast 192.168.1.255
```

### Step 2: Ensure Phone & Laptop on Same WiFi

```bash
# On your phone, connect to the same WiFi as your laptop
# (Settings → WiFi → Select your network)
```

### Step 3: Launch App with Laptop IP

```bash
# Replace 192.168.1.50 with YOUR actual IP address
flutter run --dart-define=API_BASE_URL=http://192.168.1.50:5000/api
```

### Step 4: Verify Connection

```bash
# From your laptop, test if backend responds
curl http://192.168.1.50:5000/api/health

# Should work if backend is running
```

---

## OPTION C: iOS Simulator + Local Backend

**Use when**: You're on Mac with iOS simulator

### Step 1: Launch iOS Simulator

```bash
# Start simulator
open -a Simulator

# Wait for it to boot
```

### Step 2: iOS Special IP

For iOS simulator, use `127.0.0.1` directly:

```bash
flutter run -d iPhone -d web --dart-define=API_BASE_URL=http://127.0.0.1:5000/api
```

**OR** for iPhone 15 Pro simulator:

```bash
flutter run --dart-define=API_BASE_URL=http://127.0.0.1:5000/api
```

### Step 3: Test

```bash
curl http://127.0.0.1:5000/api/health
```

---

## OPTION D: Web Development

**Use when**: Running `flutter run -d web`

### Step 1: Launch Web

```bash
flutter run -d web --dart-define=API_BASE_URL=http://localhost:5000/api
```

Browser opens at: `http://localhost:8080`

### Step 2: Backend Must Support CORS

If you get CORS errors, your backend needs:

```python
# FastAPI example
from fastapi.middleware.cors import CORSMiddleware

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

### Step 3: Test

```bash
curl -i http://localhost:5000/api/health
```

Should include:
```
access-control-allow-origin: http://localhost:8080
```

---

## OPTION E: Permanent Code Fix

**Use when**: You want the code to automatically detect and use correct URL

### Step 1: Update api_client.dart

**File**: `lib/src/data/api/api_client.dart`

Replace the `kBaseUrl` getter (lines 12-16):

```dart
String get kBaseUrl {
  // 1. Command-line override has highest priority
  if (_kApiBaseUrlOverride.isNotEmpty) {
    return _kApiBaseUrlOverride;
  }
  
  // 2. Environment-based defaults
  const bool isProduction = bool.fromEnvironment('PRODUCTION', defaultValue: false);
  
  if (isProduction) {
    // Production: Your actual backend
    return 'https://api.ethioload.com/api';
  }
  
  // 3. Development: Smart platform detection
  if (kIsWeb) {
    // Web: Can use relative URL (same origin)
    return '/api';
  }
  
  // 4. Mobile: Use localhost
  // (But this won't work for Android emulator without the special IP)
  return 'http://localhost:5000/api';
}
```

### Step 2: Create .vscode/launch.json (VS Code Users)

**Create file**: `.vscode/launch.json`

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "name": "Flutter - Android Emulator (Local Backend)",
            "type": "dart",
            "request": "launch",
            "args": ["--dart-define=API_BASE_URL=http://10.0.2.2:5000/api"],
            "deviceId": "emulator"
        },
        {
            "name": "Flutter - iOS Simulator (Local Backend)",
            "type": "dart",
            "request": "launch",
            "args": ["--dart-define=API_BASE_URL=http://127.0.0.1:5000/api"],
            "deviceId": "iphone"
        },
        {
            "name": "Flutter - Web (Local Backend)",
            "type": "dart",
            "request": "launch",
            "args": ["-d", "web", "--dart-define=API_BASE_URL=http://localhost:5000/api"]
        }
    ]
}
```

Now you can debug with F5 and select the configuration!

### Step 3: Create launch configuration for Android Studio

**File**: Edit → Run Configurations

1. Click `+` → Flutter
2. Name: "Local Backend"
3. Additional Arguments: `--dart-define=API_BASE_URL=http://10.0.2.2:5000/api`
4. Click "Apply"

---

## OPTION F: Environment-Based Configuration

**Use when**: You want dev/staging/prod environments

### Step 1: Create Configuration File

**Create file**: `lib/config/api_config.dart`

```dart
const String kBackendHost = String.fromEnvironment(
  'BACKEND_HOST',
  defaultValue: 'localhost',
);

const String kBackendPort = String.fromEnvironment(
  'BACKEND_PORT',
  defaultValue: '5000',
);

const String kBackendScheme = String.fromEnvironment(
  'BACKEND_SCHEME',
  defaultValue: 'http',
);

String get apiBaseUrl {
  final port = kBackendPort.isEmpty ? '' : ':$kBackendPort';
  return '$kBackendScheme://$kBackendHost$port/api';
}
```

### Step 2: Use in api_client.dart

```dart
import '../config/api_config.dart';

String get kBaseUrl => apiBaseUrl;
```

### Step 3: Launch with Parameters

```bash
# Development
flutter run --dart-define=BACKEND_HOST=localhost --dart-define=BACKEND_PORT=5000

# Staging
flutter run --dart-define=BACKEND_HOST=staging-api.example.com --dart-define=BACKEND_SCHEME=https

# Production
flutter build apk --dart-define=BACKEND_HOST=api.ethioload.com --dart-define=BACKEND_SCHEME=https
```

---

## Troubleshooting

### "Connection refused"

```
❌ Error: Connection refused: 'backend.test'
```

**Causes**:
1. Backend not running
2. Backend on wrong port
3. Wrong IP address

**Fix**:
```bash
# Check if backend is running
curl http://localhost:5000/api/health

# If it works, try Android emulator IP:
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:5000/api
```

---

### "Failed host lookup"

```
❌ Error: Failed host lookup: 'backend.test'
```

**Causes**:
1. `backend.test` domain doesn't exist globally
2. No `/etc/hosts` entry on your machine
3. Using wrong URL entirely

**Fix**:
1. Stop using `backend.test`
2. Use actual localhost or IP:
   ```bash
   flutter run --dart-define=API_BASE_URL=http://127.0.0.1:5000/api
   ```

---

### "Network timeout"

```
❌ Error: Connection timed out after 30000ms
```

**Causes**:
1. Backend running but very slow
2. Firewall blocking connection
3. Backend not responding on that port

**Fix**:
```bash
# Test backend manually
curl -v http://localhost:5000/api/health

# If it hangs for 30 seconds, backend is unreachable
# If it returns quickly, Flutter app issue
```

---

### "Response status 401 or 403"

```
✗ 401 /login
```

**Cause**: Credentials wrong, not network issue

**Fix**: 
- Check if you're sending correct email/password
- Check if backend is expecting different format

---

### Android Emulator Specific Issues

**Problem**: Android emulator says `backend.test` doesn't exist

**Solution**: Use `10.0.2.2` instead of `localhost`

```bash
# WRONG for Android emulator:
flutter run --dart-define=API_BASE_URL=http://localhost:5000/api

# CORRECT for Android emulator:
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:5000/api
```

---

## Verification Checklist

After making changes, verify:

- [ ] Backend is running (`curl http://localhost:5000/api/health` works)
- [ ] App launches without crashes
- [ ] Console shows connection attempt to correct URL
- [ ] Logs show: `→ POST /login` not timeout
- [ ] Login screen appears (even if login fails, network is working)
- [ ] You can type email/password without app crashing
- [ ] Click login button - check console for API response
- [ ] If you see response, network is working!

---

## Quick Test

### Fastest Way to Verify Network Works

1. Launch the app with correct URL:
   ```bash
   flutter run --dart-define=API_BASE_URL=http://10.0.2.2:5000/api
   ```

2. App loads

3. Press `L` in console to open DevTools

4. Go to Inspector tab

5. Find any text - if text renders, network isn't crashing the app

6. Tap Login button

7. Check console - you should see:
   ```
   → POST /login
   ← XXX /login
   ```

If you see this, **network is working**! The failure (if any) is backend-specific, not connectivity.

---

## Still Not Working?

### Check These in Order

1. **Is backend running?**
   ```bash
   curl http://localhost:5000/api/health
   ```

2. **Can you reach it from same machine?**
   ```bash
   curl http://127.0.0.1:5000/api/health
   ```

3. **Is it on correct port?**
   ```bash
   lsof -i :5000  # Mac/Linux - what's listening on port 5000?
   netstat -ano | findstr :5000  # Windows
   ```

4. **Is firewall blocking?**
   - Windows Firewall settings
   - Mac Security preferences
   - Router port forwarding

5. **Is your phone/emulator on same network?**
   ```bash
   # Phone WiFi settings - same network as laptop?
   ```

If all checks pass and it still doesn't work, the issue is in your backend API code, not Flutter networking.


