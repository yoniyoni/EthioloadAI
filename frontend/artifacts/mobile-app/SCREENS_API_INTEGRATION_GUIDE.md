# Screens API Integration Guide

**Purpose**: Quick reference for connecting each screen to backend APIs  
**Phase**: 2.5 (API Integration)  
**Estimated Time**: 8-12 hours

---

## 1. HomeScreen API Integration

### Current State
```dart
// Mock data - hardcoded in build()
final user = authNotifierProvider; // Already using provider
final stats = {
  'totalShipments': 24,
  'completed': 22,
  'rating': 4.8,
  'earnings': '$2500'
};
```

### Integration Steps
1. **User Data** - Already reading from `authNotifierProvider`
   - Current implementation: ✅ Ready
   - No changes needed - use existing `ref.watch(authNotifierProvider)`

2. **Recent Shipments** - Add new provider
   ```dart
   // In data_providers.dart
   final myFreightProvider = FutureProvider<List<FreightRequest>>((ref) async {
     final repo = ref.watch(freightRepositoryProvider);
     return repo.getMyFreight();
   });
   
   // In home_screen.dart
   final recentFreight = ref.watch(myFreightProvider);
   ```

3. **Statistics** - Calculate from API data
   ```dart
   final shipments = ref.watch(myFreightProvider);
   final stats = {
     'totalShipments': shipments.value?.length ?? 0,
     'completed': shipments.value?.where((f) => f.status == 'completed').length ?? 0,
     'earnings': calculateEarnings(shipments.value),
     'rating': currentUser.rating,
   };
   ```

### Mock Data to Replace
```dart
// Replace these lines (around line 150-155):
mock_stats = {
  'activeJobs': 2,
  'completedThisWeek': 5,
  'earnings': '$1,250.50',
  'rating': 4.9,
  'status': 'online',
};
```

### API Calls Needed
- `GET /api/users/me` (already in authNotifierProvider)
- `GET /api/freight/my-freight` (needs myFreightProvider)

### Loading/Error States
```dart
// Add to HomeScreen
return recentFreight.when(
  data: (freight) => _buildContent(freight),
  loading: () => _buildLoadingState(),
  error: (error, stack) => _buildErrorState(error),
);
```

---

## 2. FreightListScreen API Integration

### Current State
```dart
final mockFreight = [
  { 'id': 1, 'route': '...', 'cargo': '...', ... }
];
```

### Integration Steps
1. **Replace Mock with API Provider**
   ```dart
   // In data_providers.dart
   final freightListProvider = StateNotifierProvider<FreightListNotifier, FreightListState>((ref) {
     return FreightListNotifier(ref);
   });
   
   // In FreightListScreen
   final freightList = ref.watch(freightListProvider);
   var filteredFreight = _applyFilters(freightList.items);
   ```

2. **Keep Search/Filter Logic**
   - Filtering already works locally on mock data
   - Apply same filtering to API results

3. **Add Pagination** (optional for Phase 2)
   ```dart
   // FreightListNotifier already has pagination structure
   // Just enable scrolling listener:
   if (scrollController.position.extentAfter < 500) {
     ref.read(freightListProvider.notifier).loadMore();
   }
   ```

### API Calls Needed
- `GET /api/freight?status=open&page=1&limit=20`
- `GET /api/freight?search=...` (for search filter)

### Code Changes Required
```dart
// Before:
var filteredFreight = mockFreight.where((f) {
  final matchesFilter = selectedFilter == 'all' || f['status'] == selectedFilter;
  final matchesSearch = searchController.text.isEmpty || ...;
  return matchesFilter && matchesSearch;
}).toList();

// After:
var filteredFreight = (freightList.items ?? []).where((f) {
  final matchesFilter = selectedFilter == 'all' || f.status == selectedFilter;
  final matchesSearch = searchController.text.isEmpty ||
    f.route.toLowerCase().contains(searchController.text.toLowerCase()) ||
    f.cargo.toLowerCase().contains(searchController.text.toLowerCase());
  return matchesFilter && matchesSearch;
}).toList();
```

### Loading/Error Handling
```dart
// Wrap list in:
freightList.isLoading
    ? Center(child: CircularProgressIndicator())
    : filteredFreight.isEmpty
        ? _buildEmptyState()  // Already implemented
        : _buildFreightList(filteredFreight);

if (freightList.error != null) {
  ScaffoldMessenger.of(context).showSnackBar(
    SnackBar(content: Text(freightList.error!), backgroundColor: Colors.red)
  );
}
```

---

## 3. FreightDetailScreen API Integration

### Current State
```dart
final freightDetail = {
  'id': freightId,
  'route': 'Addis Ababa → Dire Dawa',
  ...
};
```

