# Phase 2.5: API Integration Code Patches

**Status**: Ready for Implementation  
**Date**: June 2, 2026  
**Est. Time**: 12 hours for full integration

---

## Quick Overview

Each screen has 3 steps:
1. **Replace mock data** with API provider watches
2. **Add loading/error states** using `.when(data:, loading:, error:)`
3. **Update callback handlers** to call APIs

---

## 1. HomeScreen Integration ✅ (COMPLETED)

**File**: `lib/src/features/home/home_screen.dart`

**Changes Made**:
- ✅ Connected `myFreightProvider` to load user's freight
- ✅ Replaced hardcoded stats with calculated values
- ✅ Updated Recent Activity to show real freight data
- ✅ Added loading/error states

**What's Working Now**:
```dart
final myFreightAsync = ref.watch(myFreightProvider);
// Calculates: total shipments, completed count, earnings, rating
// Shows real freight in activity feed
```

**Next Step**: Ensure `myFreightProvider` is defined in your providers file

---

## 2. FreightListScreen Integration 🔄 (IN PROGRESS)

**File**: `lib/src/features/shipper/freight_list_screen.dart`

**Step 1: Update Build Method Signature** (REQUIRED)
```dart
// Change from:
@override
Widget build(BuildContext context) {

// To:
@override
Widget build(BuildContext context, WidgetRef ref) {
  final freightListAsync = ref.watch(freightListProvider);
```

**Step 2: Add Loading/Error States**
```dart
freightListAsync.when(
  data: (freightListState) {
    final filteredFreight = freightListState.items ?? [];
    // Apply filters and display
  },
  loading: () => Center(child: CircularProgressIndicator()),
  error: (error, stack) => ErrorWidget(error),
);
```

**Step 3: Update _FreightCard to Accept Real Objects**
```dart
// Change from Map<String, String> to FreightRequest object
class _FreightCard extends StatelessWidget {
  final FreightRequest freight;  // Not Map
  final VoidCallback onTap;

  // Update access: freight.id instead of freight['id']
```

**Est. Time**: 2 hours

---

## 3. FreightDetailScreen Integration (SIMPLE)

**File**: `lib/src/features/shipper/freight_detail_screen.dart`

**Replace Build Content**:
```dart
@override
Widget build(BuildContext context, WidgetRef ref) {
  final freightAsync = ref.watch(singleFreightProvider(freightId));

  return freightAsync.when(
    data: (freight) => Scaffold(
      // Use freight object instead of freightDetail map
      body: SingleChildScrollView(
        child: Column(
          children: [
            // Build UI using freight.field instead of freight['field']
          ],
        ),
      ),
      bottomNavigationBar: Padding(
        padding: EdgeInsets.all(16),
        child: Row(
          children: [
            Expanded(
              child: OutlinedButton(
                onPressed: () => context.pop(),
                child: Text('Decline'),
              ),
            ),
            SizedBox(width: 12),
            Expanded(
              child: ElevatedButton(
                onPressed: () async {
                  try {
                    await ref
                        .read(freightRepositoryProvider)
                        .applyForFreight(freightId);
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Applied successfully!')),
                    );
                  } catch (e) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Error: $e')),
                    );
                  }
                },
                child: Text('Apply Now'),
              ),
            ),
          ],
        ),
      ),
    ),
    loading: () => Scaffold(
      appBar: AppBar(title: Text('Freight Details')),
      body: Center(child: CircularProgressIndicator()),
    ),
    error: (error, stack) => Scaffold(
      appBar: AppBar(title: Text('Freight Details')),
      body: Center(child: Text('Error: $error')),
    ),
  );
}
```

**Est. Time**: 1 hour

---

## 4. CreateFreightScreen Integration (MEDIUM)

**File**: `lib/src/features/shipper/create_freight_screen.dart`

**Replace _submitFreight Method**:
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

**Est. Time**: 2 hours

---

## 5. DriverDashboardScreen Integration (MEDIUM)

**File**: `lib/src/features/driver/driver_dashboard_screen.dart`

