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
  /// 'shipper' | 'driver' | 'admin' | 'fleet_owner'
  final String role;
  final String? location;
  final bool verificationStatus;
  final bool isActive;

  User({
    required this.id,
    required this.fullName,
    required this.email,
    required this.phone,
    required this.role,
    this.location,
    this.verificationStatus = false,
    this.isActive = true,
  });

  bool get isAdmin      => role.toLowerCase() == 'admin';
  bool get isDriver     => role.toLowerCase() == 'driver';
  bool get isShipper    => role.toLowerCase() == 'shipper';
  bool get isFleetOwner => role.toLowerCase() == 'fleet_owner';

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
        isActive: json['is_active'] == true || json['is_active'] == 1,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'full_name': fullName,
        'email': email,
        'phone': phone,
        'role': role,
        'location': location,
        'verification_status': verificationStatus,
        'is_active': isActive,
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
  final String? vehicleCategory;

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
    this.vehicleCategory,
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
        vehicleCategory: json['vehicle_category'] as String?,
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
  /// 'fixed' | 'negotiable'
  final String priceType;
  final DateTime? bidDeadline;
  /// 'pending' | 'matched' | 'completed'
  final String status;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  final String serviceType;
  final String? city;
  final String? pickupArea;
  final String? dropoffArea;
  final DateTime? preferredDate;
  final String? itemsDescription;
  final String? vehicleTypeNeeded;

  CargoRequest({
    required this.id,
    required this.userId,
    required this.pickupLocation,
    required this.destination,
    required this.materialType,
    required this.weight,
    required this.urgencyLevel,
    this.budget,
    this.priceType = 'negotiable',
    this.bidDeadline,
    required this.status,
    this.createdAt,
    this.updatedAt,
    this.serviceType = 'intercity',
    this.city,
    this.pickupArea,
    this.dropoffArea,
    this.preferredDate,
    this.itemsDescription,
    this.vehicleTypeNeeded,
  });

  factory CargoRequest.fromJson(Map<String, dynamic> json) => CargoRequest(
        id: json['id'] as int,
        userId: json['user_id'] as int,
        pickupLocation: (json['pickup_location'] ?? '') as String,
        destination: (json['destination'] ?? '') as String,
        materialType: (json['material_type'] ?? '') as String,
        weight: double.tryParse(json['weight']?.toString() ?? '0') ?? 0.0,
        urgencyLevel: (json['urgency_level'] ?? 'normal') as String,
        budget: json['budget'] != null
            ? double.tryParse(json['budget'].toString())
            : null,
        priceType: (json['price_type'] ?? 'negotiable') as String,
        bidDeadline: json['bid_deadline'] != null
            ? DateTime.tryParse(json['bid_deadline'] as String)
            : null,
        status: (json['status'] ?? 'pending') as String,
        createdAt: json['created_at'] != null
            ? DateTime.tryParse(json['created_at'] as String)
            : null,
        updatedAt: json['updated_at'] != null
            ? DateTime.tryParse(json['updated_at'] as String)
            : null,
        serviceType: (json['service_type'] ?? 'intercity') as String,
        city: json['city'] as String?,
        pickupArea: json['pickup_area'] as String?,
        dropoffArea: json['dropoff_area'] as String?,
        preferredDate: json['preferred_date'] != null
            ? DateTime.tryParse(json['preferred_date'] as String)
            : null,
        itemsDescription: json['items_description'] as String?,
        vehicleTypeNeeded: json['vehicle_type_needed'] as String?,
      );

  Map<String, dynamic> toCreateJson() => {
        'pickup_location': pickupLocation,
        'destination': destination,
        'material_type': materialType,
        'weight': weight,
        'urgency_level': urgencyLevel,
        if (budget != null) 'budget': budget,
        'service_type': serviceType,
        if (city != null) 'city': city,
        if (pickupArea != null) 'pickup_area': pickupArea,
        if (dropoffArea != null) 'dropoff_area': dropoffArea,
        if (preferredDate != null)
          'preferred_date': preferredDate!.toIso8601String().split('T')[0],
        if (itemsDescription != null) 'items_description': itemsDescription,
        if (vehicleTypeNeeded != null) 'vehicle_type_needed': vehicleTypeNeeded,
      };
}

