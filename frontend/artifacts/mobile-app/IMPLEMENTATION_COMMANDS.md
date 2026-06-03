# Phase 2.5 Implementation Commands

**Quick Reference** for completing the remaining API integrations

---

## Status Summary

✅ **COMPLETED (3/7)**:
- HomeScreen - stats, recent activity
- FreightListScreen - freight list, search, filters
- FreightDetailScreen - freight details, apply handler

🟡 **REMAINING (4/7)**:
- CreateFreightScreen (2h estimated)
- DriverDashboardScreen (2.5h estimated)
- TrackingScreen (2.5h estimated)
- ProfileScreen (1.5h estimated)

---

## 4. CreateFreightScreen Integration

**File**: `lib/src/features/shipper/create_freight_screen.dart`

**Key Change**: Replace _submitFreight() method

```dart
void _submitFreight() async {
  // Validate form
  if (pickupLocation == null ||
      deliveryLocation == null ||
      cargoType == null ||
      weight == null ||
      volume == null ||
      budget == null ||
      deadline == null) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Please fill in all required fields'),
        backgroundColor: Colors.red,
      ),
    );
    return;
  }

  try {
    // Show loading dialog
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text('Creating freight request...'),
          ],
        ),
      ),
    );

    // Create freight request object
    final request = FreightCreateRequest(
      pickupLocation: pickupLocation!,
      deliveryLocation: deliveryLocation!,
      cargo: cargoType!,
      weight: double.parse(weight!),
      volume: double.parse(volume!),
      budget: double.parse(budget!),
      deadline: DateTime.parse('$deadline 23:59:00'),
      description: description,
    );

    // Submit to API
    await ref.read(freightRepositoryProvider).createFreight(request);

    // Close loading dialog
    if (mounted) Navigator.pop(context);

    // Show success
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Freight request created successfully!'),
        backgroundColor: Colors.green,
      ),
    );

    // Navigate home after delay
    await Future.delayed(Duration(seconds: 1));
    if (mounted) context.go('/home');
  } catch (e) {
    // Close loading dialog
    if (mounted) Navigator.pop(context);

    // Show error
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Error: ${e.toString()}'),
        backgroundColor: Colors.red,
      ),
    );
  }
}
```

---

## 5. DriverDashboardScreen Integration

**File**: `lib/src/features/driver/driver_dashboard_screen.dart`

**Key Changes**:
1. Add to build method:
```dart
@override
Widget build(BuildContext context, WidgetRef ref) {
  final authState = ref.watch(authNotifierProvider);
  final driver = authState.user;
  final availableFreightAsync = ref.watch(availableFreightProvider);

  // Use availableFreightAsync.when(data:, loading:, error:) to display freight list
}
```

2. Replace hardcoded stats with actual data:
```dart
_StatTile(
  label: 'Active Jobs',
  value: '${driver?.activeJobCount ?? 0}',
),
_StatTile(
  label: 'This Week',
  value: '${driver?.completedThisWeek ?? 0}',
),
_StatTile(
  label: 'Rating',
  value: '${driver?.rating ?? 4.8}',
),
```

3. Replace earnings card with real data:
```dart
Text(
  '\$${driver?.totalEarningsThisWeek ?? 0.0}',
  style: TextStyle(
    color: Colors.white,
    fontSize: 28,
    fontWeight: FontWeight.bold,
  ),
),
```

4. Update Accept Job button:
```dart
onAccept: () async {
  try {
    await ref
        .read(driverRepositoryProvider)
        .acceptFreight(freight.id);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('You accepted this job!'),
        backgroundColor: Colors.green,
      ),
    );
    ref.refresh(availableFreightProvider);
  } catch (e) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Error: $e')),
    );
  }
}
```

---

## 6. TrackingScreen Integration (Most Complex)

**File**: `lib/src/features/shipper/tracking_screen.dart`

**Key Changes**:
1. Convert to ConsumerStatefulWidget:
```dart
class TrackingScreen extends ConsumerStatefulWidget {
  final int freightId;
  const TrackingScreen({required this.freightId, Key? key}) : super(key: key);
  
  @override
  ConsumerState<TrackingScreen> createState() => _TrackingScreenState();
}

class _TrackingScreenState extends ConsumerState<TrackingScreen> {
  late int freightId;

  @override
  void initState() {
    super.initState();
    freightId = widget.freightId;
    _setupSocketListener();
  }

  void _setupSocketListener() {
    final socket = ref.read(socketIOProvider);
    socket.on('driver_location_update_$freightId', (data) {
      setState(() {
        // Update UI with real-time location
      });
    });
  }

  @override
  void dispose() {
    final socket = ref.read(socketIOProvider);
    socket.off('driver_location_update_$freightId');
    super.dispose();
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trackingAsync = ref.watch(latestTrackingProvider(freightId));

    return trackingAsync.when(
      data: (tracking) => Scaffold(
        // Build UI with tracking.currentLocation, tracking.speed, tracking.distance, tracking.eta
      ),
      loading: () => Scaffold(
        appBar: AppBar(title: Text('Track Freight')),
        body: Center(child: CircularProgressIndicator()),
      ),
      error: (error, stack) => Scaffold(
        appBar: AppBar(title: Text('Track Freight')),
        body: Center(child: Text('Error: $error')),
      ),
    );
  }
}
```

2. In the UI, access tracking fields:
```dart
Text('${tracking.progress}%'),  // Progress percentage
Text('${tracking.speed} km/h'),  // Current speed
Text('${tracking.distance} km'),  // Distance traveled
Text('ETA: ${tracking.eta}'),     // Estimated arrival
```

---

## 7. ProfileScreen Integration

