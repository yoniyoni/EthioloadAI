import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../api/api_client.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';
import '../../services/spring_websocket_service.dart';

// ── Auth State ────────────────────────────────────────────────────────────

class AuthState {
  final User? user;
  final String? token;
  final bool isLoading;
  final String? error;

  const AuthState({
    this.user,
    this.token,
    this.isLoading = false,
    this.error,
  });

  bool get isAuthenticated => token != null && user != null;

  AuthState copyWith({
    User? user,
    String? token,
    bool? isLoading,
    String? error,
    bool clearError = false,
    bool clearUser = false,
    bool clearToken = false,
  }) =>
      AuthState(
        user: clearUser ? null : user ?? this.user,
        token: clearToken ? null : token ?? this.token,
        isLoading: isLoading ?? this.isLoading,
        error: clearError ? null : error ?? this.error,
      );
}

class AuthNotifier extends StateNotifier<AuthState> {
  final AuthRepository _repo;
  AuthNotifier(this._repo) : super(const AuthState());

  Future<void> checkAuthStatus() async {
    final token = await _repo.getStoredToken();
    if (token == null) return;
    state = state.copyWith(token: token, isLoading: true);
    try {
      final user = await _repo.me();
      state = state.copyWith(user: user, isLoading: false, clearError: true);
    } catch (e) {
      if (e is ApiException && e.statusCode == 401) {
        await logout();
      } else {
        state = AuthState(error: e.toString());
      }
    }
  }

  Future<void> register({
    required String fullName,
    required String email,
    required String phone,
    required String password,
    required String role,
  }) async {
    state = state.copyWith(isLoading: true, clearError: true);
    try {
      final res = await _repo.register(
        fullName: fullName,
        email: email,
        phone: phone,
        password: password,
        role: role,
      );
      state = state.copyWith(
        user: res.user,
        token: res.token,
        isLoading: false,
        clearError: true,
      );
    } catch (e) {
      state = AuthState(error: e.toString());
    }
  }

  Future<void> login({required String identifier, required String password}) async {
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
      state = AuthState(error: e.toString());
    }
  }

  Future<void> logout() async {
    try { await _repo.logout(); } catch (_) { /* Clear local state even when offline. */ }
    finally { state = const AuthState(); }
  }

  Future<void> refreshProfile() async {
    final user = await _repo.me();
    state = state.copyWith(user: user, clearError: true);
  }
  void expireSession() { state = const AuthState(); }
}

// Use ref.read — auth repository should never cause cascading rebuilds
final authNotifierProvider =
    StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final notifier = AuthNotifier(ref.read(authRepositoryProvider));
  ref.listen<int>(sessionClearedProvider, (_, __) => notifier.expireSession());
  return notifier;
});

/// Shared startup barrier: deep links must not redirect before stored auth loads.
final authInitializationProvider = FutureProvider<void>((ref) async {
  await ref.read(authNotifierProvider.notifier).checkAuthStatus();
});

// ── Cargo providers ───────────────────────────────────────────────────────

// keepAlive: true prevents the provider from being disposed on navigation,
// avoiding unnecessary re-fetches and rebuild loops
final cargoListProvider = FutureProvider.autoDispose<List<CargoRequest>>((ref) async {
  return ref.read(cargoRepositoryProvider).list();
});

// cargo list + location_unset flag (for driver dashboard city banner)
final cargoListWithMetaProvider = FutureProvider.autoDispose<
    ({bool locationUnset, List<CargoRequest> cargo})>((ref) async {
  return ref.read(cargoRepositoryProvider).listWithMeta();
});

final singleCargoProvider =
    FutureProvider.autoDispose.family<CargoRequest, int>((ref, id) async {
  return ref.read(cargoRepositoryProvider).get(id);
});

// ── Vehicle providers ─────────────────────────────────────────────────────

final vehicleListProvider = FutureProvider.autoDispose<List<Vehicle>>((ref) async {
  return ref.read(vehicleRepositoryProvider).list();
});

final nearbyVehiclesProvider = FutureProvider.autoDispose<List<Vehicle>>((ref) async {
  return ref.read(vehicleRepositoryProvider).nearby();
});

final fleetDriversProvider =
    FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) async {
  return ref.read(fleetRepositoryProvider).listDrivers();
});

final fleetVehiclesProvider =
    FutureProvider.autoDispose<List<Map<String, dynamic>>>((ref) async {
  return ref.read(fleetRepositoryProvider).listVehicles();
});

// ── Booking providers ─────────────────────────────────────────────────────

final bookingListProvider = FutureProvider.autoDispose<List<Booking>>((ref) async {
  return ref.read(bookingRepositoryProvider).list();
});

final shipperDashboardSummaryProvider =
    FutureProvider.autoDispose<ShipperDashboardSummary>((ref) async {
  return ref.read(dashboardRepositoryProvider).getShipperSummary();
});

final driverDashboardSummaryProvider =
    FutureProvider.autoDispose<DriverDashboardSummary>((ref) async {
  return ref.read(dashboardRepositoryProvider).getDriverSummary();
});

