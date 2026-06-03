import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../api/api_client.dart';
import '../models/models.dart';

// ── Auth ──────────────────────────────────────────────────────────────────

class AuthRepository {
  final ApiClient _api;
  AuthRepository(this._api);

  Future<AuthResponse> register({
    required String fullName,
    required String email,
    required String phone,
    required String password,
    required String role, // shipper | driver
  }) async {
    final response = await _api.post<AuthResponse>(
      '/register',                          // ✓ POST /register
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

  Future<AuthResponse> login({
    required String email,
    required String password,
  }) async {
    final response = await _api.post<AuthResponse>(
      '/login',                             // ✓ POST /login
      data: {'email': email, 'password': password},
      fromJson: (json) => AuthResponse.fromJson(json as Map<String, dynamic>),
    );
    await _api.saveToken(response.token);
    return response;
  }

  Future<void> logout() async {
    try {
      await _api.post<void>('/logout');     // ✓ POST /logout (auth)
    } catch (_) {
    } finally {
      await _api.deleteToken();
    }
  }

  Future<User> me() async =>              // ✓ GET /me (auth)
      _api.get<User>('/me',
          fromJson: (json) => User.fromJson(json as Map<String, dynamic>));

  Future<String?> getStoredToken() => _api.getToken();
}

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepository(ref.read(apiClientProvider)),
);

// ── Cargo Requests ────────────────────────────────────────────────────────

class CargoRepository {
  final ApiClient _api;
  CargoRepository(this._api);

