// ─────────────────────────────────────────────────────────────
//  EthioLoadAI – Data models aligned with the Laravel API
//  Resource shapes come from:
//    UserResource, CargoResource, BookingResource, VehicleResource
// ─────────────────────────────────────────────────────────────

// ── User ──────────────────────────────────────────────────────────────────

class User {
  final int id;
  final String fullName;
  final String email;
  final String phone;
  /// 'shipper' | 'driver' | 'admin'
  final String role;
  final String? location;
  final bool verificationStatus;

  User({
    required this.id,
    required this.fullName,
    required this.email,
    required this.phone,
    required this.role,
    this.location,
    this.verificationStatus = false,
  });

  bool get isAdmin => role.toLowerCase() == 'admin';
  bool get isDriver => role.toLowerCase() == 'driver';
  bool get isShipper => role.toLowerCase() == 'shipper';

  // UserResource returns: id, full_name, email, phone, role,
  //                       location, verification_status
  factory User.fromJson(Map<String, dynamic> json) => User(
        id: json['id'] as int,
        fullName: (json['full_name'] ?? json['name'] ?? '') as String,
        email: json['email'] as String,
        phone: (json['phone'] ?? '') as String,
        role: (json['role'] ?? 'shipper') as String,
        location: json['location'] as String?,
        verificationStatus: json['verification_status'] == true ||
            json['verification_status'] == 1,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'full_name': fullName,
        'email': email,
        'phone': phone,
        'role': role,
        'location': location,
        'verification_status': verificationStatus,
      };
}

// ── Auth ──────────────────────────────────────────────────────────────────

class AuthResponse {
  final String token;
  final User user;

  AuthResponse({required this.token, required this.user});

  /// POST /register and POST /login both return { token, user }
  factory AuthResponse.fromJson(Map<String, dynamic> json) => AuthResponse(
        token: json['token'] as String,
        user: User.fromJson(json['user'] as Map<String, dynamic>),
      );
}

// ── Vehicle ───────────────────────────────────────────────────────────────

class Vehicle {
  final int id;
  final int userId;
  /// 'flatbed' | 'tanker' | 'refrigerated' | 'container' etc.
  final String truckType;
  final String plateNumber;
  final double capacity;
  final String currentCity;
  final double? latitude;
  final double? longitude;
  /// 'available' | 'busy' | 'offline'
  final String availabilityStatus;
  final double rating;

  Vehicle({
    required this.id,
    required this.userId,
    required this.truckType,
    required this.plateNumber,
    required this.capacity,
    required this.currentCity,
    this.latitude,
    this.longitude,
    required this.availabilityStatus,
    required this.rating,
  });

  factory Vehicle.fromJson(Map<String, dynamic> json) => Vehicle(
        id: json['id'] as int,
        userId: json['user_id'] as int,
        truckType: (json['truck_type'] ?? '') as String,
        plateNumber: (json['plate_number'] ?? '') as String,
        // capacity can be int or double from the API
        capacity: double.tryParse(json['capacity'].toString()) ?? 0.0,
        currentCity: (json['current_city'] ?? '') as String,
        latitude: json['latitude'] != null
            ? double.tryParse(json['latitude'].toString())
            : null,
        longitude: json['longitude'] != null
            ? double.tryParse(json['longitude'].toString())
            : null,
        availabilityStatus:
            (json['availability_status'] ?? 'available') as String,
        // rating comes as "0.00" (string) or 0 (num) depending on the endpoint
        rating: double.tryParse(json['rating'].toString()) ?? 0.0,
      );
}

// ── CargoRequest ──────────────────────────────────────────────────────────

class CargoRequest {
  final int id;
  final int userId;
  final String pickupLocation;
  final String destination;
  /// 'perishable' | 'fragile' | 'electronics' | 'construction' | 'general'
  final String materialType;
  final double weight;
  /// 'low' | 'normal' | 'high' | 'express'
  final String urgencyLevel;
  final double? budget;
  /// 'pending' | 'matched' | 'completed'
  final String status;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  CargoRequest({
    required this.id,
    required this.userId,
    required this.pickupLocation,
    required this.destination,
    required this.materialType,
    required this.weight,
    required this.urgencyLevel,
    this.budget,
    required this.status,
    this.createdAt,
    this.updatedAt,
  });

  factory CargoRequest.fromJson(Map<String, dynamic> json) => CargoRequest(
        id: json['id'] as int,
        userId: json['user_id'] as int,
        pickupLocation: json['pickup_location'] as String,
        destination: json['destination'] as String,
        materialType: json['material_type'] as String,
        weight: double.tryParse(json['weight'].toString()) ?? 0.0,
        urgencyLevel: (json['urgency_level'] ?? 'normal') as String,
        budget: json['budget'] != null
            ? double.tryParse(json['budget'].toString())
            : null,
        status: (json['status'] ?? 'pending') as String,
        createdAt: json['created_at'] != null
            ? DateTime.tryParse(json['created_at'] as String)
            : null,
        updatedAt: json['updated_at'] != null
            ? DateTime.tryParse(json['updated_at'] as String)
            : null,
      );

  Map<String, dynamic> toCreateJson() => {
        'pickup_location': pickupLocation,
        'destination': destination,
        'material_type': materialType,
        'weight': weight,
        'urgency_level': urgencyLevel,
        if (budget != null) 'budget': budget,
      };
}

