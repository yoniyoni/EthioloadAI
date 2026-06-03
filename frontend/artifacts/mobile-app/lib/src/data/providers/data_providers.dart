import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/models.dart';
import '../repositories/repositories.dart';

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
    } catch (_) {
      await _repo.logout();
      state = const AuthState();
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
      state = state.copyWith(isLoading: false, error: e.toString());
    }
  }

  Future<void> login({required String email, required String password}) async {
    state = state.copyWith(isLoading: true, clearError: true);
    try {
      final res = await _repo.login(email: email, password: password);
      state = state.copyWith(
        user: res.user,
        token: res.token,
        isLoading: false,
        clearError: true,
      );
    } catch (e) {
      state = state.copyWith(isLoading: false, error: e.toString());
    }
  }

  Future<void> logout() async {
    await _repo.logout();
    state = const AuthState();
  }
}

// Use ref.read — auth repository should never cause cascading rebuilds
final authNotifierProvider =
    StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  return AuthNotifier(ref.read(authRepositoryProvider));
});

// ── Cargo providers ───────────────────────────────────────────────────────

// keepAlive: true prevents the provider from being disposed on navigation,
// avoiding unnecessary re-fetches and rebuild loops
final cargoListProvider = FutureProvider.autoDispose<List<CargoRequest>>((ref) async {
  return ref.read(cargoRepositoryProvider).list();
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

// ── Booking providers ─────────────────────────────────────────────────────

final bookingListProvider = FutureProvider.autoDispose<List<Booking>>((ref) async {
  return ref.read(bookingRepositoryProvider).list();
});

final singleBookingProvider =
    FutureProvider.autoDispose.family<Booking, int>((ref, id) async {
  return ref.read(bookingRepositoryProvider).get(id);
});

// ── Trip provider ─────────────────────────────────────────────────────────

final tripProvider = FutureProvider.autoDispose.family<Trip, int>((ref, tripId) async {
  return ref.read(tripRepositoryProvider).get(tripId);
});

// ── Payment provider ──────────────────────────────────────────────────────

final paymentByBookingProvider =
    FutureProvider.autoDispose.family<Payment, int>((ref, bookingId) async {
  return ref.read(paymentRepositoryProvider).getByBooking(bookingId);
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
