import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:dio/dio.dart' as dio_pkg;
import '../api/api_client.dart';
import '../models/models.dart';

class AuthRepository {
  final ApiClient _api;
  AuthRepository(this._api);
  Future<AuthResponse> register({required String fullName, required String email,
    required String phone, required String password, required String role}) async {
    final response = await _api.post<AuthResponse>('/auth/register', data: {
      'fullName': fullName, 'email': email.trim().toLowerCase(), 'phone': phone,
      'password': password, 'role': role,
    }, fromJson: (json) => AuthResponse.fromJson(json as Map<String, dynamic>));
    await _api.saveToken(response.token);
    return response;
  }
  Future<AuthResponse> login({required String identifier, required String password}) async {
    final response = await _api.post<AuthResponse>('/auth/login',
      data: {'identifier': identifier.trim(), 'password': password},
      fromJson: (json) => AuthResponse.fromJson(json as Map<String, dynamic>));
    await _api.saveToken(response.token);
    return response;
  }
  Future<void> logout() async {
    try { await _api.post<void>('/auth/logout'); }
    finally { await _api.deleteToken(); }
  }
  Future<User> me() => _api.get<User>('/auth/me',
    fromJson: (json) => User.fromJson(json as Map<String, dynamic>));
  Future<String?> getStoredToken() => _api.getToken();

  Future<User> updateProfile({
    String? fullName,
    String? phone,
    String? address,
    String? businessName,
  }) =>
      _api.patch<User>(
        '/auth/me',
        data: {
          if (fullName != null) 'fullName': fullName,
          if (phone != null) 'phone': phone,
          if (address != null) 'address': address,
          if (businessName != null) 'businessName': businessName,
        },
        fromJson: (json) => User.fromJson(json as Map<String, dynamic>),
      );

  // PATCH /api/auth/me/password
  Future<void> changePassword({
    required String currentPassword,
    required String newPassword,
  }) async {
    await _api.patch<void>('/auth/me/password', data: {
      'currentPassword': currentPassword,
      'newPassword': newPassword,
    });
  }
}

final authRepositoryProvider = Provider<AuthRepository>(
  (ref) => AuthRepository(ref.read(apiClientProvider)),
);

class DashboardRepository {
  final ApiClient _api;
  DashboardRepository(this._api);

  Future<ShipperDashboardSummary> getShipperSummary() =>
      _api.get<ShipperDashboardSummary>(
        '/dashboard/summary',
        fromJson: (json) =>
            ShipperDashboardSummary.fromJson(json as Map<String, dynamic>),
      );

  Future<DriverDashboardSummary> getDriverSummary() =>
      _api.get<DriverDashboardSummary>(
        '/dashboard/driver-summary',
        fromJson: (json) =>
            DriverDashboardSummary.fromJson(json as Map<String, dynamic>),
      );
}

final dashboardRepositoryProvider = Provider<DashboardRepository>(
  (ref) => DashboardRepository(ref.read(apiClientProvider)),
);

// ── Freight ───────────────────────────────────────────────────────────────

class CargoRepository {
  final ApiClient _api;
  CargoRepository(this._api);