// ── Booking ───────────────────────────────────────────────────────────────

class Booking {
  final int id;
  final int cargoId;
  final int vehicleId;
  final int driverId;
  final String bookingStatus;
  final double estimatedPrice;
  final double? commissionFee;
  final int? tripId;
  final String? tripStatus;
  /// 'single' | 'multi_stop'
  final String tripType;
  final int tripTotalStops;
  final int tripCompletedStops;
  final double? tripTotalAmount;
  // Shipper's specific stop info (multi-stop trips)
  final int? myStopOrder;
  final String? myStopStatus;
  final String? myStopLocation;
  final String? pickupLocation;
  final String? destination;
  final String? materialType;
  final double? weight;
  final String? urgencyLevel;
  // Contact info — shown after accepted/confirmed
  final String? driverPhone;
  final String? driverName;
  final String? shipperPhone;
  final String? shipperName;
  // Payment
  /// 'telebirr' | 'bank_transfer' | 'cash' | null
  final String? paymentMethod;
  // Rating
  final bool hasRating;
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
    this.tripId,
    this.tripStatus,
    this.tripType = 'single',
    this.tripTotalStops = 1,
    this.tripCompletedStops = 0,
    this.tripTotalAmount,
    this.myStopOrder,
    this.myStopStatus,
    this.myStopLocation,
    this.pickupLocation,
    this.destination,
    this.materialType,
    this.weight,
    this.urgencyLevel,
    this.driverPhone,
    this.driverName,
    this.shipperPhone,
    this.shipperName,
    this.paymentMethod,
    this.hasRating = false,
    this.createdAt,
    this.updatedAt,
  });

  bool get hasTripStarted => tripId != null;
  bool get isTripOngoing  => tripStatus == 'ongoing';
  bool get isMultiStop    => tripType == 'multi_stop';

  String get routeLabel {
    if (pickupLocation != null && destination != null) {
      return '$pickupLocation → $destination';
    }
    return 'Cargo #$cargoId';
  }

  factory Booking.fromJson(Map<String, dynamic> json) => Booking(
        id: json['id'] as int,
        cargoId: json['cargo_id'] as int,
        vehicleId: json['vehicle_id'] as int,
        driverId: json['driver_id'] as int,
        bookingStatus: (json['booking_status'] ?? 'pending') as String,
        estimatedPrice:
            double.tryParse(json['estimated_price'].toString()) ?? 0.0,
        commissionFee: json['commission_fee'] != null
            ? double.tryParse(json['commission_fee'].toString())
            : null,
        tripId: json['trip_id'] as int?,
        tripStatus: json['trip_status'] as String?,
        tripType: (json['trip_type'] ?? 'single') as String,
        tripTotalStops: (json['trip_total_stops'] ?? 1) as int,
        tripCompletedStops: (json['trip_completed_stops'] ?? 0) as int,
        tripTotalAmount: json['trip_total_amount'] != null
            ? double.tryParse(json['trip_total_amount'].toString())
            : null,
        myStopOrder: json['my_stop_order'] as int?,
        myStopStatus: json['my_stop_status'] as String?,
        myStopLocation: json['my_stop_location'] as String?,
        pickupLocation: json['pickup_location'] as String?,
        destination: json['destination'] as String?,
        materialType: json['material_type'] as String?,
        weight: json['weight'] != null
            ? double.tryParse(json['weight'].toString())
            : null,
        urgencyLevel: json['urgency_level'] as String?,
        driverPhone: json['driver_phone'] as String?,
        driverName: json['driver_name'] as String?,
        shipperPhone: json['shipper_phone'] as String?,
        shipperName: json['shipper_name'] as String?,
        paymentMethod: json['payment_method'] as String?,
        hasRating: json['has_rating'] == true,
        createdAt: json['created_at'] != null
            ? DateTime.tryParse(json['created_at'] as String)
            : null,
        updatedAt: json['updated_at'] != null
            ? DateTime.tryParse(json['updated_at'] as String)
            : null,
      );
}