**Replace Build Method**:
```dart
@override
Widget build(BuildContext context, WidgetRef ref) {
  final authState = ref.watch(authNotifierProvider);
  final driver = authState.user;

  // Get available freight for driver
  final availableFreightAsync = ref.watch(availableFreightProvider);

  return Scaffold(
    appBar: AppBar(
      title: Text('Driver Dashboard'),
      elevation: 0,
      actions: [
        IconButton(
          icon: Icon(Icons.notifications),
          onPressed: () {},
        ),
      ],
    ),
    body: SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Status Card
          Container(
            color: Colors.blue[50],
            padding: EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Online Status'),
                        SizedBox(height: 4),
                        Row(
                          children: [
                            Container(
                              width: 12,
                              height: 12,
                              decoration: BoxDecoration(
                                color: Colors.green,
                                shape: BoxShape.circle,
                              ),
                            ),
                            SizedBox(width: 8),
                            Text('Available for Jobs'),
                          ],
                        ),
                      ],
                    ),
                    OutlinedButton(
                      onPressed: () async {
                        try {
                          await ref
                              .read(driverRepositoryProvider)
                              .updateStatus('offline');
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('Status updated')),
                          );
                        } catch (e) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text('Error: $e')),
                          );
                        }
                      },
                      child: Text('Go Offline'),
                    ),
                  ],
                ),
                SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: _StatTile(
                        label: 'Active Jobs',
                        value: '${driver?.activeJobCount ?? 0}',
                      ),
                    ),
                    SizedBox(width: 12),
                    Expanded(
                      child: _StatTile(
                        label: 'This Week',
                        value: '${driver?.completedThisWeek ?? 0}',
                      ),
                    ),
                    SizedBox(width: 12),
                    Expanded(
                      child: _StatTile(
                        label: 'Rating',
                        value: '${driver?.rating ?? 0.0}',
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),

          // Earnings Card
          Padding(
            padding: EdgeInsets.all(16),
            child: Container(
              padding: EdgeInsets.all(16),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [Colors.green[600]!, Colors.green[400]!],
                ),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Earnings',
                    style: TextStyle(color: Colors.white, fontSize: 13),
                  ),
                  SizedBox(height: 8),
                  Text(
                    '\$${driver?.totalEarningsThisWeek ?? 0.0}',
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  SizedBox(height: 8),
                  Text(
                    'This week • ${driver?.completedThisWeek ?? 0} completed jobs',
                    style: TextStyle(
                      color: Colors.white.withOpacity(0.9),
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Available Freight
          Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Available Freight',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                ),
                TextButton(
                  onPressed: () => context.go('/freight'),
                  child: Text('View All'),
                ),
              ],
            ),
          ),

          SizedBox(height: 8),

          // Available Freight List
          Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            child: availableFreightAsync.when(
              data: (freightList) {
                if (freightList.isEmpty) {
                  return Padding(
                    padding: EdgeInsets.all(32),
                    child: Center(
                      child: Text(
                        'No available freight at the moment',
                        style: TextStyle(color: Colors.grey),
                      ),
                    ),
                  );
                }
                return Column(
                  children: List.generate(
                    freightList.length,
                    (index) {
                      final freight = freightList[index];
                      return Padding(
                        padding: EdgeInsets.only(bottom: 12),
                        child: _DriverFreightCard(
                          freight: freight,
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
                          },
                        ),
                      );
                    },
                  ),
                );
              },
              loading: () => Center(
                child: CircularProgressIndicator(),
              ),
              error: (error, stack) => Center(
                child: Text('Error loading freight: $error'),
              ),
            ),
          ),

          SizedBox(height: 24),
        ],
      ),
    ),
  );
}
```

**Est. Time**: 2.5 hours

---

## 6. TrackingScreen Integration (ADVANCED)

**File**: `lib/src/features/shipper/tracking_screen.dart`

**Step 1: Add Socket.IO Listener in initState** (if StatefulWidget)
```dart
void initState() {
  super.initState();
  _setupTrackingSocket();
}

void _setupTrackingSocket() {
  final socket = ref.read(socketIOProvider);
  socket.on('driver_location_update_$freightId', (data) {
    setState(() {
      trackingData['currentLocation'] = data['location'];
      trackingData['speed'] = data['speed'];
      trackingData['progress'] = data['progress'];
      trackingData['eta'] = data['eta'];
    });
  });
}

void dispose() {
  final socket = ref.read(socketIOProvider);
  socket.off('driver_location_update_$freightId');
  super.dispose();
}
```

**Step 2: Load Initial Tracking Data**
```dart
@override
Widget build(BuildContext context, WidgetRef ref) {
  final trackingAsync = ref.watch(latestTrackingProvider(freightId));

  return trackingAsync.when(
    data: (tracking) => Scaffold(
      // Build UI with tracking data
      // Map automatically updates via Socket.IO listener
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
```