// ── Booking ───────────────────────────────────────────────────────────────

class Booking {
  final int id;
  final int cargoId;
  final int vehicleId;
  final int driverId;
  /// 'pending' | 'confirmed' | 'in_transit' | 'completed' | 'cancelled'
  final String bookingStatus;
  final double estimatedPrice;
  final double? commissionFee;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  Booking({
    required this.id,
    required this.cargoId,
    required this.vehicleId,
    required this.driverId,
    required this.bookingStatus,
    required this.estimatedPrice,
    this.commissionFee,
    this.createdAt,
    this.updatedAt,
  });

  factory Booking.fromJson(Map<String, dynamic> json) => Booking(
        id: json['id'] as int,
        cargoId: json['cargo_id'] as int,
        vehicleId: json['vehicle_id'] as int,
        driverId: json['driver_id'] as int,
        bookingStatus: (json['booking_status'] ?? 'pending') as String,
        estimatedPrice: double.tryParse(json['estimated_price'].toString()) ?? 0.0,
        commissionFee: json['commission_fee'] != null
            ? double.tryParse(json['commission_fee'].toString())
            : null,
        createdAt: json['created_at'] != null
            ? DateTime.tryParse(json['created_at'] as String)
            : null,
        updatedAt: json['updated_at'] != null
            ? DateTime.tryParse(json['updated_at'] as String)
            : null,
      );
}

// ── Trip ──────────────────────────────────────────────────────────────────

class Trip {
  final int id;
  final int bookingId;
  /// 'ongoing' | 'completed'
  final String tripStatus;
  final String? startLocation;
  final String? destination;
  final List<dynamic>? routeData;
  final DateTime? startTime;
  final DateTime? endTime;
  final DateTime? createdAt;

  Trip({
    required this.id,
    required this.bookingId,
    required this.tripStatus,
    this.startLocation,
    this.destination,
    this.routeData,
    this.startTime,
    this.endTime,
    this.createdAt,
  });

  factory Trip.fromJson(Map<String, dynamic> json) => Trip(
        id: json['id'] as int,
        bookingId: json['booking_id'] as int,
        tripStatus: (json['trip_status'] ?? 'ongoing') as String,
        startLocation: json['start_location'] as String?,
        destination: json['destination'] as String?,
        routeData: json['route_data'] is List
            ? json['route_data'] as List
            : null,
        startTime: json['start_time'] != null
            ? DateTime.tryParse(json['start_time'] as String)
            : null,
        endTime: json['end_time'] != null
            ? DateTime.tryParse(json['end_time'] as String)
            : null,
        createdAt: json['created_at'] != null
            ? DateTime.tryParse(json['created_at'] as String)
            : null,
      );
}

// ── Payment ───────────────────────────────────────────────────────────────

class Payment {
  final int id;
  final int bookingId;
  final double amount;
  /// 'in_app' | 'cash' | 'bank_transfer' | 'telebirr' | 'cbe_birr' | 'chapa'
  final String paymentMethod;
  /// 'pending' | 'paid' | 'failed'
  final String paymentStatus;
  final DateTime? createdAt;

  Payment({
    required this.id,
    required this.bookingId,
    required this.amount,
    required this.paymentMethod,
    required this.paymentStatus,
    this.createdAt,
  });

  factory Payment.fromJson(Map<String, dynamic> json) => Payment(
        id: json['id'] as int,
        bookingId: json['booking_id'] as int,
        amount: double.tryParse(json['amount'].toString()) ?? 0.0,
        paymentMethod: (json['payment_method'] ?? 'cash') as String,
        paymentStatus: (json['payment_status'] ?? 'pending') as String,
        createdAt: json['created_at'] != null
            ? DateTime.tryParse(json['created_at'] as String)
            : null,
      );
}

// ── AI Recommendation result ──────────────────────────────────────────────

class TruckRecommendation {
  final int truckId;
  final String driverName;
  final String plateNumber;
  final double capacity;
  final double distanceKm;
  final int estimatedPrice;
  final double score;

  TruckRecommendation({
    required this.truckId,
    required this.driverName,
    required this.plateNumber,
    required this.capacity,
    required this.distanceKm,
    required this.estimatedPrice,
    required this.score,
  });

  factory TruckRecommendation.fromJson(Map<String, dynamic> json) =>
      TruckRecommendation(
        truckId: json['truck_id'] as int,
        driverName: json['driver_name'] as String,
        plateNumber: json['plate_number'] as String,
        capacity: (json['capacity'] as num).toDouble(),
        distanceKm: (json['distance_km'] as num).toDouble(),
        estimatedPrice: json['estimated_price'] as int,
        score: (json['score'] as num).toDouble(),
      );
}

class BackhaulOpportunity {
  final int cargoId;
  final String pickupLocation;
  final String destination;
  final double weight;
  final double price;
  final double score;

  BackhaulOpportunity({
    required this.cargoId,
    required this.pickupLocation,
    required this.destination,
    required this.weight,
    required this.price,
    required this.score,
  });

  factory BackhaulOpportunity.fromJson(Map<String, dynamic> json) =>
      BackhaulOpportunity(
        cargoId: json['cargo_id'] as int,
        pickupLocation: json['pickup_location'] as String,
        destination: json['destination'] as String,
        weight: (json['weight'] as num).toDouble(),
        price: (json['price'] as num).toDouble(),
        score: (json['score'] as num).toDouble(),
      );
}