// ── DriverDocument ────────────────────────────────────────────────────────

class DriverDocument {
  final int id;
  final int userId;
  /// 'license' | 'national_id' | 'vehicle_registration' | 'insurance' | 'tin'
  final String documentType;
  final String documentLabel;
  final String originalName;
  /// 'pending' | 'approved' | 'rejected'
  final String status;
  final String? rejectionReason;
  final String fileUrl;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  DriverDocument({
    required this.id,
    required this.userId,
    required this.documentType,
    required this.documentLabel,
    required this.originalName,
    required this.status,
    this.rejectionReason,
    required this.fileUrl,
    this.createdAt,
    this.updatedAt,
  });

  bool get isPending  => status == 'pending';
  bool get isApproved => status == 'approved';
  bool get isRejected => status == 'rejected';

  factory DriverDocument.fromJson(Map<String, dynamic> json) => DriverDocument(
        id:              json['id'] as int,
        userId:          json['user_id'] as int,
        documentType:    json['document_type'] as String,
        documentLabel:   (json['document_label'] ?? json['document_type']) as String,
        originalName:    json['original_name'] as String,
        status:          (json['status'] ?? 'pending') as String,
        rejectionReason: json['rejection_reason'] as String?,
        fileUrl:         json['file_url'] as String,
        createdAt: json['created_at'] != null
            ? DateTime.tryParse(json['created_at'] as String)
            : null,
        updatedAt: json['updated_at'] != null
            ? DateTime.tryParse(json['updated_at'] as String)
            : null,
      );
}

// ── Backhaul Recommendation ───────────────────────────────────────────────

class BackhaulCargo {
  final int id;
  final String pickupLocation;
  final String destination;
  final String materialType;
  final double weight;
  final String urgencyLevel;
  final double? budget;

  BackhaulCargo({
    required this.id,
    required this.pickupLocation,
    required this.destination,
    required this.materialType,
    required this.weight,
    required this.urgencyLevel,
    this.budget,
  });

  factory BackhaulCargo.fromJson(Map<String, dynamic> j) => BackhaulCargo(
        id:             j['id'] as int,
        pickupLocation: j['pickup_location'] as String,
        destination:    j['destination'] as String,
        materialType:   j['material_type'] as String,
        weight:         double.tryParse(j['weight'].toString()) ?? 0.0,
        urgencyLevel:   (j['urgency_level'] ?? 'normal') as String,
        budget:         j['budget'] != null
            ? double.tryParse(j['budget'].toString())
            : null,
      );
}

class BackhaulRecommendation {
  final int id;
  final double score;
  /// 'pending' | 'viewed' | 'bid_placed' | 'dismissed'
  final String status;
  final BackhaulCargo cargo;
  final double? distanceKm;
  final String? urgency;
  final double? estimatedPriceMin;
  final double? estimatedPriceMax;
  final String? pickupLocationName;

  BackhaulRecommendation({
    required this.id,
    required this.score,
    required this.status,
    required this.cargo,
    this.distanceKm,
    this.urgency,
    this.estimatedPriceMin,
    this.estimatedPriceMax,
    this.pickupLocationName,
  });

  factory BackhaulRecommendation.fromJson(Map<String, dynamic> j) {
    final meta  = j['metadata'] as Map<String, dynamic>? ?? {};
    final range = meta['estimated_price_range'] as Map<String, dynamic>?;
    return BackhaulRecommendation(
      id:                  j['id'] as int,
      score:               double.tryParse(j['score'].toString()) ?? 0.0,
      status:              (j['status'] ?? 'pending') as String,
      cargo:               BackhaulCargo.fromJson(
                               j['cargo_request'] as Map<String, dynamic>),
      distanceKm:          meta['distance_km'] != null
          ? double.tryParse(meta['distance_km'].toString())
          : null,
      urgency:             meta['urgency'] as String?,
      estimatedPriceMin:   range?['min'] != null
          ? double.tryParse(range!['min'].toString())
          : null,
      estimatedPriceMax:   range?['max'] != null
          ? double.tryParse(range!['max'].toString())
          : null,
      pickupLocationName:  meta['pickup_location_name'] as String?,
    );
  }
}

