import 'package:dio/dio.dart' as dio_pkg;
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

  // POST /ai/predict-price — AI price estimate for a cargo job
  Future<({int? min, int? max, int? distanceKm})> predictPrice({
    required String pickup,
    required String destination,
    required double weight,
    String urgencyLevel = 'normal',
  }) async {
    try {
      final response = await _api.dio.post('/ai/predict-price', data: {
        'pickup_location': pickup,
        'destination': destination,
        'weight': weight,
        'material_type': 'general',
      });
      if (response.statusCode == 200) {
        final raw = response.data;
        final d = (raw is Map && raw.containsKey('data')) ? raw['data'] : raw;
        return (
          min: (d['min_price'] ?? d['min']) as int?,
          max: (d['max_price'] ?? d['max']) as int?,
          distanceKm: (d['distance_km'] ?? d['distanceKm']) as int?,
        );
      }
    } catch (_) {}
    return (min: null, max: null, distanceKm: null);
  }

  // GET /cargo-requests/return-cargo — cargo at driver's current destination
  Future<({String? city, List<CargoRequest> cargo})> returnCargo() async {
    try {
      final response = await _api.dio.get('/cargo-requests/return-cargo');
      if (response.statusCode == 200) {
        final raw = response.data as Map<String, dynamic>;
        final city = raw['city'] as String?;
        final list = (raw['cargo'] as List? ?? [])
            .map((e) => CargoRequest.fromJson(e as Map<String, dynamic>))
            .toList();
        return (city: city, cargo: list);
      }
    } catch (_) {}
    return (city: null, cargo: <CargoRequest>[]);
  }
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

  // ✓ GET /trips/{trip}/stops
  Future<List<TripStop>> getStops(int tripId) async {
    final response = await _api.dio.get('/trips/$tripId/stops');
    if (response.statusCode == 200) {
      final raw = response.data;
      final list = (raw is Map && raw.containsKey('data'))
          ? raw['data'] as List
          : raw as List;
      return list
          .map((e) => TripStop.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    throw ApiException(
        message: 'Failed to load stops', statusCode: response.statusCode);
  }

  // ✓ POST /trips/{trip}/stops
  Future<TripStop> addStop(
    int tripId, {
    required int stopOrder,
    required String locationName,
    required double agreedPrice,
    int? cargoRequestId,
    double? pickupLat,
    double? pickupLng,
    String? notes,
  }) async {
    final response = await _api.dio.post('/trips/$tripId/stops', data: {
      'stop_order': stopOrder,
      'location_name': locationName,
      'agreed_price': agreedPrice,
      if (cargoRequestId != null) 'cargo_request_id': cargoRequestId,
      if (pickupLat != null) 'pickup_lat': pickupLat,
      if (pickupLng != null) 'pickup_lng': pickupLng,
      if (notes != null && notes.isNotEmpty) 'notes': notes,
    });
    if (response.statusCode == 201) {
      final data = (response.data as Map<String, dynamic>)['data'];
      return TripStop.fromJson(
          (data as Map<String, dynamic>)['stop'] as Map<String, dynamic>);
    }
    throw ApiException(
        message: 'Failed to add stop', statusCode: response.statusCode);
  }

  // ✓ PATCH /trips/{trip}/stops/{stop}/arrive
  Future<TripStop> arriveAtStop(int tripId, int stopId) async {
    final response =
        await _api.dio.patch('/trips/$tripId/stops/$stopId/arrive');
    if (response.statusCode == 200) {
      final data = (response.data as Map<String, dynamic>)['data'];
      return TripStop.fromJson(
          (data as Map<String, dynamic>)['stop'] as Map<String, dynamic>);
    }
    throw ApiException(
        message: 'Failed to mark arrived', statusCode: response.statusCode);
  }

  // ✓ PATCH /trips/{trip}/stops/{stop}/load
  Future<TripStop> loadAtStop(int tripId, int stopId) async {
    final response =
        await _api.dio.patch('/trips/$tripId/stops/$stopId/load');
    if (response.statusCode == 200) {
      final data = (response.data as Map<String, dynamic>)['data'];
      return TripStop.fromJson(
          (data as Map<String, dynamic>)['stop'] as Map<String, dynamic>);
    }
    throw ApiException(
        message: 'Failed to mark loaded', statusCode: response.statusCode);
  }

  // ✓ PATCH /trips/{trip}/stops/{stop}/complete
  Future<TripStop> completeStop(int tripId, int stopId) async {
    final response =
        await _api.dio.patch('/trips/$tripId/stops/$stopId/complete');
    if (response.statusCode == 200) {
      final data = (response.data as Map<String, dynamic>)['data'];
      return TripStop.fromJson(
          (data as Map<String, dynamic>)['stop'] as Map<String, dynamic>);
    }
    throw ApiException(
        message: 'Failed to complete stop', statusCode: response.statusCode);
  }

  // ✓ DELETE /trips/{trip}/stops/{stop}
  Future<void> removeStop(int tripId, int stopId) async {
    final response =
        await _api.dio.delete('/trips/$tripId/stops/$stopId');
    if (response.statusCode != 200) {
      throw ApiException(
          message: 'Failed to remove stop', statusCode: response.statusCode);
    }
  }

  // GET /trips/{tripId}/backhaul-recommendations
  Future<List<BackhaulRecommendation>> getBackhaulRecommendations(int tripId) async {
    try {
      final response = await _api.dio.get('/trips/$tripId/backhaul-recommendations');
      if (response.statusCode == 200) {
        final raw = response.data;
        final list = (raw is Map && raw.containsKey('data'))
            ? raw['data'] as List
            : (raw is List ? raw : []);
        return list
            .map((e) => BackhaulRecommendation.fromJson(e as Map<String, dynamic>))
            .toList();
      }
    } catch (_) {}
    return [];
  }

  // DELETE /trips/backhaul-recommendations/{id}
  Future<void> dismissRecommendation(int recId) async {
    try {
      await _api.dio.delete('/trips/backhaul-recommendations/$recId');
    } catch (_) {}
  }
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
  // Schema requires: origin (str), destination (str), truck_type (str, optional)
  Future<Map<String, dynamic>> predictEmptyReturn(String destination) async =>
      _api.post<Map<String, dynamic>>(
        '/ai/predict-empty-return',
        data: {
          'origin': 'Addis Ababa', // default origin
          'destination': destination,
          'truck_type': 'general',
        },
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

// ── Driver Documents ──────────────────────────────────────────────────────

class DocumentRepository {
  final ApiClient _api;
  DocumentRepository(this._api);

  // ✓ GET /driver/documents
  Future<List<DriverDocument>> list() async {
    final response = await _api.dio.get('/driver/documents');
    if (response.statusCode == 200) {
      final raw = response.data;
      final list = (raw is Map && raw.containsKey('data'))
          ? raw['data'] as List
          : raw as List;
      return list
          .map((e) => DriverDocument.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    throw ApiException(
        message: 'Failed to load documents', statusCode: response.statusCode);
  }

  // ✓ POST /driver/documents  (multipart)
  Future<DriverDocument> upload({
    required String documentType,
    required String filePath,
    required String fileName,
  }) async {
    final formData = dio_pkg.FormData.fromMap({
      'document_type': documentType,
      'file': await dio_pkg.MultipartFile.fromFile(filePath, filename: fileName),
    });
    final response = await _api.dio.post(
      '/driver/documents',
      data: formData,
      options: dio_pkg.Options(contentType: 'multipart/form-data'),
    );
    if (response.statusCode == 201 || response.statusCode == 200) {
      final raw = response.data;
      final data = (raw is Map && raw.containsKey('data')) ? raw['data'] : raw;
      return DriverDocument.fromJson(data as Map<String, dynamic>);
    }
    throw ApiException(
        message: 'Upload failed', statusCode: response.statusCode);
  }
}

final documentRepositoryProvider = Provider<DocumentRepository>(
  (ref) => DocumentRepository(ref.read(apiClientProvider)),
);

// ── Bids ──────────────────────────────────────────────────────────────────

class BidRepository {
  final ApiClient _api;
  BidRepository(this._api);

  // ✓ GET /cargo-requests/{cargoId}/bids
  Future<List<Bid>> listForCargo(int cargoId) async {
    final response = await _api.dio.get('/cargo-requests/$cargoId/bids');
    if (response.statusCode == 200) {
      final raw = response.data;
      final list = (raw is Map && raw.containsKey('data'))
          ? raw['data'] as List
          : raw as List;
      return list.map((e) => Bid.fromJson(e as Map<String, dynamic>)).toList();
    }
    throw ApiException(
        message: 'Failed to load bids', statusCode: response.statusCode);
  }

  // ✓ POST /cargo-requests/{cargoId}/bids  — driver places bid
  Future<Bid> place({
    required int cargoId,
    required int vehicleId,
    required double amount,
    String? note,
  }) async =>
      _api.post<Bid>(
        '/cargo-requests/$cargoId/bids',
        data: {
          'vehicle_id': vehicleId,
          'amount': amount,
          if (note != null && note.isNotEmpty) 'note': note,
        },
        fromJson: (json) => Bid.fromJson(json as Map<String, dynamic>),
      );

  // ✓ PATCH /bids/{bidId}/accept  — shipper accepts, returns Booking JSON
  Future<Booking> acceptBid(int bidId) async {
    final response = await _api.dio.patch('/bids/$bidId/accept');
    if (response.statusCode == 200) {
      final data = (response.data as Map<String, dynamic>)['data'];
      return Booking.fromJson(data as Map<String, dynamic>);
    }
    throw ApiException(
        message: 'Failed to accept bid', statusCode: response.statusCode);
  }

  // ✓ PATCH /bids/{bidId}/reject
  Future<void> rejectBid(int bidId) async {
    await _api.dio.patch('/bids/$bidId/reject');
  }

  // ✓ PATCH /bids/{bidId}/counter — send a counter-offer (shipper or driver)
  Future<Bid> counterBid(int bidId,
      {required double counterAmount, String? counterNote}) async {
    final response = await _api.dio.patch('/bids/$bidId/counter', data: {
      'counter_amount': counterAmount,
      if (counterNote != null && counterNote.isNotEmpty) 'counter_note': counterNote,
    });
    if (response.statusCode == 200) {
      final data = (response.data as Map<String, dynamic>)['data'];
      return Bid.fromJson(data as Map<String, dynamic>);
    }
    throw ApiException(
        message: 'Failed to send counter-offer', statusCode: response.statusCode);
  }

  // ✓ PATCH /bids/{bidId}/accept-counter — accept the current counter-offer
  Future<Booking> acceptCounter(int bidId) async {
    final response = await _api.dio.patch('/bids/$bidId/accept-counter');
    if (response.statusCode == 200) {
      final data = (response.data as Map<String, dynamic>)['data'];
      return Booking.fromJson(data as Map<String, dynamic>);
    }
    throw ApiException(
        message: 'Failed to accept counter-offer', statusCode: response.statusCode);
  }

  // ✓ GET /driver/bids — driver sees all their bids with counter-offer state
  Future<List<Bid>> listMyBids() async {
    final response = await _api.dio.get('/driver/bids');
    if (response.statusCode == 200) {
      final raw = response.data;
      final list = (raw is Map && raw.containsKey('data'))
          ? raw['data'] as List
          : raw as List;
      return list.map((e) => Bid.fromJson(e as Map<String, dynamic>)).toList();
    }
    throw ApiException(
        message: 'Failed to load your bids', statusCode: response.statusCode);
  }
}

final bidRepositoryProvider = Provider<BidRepository>(
  (ref) => BidRepository(ref.read(apiClientProvider)),
);

// ── Rating ────────────────────────────────────────────────────────────────

class RatingRepository {
  final ApiClient _api;
  RatingRepository(this._api);

  Future<void> submitRating({
    required int bookingId,
    required int rating,
    String? feedback,
  }) async {
    await _api.post<void>(
      '/ratings',
      data: {
        'booking_id': bookingId,
        'rating': rating,
        if (feedback != null && feedback.isNotEmpty) 'feedback': feedback,
      },
    );
  }
}

final ratingRepositoryProvider = Provider<RatingRepository>(
  (ref) => RatingRepository(ref.read(apiClientProvider)),
);