  // GET /api/freight — Spring returns a Page<FreightResponse>.
  Future<List<CargoRequest>> list() async {
    final response = await _api.dio.get('/freight', queryParameters: {'size': 100});
    if (response.statusCode == 200) {
      final raw = response.data;
      final list = raw is Map && raw['content'] is List
          ? raw['content'] as List
          : raw is List ? raw : <dynamic>[];
      return list
          .map((e) => CargoRequest.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    throw ApiException(
        message: 'Failed to load cargo requests',
        statusCode: response.statusCode);
  }

    // GET /api/freight/{id}
  Future<CargoRequest> get(int id) async =>
      _api.get<CargoRequest>('/freight/$id',
          fromJson: (json) =>
              CargoRequest.fromJson(Map<String, dynamic>.from(json as Map)));

  // Maps Flutter cargo type labels to the values accepted by the Spring @Pattern constraint.
  // Spring accepts: grain|cement|construction|perishables|electronics|livestock|fuel|general|other
  static String _toSpringMaterialType(String flutterType) {
    const map = <String, String>{
      'agricultural':  'grain',
      'construction':  'construction',
      'electronics':   'electronics',
      'fragile':       'general',
      'fuel':          'fuel',
      'general':       'general',
      'livestock':     'livestock',
      'machinery':     'general',
      'medical':       'general',
      'perishable':    'perishables',
      'perishables':   'perishables',
      'textiles':      'general',
      'grain':         'grain',
      'cement':        'cement',
      'other':         'other',
    };
    return map[flutterType.toLowerCase()] ?? 'general';
  }

  // Formats a DateTime to 'yyyy-MM-ddTHH:mm:ss' — drops sub-second precision so
  // Spring @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") can deserialise it correctly.
  static String _formatDateTime(DateTime dt) {
    final local = dt.toLocal();
    final y = local.year.toString().padLeft(4, '0');
    final mo = local.month.toString().padLeft(2, '0');
    final d = local.day.toString().padLeft(2, '0');
    final h = local.hour.toString().padLeft(2, '0');
    final mi = local.minute.toString().padLeft(2, '0');
    final s = local.second.toString().padLeft(2, '0');
    return '$y-$mo-${d}T$h:$mi:$s';
  }

  // POST /api/freight
  Future<CargoRequest> create({
    required String pickupLocation,
    required String destination,
    required String materialType,
    required double weight,
    required String urgencyLevel,
    double? budget,
    String priceType = 'negotiable',
    DateTime? bidDeadline,
    double? pickupLat,
    double? pickupLng,
    double? destinationLat,
    double? destinationLng,
    String? cargoDescription,
    double? volumeM3,
  }) async =>
      _api.post<CargoRequest>(
        '/freight',
        data: {
          'pickupLocation': pickupLocation,
          'destination': destination,
          // Map Flutter cargo type labels to Spring-accepted materialType values.
          'materialType': _toSpringMaterialType(materialType),
          'weightTons': weight,
          'urgencyLevel': urgencyLevel,
          // budget is NOT NULL in the DB. Send it only if the user set one.
          // The Spring service defaults null budget to BigDecimal.ZERO for NEGOTIABLE freight.
          if (budget != null && budget > 0) 'budget': budget,
          'priceType': priceType.toUpperCase(),
          // Spring @JsonFormat expects 'yyyy-MM-ddTHH:mm:ss' — no sub-second precision.
          if (bidDeadline != null) 'bidDeadline': _formatDateTime(bidDeadline),
          if (pickupLat != null && pickupLng != null) 'pickupLatitude': pickupLat,
          if (pickupLat != null && pickupLng != null) 'pickupLongitude': pickupLng,
          if (destinationLat != null && destinationLng != null) 'destinationLatitude': destinationLat,
          if (destinationLat != null && destinationLng != null) 'destinationLongitude': destinationLng,
          if (cargoDescription != null) 'cargoDescription': cargoDescription,
          if (volumeM3 != null) 'volumeM3': volumeM3,
        },
        fromJson: (json) =>
            CargoRequest.fromJson(json as Map<String, dynamic>),
      );

  // PATCH /api/freight/{id}
  Future<CargoRequest> update(int id, {String? status}) async =>
      _api.patch<CargoRequest>(
        '/freight/$id',
        data: {if (status != null) 'status': status},
        fromJson: (json) =>
            CargoRequest.fromJson(json as Map<String, dynamic>),
      );

  // Spring has no separate direct fixed-price booking endpoint.
  Future<Booking> bookDirectFixed(int cargoId) async {
    throw ApiException(message: 'Direct fixed-price booking is not available in Spring Boot.', statusCode: 501);
  }

  // Spring uses the standard bid flow for all supported freight.
  Future<Bid> acceptFixedPrice(int cargoId) async {
    throw ApiException(message: 'Use the standard Spring bid flow.', statusCode: 501);
  }

  // DELETE /api/freight/{id}
  Future<void> delete(int id) => _api.delete('/freight/$id');

  // POST /ai/predict-price — AI price estimate for a cargo job
  Future<({int? min, int? max, int? distanceKm})> predictPrice({
    required String pickup,
    required String destination,
    required double weight,
    String urgencyLevel = 'normal',
    String materialType = 'general',
    double? fromLat,
    double? fromLng,
    double? toLat,
    double? toLng,
  }) async {
    try {
      final body = <String, dynamic>{
        'pickup_location': pickup,
        'destination': destination,
        'weight': weight,
        'material_type': materialType,
        'from': pickup,
        'to':   destination,
      };
      if (fromLat != null) body['from_lat'] = fromLat;
      if (fromLng != null) body['from_lng'] = fromLng;
      if (toLat   != null) body['to_lat']   = toLat;
      if (toLng   != null) body['to_lng']   = toLng;

      final response = await _api.dio.post('/ai/predict-price', data: body);
      if (response.statusCode == 200) {
        final raw = response.data;
        final d = (raw is Map && raw.containsKey('data')) ? raw['data'] : raw;
        return (
          min: (d['price_min'] ?? d['min_price'] ?? d['min']) as int?,
          max: (d['price_max'] ?? d['max_price'] ?? d['max']) as int?,
          distanceKm: (d['distance_km'] ?? d['distanceKm']) as int?,
        );
      }
    } catch (_) {}
    return (min: null, max: null, distanceKm: null);
  }

  // POST /api/freight — intracity variant
  Future<CargoRequest> createIntracity({
    required String city,
    required String pickupArea,
    required String dropoffArea,
    required String itemsDescription,
    required DateTime preferredDate,
    String? vehicleTypeNeeded,
    String priceType = 'negotiable',
    double? budget,
    DateTime? bidDeadline,
    double? pickupLat,
    double? pickupLng,
  }) async =>
      _api.post<CargoRequest>(
        '/freight',
        data: {
          // Spring enum must be UPPERCASE — 'intracity' (lowercase) causes 500.
          'serviceType': 'INTRACITY',
          'city': city,
          'pickupArea': pickupArea,
          'dropoffArea': dropoffArea,
          'itemsDescription': itemsDescription,
          'preferredDate': preferredDate.toIso8601String().split('T')[0],
          if (vehicleTypeNeeded != null && vehicleTypeNeeded.isNotEmpty)
            'vehicleTypeNeeded': vehicleTypeNeeded,
          // priceType must also be UPPERCASE for Jackson enum deserialization.
          'priceType': priceType.toUpperCase(),
          // Send budget only if the user specified a positive amount.
          // The Spring service defaults null budget to BigDecimal.ZERO for NEGOTIABLE freight.
          if (budget != null && budget > 0) 'budget': budget,
          // Spring @JsonFormat expects 'yyyy-MM-ddTHH:mm:ss' — no sub-second precision.
          if (bidDeadline != null) 'bidDeadline': _formatDateTime(bidDeadline),
          if (pickupLat != null && pickupLng != null) 'pickupLatitude': pickupLat,
          if (pickupLat != null && pickupLng != null) 'pickupLongitude': pickupLng,
        },
        fromJson: (json) =>
            CargoRequest.fromJson(json as Map<String, dynamic>),
      );

  // POST /geocode/nearest-city — reverse-geocode GPS coords to nearest city
  Future<({String city, double distanceKm, double lat, double lng})>
      nearestCity(double lat, double lng) async {
    final response = await _api.dio.post(
      '/geocode/nearest-city',
      data: {'lat': lat, 'lng': lng},
    );
    if (response.statusCode == 200) {
      final d = response.data as Map<String, dynamic>;
      return (
        city: d['city'] as String,
        distanceKm: (d['distance_km'] as num).toDouble(),
        lat: (d['lat'] as num).toDouble(),
        lng: (d['lng'] as num).toDouble(),
      );
    }
    throw ApiException(
        message: 'Geocoding failed', statusCode: response.statusCode);
  }

  // Spring does not return the legacy location metadata envelope.
  Future<({bool locationUnset, List<CargoRequest> cargo})> listWithMeta() async {
    return (locationUnset: false, cargo: await list());
  }

  // GET /driver/return-cargo — cargo at driver's current destination
  Future<({String? city, List<CargoRequest> cargo})> returnCargo() async {
    try {
      final response = await _api.dio.get('/driver/return-cargo');
      if (response.statusCode == 200) {
        final raw = response.data as Map<String, dynamic>;
        final city = raw['destination_city'] as String?;
        final list = (raw['data'] as List? ?? [])
            .map((e) => CargoRequest.fromJson(e as Map<String, dynamic>))
            .toList();
        return (city: city, cargo: list);
      }
    } catch (_) {}
    return (city: null, cargo: <CargoRequest>[]);
  }

  // GET /api/vehicles/nearby — Spring returns VehicleResponse records rather
  // than driver profiles, so this legacy method remains empty for compatibility.
  @Deprecated(
      'Spring exposes nearby vehicles, not driver profiles. Use nearbyVehicles.')
  Future<List<NearbyDriver>> nearbyDrivers(int cargoId) async {
    return <NearbyDriver>[];
  }

  // GET /api/vehicles/nearby — this endpoint does not expose driver names,
  // trip counts, or service-type filtering.
  Future<List<Vehicle>> nearbyVehiclesByLocation(
    double latitude,
    double longitude, {
    double radiusKm = 10.0,
  }) async {
    final response = await _api.dio.get(
      '/vehicles/nearby',
      queryParameters: {
        'latitude': latitude,
        'longitude': longitude,
        'radius': radiusKm,
      },
    );
    if (response.statusCode != 200 || response.data is! List) {
      throw ApiException(
        message: 'Failed to load nearby vehicles',
        statusCode: response.statusCode,
      );
    }
    return (response.data as List)
        .map((item) => Vehicle.fromJson(Map<String, dynamic>.from(item as Map)))
        .toList();
  }

  Future<List<Vehicle>> nearbyVehicles(int cargoId) async {
    final cargo = await get(cargoId);
    final latitude = cargo.pickupLatitude;
    final longitude = cargo.pickupLongitude;
    if (latitude == null || longitude == null) {
      throw ApiException(
        message: 'Freight has no pickup coordinates for nearby vehicle search',
        statusCode: 422,
      );
    }
    return nearbyVehiclesByLocation(latitude, longitude);
  }
}

final cargoRepositoryProvider = Provider<CargoRepository>(
  (ref) => CargoRepository(ref.read(apiClientProvider)),
);

// ── Vehicles ──────────────────────────────────────────────────────────────

class VehicleRepository {
  final ApiClient _api;
  VehicleRepository(this._api);

  // GET /api/vehicles/my-vehicles
  Future<List<Vehicle>> list() async {
    final response = await _api.dio.get('/vehicles/my-vehicles');
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

  Future<Map<String, dynamic>> getLocationSnapshot(int vehicleId) =>
      _api.get<Map<String, dynamic>>(
        '/vehicles/$vehicleId',
        fromJson: (json) {
          if (json is! Map) {
            throw const FormatException('Expected a vehicle response object');
          }
          return Map<String, dynamic>.from(json);
        },
      );

  Future<Vehicle> reportDriverLocation(double latitude, double longitude) =>
      _api.post<Vehicle>(
        '/vehicles/driver/location',
        data: {'latitude': latitude, 'longitude': longitude},
        fromJson: (json) => Vehicle.fromJson(json as Map<String, dynamic>),
      );

  // POST /api/fleet/vehicles
  Future<Vehicle> register({
    required String truckType,
    required String plateNumber,
    required double capacity,
    required String currentCity,
    String? vehicleCategory,
  }) async =>
      _api.post<Vehicle>(
        '/fleet/vehicles',
        data: {
          'truckType': truckType,
          'plateNumber': plateNumber,
          'capacity': capacity,
          'currentCity': currentCity,
          if (vehicleCategory != null) 'vehicleCategory': vehicleCategory,
        },
        fromJson: (json) => Vehicle.fromJson(json as Map<String, dynamic>),
      );

  // ✓ PATCH /vehicles/{id}/current-city?currentCity=...
  Future<void> updateCurrentCity(String city) async {
    final vehicles = await list();
    if (vehicles.isEmpty) {
      throw ApiException(message: 'No vehicle registered', statusCode: 404);
    }
    await _api.dio.patch('/vehicles/${vehicles.first.id}/current-city',
        queryParameters: {'currentCity': city});
  }

  // ✓ PATCH /vehicles/{vehicle}/location
  Future<void> updateLocation(int vehicleId, double lat, double lng) =>
      _api.patch<void>(
        '/vehicles/$vehicleId/location',
        data: {'latitude': lat, 'longitude': lng},
      );

  // ✓ GET /vehicle/nearby
  Future<List<Vehicle>> nearby() async {
    final response = await _api.dio.get('/vehicles/nearby', queryParameters: {
      'latitude': 9.0320,
      'longitude': 38.7469,
      'radius': 100.0,
    });
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

// ── Legacy fleet owner operations ─────────────────────────────────────────

class FleetRepository {
  final ApiClient _api;
  FleetRepository(this._api);

  Future<Map<String, dynamic>> getDashboard({
    int page = 0,
    int size = 20,
  }) =>
      _api.get<Map<String, dynamic>>(
        '/fleet/dashboard',
        queryParameters: {'page': page, 'size': size},
        fromJson: _asMap,
      );

  Future<List<Map<String, dynamic>>> listDrivers() =>
      _api.get<List<Map<String, dynamic>>>(
        '/fleet/drivers',
        fromJson: _asMapList,
      );

  Future<List<Map<String, dynamic>>> listVehicles() =>
      _api.get<List<Map<String, dynamic>>>(
        '/fleet/vehicles',
        fromJson: _asMapList,
      );

  Future<Map<String, dynamic>> addDriver(int driverId) =>
      _api.post<Map<String, dynamic>>(
        '/fleet/drivers',
        data: {'driverId': driverId},
        fromJson: _asMap,
      );

  Future<void> removeDriver(int driverId) =>
      _api.delete('/fleet/drivers/$driverId');

  Map<String, dynamic> _asMap(dynamic json) {
    if (json is! Map) {
      throw const FormatException('Expected a fleet response object');
    }
    return Map<String, dynamic>.from(json);
  }

  List<Map<String, dynamic>> _asMapList(dynamic json) {
    if (json is! List) {
      throw const FormatException('Expected a fleet response list');
    }
    return json.map((item) {
      if (item is! Map) {
        throw const FormatException('Expected fleet list items to be objects');
      }
      return Map<String, dynamic>.from(item);
    }).toList();
  }
}

final fleetRepositoryProvider = Provider<FleetRepository>(
  (ref) => FleetRepository(ref.read(apiClientProvider)),
);

// ── Bookings ──────────────────────────────────────────────────────────────

class BookingRepository {
  final ApiClient _api;
  BookingRepository(this._api);

  Future<BookingPage> page({int page = 0, int size = 20}) async {
    final response = await _api.dio.get(
      '/bookings/page',
      queryParameters: {'page': page, 'size': size},
    );
    if (response.statusCode == 200) {
      final raw = response.data;
      final data = raw is Map && raw['data'] is Map ? raw['data'] : raw;
      return BookingPage.fromJson(Map<String, dynamic>.from(data as Map));
    }
    throw ApiException(
      message: 'Failed to load booking page',
      statusCode: response.statusCode,
    );
  }

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

  Future<BookingTripResolution> getTripForBooking(int bookingId) =>
      _api.get<BookingTripResolution>(
        '/bookings/$bookingId/trip',
        fromJson: (json) =>
            BookingTripResolution.fromJson(json as Map<String, dynamic>),
      );

  // Spring creates bookings transactionally when a bid is accepted.
  Future<Booking> create({
    required int cargoId,
    required int vehicleId,
    required int driverId,
    required double estimatedPrice,
  }) async => throw ApiException(
        message: 'Bookings are created by Spring when a bid is accepted.',
        statusCode: 501,
      );
}

final bookingRepositoryProvider = Provider<BookingRepository>(
  (ref) => BookingRepository(ref.read(apiClientProvider)),
);

// ── Trips ─────────────────────────────────────────────────────────────────

class TripRepository {
  final ApiClient _api;
  TripRepository(this._api);

  Future<int> getAuthorizedVehicleId(int tripId) async {
    final response = await _api.get<Map<String, dynamic>>(
      '/v1/tracking/trips/$tripId/vehicle',
      fromJson: (json) {
        if (json is! Map) {
          throw const FormatException(
              'Expected an authorized vehicle response object');
        }
        return Map<String, dynamic>.from(json);
      },
    );
    final vehicleId =
        (response['authorizedVehicleId'] ?? response['authorized_vehicle_id'])
            as num?;
    if (vehicleId == null || vehicleId <= 0) {
      throw const FormatException(
          'Tracking response did not include an authorized vehicle ID');
    }
    return vehicleId.toInt();
  }

  Future<void> reportGpsLocation({
    required int tripId,
    required int vehicleId,
    required double latitude,
    required double longitude,
    required DateTime timestamp,
    double? speed,
    double? accuracy,
    double? heading,
  }) async {
    final utcTimestamp = timestamp.toUtc().toIso8601String().split('.').first;
    await _api.post<Map<String, dynamic>>(
      '/v1/tracking/trips/$tripId/location',
      data: {
        'vehicleId': vehicleId,
        'latitude': latitude,
        'longitude': longitude,
        'timestamp': '${utcTimestamp}Z',
        if (speed != null && speed >= 0) 'speed': speed,
        if (accuracy != null && accuracy >= 0) 'accuracy': accuracy,
        if (heading != null && heading >= 0 && heading <= 360)
          'heading': heading,
      },
      fromJson: (json) {
        if (json is! Map) {
          throw const FormatException('Expected a GPS tracking response object');
        }
        return Map<String, dynamic>.from(json);
      },
    );
  }

  Future<TripPage> getOngoingTripsForCurrentDriver() =>
      _api.get<TripPage>(
        '/trips',
        queryParameters: {
          'status': 'ONGOING',
          'page': 0,
          'size': 3,
          'sortBy': 'id',
          'sortDir': 'asc',
        },
        fromJson: (json) => TripPage.fromJson(json as Map<String, dynamic>),
      );

  // POST /api/trips
  Future<Trip> start(int bookingId) async =>
      _api.post<Trip>(
        '/trips',
        data: {'bookingId': bookingId},
        fromJson: (json) => Trip.fromJson(json as Map<String, dynamic>),
      );

  // ✓ GET /trips/{id}
  Future<Trip> get(int tripId) async =>
      _api.get<Trip>('/trips/$tripId',
          fromJson: (json) => Trip.fromJson(json as Map<String, dynamic>));

  // POST /api/trips/{id}/start
  Future<Trip> begin(int tripId) async =>
      _api.post<Trip>('/trips/$tripId/start',
          fromJson: (json) => Trip.fromJson(json as Map<String, dynamic>));

  // ✓ POST /trips/{id}/gps
  Future<Trip> updateLocation(int tripId, double lat, double lng,
          {double? speed, double? accuracy, DateTime? timestamp}) async =>
      _api.post<Trip>(
        '/trips/$tripId/gps',
        data: {
          'latitude': lat,
          'longitude': lng,
          if (speed != null) 'speed': speed,
          if (accuracy != null) 'accuracy': accuracy,
          if (timestamp != null) 'timestamp': timestamp.toIso8601String(),
        },
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
      'stopOrder': stopOrder,
      'locationName': locationName,
      'agreedPrice': agreedPrice,
      'cargoRequestId': cargoRequestId,
      'pickupLatitude': pickupLat,
      'pickupLongitude': pickupLng,
    });
    if (response.statusCode == 201) {
      return _tripStopFromSpringResponse(response.data);
    }
    throw ApiException(
        message: 'Failed to add stop', statusCode: response.statusCode);
  }

  // ✓ POST /trips/{trip}/stops/{stop}/arrive
  Future<TripStop> arriveAtStop(int tripId, int stopId) async {
    final response =
        await _api.dio.post('/trips/$tripId/stops/$stopId/arrive');
    if (response.statusCode == 200) {
      return _tripStopFromSpringResponse(response.data);
    }
    throw ApiException(
        message: 'Failed to mark arrived', statusCode: response.statusCode);
  }

  // ✓ POST /trips/{trip}/stops/{stop}/load
  Future<TripStop> loadAtStop(int tripId, int stopId) async {
    final response =
        await _api.dio.post('/trips/$tripId/stops/$stopId/load');
    if (response.statusCode == 200) {
      return _tripStopFromSpringResponse(response.data);
    }
    throw ApiException(
        message: 'Failed to mark loaded', statusCode: response.statusCode);
  }

  // ✓ POST /trips/{trip}/stops/{stop}/complete
  Future<TripStop> completeStop(int tripId, int stopId) async {
    final response =
        await _api.dio.post('/trips/$tripId/stops/$stopId/complete');
    if (response.statusCode == 200) {
      return _tripStopFromSpringResponse(response.data);
    }
    throw ApiException(
        message: 'Failed to complete stop', statusCode: response.statusCode);
  }

  // ✓ DELETE /trips/{trip}/stops/{stop}
  Future<void> removeStop(int tripId, int stopId) async {
    final response =
        await _api.dio.delete('/trips/$tripId/stops/$stopId');
    if (response.statusCode != 204) {
      throw ApiException(
          message: 'Failed to remove stop', statusCode: response.statusCode);
    }
  }

  // Backhaul recommendation endpoints are outside the current Spring trip contract.
  Future<List<BackhaulRecommendation>> getBackhaulRecommendations(int tripId) async {
    return [];
  }

  // PATCH /recommendations/{id}/dismiss
  Future<void> dismissRecommendation(int recId) async {
    // No current Spring endpoint; preserve the UI without issuing a request.
  }

  // GET /trips/{trip}/location
  Future<TripLocation> getLocation(int tripId) async {
    final response = await _api.dio.get('/trips/$tripId/location');
    if (response.statusCode == 200) {
      final raw = response.data;
      final data = (raw is Map && raw.containsKey('data'))
          ? raw['data'] as Map<String, dynamic>
          : raw as Map<String, dynamic>;
      return TripLocation.fromJson(data);
    }
    throw ApiException(
        message: 'Failed to load trip location',
        statusCode: response.statusCode);
  }

  Future<List<Map<String, dynamic>>> getRoute(int tripId) async {
    final response = await _api.dio.get('/trips/$tripId/route', queryParameters: {'limit': 500});
    if (response.statusCode == 200) {
      final raw = response.data;
      final list = raw is Map && raw['data'] is List ? raw['data'] as List : raw as List;
      return list.whereType<Map>().map((item) => Map<String, dynamic>.from(item)).toList();
    }
    throw ApiException(message: 'Failed to load trip route', statusCode: response.statusCode);
  }

  Future<Map<String, dynamic>> getRouteAnalytics(int tripId) async {
    final response = await _api.dio.get('/trips/$tripId/route/analytics');
    if (response.statusCode == 200) {
      return Map<String, dynamic>.from(response.data as Map);
    }
    throw ApiException(message: 'Failed to load route analytics', statusCode: response.statusCode);
  }
}

TripStop _tripStopFromSpringResponse(dynamic response) {
  final raw = Map<String, dynamic>.from(response as Map);
  final nested = raw['stop'] ?? raw['data'];
  final source = nested is Map && nested['stop'] is Map
      ? Map<String, dynamic>.from(nested['stop'] as Map)
      : nested is Map
          ? Map<String, dynamic>.from(nested)
          : raw;

  return TripStop.fromJson({
    'id': source['id'],
    'trip_id': source['trip_id'] ?? source['tripId'],
    'cargo_request_id': source['cargo_request_id'] ??
        source['cargoRequestId'] ?? source['cargoInfo']?['cargoRequestId'],
    'stop_order': source['stop_order'] ?? source['stopOrder'],
    'location_name': source['location_name'] ?? source['locationName'],
    'pickup_lat': source['pickup_lat'] ?? source['pickupLatitude'],
    'pickup_lng': source['pickup_lng'] ?? source['pickupLongitude'],
    'agreed_price': source['agreed_price'] ?? source['agreedPrice'] ?? 0,
    'agreed_price_formatted': source['agreed_price_formatted'] ??
        'ETB ${(source['agreed_price'] ?? source['agreedPrice'] ?? 0)}',
    'status': source['status'] ?? source['stop_status'] ?? source['stopStatus'],
    'arrived_at': source['arrived_at'] ?? source['arrivalTime'],
    'completed_at': source['completed_at'] ?? source['completionTime'],
    'cargo_material': source['cargo_material'] ?? source['cargoInfo']?['materialType'],
    'cargo_weight': source['cargo_weight'] ?? source['cargoInfo']?['weightTons'],
    'shipper_name': source['shipper_name'] ?? source['cargoInfo']?['shipperName'],
  });
}

final tripRepositoryProvider = Provider<TripRepository>(
  (ref) => TripRepository(ref.read(apiClientProvider)),
);

// ── Payments ──────────────────────────────────────────────────────────────

class PaymentRepository {
  final ApiClient _api;
  PaymentRepository(this._api);

  // POST /api/payments. Amount is calculated by Spring; only manual cash is active.
  Future<Payment> create({
    required int bookingId,
    required String paymentMethod, // currently cash only
  }) async =>
      _api.post<Payment>(
        '/payments',
        data: {
          'bookingId': bookingId,
          'paymentMethod': paymentMethod,
        },
        fromJson: (json) => Payment.fromJson(json as Map<String, dynamic>),
      );

  // GET /api/payments/booking/{bookingId}
  Future<Payment> getByBooking(int bookingId) async =>
      _api.get<Payment>('/payments/booking/$bookingId',
          fromJson: (json) => Payment.fromJson(json as Map<String, dynamic>));

  Future<Payment> getByFreight(int freightId) async =>
      _api.get<Payment>('/payments/freight/$freightId',
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

  // ✓ GET /documents/driver/{driverId}
  Future<List<DriverDocument>> list(int driverId) async {
    final response = await _api.dio.get('/documents/driver/$driverId');
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

  // POST /driver/documents/upload (web and native multipart bytes).
  Future<DriverDocument> upload({
    required String documentType,
    required List<int> fileBytes,
    required String fileName,
  }) async {
    if (fileBytes.isEmpty || fileBytes.length > 5 * 1024 * 1024) {
      throw ApiException(message: 'Choose a non-empty file no larger than 5 MB.');
    }
    final mime = {'pdf': 'application/pdf', 'jpg': 'image/jpeg',
      'jpeg': 'image/jpeg', 'png': 'image/png'}[fileName.split('.').last.toLowerCase()];
    if (mime == null) throw ApiException(message: 'Choose a JPG, PNG or PDF file.');
    final formData = dio_pkg.FormData.fromMap({
      'documentType': documentType.toUpperCase(),
      'file': dio_pkg.MultipartFile.fromBytes(fileBytes, filename: fileName,
        contentType: dio_pkg.DioMediaType.parse(mime)),
    });
    final response = await _api.dio.post(
      '/driver/documents/upload',
      data: formData,
      options: dio_pkg.Options(contentType: 'multipart/form-data'),
    );
    if (response.statusCode == 201 || response.statusCode == 200) {
      final raw = response.data;
      final data = (raw is Map && raw.containsKey('data')) ? raw['data'] : raw;
      return DriverDocument.fromJson(data as Map<String, dynamic>);
    }
    throw ApiException(
        message: response.data is Map ? response.data['message']?.toString() ?? 'Upload failed' : 'Upload failed',
        statusCode: response.statusCode);
  }
}

class VehicleDocumentRepository {
  final ApiClient _api;
  VehicleDocumentRepository(this._api);

  Future<List<VehicleDocument>> list(int vehicleId) async {
    final response = await _api.dio.get('/vehicles/$vehicleId/documents');
    if (response.statusCode == 200) {
      final raw = response.data;
      final list = (raw is Map && raw.containsKey('data'))
          ? raw['data'] as List
          : raw as List;
      return list
          .map((e) => VehicleDocument.fromJson(e as Map<String, dynamic>))
          .toList();
    }
    throw ApiException(
      message: 'Failed to load vehicle documents',
      statusCode: response.statusCode,
    );
  }

  Future<VehicleDocument> upload({
    required int vehicleId,
    required String documentType,
    required List<int> fileBytes,
    required String fileName,
  }) async {
    if (fileBytes.isEmpty || fileBytes.length > 5 * 1024 * 1024) {
      throw ApiException(message: 'Choose a non-empty file no larger than 5 MB.');
    }
    final mime = {'pdf': 'application/pdf', 'jpg': 'image/jpeg',
      'jpeg': 'image/jpeg', 'png': 'image/png'}[fileName.split('.').last.toLowerCase()];
    if (mime == null) {
      throw ApiException(message: 'Choose a JPG, PNG or PDF file.');
    }
    final formData = dio_pkg.FormData.fromMap({
      'documentType': documentType,
      'file': dio_pkg.MultipartFile.fromBytes(fileBytes, filename: fileName,
        contentType: dio_pkg.DioMediaType.parse(mime)),
    });
    final response = await _api.dio.post(
      '/vehicles/$vehicleId/documents',
      data: formData,
      options: dio_pkg.Options(contentType: 'multipart/form-data'),
    );
    if (response.statusCode == 201 || response.statusCode == 200) {
      final raw = response.data;
      final data = (raw is Map && raw.containsKey('data')) ? raw['data'] : raw;
      return VehicleDocument.fromJson(data as Map<String, dynamic>);
    }
    throw ApiException(
      message: response.data is Map
          ? response.data['message']?.toString() ?? 'Upload failed'
          : 'Upload failed',
      statusCode: response.statusCode,
    );
  }
}

final documentRepositoryProvider = Provider<DocumentRepository>(
  (ref) => DocumentRepository(ref.read(apiClientProvider)),
);

final vehicleDocumentRepositoryProvider = Provider<VehicleDocumentRepository>(
  (ref) => VehicleDocumentRepository(ref.read(apiClientProvider)),
);

// ── Bids ──────────────────────────────────────────────────────────────────

class BidRepository {
  final ApiClient _api;
  BidRepository(this._api);

  // GET /api/freight/{freightId}/bids/page
  Future<BidPage> listForCargo(int cargoId, {int page = 0, int size = 20}) async {
    final response = await _api.dio.get(
      '/freight/$cargoId/bids/page',
      queryParameters: {'page': page, 'size': size},
    );
    if (response.statusCode == 200) {
      return BidPage.fromJson(Map<String, dynamic>.from(response.data as Map));
    }
    throw ApiException(
        message: 'Failed to load bids', statusCode: response.statusCode);
  }

  // POST /api/freight/{freightId}/bids — driver places bid
  Future<Bid> place({
    required int cargoId,
    required int vehicleId,
    required double amount,
    String? note,
  }) async =>
      _api.post<Bid>(
        '/freight/$cargoId/bids',
        data: {
          'vehicleId': vehicleId,
          'amount': amount,
          if (note != null && note.isNotEmpty) 'note': note,
        },
        fromJson: (json) => Bid.fromJson(Map<String, dynamic>.from(json as Map)),
      );

  // POST /api/freight/{freightId}/bids — with availableDatetime
  Future<Bid> placeWithDatetime({
    required int cargoId,
    required int vehicleId,
    required double amount,
    String? note,
    DateTime? availableDatetime,
  }) async =>
      _api.post<Bid>(
        '/freight/$cargoId/bids',
        data: {
          'vehicleId': vehicleId,
          'amount': amount,
          if (note != null && note.isNotEmpty) 'note': note,
          if (availableDatetime != null)
            'availableDatetime': availableDatetime.toIso8601String(),
        },
        fromJson: (json) => Bid.fromJson(Map<String, dynamic>.from(json as Map)),
      );

  // ✓ PATCH /bids/{bidId}/accept  — shipper accepts, returns Booking JSON
  Future<Booking> acceptBid(int bidId) async {
    final response = await _api.dio.patch('/bids/$bidId/accept');
    if (response.statusCode == 200) {
      final raw = response.data;
      final data = raw is Map && raw['data'] is Map ? raw['data'] : raw;
      return Booking.fromJson(Map<String, dynamic>.from(data as Map));
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
      'counterAmount': counterAmount,
      if (counterNote != null && counterNote.isNotEmpty) 'counterNote': counterNote,
    });
    if (response.statusCode == 200) {
      final raw = response.data;
      final data = raw is Map && raw['data'] is Map ? raw['data'] : raw;
      return Bid.fromJson(Map<String, dynamic>.from(data as Map));
    }
    throw ApiException(
        message: 'Failed to send counter-offer', statusCode: response.statusCode);
  }

  // ✓ PATCH /bids/{bidId}/accept-counter — accept the current counter-offer
  Future<Booking> acceptCounter(int bidId) async {
    final response = await _api.dio.patch('/bids/$bidId/accept-counter');
    if (response.statusCode == 200) {
      final raw = response.data;
      final data = raw is Map && raw['data'] is Map ? raw['data'] : raw;
      return Booking.fromJson(Map<String, dynamic>.from(data as Map));
    }
    throw ApiException(
        message: 'Failed to accept counter-offer', statusCode: response.statusCode);
  }

  // ✓ PATCH /bids/{bidId} — driver updates their own pending bid
  Future<Bid> update(int bidId, {required double amount, String? note}) async {
    final response = await _api.dio.patch('/bids/$bidId', data: {
      'amount': amount,
      if (note != null && note.isNotEmpty) 'note': note,
    });
    if (response.statusCode == 200) {
      final raw = response.data;
      final data = (raw is Map && raw.containsKey('data')) ? raw['data'] : raw;
      return Bid.fromJson(Map<String, dynamic>.from(data as Map));
    }
    throw ApiException(
        message: 'Failed to update bid', statusCode: response.statusCode);
  }

  // ✓ GET /driver/bids — driver sees all their bids with counter-offer state
  Future<List<Bid>> listMyBids({int page = 0, int size = 100}) async {
    final response = await _api.dio.get('/driver/bids', queryParameters: {
      'page': page,
      'size': size,
    });
    if (response.statusCode == 200) {
      final raw = response.data;
      final list = raw is Map && raw['content'] is List
          ? raw['content'] as List
          : raw is Map && raw['data'] is List
              ? raw['data'] as List
              : raw is List ? raw : <dynamic>[];
      final result = <Bid>[];
      for (final item in list) {
        try {
          if (item is Map<String, dynamic>) {
            result.add(Bid.fromJson(item));
          } else if (item is Map) {
            result.add(Bid.fromJson(Map<String, dynamic>.from(item)));
          }
        } catch (_) {}
      }
      return result;
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

// ── NotificationRepository ────────────────────────────────────────────────

class NotificationRepository {
  final ApiClient _api;
  NotificationRepository(this._api);

  Future<({List<AppNotification> items, int unreadCount})> list() async {
    final response = await _api.dio.get('/notifications');
    if (response.statusCode != 200) {
      throw ApiException(message: 'Failed to load notifications', statusCode: response.statusCode);
    }
    final raw = Map<String, dynamic>.from(response.data as Map);
    final List items = raw['data'] as List? ?? raw['notifications'] as List? ?? [];
    return (
      items: items
          .map((e) => AppNotification.fromJson(Map<String, dynamic>.from(e as Map)))
          .toList(),
      unreadCount: ((raw['unread_count'] ?? raw['unreadCount']) as num?)?.toInt() ?? 0,
    );
  }

  Future<void> markRead(String id) async {
    await _api.dio.patch('/notifications/$id/read');
  }

  Future<void> markAllRead() async {
    await _api.dio.patch('/notifications/read-all');
  }
}

final notificationRepositoryProvider = Provider<NotificationRepository>(
  (ref) => NotificationRepository(ref.read(apiClientProvider)),
);

class SharedWorkflowRepository {
  final ApiClient _api;
  SharedWorkflowRepository(this._api);

  Future<MessageHistoryPage> messages(
    int freightId, {
    int limit = 50,
    String? before,
    String? after,
  }) async {
    final query = <String, String>{'limit': '$limit'};
    if (before != null) query['before'] = before;
    if (after != null) query['after'] = after;
    final response = await _api.get<Map<String, dynamic>>(
      '/messages/$freightId?${Uri(queryParameters: query).query}',
    );
    return MessageHistoryPage.fromJson(response);
  }

  Future<ChatMessage> sendMessage({required int freightId, int? receiverId, required String content}) =>
      _api.post<ChatMessage>('/messages', data: {
        'freightId': freightId, if (receiverId != null) 'receiverId': receiverId,
        'content': content, 'type': 'text',
      }, fromJson: (json) => ChatMessage.fromJson(Map<String, dynamic>.from(json as Map)));

  Future<ContractView> contract(int freightId) => _api.get<ContractView>('/contracts/$freightId',
      fromJson: (json) => ContractView.fromJson(Map<String, dynamic>.from(json as Map)));

  Future<List<DisputeView>> disputes(int freightId) async {
    final response = await _api.get<Map<String, dynamic>>('/disputes?freightId=$freightId');
    final list = response['disputes'] as List? ?? [];
    return list.map((e) => DisputeView.fromJson(Map<String, dynamic>.from(e as Map))).toList();
  }

  Future<DisputeView> fileDispute({required int freightId, required String reason, String? description}) =>
      _api.post<DisputeView>('/disputes', data: {
        'freightId': freightId, 'reason': reason, if (description != null) 'description': description,
      }, fromJson: (json) => DisputeView.fromJson(Map<String, dynamic>.from(json as Map)));
}

final sharedWorkflowRepositoryProvider = Provider<SharedWorkflowRepository>(
  (ref) => SharedWorkflowRepository(ref.read(apiClientProvider)),
);

// ── Routing ───────────────────────────────────────────────────────────────

class RoutingRepository {
  final ApiClient _api;
  RoutingRepository(this._api);

  /// GET /routing/route — OSRM route with fallback.
  Future<RouteResult?> getRoute({
    required double fromLat,
    required double fromLng,
    required double toLat,
    required double toLng,
  }) async {
    try {
      final resp = await _api.dio.get('/routing/route', queryParameters: {
        'fromLat':  fromLat,
        'fromLng':  fromLng,
        'toLat':    toLat,
        'toLng':    toLng,
        'from_lat': fromLat,
        'from_lng': fromLng,
        'to_lat':   toLat,
        'to_lng':   toLng,
      });
      if (resp.statusCode == 200 && resp.data is Map) {
        return RouteResult.fromJson(Map<String, dynamic>.from(resp.data as Map));
      }
      return null;
    } catch (_) {
      return null;
    }
  }

  /// GET /routing/search?q= — place name search via Nominatim.
  Future<List<PlaceResult>> searchPlace(String query) async {
    if (query.trim().isEmpty) return [];
    try {
      final resp = await _api.dio.get('/routing/search',
          queryParameters: {'q': query.trim()});
      if (resp.statusCode == 200 && resp.data is Map) {
        final raw = (resp.data as Map)['places'] as List? ?? [];
        return raw
            .whereType<Map>()
            .map((p) => PlaceResult.fromJson(Map<String, dynamic>.from(p)))
            .toList();
      }
      return [];
    } catch (_) {
      return [];
    }
  }

  /// GET /routing/reverse?lat=&lng= — reverse geocode via Nominatim.
  Future<Map<String, dynamic>?> reverseGeocode(double lat, double lng) async {
    try {
      final resp = await _api.dio.get('/routing/reverse',
          queryParameters: {'lat': lat, 'lng': lng});
      if (resp.statusCode == 200 && resp.data is Map) {
        return Map<String, dynamic>.from(resp.data as Map);
      }
      return null;
    } catch (_) {
      return null;
    }
  }

  /// GET /vehicles/nearby?latitude=&longitude=&radius=
  Future<List<NearbyTruck>> nearbyTrucks({
    required double lat,
    required double lng,
    double radiusKm = 100,
    String? category,
  }) async {
    try {
      final resp = await _api.dio.get('/vehicles/nearby', queryParameters: {
        'latitude': lat,
        'longitude': lng,
        'radius': radiusKm,
      });
      if (resp.statusCode == 200 && resp.data is List) {
        return (resp.data as List).whereType<Map>().map((vehicle) {
          final data = Map<String, dynamic>.from(vehicle);
          final vehicleCategory = data['vehicleCategory']?.toString();
          return NearbyTruck.fromJson({
            'vehicle_id': data['id'],
            'driver_id': data['userId'],
            'driver_name': 'Driver ${data['userId'] ?? ''}'.trim(),
            'vehicle_type': data['truckType'],
            'vehicle_plate': data['plateNumber'],
            'vehicle_category': vehicleCategory,
            'latitude': data['latitude'],
            'longitude': data['longitude'],
            'current_city': data['currentCity'],
            'rating': data['rating'],
            'distance_km': data['distanceKm'] ?? 0,
            'last_seen': data['lastLocationAt'] ?? '',
          });
        }).where((truck) => category == null ||
            truck.vehicleCategory?.toLowerCase() == category.toLowerCase()).toList();
      }
      return [];
    } catch (_) {
      return [];
    }
  }
}

final routingRepositoryProvider = Provider<RoutingRepository>(
  (ref) => RoutingRepository(ref.read(apiClientProvider)),
);