// ── Bid ───────────────────────────────────────────────────────────────────

class Bid {
  final int id;
  final int cargoRequestId;
  final int driverId;
  final int vehicleId;
  final double amount;
  final String? note;
  /// 'pending' | 'countered' | 'accepted' | 'rejected' | 'expired'
  final String status;
  final double? aiScore;
  final bool isRecommended;
  /// Straight-line km from vehicle location to cargo pickup (informational only)
  final double? distanceKm;
  /// 'driver' | 'fleet_owner'
  final String? bidderType;
  final String? driverName;
  final String? driverPhone;
  final double? driverRating;
  final int? driverTripCount;
  final String? truckType;
  final double? vehicleCapacity;
  final String? plateNumber;
  // Shipper contact — shown to driver when bid is accepted
  final String? shipperName;
  final String? shipperPhone;
  // Counter-offer fields
  final double? counterAmount;
  final String? counterNote;
  /// 'shipper' | 'driver' — who sent the current counter-offer
  final String? counterBy;
  final DateTime? counterAt;
  // Cargo summary (populated when fetched as /driver/bids)
  final String? cargoPickup;
  final String? cargoDestination;
  final String? cargoMaterial;
  final double? cargoWeight;
  final double? cargoBudget;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  final DateTime? availableDatetime;
  final String? vehicleCategory;
  final String? cargoServiceType;
  final String? cargoCity;
  final String? cargoPickupArea;
  final String? cargoDropoffArea;

  Bid({
    required this.id,
    required this.cargoRequestId,
    required this.driverId,
    required this.vehicleId,
    required this.amount,
    this.note,
    required this.status,
    this.aiScore,
    this.isRecommended = false,
    this.distanceKm,
    this.bidderType,
    this.driverName,
    this.driverPhone,
    this.driverRating,
    this.driverTripCount,
    this.truckType,
    this.vehicleCapacity,
    this.plateNumber,
    this.shipperName,
    this.shipperPhone,
    this.counterAmount,
    this.counterNote,
    this.counterBy,
    this.counterAt,
    this.cargoPickup,
    this.cargoDestination,
    this.cargoMaterial,
    this.cargoWeight,
    this.cargoBudget,
    this.createdAt,
    this.updatedAt,
    this.availableDatetime,
    this.vehicleCategory,
    this.cargoServiceType,
    this.cargoCity,
    this.cargoPickupArea,
    this.cargoDropoffArea,
  });

  bool get isCountered => status == 'countered';
  bool get needsDriverAction => isCountered && counterBy == 'shipper';
  bool get needsShipperAction => isCountered && counterBy == 'driver';