### Integration Steps
```dart
// In data_providers.dart - ALREADY EXISTS as singleFreightProvider
// Just need to use it:

final freight = ref.watch(singleFreightProvider(freightId));

return freight.when(
  data: (freightData) => _buildContent(freightData),
  loading: () => Scaffold(
    appBar: AppBar(title: Text('Freight Details')),
    body: Center(child: CircularProgressIndicator()),
  ),
  error: (error, stack) => Scaffold(
    appBar: AppBar(title: Text('Freight Details')),
    body: Center(child: Text('Error: $error')),
  ),
);
```

### API Calls Needed
- `GET /api/freight/{id}`

### Apply Now Button Integration
```dart
// In bottom button:
ElevatedButton(
  onPressed: () async {
    try {
      await ref.read(freightRepositoryProvider)
        .applyForFreight(freightId, currentUserId);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Applied successfully!'))
      );
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Error: $e'), backgroundColor: Colors.red)
      );
    }
  },
  child: Text('Apply Now'),
),
```

---

## 4. CreateFreightScreen API Integration

### Current State
```dart
void _submitFreight() {
  // Currently just navigates to home
  context.go('/home');
}
```

### Integration Steps
1. **Gather Form Data**
   ```dart
   final formData = FreightRequest(
     pickupLocation: pickupLocation!,
     deliveryLocation: deliveryLocation!,
     cargoType: cargoType!,
     weight: double.parse(weight!),
     volume: double.parse(volume!),
     budget: double.parse(budget!),
     deadline: DateTime.parse(deadline!),
     description: description,
   );
   ```

2. **Validate Before Submit**
   ```dart
   bool _validateForm() {
     return pickupLocation != null &&
            deliveryLocation != null &&
            cargoType != null &&
            weight != null &&
            volume != null &&
            budget != null &&
            deadline != null;
   }
   ```

3. **Submit to API**
   ```dart
   void _submitFreight() async {
     if (!_validateForm()) {
       ScaffoldMessenger.of(context).showSnackBar(
         SnackBar(content: Text('Please fill all fields'))
       );
       return;
     }

     try {
       showDialog(
         context: context,
         builder: (context) => Center(child: CircularProgressIndicator()),
         barrierDismissible: false,
       );

       await ref.read(freightRepositoryProvider).createFreight(formData);

       Navigator.pop(context); // Close loading dialog
       ScaffoldMessenger.of(context).showSnackBar(
         SnackBar(
           content: Text('Freight created successfully!'),
           backgroundColor: Colors.green,
         )
       );

       Future.delayed(Duration(seconds: 1), () {
         context.go('/home');
       });
     } catch (e) {
       Navigator.pop(context); // Close loading dialog
       ScaffoldMessenger.of(context).showSnackBar(
         SnackBar(
           content: Text('Error: $e'),
           backgroundColor: Colors.red,
         )
       );
     }
   }
   ```

### API Calls Needed
- `POST /api/freight` (create)
- `GET /api/recommendations/price` (for AI price suggestion - optional)

---

## 5. DriverDashboardScreen API Integration

### Current State
```dart
final driverStats = {
  'activeJobs': 2,
  'completedThisWeek': 5,
  'earnings': '$1,250.50',
  'rating': 4.9,
  'status': 'online',
};

final mockAvailableFreight = [...];
```

### Integration Steps
1. **Load Driver Stats**
   ```dart
   final currentUser = ref.watch(authNotifierProvider);
   final driverStats = {
     'activeJobs': currentUser.activeJobCount,
     'completedThisWeek': currentUser.completedThisWeek,
     'earnings': currentUser.totalEarningsThisWeek,
     'rating': currentUser.rating,
     'status': currentUser.onlineStatus,
   };
   ```

2. **Load Available Freight**
   ```dart
   // In data_providers.dart
   final availableFreightProvider = FutureProvider<List<FreightRequest>>((ref) async {
     final repo = ref.watch(freightRepositoryProvider);
     final driverLoc = ref.watch(locationProvider); // Get driver current location
     return repo.getAvailableFreight(
       driverLocation: driverLoc,
       driverCapacity: currentUserCapacity,
     );
   });
   ```

3. **Accept Job Handler**
   ```dart
   void _acceptJob(String freightId) async {
     try {
       await ref.read(driverRepositoryProvider).acceptFreight(freightId);
       ScaffoldMessenger.of(context).showSnackBar(
         SnackBar(
           content: Text('You accepted this job!'),
           backgroundColor: Colors.green,
         )
       );
       // Refresh available freight list
       ref.refresh(availableFreightProvider);
     } catch (e) {
       ScaffoldMessenger.of(context).showSnackBar(
         SnackBar(content: Text('Error: $e'), backgroundColor: Colors.red)
       );
     }
   }
   ```

### API Calls Needed
- `GET /api/drivers/me` (get driver stats)
- `GET /api/freight/available?driverLocation=...` (get available freight)
- `POST /api/freight/{id}/apply` (accept job)
- `PUT /api/drivers/me/status` (go offline)

---

## 6. TrackingScreen API Integration

### Current State
```dart
final trackingData = {
  'id': freightId,
  'status': 'In Transit',
  'currentLocation': 'Bishoftu (78 km from pickup)',
  ...
};
```