  // ✓ GET /cargo-requests  (apiResource)
  Future<List<CargoRequest>> list() async {
    final response = await _api.dio.get('/cargo-requests');
    if (response.statusCode == 200) {
      final raw = response.data;
      final list = (raw is Map && raw.containsKey('data'))
          ? raw['data'] as List
          : raw as List;
      return list
          .map((e) => CargoRequest.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    throw ApiException(
        message: 'Failed to load cargo requests',
        statusCode: response.statusCode);
  }

  // ✓ GET /cargo-requests/{id}  (apiResource show)
  Future<CargoRequest> get(int id) async =>
      _api.get<CargoRequest>('/cargo-requests/$id',
          fromJson: (json) =>
              CargoRequest.fromJson(json as Map<String, dynamic>));

  // ✓ POST /cargo/create  (dedicated alias)
  Future<CargoRequest> create({
    required String pickupLocation,
    required String destination,
    required String materialType,
    required double weight,
    required String urgencyLevel,
    double? budget,
  }) async =>
      _api.post<CargoRequest>(
        '/cargo/create',
        data: {
          'pickup_location': pickupLocation,
          'destination': destination,
          'material_type': materialType,
          'weight': weight,
          'urgency_level': urgencyLevel,
          if (budget != null) 'budget': budget,
        },
        fromJson: (json) =>
            CargoRequest.fromJson(json as Map<String, dynamic>),
      );

  // ✓ PUT/PATCH /cargo-requests/{id}  (apiResource update)
  Future<CargoRequest> update(int id, {String? status}) async =>
      _api.patch<CargoRequest>(
        '/cargo-requests/$id',
        data: {if (status != null) 'status': status},
        fromJson: (json) =>
            CargoRequest.fromJson(json as Map<String, dynamic>),
      );

  // ✓ DELETE /cargo-requests/{id}  (apiResource destroy)
  Future<void> delete(int id) => _api.delete('/cargo-requests/$id');
}

final cargoRepositoryProvider = Provider<CargoRepository>(
  (ref) => CargoRepository(ref.read(apiClientProvider)),
);

// ── Vehicles ──────────────────────────────────────────────────────────────

class VehicleRepository {
  final ApiClient _api;
  VehicleRepository(this._api);

  // ✓ GET /vehicles  (apiResource)
  Future<List<Vehicle>> list() async {
    final response = await _api.dio.get('/vehicles');
    if (response.statusCode == 200) {
      final raw = response.data;
      final list = (raw is Map && raw.containsKey('data'))
          ? raw['data'] as List
          : raw as List;
      return list
          .map((e) => Vehicle.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    throw ApiException(
        message: 'Failed to load vehicles', statusCode: response.statusCode);
  }

  // ✓ POST /vehicle/register  (dedicated alias)
  Future<Vehicle> register({
    required String truckType,
    required String plateNumber,
    required double capacity,
    required String currentCity,
  }) async =>
      _api.post<Vehicle>(
        '/vehicle/register',
        data: {
          'truck_type': truckType,
          'plate_number': plateNumber,
          'capacity': capacity,
          'current_city': currentCity,
        },
        fromJson: (json) => Vehicle.fromJson(json as Map<String, dynamic>),
      );

  // ✓ PATCH /vehicles/{vehicle}/location
  Future<void> updateLocation(int vehicleId, double lat, double lng) =>
      _api.patch<void>(
        '/vehicles/$vehicleId/location',
        data: {'latitude': lat, 'longitude': lng},
      );

  // ✓ GET /vehicle/nearby
  Future<List<Vehicle>> nearby() async {
    final response = await _api.dio.get('/vehicle/nearby');
    if (response.statusCode == 200) {
      final raw = response.data;
      final list = (raw is Map && raw.containsKey('data'))
          ? raw['data'] as List
          : raw as List;
      return list
          .map((e) => Vehicle.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    throw ApiException(
        message: 'Failed to load nearby vehicles',
        statusCode: response.statusCode);
  }
}

final vehicleRepositoryProvider = Provider<VehicleRepository>(
  (ref) => VehicleRepository(ref.read(apiClientProvider)),
);

// ── Bookings ──────────────────────────────────────────────────────────────

class BookingRepository {
  final ApiClient _api;
  BookingRepository(this._api);

  // ✓ GET /bookings  (apiResource)
  Future<List<Booking>> list() async {
    final response = await _api.dio.get('/bookings');
    if (response.statusCode == 200) {
      final raw = response.data;
      final list = (raw is Map && raw.containsKey('data'))
          ? raw['data'] as List
          : raw as List;
      return list
          .map((e) => Booking.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    throw ApiException(
        message: 'Failed to load bookings', statusCode: response.statusCode);
  }

  // ✓ GET /bookings/{id}  (apiResource show)
  Future<Booking> get(int id) async =>
      _api.get<Booking>('/bookings/$id',
          fromJson: (json) => Booking.fromJson(json as Map<String, dynamic>));

  // ✓ POST /booking/create  (dedicated alias)
  Future<Booking> create({
    required int cargoId,
    required int vehicleId,
    required int driverId,
    required double estimatedPrice,
  }) async =>
      _api.post<Booking>(
        '/booking/create',
        data: {
          'cargo_id': cargoId,
          'vehicle_id': vehicleId,
          'driver_id': driverId,
          'booking_status': 'pending',
          'estimated_price': estimatedPrice,
        },
        fromJson: (json) => Booking.fromJson(json as Map<String, dynamic>),
      );
}

final bookingRepositoryProvider = Provider<BookingRepository>(
  (ref) => BookingRepository(ref.read(apiClientProvider)),
);

// ── Trips ─────────────────────────────────────────────────────────────────

class TripRepository {
  final ApiClient _api;
  TripRepository(this._api);

  // ✓ POST /trips
  Future<Trip> start(int bookingId) async =>
      _api.post<Trip>(
        '/trips',
        data: {'booking_id': bookingId},
        fromJson: (json) => Trip.fromJson(json as Map<String, dynamic>),
      );

  // ✓ GET /trips/{id}
  Future<Trip> get(int tripId) async =>
      _api.get<Trip>('/trips/$tripId',
          fromJson: (json) => Trip.fromJson(json as Map<String, dynamic>));

  // ✓ PATCH /trips/{id}/status  — valid: ongoing | completed
  Future<Trip> complete(int tripId) async =>
      _api.patch<Trip>(
        '/trips/$tripId/status',
        data: {'trip_status': 'completed'},
        fromJson: (json) => Trip.fromJson(json as Map<String, dynamic>),
      );

  // ✓ PATCH /trips/{id}/location
  Future<Trip> updateLocation(int tripId, double lat, double lng) async =>
      _api.patch<Trip>(
        '/trips/$tripId/location',
        data: {'lat': lat, 'lng': lng},
        fromJson: (json) => Trip.fromJson(json as Map<String, dynamic>),
      );
}

final tripRepositoryProvider = Provider<TripRepository>(
  (ref) => TripRepository(ref.read(apiClientProvider)),
);

// ── Payments ──────────────────────────────────────────────────────────────

class PaymentRepository {
  final ApiClient _api;
  PaymentRepository(this._api);

  // ✓ POST /payments
  Future<Payment> create({
    required int bookingId,
    required double amount,
    required String paymentMethod, // telebirr | cbe_birr | chapa | cash
  }) async =>
      _api.post<Payment>(
        '/payments',
        data: {
          'booking_id': bookingId,
          'amount': amount,
          'payment_method': paymentMethod,
        },
        fromJson: (json) => Payment.fromJson(json as Map<String, dynamic>),
      );

  // ✓ GET /payments/{booking_id}
  Future<Payment> getByBooking(int bookingId) async =>
      _api.get<Payment>('/payments/$bookingId',
          fromJson: (json) => Payment.fromJson(json as Map<String, dynamic>));
}

final paymentRepositoryProvider = Provider<PaymentRepository>(
  (ref) => PaymentRepository(ref.read(apiClientProvider)),
);

// ── AI Engine ─────────────────────────────────────────────────────────────

class AiRepository {
  final ApiClient _api;
  AiRepository(this._api);

  // ✓ POST /ai/recommend-truck
  Future<List<TruckRecommendation>> recommendTruck({
    required String pickupLocation,
    required String destination,
    required double weight,
    required String materialType,
    required String urgencyLevel,
  }) async {
    final data = await _api.post<Map<String, dynamic>>(
      '/ai/recommend-truck',
      data: {
        'pickup_location': pickupLocation,
        'destination': destination,
        'weight': weight,
        'material_type': materialType,
        'urgency_level': urgencyLevel,
      },
    );
    final list = (data as Map)['recommended_trucks'] as List? ?? [];
    return list
        .map((e) => TruckRecommendation.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  // ✓ POST /ai/backhaul-opportunities
  Future<List<BackhaulOpportunity>> backhaulOpportunities({
    required String currentLocation,
    required String destination,
    required double availableCapacity,
  }) async {
    final data = await _api.post<Map<String, dynamic>>(
      '/ai/backhaul-opportunities',
      data: {
        'current_location': currentLocation,
        'destination': destination,
        'available_capacity': availableCapacity,
      },
    );
    final list = (data as Map)['opportunities'] as List? ?? [];
    return list
        .map((e) =>
            BackhaulOpportunity.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  // ✓ POST /ai/predict-price
  Future<Map<String, dynamic>> predictPrice({
    required String pickupLocation,
    required String destination,
    required double weight,
    required String materialType,
  }) async =>
      _api.post<Map<String, dynamic>>(
        '/ai/predict-price',
        data: {
          'pickup_location': pickupLocation,
          'destination': destination,
          'weight': weight,
          'material_type': materialType,
        },
      );

  // ✓ POST /ai/predict-empty-return
  Future<Map<String, dynamic>> predictEmptyReturn(
          String destination) async =>
      _api.post<Map<String, dynamic>>(
        '/ai/predict-empty-return',
        data: {'destination': destination},
      );

  // ✓ POST /ai/optimize-route
  Future<Map<String, dynamic>> optimizeRoute({
    required String origin,
    required String destination,
    required List<String> waypoints,
  }) async =>
      _api.post<Map<String, dynamic>>(
        '/ai/optimize-route',
        data: {
          'origin': origin,
          'destination': destination,
          'waypoints': waypoints,
        },
      );
}

final aiRepositoryProvider = Provider<AiRepository>(
  (ref) => AiRepository(ref.read(apiClientProvider)),
);