**File**: `lib/src/features/profile/profile_screen.dart`

**Key Changes**:
1. Load user from auth provider:
```dart
@override
Widget build(BuildContext context, WidgetRef ref) {
  final authState = ref.watch(authNotifierProvider);
  final user = authState.user;

  return Scaffold(
    // Use user.name, user.email, user.phone, user.rating, etc.
  );
}
```

2. Update profile handler:
```dart
void _editProfile(String name, String phone, String address) async {
  try {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => Center(child: CircularProgressIndicator()),
    );

    await ref.read(authNotifierProvider.notifier).updateProfile(
      name: name,
      phone: phone,
      address: address,
    );

    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Profile updated successfully!')),
    );
  } catch (e) {
    Navigator.pop(context);
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Error: $e'), backgroundColor: Colors.red),
    );
  }
}
```

3. Logout handler:
```dart
void _logout() async {
  try {
    await ref.read(authNotifierProvider.notifier).logout();
    context.go('/login');
  } catch (e) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Error: $e')),
    );
  }
}
```

4. Replace profile fields in UI:
```dart
// Instead of userProfile['name'], use:
user?.name ?? 'User',
user?.email ?? '',
user?.phone ?? '',
user?.businessName ?? '',
user?.rating ?? 0.0,
user?.reviewCount ?? 0,
```

---

## Provider Checklist

Before running screens, verify these exist in `data_providers.dart`:

```dart
// Auth
final authNotifierProvider = StateNotifierProvider<AuthNotifier, AuthState>(...);

// Freight List
final freightListProvider = FutureProvider<FreightListResponse>((ref) async {
  return ref.watch(freightRepositoryProvider).listFreight();
});

// Single Freight
final singleFreightProvider = FutureProvider.family<FreightRequest, int>((ref, id) async {
  return ref.watch(freightRepositoryProvider).getFreight(id);
});

// Available Freight (for drivers)
final availableFreightProvider = FutureProvider<List<FreightRequest>>((ref) async {
  return ref.watch(freightRepositoryProvider).listFreight(status: 'open');
});

// Tracking
final latestTrackingProvider = FutureProvider.family<TrackingData, int>((ref, id) async {
  return ref.watch(trackingRepositoryProvider).getLatestTracking(id);
});

// Repositories
final freightRepositoryProvider = Provider((ref) => FreightRepository(ref.read(apiClientProvider)));
final driverRepositoryProvider = Provider((ref) => DriverRepository(ref.read(apiClientProvider)));
final trackingRepositoryProvider = Provider((ref) => TrackingRepository(ref.read(apiClientProvider)));

// Socket.IO
final socketIOProvider = Provider((ref) => SocketIOClient());

// API Client
final apiClientProvider = Provider((ref) => ApiClient());
```

---

## Quick Copy-Paste Blocks

### Standard Async Loading Pattern
```dart
final dataAsync = ref.watch(someProvider);

return dataAsync.when(
  data: (data) => buildUI(data),
  loading: () => Center(child: CircularProgressIndicator()),
  error: (error, stack) => Center(child: Text('Error: $error')),
);
```

### Standard API Call with Dialog
```dart
try {
  showDialog(
    context: context,
    barrierDismissible: false,
    builder: (context) => Center(child: CircularProgressIndicator()),
  );

  // Make API call here
  await ref.read(someRepositoryProvider).someMethod();

  if (mounted) Navigator.pop(context);
  
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text('Success!'), backgroundColor: Colors.green),
  );
} catch (e) {
  if (mounted) Navigator.pop(context);
  
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text('Error: $e'), backgroundColor: Colors.red),
  );
}
```

### Standard Navigation
```dart
// Navigate to detail
context.go('/freight/$id');

// Navigate home
context.go('/home');

// Navigate login
context.go('/login');

// Pop back
context.pop();
```

---

## Testing Each Screen

### CreateFreightScreen
1. Fill all form fields
2. Click "Review" → Check summary
3. Click "Create" → Check loading dialog
4. Verify success snackbar + navigation home

### DriverDashboardScreen  
1. Check stats load (active jobs, completed, rating)
2. Check earnings display
3. Check available freight loads
4. Click "Accept Job" → Check refresh + snackbar

### TrackingScreen
1. Click freight with tracking status
2. Check initial location loads
3. Wait for Socket.IO updates
4. Check progress bar updates
5. Check ETA updates

### ProfileScreen
1. Check user data loads
2. Click edit → change name/phone
3. Click save → Check update
4. Click logout → Check navigation to /login

---

## Common Patterns Summary

| Pattern | Example |
|---------|---------|
| Load data | `final data = ref.watch(provider);` |
| Handle async | `data.when(data: (), loading: (), error: ())` |
| Call API | `await ref.read(repo).method();` |
| Show dialog | `showDialog(context: context, ...)` |
| Show snackbar | `ScaffoldMessenger.of(context).showSnackBar(...)` |
| Navigate | `context.go('/route');` |
| Refresh data | `ref.refresh(provider);` |
| Access params | `widget.freight Id` (from constructor) |
| Update state | `setState(() { /* change */ });` |

---

## Success Checklist

- [ ] CreateFreightScreen form submits to API
- [ ] DriverDashboardScreen shows available freight
- [ ] DriverDashboardScreen accept job works
- [ ] TrackingScreen loads and updates in real-time
- [ ] ProfileScreen loads and updates user data
- [ ] All 7 screens working with real APIs
- [ ] No mock data visible in UI
- [ ] All loading states working
- [ ] All error states working
- [ ] All navigation working

---

**Ready to implement remaining 4 screens?** → Follow the patterns above!