### Integration Steps
1. **Real-time Location Updates via Socket.IO**
   ```dart
   // In initState or on mount
   void _setupTrackingListener() {
     final socket = ref.read(socketIOProvider);
     socket.on('driver_location_update', (data) {
       setState(() {
         trackingData['currentLocation'] = data['location'];
         trackingData['speed'] = data['speed'];
         trackingData['progress'] = data['progress'];
       });
     });
   }
   ```

2. **Initial Load**
   ```dart
   final trackingData = ref.watch(latestTrackingProvider(freightId));
   final trackingHistory = ref.watch(trackingHistoryProvider(freightId));

   return trackingData.when(
     data: (data) => _buildTrackingUI(data),
     loading: () => _buildLoadingState(),
     error: (error, stack) => _buildErrorState(error),
   );
   ```

3. **Map Integration** (Future)
   ```dart
   // Replace map placeholder with real map:
   GoogleMap(
     initialCameraPosition: CameraPosition(
       target: LatLng(9.0320, 38.7469), // Addis Ababa
       zoom: 12,
     ),
     markers: {
       Marker(
         markerId: MarkerId('current'),
         position: LatLng(currentLat, currentLng),
         icon: BitmapDescriptor.defaultMarkerWithHue(BitmapDescriptor.hueGreen),
       ),
     },
   ),
   ```

### API Calls Needed
- `GET /api/tracking/{freightId}/latest` (initial load)
- `GET /api/tracking/{freightId}/history` (route history)
- WebSocket: `driver_location_update` events

---

## 7. ProfileScreen API Integration

### Current State
```dart
final userProfile = {
  'name': 'Ahmed Hassan',
  'email': 'ahmed.hassan@example.com',
  ...
};
```

### Integration Steps
1. **Load User Data**
   ```dart
   final user = ref.watch(authNotifierProvider);
   final userProfile = {
     'name': user.name,
     'email': user.email,
     'phone': user.phone,
     'role': user.role,
     'businessName': user.businessName,
     'rating': user.rating,
     'reviews': user.reviewCount,
     'memberSince': user.createdAt.year.toString(),
     ...
   };
   ```

2. **Edit Profile Handler**
   ```dart
   void _editProfile(String name, String phone, String address) async {
     try {
       await ref.read(authNotifierProvider.notifier).updateProfile(
         name: name,
         phone: phone,
         address: address,
       );
       ScaffoldMessenger.of(context).showSnackBar(
         SnackBar(content: Text('Profile updated successfully!'))
       );
     } catch (e) {
       ScaffoldMessenger.of(context).showSnackBar(
         SnackBar(content: Text('Error: $e'), backgroundColor: Colors.red)
       );
     }
   }
   ```

3. **Logout Handler**
   ```dart
   void _logout() async {
     await ref.read(authNotifierProvider.notifier).logout();
     context.go('/login');
   }
   ```

### API Calls Needed
- `GET /api/users/me` (already in authNotifierProvider)
- `PUT /api/users/me` (update profile)
- `POST /api/auth/logout` (logout)
- `PUT /api/users/me/password` (change password)

---

## Integration Priority Matrix

| Screen | Priority | Complexity | Estimated Time |
|--------|----------|------------|-----------------|
| HomeScreen | High | Low | 1 hour |
| FreightListScreen | High | Medium | 2 hours |
| FreightDetailScreen | High | Low | 1 hour |
| CreateFreightScreen | High | Medium | 2 hours |
| DriverDashboardScreen | Medium | Medium | 2.5 hours |
| TrackingScreen | Medium | High | 2.5 hours |
| ProfileScreen | Medium | Low | 1.5 hours |
| **Total** | - | - | **12 hours** |

---

## Common Integration Patterns

### Pattern 1: Simple Read from Provider
```dart
final data = ref.watch(myProvider);
return data.when(
  data: (d) => buildContent(d),
  loading: () => LoadingWidget(),
  error: (e, s) => ErrorWidget(e),
);
```

### Pattern 2: Form Submit
```dart
Future<void> _submit() async {
  if (!_validate()) return;
  try {
    showLoadingDialog();
    await repository.method(data);
    closeDialog();
    showSuccessSnackbar();
    navigate();
  } catch (e) {
    closeDialog();
    showErrorSnackbar(e);
  }
}
```

### Pattern 3: Real-time Updates
```dart
void initState() {
  socket.on('event', (data) {
    setState(() => state = data);
  });
}

void dispose() {
  socket.off('event');
  super.dispose();
}
```

---

## Testing Checklist

Before deploying integrated screens:
- [ ] Mock data removed/replaced
- [ ] Loading states implemented
- [ ] Error handling with user feedback
- [ ] API calls tested with backend
- [ ] Navigation flows verified
- [ ] Form validation working
- [ ] Real-time updates working (where applicable)

---

**Generated**: June 2, 2026 | Flutter 3.x | Riverpod 2.4.0