final bookingPageProvider =
    FutureProvider.autoDispose.family<BookingPage, int>((ref, page) async {
  return ref.read(bookingRepositoryProvider).page(page: page);
});

final singleBookingProvider =
    FutureProvider.autoDispose.family<Booking, int>((ref, id) async {
  return ref.read(bookingRepositoryProvider).get(id);
});

// ── Trip provider ─────────────────────────────────────────────────────────

final tripProvider = FutureProvider.autoDispose.family<Trip, int>((ref, tripId) async {
  return ref.read(tripRepositoryProvider).get(tripId);
});

final tripStopsProvider =
    FutureProvider.autoDispose.family<List<TripStop>, int>((ref, tripId) async {
  return ref.read(tripRepositoryProvider).getStops(tripId);
});

// ── Payment provider ──────────────────────────────────────────────────────

final paymentByBookingProvider =
    FutureProvider.autoDispose.family<Payment, int>((ref, bookingId) async {
  return ref.read(paymentRepositoryProvider).getByBooking(bookingId);
});

// ── Document providers ────────────────────────────────────────────────────

final driverDocumentsProvider =
    FutureProvider.autoDispose<List<DriverDocument>>((ref) async {
  final user = ref.read(authNotifierProvider).user;
  if (user == null || !user.isDriver) {
    throw ApiException(message: 'Driver authentication required');
  }
  return ref.read(documentRepositoryProvider).list(user.id);
});

final vehicleDocumentsProvider =
    FutureProvider.autoDispose.family<List<VehicleDocument>, int>((ref, vehicleId) async {
  return ref.read(vehicleDocumentRepositoryProvider).list(vehicleId);
});

// ── Bid providers ─────────────────────────────────────────────────────────

final bidsForCargoPageProvider =
    FutureProvider.autoDispose.family<BidPage, (int, int)>((ref, request) async {
  return ref.read(bidRepositoryProvider).listForCargo(
        request.$1,
        page: request.$2,
      );
});

// Driver's own bids — sorted: countered first, then pending, then history
final myBidsProvider = FutureProvider.autoDispose<List<Bid>>((ref) async {
  final authState = ref.watch(authNotifierProvider);
  if (!authState.isAuthenticated || authState.user?.isDriver != true) {
    return <Bid>[];
  }
  return ref.read(bidRepositoryProvider).listMyBids();
});

// Backhaul recommendations for an active trip (keyed by tripId)
final backhaulRecommendationsProvider =
    FutureProvider.autoDispose.family<List<BackhaulRecommendation>, int>(
        (ref, tripId) async {
  return ref.read(tripRepositoryProvider).getBackhaulRecommendations(tripId);
});

// Return cargo — available cargo from the driver's current destination
final returnCargoProvider =
    FutureProvider.autoDispose<({String? city, List<CargoRequest> cargo})>(
        (ref) async {
  return ref.read(cargoRepositoryProvider).returnCargo();
});

// Live GPS location for a trip (shipper tracking screen)
final tripLocationProvider =
    FutureProvider.autoDispose.family<TripLocation, int>((ref, tripId) async {
  return ref.read(tripRepositoryProvider).getLocation(tripId);
});

// Legacy provider retained for source compatibility. Spring does not expose
// nearby driver profiles; use nearbyVehiclesForCargoProvider instead.
@Deprecated(
    'Spring exposes nearby vehicles, not driver profiles. Use nearbyVehiclesForCargoProvider.')
final nearbyDriversProvider =
    FutureProvider.autoDispose.family<List<NearbyDriver>, int>(
        (ref, cargoId) async {
  return ref.read(cargoRepositoryProvider).nearbyDrivers(cargoId);
});

final nearbyVehiclesForCargoProvider =
    FutureProvider.autoDispose.family<List<Vehicle>, int>(
        (ref, cargoId) async {
  return ref.read(cargoRepositoryProvider).nearbyVehicles(cargoId);
});

// ── AI providers ──────────────────────────────────────────────────────────

class TruckRecommendationParams {
  final String pickup;
  final String destination;
  final double weight;
  final String materialType;
  final String urgencyLevel;

  const TruckRecommendationParams({
    required this.pickup,
    required this.destination,
    required this.weight,
    required this.materialType,
    required this.urgencyLevel,
  });
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

final emptyReturnRiskProvider =
    FutureProvider.autoDispose.family<Map<String, dynamic>, String>(
        (ref, destination) async {
  return ref.read(aiRepositoryProvider).predictEmptyReturn(destination);
});

// ── Notification providers ────────────────────────────────────────────────

final notificationsProvider = FutureProvider.autoDispose<
    ({List<AppNotification> items, int unreadCount})>((ref) async {
  final user = ref.read(authNotifierProvider).user;
  if (user != null) {
    final service = SpringWebSocketService();
    service.messages.listen((message) {
      if (message['type'] == 'NOTIFICATION' ||
          message['type'] == 'TRIP_STATUS_CHANGED') {
        ref.invalidateSelf();
      }
    });
    unawaited(service.connect(
      endpoint: '/ws/notifications',
      topic: 'user.${user.id}.notifications',
    ));
    ref.onDispose(service.dispose);
  }
  return ref.read(notificationRepositoryProvider).list();
});