  factory Bid.fromJson(Map<String, dynamic> json) => Bid(
        id: json['id'] as int,
        cargoRequestId: json['cargo_request_id'] as int,
        driverId: json['driver_id'] as int,
        vehicleId: json['vehicle_id'] as int,
        amount: double.tryParse(json['amount'].toString()) ?? 0.0,
        note: json['note'] as String?,
        status: (json['status'] ?? 'pending') as String,
        aiScore: json['ai_score'] != null
            ? double.tryParse(json['ai_score'].toString())
            : null,
        isRecommended: json['is_recommended'] == true,
        distanceKm: json['distance_km'] != null
            ? double.tryParse(json['distance_km'].toString())
            : null,
        bidderType: json['bidder_type'] as String?,
        driverName: json['driver_name'] as String?,
        driverPhone: json['driver_phone'] as String?,
        driverRating: json['driver_rating'] != null
            ? double.tryParse(json['driver_rating'].toString())
            : null,
        driverTripCount: json['driver_trip_count'] as int?,
        truckType: json['truck_type'] as String?,
        vehicleCapacity: json['vehicle_capacity'] != null
            ? double.tryParse(json['vehicle_capacity'].toString())
            : null,
        plateNumber: json['plate_number'] as String?,
        shipperName: json['shipper_name'] as String?,
        shipperPhone: json['shipper_phone'] as String?,
        counterAmount: json['counter_amount'] != null
            ? double.tryParse(json['counter_amount'].toString())
            : null,
        counterNote: json['counter_note'] as String?,
        counterBy: json['counter_by'] as String?,
        counterAt: json['counter_at'] != null
            ? DateTime.tryParse(json['counter_at'].toString())
            : null,
        cargoPickup: json['cargo_pickup'] as String?,
        cargoDestination: json['cargo_destination'] as String?,
        cargoMaterial: json['cargo_material'] as String?,
        cargoWeight: json['cargo_weight'] != null
            ? double.tryParse(json['cargo_weight'].toString())
            : null,
        cargoBudget: json['cargo_budget'] != null
            ? double.tryParse(json['cargo_budget'].toString())
            : null,
        createdAt: json['created_at'] != null
            ? DateTime.tryParse(json['created_at'].toString())
            : null,
        updatedAt: json['updated_at'] != null
            ? DateTime.tryParse(json['updated_at'].toString())
            : null,
        availableDatetime: json['available_datetime'] != null
            ? DateTime.tryParse(json['available_datetime'].toString())
            : null,
        vehicleCategory: json['vehicle_category'] as String?,
        cargoServiceType: json['cargo_service_type'] as String?,
        cargoCity: json['cargo_city'] as String?,
        cargoPickupArea: json['cargo_pickup_area'] as String?,
        cargoDropoffArea: json['cargo_dropoff_area'] as String?,
      );
}

// ── TripStop ──────────────────────────────────────────────────────────────

class TripStop {
  final int id;
  final int tripId;
  final int? cargoRequestId;
  final int stopOrder;
  final String locationName;
  final double? pickupLat;
  final double? pickupLng;
  final double agreedPrice;
  final String agreedPriceFormatted;
  /// 'pending' | 'arrived' | 'loaded' | 'completed'
  final String status;
  final String? notes;
  final DateTime? arrivedAt;
  final DateTime? completedAt;
  // Cargo summary (may be null if shipper privacy applies)
  final String? cargoMaterial;
  final double? cargoWeight;
  final String? cargoPickup;
  final String? cargoDestination;
  final String? shipperName;

  TripStop({
    required this.id,
    required this.tripId,
    this.cargoRequestId,
    required this.stopOrder,
    required this.locationName,
    this.pickupLat,
    this.pickupLng,
    required this.agreedPrice,
    required this.agreedPriceFormatted,
    required this.status,
    this.notes,
    this.arrivedAt,
    this.completedAt,
    this.cargoMaterial,
    this.cargoWeight,
    this.cargoPickup,
    this.cargoDestination,
    this.shipperName,
  });

  bool get isPending   => status == 'pending';
  bool get isArrived   => status == 'arrived';
  bool get isLoaded    => status == 'loaded';
  bool get isCompleted => status == 'completed';

  factory TripStop.fromJson(Map<String, dynamic> json) => TripStop(
        id: json['id'] as int,
        tripId: json['trip_id'] as int,
        cargoRequestId: json['cargo_request_id'] as int?,
        stopOrder: json['stop_order'] as int,
        locationName: json['location_name'] as String,
        pickupLat: json['pickup_lat'] != null
            ? double.tryParse(json['pickup_lat'].toString())
            : null,
        pickupLng: json['pickup_lng'] != null
            ? double.tryParse(json['pickup_lng'].toString())
            : null,
        agreedPrice: double.tryParse(json['agreed_price'].toString()) ?? 0.0,
        agreedPriceFormatted:
            (json['agreed_price_formatted'] ?? 'ETB 0') as String,
        status: (json['status'] ?? 'pending') as String,
        notes: json['notes'] as String?,
        arrivedAt: json['arrived_at'] != null
            ? DateTime.tryParse(json['arrived_at'].toString())
            : null,
        completedAt: json['completed_at'] != null
            ? DateTime.tryParse(json['completed_at'].toString())
            : null,
        cargoMaterial: json['cargo_material'] as String?,
        cargoWeight: json['cargo_weight'] != null
            ? double.tryParse(json['cargo_weight'].toString())
            : null,
        cargoPickup: json['cargo_pickup'] as String?,
        cargoDestination: json['cargo_destination'] as String?,
        shipperName: json['shipper_name'] as String?,
      );
}