**Est. Time**: 2.5 hours

---

## 7. ProfileScreen Integration (SIMPLE)

**File**: `lib/src/features/profile/profile_screen.dart`

**Load User Data**:
```dart
@override
Widget build(BuildContext context, WidgetRef ref) {
  final authState = ref.watch(authNotifierProvider);
  final user = authState.user;

  // Use user object throughout instead of userProfile map
  return Scaffold(
    // Build UI with:
    // user.name, user.email, user.phone, user.role,
    // user.businessName, user.rating, user.reviewCount, etc.
  );
}
```

**Update Profile Handler**:
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

**Logout Handler**:
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

**Est. Time**: 1.5 hours

---

## Provider Setup Checklist

Before integrating screens, ensure these providers exist in `lib/src/providers/`:

- [ ] `authNotifierProvider` - User authentication state
- [ ] `myFreightProvider` - User's freight list
- [ ] `freightListProvider` - All available freight
- [ ] `singleFreightProvider(id)` - Single freight details
- [ ] `availableFreightProvider` - Driver available jobs
- [ ] `latestTrackingProvider(id)` - Current tracking data
- [ ] `freightRepositoryProvider` - API client
- [ ] `driverRepositoryProvider` - Driver API client
- [ ] `socketIOProvider` - Socket.IO client

**If any are missing**, define them following the pattern:
```dart
final myFreightProvider = FutureProvider<List<FreightRequest>>((ref) async {
  final repo = ref.watch(freightRepositoryProvider);
  return repo.getMyFreight();
});
```

---

## Testing After Integration

### Test Checklist
- [ ] HomeScreen loads and displays real stats
- [ ] FreightListScreen fetches and filters real data
- [ ] Search and filter chips work with real data
- [ ] FreightDetailScreen loads single freight correctly
- [ ] "Apply Now" button submits to API
- [ ] CreateFreightScreen form validates and submits
- [ ] DriverDashboardScreen shows available jobs
- [ ] "Accept Job" button works
- [ ] TrackingScreen loads initial data
- [ ] Real-time tracking updates via Socket.IO
- [ ] ProfileScreen loads and updates user data
- [ ] Error states display correctly
- [ ] Loading states show during API calls

---

## Common Issues & Fixes

### Issue 1: "Provider not found"
**Fix**: Add provider definitions to `lib/src/providers/data_providers.dart`

### Issue 2: "Type mismatch - expected FreightRequest, got Map"
**Fix**: Update widget to accept model objects, not Maps
```dart
// Before
final freight = mockFreight[index];  // Map
freight['id']  // Access map

// After
final freight = freightList[index];  // FreightRequest object
freight.id     // Access object property
```

### Issue 3: "Socket.IO events not firing"
**Fix**: Verify Socket.IO connection is active and events match backend
```dart
// Debug: Log all incoming events
socket.on('driver_location_update_$freightId', (data) {
  print('Received tracking update: $data');
  setState(() { /* update UI */ });
});
```

### Issue 4: "Loading state never completes"
**Fix**: Check backend is responding, not stuck loading
```dart
loading: () => Scaffold(
  appBar: AppBar(title: Text('Loading')),
  body: Center(
    child: Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        CircularProgressIndicator(),
        SizedBox(height: 16),
        Text('If this takes too long, backend may be offline'),
      ],
    ),
  ),
),
```

---

## Success Criteria

After completing Phase 2.5, all screens should:

✅ Load real data from API  
✅ Display loading indicators  
✅ Show error messages on failures  
✅ Allow user actions (apply, accept, submit, edit)  
✅ Update UI based on API responses  
✅ Support real-time updates (Socket.IO)  
✅ No hardcoded mock data visible to user  

---

## Phase 2.5 Completion Timeline

| Task | Time | Status |
|------|------|--------|
| HomeScreen Integration | 1h | ✅ Done |
| FreightListScreen | 2h | 🔄 In Progress |
| FreightDetailScreen | 1h | ⏳ Ready |
| CreateFreightScreen | 2h | ⏳ Ready |
| DriverDashboardScreen | 2.5h | ⏳ Ready |
| TrackingScreen | 2.5h | ⏳ Ready |
| ProfileScreen | 1.5h | ⏳ Ready |
| Testing & Debugging | 2h | ⏳ Ready |
| **Total** | **12h** | **On Track** |

---

**Generated**: June 2, 2026 | Ready for Implementation