// ── Trip ──────────────────────────────────────────────────────────────────

class Trip {
  final int id;
  final int bookingId;
  /// 'ongoing' | 'completed'
  final String tripStatus;
  /// 'single' | 'multi_stop'
  final String tripType;
  final int totalStops;
  final int completedStops;
  final double? totalAmount;
  final String? startLocation;
  final String? destination;
  final List<dynamic>? routeData;
  final List<TripStop> stops;
  final DateTime? startTime;
  final DateTime? endTime;
  final DateTime? createdAt;
  final double? bookingEstimatedPrice;
  final double? bookingCommissionFee;

  Trip({
    required this.id,
    required this.bookingId,
    required this.tripStatus,
    this.tripType = 'single',
    this.totalStops = 1,
    this.completedStops = 0,
    this.totalAmount,
    this.startLocation,
    this.destination,
    this.routeData,
    this.stops = const [],
    this.startTime,
    this.endTime,
    this.createdAt,
    this.bookingEstimatedPrice,
    this.bookingCommissionFee,
  });

  bool get isMultiStop => tripType == 'multi_stop';
  int  get remainingStops => totalStops - completedStops;

  factory Trip.fromJson(Map<String, dynamic> json) => Trip(
        id: json['id'] as int,
        bookingId: json['booking_id'] as int,
        tripStatus: (json['trip_status'] ?? 'ongoing') as String,
        tripType: (json['trip_type'] ?? 'single') as String,
        totalStops: (json['total_stops'] ?? 1) as int,
        completedStops: (json['completed_stops'] ?? 0) as int,
        totalAmount: json['total_amount'] != null
            ? double.tryParse(json['total_amount'].toString())
            : null,
        startLocation: json['start_location'] as String?,
        destination: json['destination'] as String?,
        routeData: json['route_data'] is List
            ? json['route_data'] as List
            : null,
        stops: json['stops'] != null
            ? (json['stops'] as List)
                .map((s) => TripStop.fromJson(s as Map<String, dynamic>))
                .toList()
            : const [],
        startTime: json['start_time'] != null
            ? DateTime.tryParse(json['start_time'] as String)
            : null,
        endTime: json['end_time'] != null
            ? DateTime.tryParse(json['end_time'] as String)
            : null,
        createdAt: json['created_at'] != null
            ? DateTime.tryParse(json['created_at'] as String)
            : null,
        bookingEstimatedPrice: json['booking_estimated_price'] != null
            ? double.tryParse(json['booking_estimated_price'].toString())
            : null,
        bookingCommissionFee: json['booking_commission_fee'] != null
            ? double.tryParse(json['booking_commission_fee'].toString())
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

// ── AppNotification ───────────────────────────────────────────────────────

class AppNotification {
  final String id;
  final String type;
  final Map<String, dynamic> data;
  final DateTime? readAt;
  final DateTime createdAt;

  const AppNotification({
    required this.id,
    required this.type,
    required this.data,
    this.readAt,
    required this.createdAt,
  });

  bool get isRead => readAt != null;
  String get message => data['message'] as String? ?? 'New notification';
  String? get route => data['route'] as String?;
  int? get cargoId => data['cargo_id'] as int?;
  String? get driverName => data['driver_name'] as String?;
  double? get amount =>
      data['amount'] != null ? double.tryParse(data['amount'].toString()) : null;

  factory AppNotification.fromJson(Map<String, dynamic> json) {
    final rawData = json['data'];
    final Map<String, dynamic> d =
        rawData is Map ? Map<String, dynamic>.from(rawData) : {};
    return AppNotification(
      id: json['id'] as String,
      type: d['type'] as String? ?? json['type'] as String? ?? '',
      data: d,
      readAt: json['read_at'] != null
          ? DateTime.tryParse(json['read_at'] as String)
          : null,
      createdAt:
          DateTime.tryParse(json['created_at'] as String? ?? '') ??
              DateTime.now(),
    );
  }
}
