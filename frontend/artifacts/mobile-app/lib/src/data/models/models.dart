// ─────────────────────────────────────────────────────────────
//  EthioLoadAI – shared models for the Spring Boot API
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

    // Spring UserResponse returns: id, full_name, email, phone, role,
    // location, verification_status, and is_active.
  factory User.fromJson(Map<String, dynamic> json) => User(
      id: (json['id'] as num).toInt(),
        fullName: (json['full_name'] ?? json['fullName'] ?? json['name'] ?? '') as String,
      email: (json['email'] ?? '') as String,
        phone: (json['phone'] ?? '') as String,
      role: ((json['role'] ?? 'shipper') as String).toLowerCase(),
        location: json['location'] as String?,
        verificationStatus: json['verification_status'] == true ||
          json['verification_status'] == 1 || json['verificationStatus'] == true ||
          json['verificationStatus'] == 1,
        isActive: json['is_active'] == true || json['is_active'] == 1 ||
          json['isActive'] == true || json['isActive'] == 1,
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
  final String? approvalStatus;
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
    this.approvalStatus,
    required this.rating,
    this.vehicleCategory,
  });

  factory Vehicle.fromJson(Map<String, dynamic> json) => Vehicle(
        id: json['id'] as int,
        userId: (json['user_id'] ?? json['userId']) as int,
        truckType: (json['truck_type'] ?? json['truckType'] ?? '') as String,
        plateNumber: (json['plate_number'] ?? json['plateNumber'] ?? '') as String,
        // capacity can be int or double from the API
        capacity: double.tryParse(json['capacity'].toString()) ?? 0.0,
        currentCity: (json['current_city'] ?? json['currentCity'] ?? '') as String,
        latitude: json['latitude'] != null
            ? double.tryParse(json['latitude'].toString())
            : null,
        longitude: json['longitude'] != null
            ? double.tryParse(json['longitude'].toString())
            : null,
        availabilityStatus:
            (json['availability_status'] ?? json['availabilityStatus'] ?? 'available') as String,
        approvalStatus:
            (json['approval_status'] ?? json['approvalStatus']) as String?,
        // rating comes as "0.00" (string) or 0 (num) depending on the endpoint
        rating: double.tryParse(json['rating'].toString()) ?? 0.0,
        vehicleCategory: (json['vehicle_category'] ?? json['vehicleCategory']) as String?,
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
  final double? distanceKm;
  final double? volumeM3;
  final String? cargoDescription;
  final double? pickupLatitude;
  final double? pickupLongitude;
  final double? destinationLatitude;
  final double? destinationLongitude;

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
    this.distanceKm,
    this.volumeM3,
    this.cargoDescription,
    this.pickupLatitude,
    this.pickupLongitude,
    this.destinationLatitude,
    this.destinationLongitude,
  });

  bool get isFixed => priceType.toLowerCase() == 'fixed';

  factory CargoRequest.fromJson(Map<String, dynamic> json) => CargoRequest(
      id: (json['id'] as num).toInt(),
      userId: ((json['user_id'] ?? json['shipperId'] ?? 0) as num).toInt(),
      pickupLocation: (json['pickup_location'] ?? json['pickupLocation'] ?? '') as String,
      destination: (json['destination'] ?? '') as String,
      materialType: (json['material_type'] ?? json['materialType'] ?? '') as String,
      weight: double.tryParse((json['weight'] ?? json['weightTons'] ?? '0').toString()) ?? 0.0,
      urgencyLevel: (json['urgency_level'] ?? json['urgencyLevel'] ?? 'normal') as String,
        budget: (json['budget'] ?? json['price'] ?? json['fixed_price'] ?? json['fixedPrice'] ?? json['offered_price']) != null
            ? double.tryParse((json['budget'] ?? json['price'] ?? json['fixed_price'] ?? json['fixedPrice'] ?? json['offered_price']).toString())
            : null,
        priceType: (json['price_type'] ?? json['priceType'] ?? 'negotiable').toString().toLowerCase(),
        bidDeadline: (json['bid_deadline'] ?? json['bidDeadline']) != null
          ? DateTime.tryParse((json['bid_deadline'] ?? json['bidDeadline']).toString())
            : null,
        status: (json['status'] ?? 'posted') as String,
        createdAt: (json['created_at'] ?? json['createdAt']) != null
          ? DateTime.tryParse((json['created_at'] ?? json['createdAt']).toString())
            : null,
        updatedAt: (json['updated_at'] ?? json['updatedAt']) != null
          ? DateTime.tryParse((json['updated_at'] ?? json['updatedAt']).toString())
            : null,
        serviceType: (json['service_type'] ?? json['serviceType'] ?? 'intercity') as String,
        city: json['city'] as String?,
        pickupArea: (json['pickup_area'] ?? json['pickupArea']) as String?,
        dropoffArea: (json['dropoff_area'] ?? json['dropoffArea']) as String?,
        preferredDate: (json['preferred_date'] ?? json['preferredDate']) != null
          ? DateTime.tryParse((json['preferred_date'] ?? json['preferredDate']).toString())
            : null,
        itemsDescription: (json['items_description'] ?? json['itemsDescription']) as String?,
        vehicleTypeNeeded: (json['vehicle_type_needed'] ?? json['vehicleTypeNeeded']) as String?,
        distanceKm: (json['distance_km'] ?? json['distanceKm']) != null
          ? double.tryParse((json['distance_km'] ?? json['distanceKm']).toString())
            : null,
        volumeM3: double.tryParse((json['volume_m3'] ?? json['volumeM3'] ?? '').toString()),
        cargoDescription: (json['cargo_description'] ?? json['cargoDescription']) as String?,
        pickupLatitude: double.tryParse((json['pickup_latitude'] ?? json['pickupLatitude'] ?? '').toString()),
        pickupLongitude: double.tryParse((json['pickup_longitude'] ?? json['pickupLongitude'] ?? '').toString()),
        destinationLatitude: double.tryParse((json['destination_latitude'] ?? json['destinationLatitude'] ?? '').toString()),
        destinationLongitude: double.tryParse((json['destination_longitude'] ?? json['destinationLongitude'] ?? '').toString()),
      );

  Map<String, dynamic> toCreateJson() => {
        'pickupLocation': pickupLocation,
        'destination': destination,
        'materialType': materialType,
        'weightTons': weight,
        'urgencyLevel': urgencyLevel,
        if (budget != null) 'budget': budget,
        'serviceType': serviceType,
        if (city != null) 'city': city,
        if (pickupArea != null) 'pickupArea': pickupArea,
        if (dropoffArea != null) 'dropoffArea': dropoffArea,
        if (preferredDate != null)
          'preferredDate': preferredDate!.toIso8601String().split('T')[0],
        if (itemsDescription != null) 'itemsDescription': itemsDescription,
        if (vehicleTypeNeeded != null) 'vehicleTypeNeeded': vehicleTypeNeeded,
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
  final String serviceType;
  final String? city;
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
    this.serviceType = 'intercity',
    this.city,
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
        id: (json['id'] as num).toInt(),
        cargoId: ((json['cargo_id'] ?? json['freightId'] ?? 0) as num).toInt(),
        vehicleId: ((json['vehicle_id'] ?? json['vehicleId'] ?? 0) as num).toInt(),
        driverId: ((json['driver_id'] ?? json['driverId'] ?? 0) as num).toInt(),
        bookingStatus: (json['booking_status'] ?? json['bookingStatus'] ?? 'pending') as String,
        estimatedPrice: double.tryParse((json['estimated_price'] ?? json['estimatedPrice'] ?? '0').toString()) ?? 0.0,
        commissionFee: (json['commission_fee'] ?? json['commissionFee']) != null
          ? double.tryParse((json['commission_fee'] ?? json['commissionFee']).toString())
            : null,
        tripId: (json['trip_id'] ?? json['tripId']) as int?,
        tripStatus: ((json['trip_status'] ?? json['tripStatus']) as String?)?.toLowerCase(),
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
        serviceType: (json['service_type'] ?? 'intercity') as String,
        city: json['city'] as String?,
        driverPhone: json['driver_phone'] as String?,
        driverName: (json['driver_name'] ?? json['driverName']) as String?,
        shipperPhone: json['shipper_phone'] as String?,
        shipperName: json['shipper_name'] as String?,
        paymentMethod: json['payment_method'] as String?,
        hasRating: json['has_rating'] == true,
        createdAt: (json['created_at'] ?? json['createdAt']) != null
          ? DateTime.tryParse((json['created_at'] ?? json['createdAt']).toString())
            : null,
        updatedAt: (json['updated_at'] ?? json['updatedAt']) != null
          ? DateTime.tryParse((json['updated_at'] ?? json['updatedAt']).toString())
            : null,
      );
}

class ShipperDashboardSummary {
  final int totalBookings;
  final int pendingBookings;

  const ShipperDashboardSummary({
    required this.totalBookings,
    required this.pendingBookings,
  });

  factory ShipperDashboardSummary.fromJson(Map<String, dynamic> json) =>
      ShipperDashboardSummary(
        totalBookings: (json['totalBookings'] as num? ?? 0).toInt(),
        pendingBookings: (json['pendingBookings'] as num? ?? 0).toInt(),
      );
}

class DriverDashboardSummary {
  final int totalBookings;
  final int confirmedBookings;
  final int completedBookings;
  final int pendingBookings;
  final List<Booking> pendingBookingRows;

  const DriverDashboardSummary({
    required this.totalBookings,
    required this.confirmedBookings,
    required this.completedBookings,
    required this.pendingBookings,
    required this.pendingBookingRows,
  });

  factory DriverDashboardSummary.fromJson(Map<String, dynamic> json) =>
      DriverDashboardSummary(
        totalBookings: (json['totalBookings'] as num? ?? 0).toInt(),
        confirmedBookings: (json['confirmedBookings'] as num? ?? 0).toInt(),
        completedBookings: (json['completedBookings'] as num? ?? 0).toInt(),
        pendingBookings: (json['pendingBookings'] as num? ?? 0).toInt(),
        pendingBookingRows: (json['pendingBookingRows'] as List<dynamic>? ?? const [])
            .map((item) => Booking.fromJson(
                Map<String, dynamic>.from(item as Map)))
            .toList(),
      );
}

class BookingPage {
      final List<Booking> content;
      final int number;
      final int size;
      final bool hasNext;
      final int? totalBookings;

      const BookingPage({
        required this.content,
        required this.number,
        required this.size,
        required this.hasNext,
        this.totalBookings,
      });

      factory BookingPage.fromJson(Map<String, dynamic> json) => BookingPage(
            content: (json['content'] as List<dynamic>? ?? const [])
                .map((item) => Booking.fromJson(Map<String, dynamic>.from(item as Map)))
                .toList(),
            number: (json['number'] as num? ?? 0).toInt(),
            size: (json['size'] as num? ?? 20).toInt(),
            hasNext: json['hasNext'] as bool? ?? false,
            totalBookings: (json['totalBookings'] as num?)?.toInt(),
          );
}

class BookingTripResolution {
  final int bookingId;
  final int freightId;
  final String bookingStatus;
  final int? tripId;
  final String? tripStatus;
  final String? tripType;
  final String? serviceType;

  const BookingTripResolution({
    required this.bookingId,
    required this.freightId,
    required this.bookingStatus,
    required this.tripId,
    required this.tripStatus,
    required this.tripType,
    required this.serviceType,
  });

  factory BookingTripResolution.fromJson(Map<String, dynamic> json) =>
      BookingTripResolution(
        bookingId: (json['bookingId'] as num).toInt(),
        freightId: (json['freightId'] as num).toInt(),
        bookingStatus: (json['bookingStatus'] as String).toLowerCase(),
        tripId: (json['tripId'] as num?)?.toInt(),
        tripStatus: (json['tripStatus'] as String?)?.toLowerCase(),
        tripType: (json['tripType'] as String?)?.toLowerCase(),
        serviceType: (json['serviceType'] as String?)?.toLowerCase(),
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

class VehicleDocument {
  final int id;
  final int vehicleId;
  final int userId;
  final String documentType;
  final String documentLabel;
  final String originalName;
  final String mimeType;
  final int fileSize;
  final String status;
  final String? rejectionReason;
  final int? reviewedByUserId;
  final DateTime? reviewedAt;
  final DateTime? createdAt;
  final DateTime? updatedAt;
  final String fileUrl;

  VehicleDocument({
    required this.id,
    required this.vehicleId,
    required this.userId,
    required this.documentType,
    required this.documentLabel,
    required this.originalName,
    required this.mimeType,
    required this.fileSize,
    required this.status,
    this.rejectionReason,
    this.reviewedByUserId,
    this.reviewedAt,
    this.createdAt,
    this.updatedAt,
    required this.fileUrl,
  });

  bool get isPending => status == 'pending';
  bool get isApproved => status == 'approved';
  bool get isRejected => status == 'rejected';

  factory VehicleDocument.fromJson(Map<String, dynamic> json) => VehicleDocument(
        id: (json['id'] as num).toInt(),
        vehicleId: (json['vehicle_id'] as num).toInt(),
        userId: (json['user_id'] as num).toInt(),
        documentType: (json['document_type'] ?? '').toString(),
        documentLabel: (json['document_label'] ?? json['document_type'] ?? '').toString(),
        originalName: (json['original_name'] ?? '').toString(),
        mimeType: (json['mime_type'] ?? 'application/octet-stream').toString(),
        fileSize: (json['file_size'] as num? ?? 0).toInt(),
        status: (json['status'] ?? 'pending').toString(),
        rejectionReason: json['rejection_reason'] as String?,
        reviewedByUserId: (json['reviewed_by_user_id'] as num?)?.toInt(),
        reviewedAt: json['reviewed_at'] != null ? DateTime.tryParse(json['reviewed_at'] as String) : null,
        createdAt: json['created_at'] != null ? DateTime.tryParse(json['created_at'] as String) : null,
        updatedAt: json['updated_at'] != null ? DateTime.tryParse(json['updated_at'] as String) : null,
        fileUrl: (json['file_url'] ?? '/api/vehicle-documents/${json['id']}/file').toString(),
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
  final String? cargoPriceType;

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
    this.cargoPriceType,
  });

  bool get isCountered => status == 'countered';
  bool get needsDriverAction => isCountered && counterBy == 'shipper';
  bool get needsShipperAction => isCountered && counterBy == 'driver';
  bool get isFixedCargo => cargoPriceType?.toLowerCase() == 'fixed';

  factory Bid.fromJson(Map<String, dynamic> json) => Bid(
        id: (json['id'] as num?)?.toInt() ?? 0,
        cargoRequestId: ((json['cargo_request_id'] ?? json['freightId'] ?? 0) as num).toInt(),
        driverId: ((json['driver_id'] ?? json['driverId'] ?? 0) as num).toInt(),
        vehicleId: ((json['vehicle_id'] ?? json['vehicleId'] ?? 0) as num).toInt(),
        amount: double.tryParse(json['amount']?.toString() ?? '0') ?? 0.0,
        note: json['note'] as String?,
        status: (json['status'] ?? 'pending').toString().toLowerCase(),
        aiScore: (json['ai_score'] ?? json['aiScore']) != null
          ? double.tryParse((json['ai_score'] ?? json['aiScore']).toString())
            : null,
        isRecommended: json['is_recommended'] == true || json['isRecommended'] == true,
        distanceKm: (json['distance_km'] ?? json['distanceKm']) != null
          ? double.tryParse((json['distance_km'] ?? json['distanceKm']).toString())
            : null,
        bidderType: (json['bidder_type'] ?? json['bidderType']) as String?,
        driverName: (json['driver_name'] ?? json['driverName']) as String?,
        driverPhone: (json['driver_phone'] ?? json['driverPhone']) as String?,
        driverRating: (json['driver_rating'] ?? json['driverRating']) != null
            ? double.tryParse((json['driver_rating'] ?? json['driverRating']).toString())
            : null,
        driverTripCount: (json['driver_trip_count'] ?? json['driverTripCount']) != null
            ? int.tryParse((json['driver_trip_count'] ?? json['driverTripCount']).toString())
            : null,
        truckType: (json['truck_type'] ?? json['vehicleType'] ?? json['vehicle_type']) as String?,
        vehicleCapacity: (json['vehicle_capacity'] ?? json['vehicleCapacity']) != null
            ? double.tryParse((json['vehicle_capacity'] ?? json['vehicleCapacity']).toString())
            : null,
        plateNumber: (json['plate_number'] ?? json['vehiclePlateNumber'] ?? json['plateNumber']) as String?,
        shipperName: (json['shipperName'] ?? json['shipper_name']) as String?,
        shipperPhone: (json['shipperPhone'] ?? json['shipper_phone']) as String?,
        counterAmount: (json['counter_amount'] ?? json['counterAmount']) != null
            ? double.tryParse((json['counter_amount'] ?? json['counterAmount']).toString())
            : null,
        counterNote: (json['counter_note'] ?? json['counterNote']) as String?,
        counterBy: (json['counter_by'] ?? json['counterBy'])?.toString().toLowerCase(),
        counterAt: (json['counter_at'] ?? json['counterAt']) != null
            ? DateTime.tryParse((json['counter_at'] ?? json['counterAt']).toString())
            : null,
        cargoPickup: (json['pickupLocation'] ?? json['cargo_pickup']) as String?,
        cargoDestination: (json['destination'] ?? json['cargo_destination']) as String?,
        cargoMaterial: (json['materialType'] ?? json['cargo_material']) as String?,
        cargoWeight: (json['weightTons'] ?? json['cargo_weight']) != null
            ? double.tryParse((json['weightTons'] ?? json['cargo_weight']).toString())
            : null,
        cargoBudget: (json['budget'] ?? json['cargo_budget']) != null
            ? double.tryParse((json['budget'] ?? json['cargo_budget']).toString())
            : null,
        createdAt: (json['created_at'] ?? json['createdAt']) != null
          ? DateTime.tryParse((json['created_at'] ?? json['createdAt']).toString())
            : null,
        updatedAt: (json['updated_at'] ?? json['updatedAt']) != null
          ? DateTime.tryParse((json['updated_at'] ?? json['updatedAt']).toString())
            : null,
        availableDatetime: (json['available_datetime'] ?? json['availableDatetime']) != null
            ? DateTime.tryParse((json['available_datetime'] ?? json['availableDatetime']).toString())
            : null,
        vehicleCategory: (json['vehicle_category'] ?? json['vehicleCategory']) as String?,
        cargoServiceType: (json['cargo_service_type'] ?? json['cargoServiceType']) as String?,
        cargoCity: (json['cargo_city'] ?? json['cargoCity']) as String?,
        cargoPickupArea: (json['cargo_pickup_area'] ?? json['cargoPickupArea']) as String?,
        cargoDropoffArea: (json['cargo_dropoff_area'] ?? json['cargoDropoffArea']) as String?,
        cargoPriceType: (json['price_type'] ?? json['priceType'] ?? json['cargo_price_type'] ?? json['cargoPriceType'])?.toString().toLowerCase(),
      );
}

class BidPage {
  final List<Bid> content;
  final int page;
  final int size;
  final bool hasNext;
  final int? nextPage;
  final int? recommendedBidId;
  final int pendingCount;

  const BidPage({
    required this.content,
    required this.page,
    required this.size,
    required this.hasNext,
    required this.nextPage,
    required this.recommendedBidId,
    required this.pendingCount,
  });

  factory BidPage.fromJson(Map<String, dynamic> json) => BidPage(
        content: (json['content'] as List? ?? const [])
            .map((item) => Bid.fromJson(Map<String, dynamic>.from(item as Map)))
            .toList(),
        page: (json['page'] as num?)?.toInt() ?? 0,
        size: (json['size'] as num?)?.toInt() ?? 20,
        hasNext: json['hasNext'] as bool? ?? false,
        nextPage: (json['nextPage'] as num?)?.toInt(),
        recommendedBidId: (json['recommendedBidId'] as num?)?.toInt(),
        pendingCount: (json['pendingCount'] as num?)?.toInt() ?? 0,
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
  final int? cargoId;
  final String? bookingStatus;
  final String? serviceType;
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
    this.cargoId,
    this.bookingStatus,
    this.serviceType,
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
      id: (json['id'] as num).toInt(),
      bookingId: ((json['booking_id'] ?? json['bookingId'] ?? json['booking']?['id'] ?? 0) as num).toInt(),
      cargoId: ((json['cargo'] as Map?)?['id'] as num?)?.toInt(),
      bookingStatus: ((json['booking'] as Map?)?['status'] as String?)?.toLowerCase(),
      serviceType: ((json['cargo'] as Map?)?['serviceType'] as String?)?.toLowerCase(),
      tripStatus: (json['trip_status'] ?? json['tripStatus'] ?? 'ongoing') as String,
      tripType: (json['trip_type'] ?? json['tripType'] ?? 'single') as String,
      totalStops: (json['total_stops'] ?? json['totalStops'] ?? 1) as int,
      completedStops: (json['completed_stops'] ?? json['completedStops'] ?? 0) as int,
        totalAmount: json['total_amount'] != null
            ? double.tryParse(json['total_amount'].toString())
            : null,
        startLocation: (json['start_location'] ?? json['startLocation']) as String?,
        destination: json['destination'] as String?,
        routeData: json['route_data'] is List
            ? json['route_data'] as List
            : null,
        stops: json['stops'] != null
            ? (json['stops'] as List)
                .map((s) => TripStop.fromJson(s as Map<String, dynamic>))
                .toList()
            : const [],
        startTime: (json['start_time'] ?? json['startTime']) != null
          ? DateTime.tryParse((json['start_time'] ?? json['startTime']).toString())
            : null,
        endTime: (json['end_time'] ?? json['endTime']) != null
          ? DateTime.tryParse((json['end_time'] ?? json['endTime']).toString())
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

class TripPage {
  final List<Trip> content;
  final int number;
  final int size;
  final bool hasNext;
  final int totalElements;

  const TripPage({
    required this.content,
    required this.number,
    required this.size,
    required this.hasNext,
    required this.totalElements,
  });

  factory TripPage.fromJson(Map<String, dynamic> json) => TripPage(
        content: (json['content'] as List<dynamic>? ?? const [])
            .map((item) => Trip.fromJson(Map<String, dynamic>.from(item as Map)))
            .toList(),
        number: (json['number'] as num? ?? 0).toInt(),
        size: (json['size'] as num? ?? 3).toInt(),
        hasNext: json['hasNext'] as bool? ?? json['last'] != true,
        totalElements: (json['totalElements'] as num? ?? 0).toInt(),
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
  final double? commissionAmount;
  final double? driverNetAmount;
  final int? freightId;
  final String? transactionRef;
  final DateTime? updatedAt;
  final DateTime? createdAt;

  Payment({
    required this.id,
    required this.bookingId,
    required this.amount,
    required this.paymentMethod,
    required this.paymentStatus,
    this.commissionAmount,
    this.driverNetAmount,
    this.freightId,
    this.transactionRef,
    this.createdAt,
    this.updatedAt,
  });

  factory Payment.fromJson(Map<String, dynamic> json) => Payment(
        id: (json['id'] as num).toInt(),
        bookingId: ((json['booking_id'] ?? json['bookingId'] ?? 0) as num).toInt(),
        amount: double.tryParse(json['amount'].toString()) ?? 0.0,
        paymentMethod: (json['payment_method'] ?? json['paymentMethod'] ?? 'cash') as String,
        paymentStatus: (json['payment_status'] ?? json['paymentStatus'] ?? 'PENDING').toString().toUpperCase(),
        commissionAmount: double.tryParse((json['commission_amount'] ?? json['commissionAmount'] ?? '').toString()),
        driverNetAmount: double.tryParse((json['driver_net_amount'] ?? json['driverNetAmount'] ?? '').toString()),
        freightId: (json['freight_id'] ?? json['freightId']) is num ? ((json['freight_id'] ?? json['freightId']) as num).toInt() : null,
        transactionRef: (json['transaction_ref'] ?? json['transactionRef']) as String?,
        createdAt: (json['created_at'] ?? json['createdAt']) != null
          ? DateTime.tryParse((json['created_at'] ?? json['createdAt']).toString())
            : null,
        updatedAt: (json['updated_at'] ?? json['updatedAt']) != null
          ? DateTime.tryParse((json['updated_at'] ?? json['updatedAt']).toString())
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
      readAt: (json['read_at'] ?? json['readAt']) != null
        ? DateTime.tryParse((json['read_at'] ?? json['readAt']).toString())
          : null,
      createdAt:
        DateTime.tryParse((json['created_at'] ?? json['createdAt']).toString()) ??
              DateTime.now(),
    );
  }
}

class ChatMessage {
  final int id;
  final int senderId;
  final int receiverId;
  final int freightId;
  final String type;
  final String content;
  final String? maskedContent;
  final bool hasPhoneNumber;
  final bool hasPaymentRequest;
  final bool isRead;
  final DateTime? createdAt;
  final String? senderName;

  ChatMessage({required this.id, required this.senderId, required this.receiverId,
    required this.freightId, required this.type, required this.content,
    this.maskedContent, this.hasPhoneNumber = false, this.hasPaymentRequest = false,
    this.isRead = false, this.createdAt, this.senderName});

  factory ChatMessage.fromJson(Map<String, dynamic> json) => ChatMessage(
    id: (json['id'] as num).toInt(), senderId: (json['senderId'] as num).toInt(),
    receiverId: (json['receiverId'] as num).toInt(), freightId: (json['freightId'] as num).toInt(),
    type: (json['type'] ?? 'text') as String, content: (json['content'] ?? '') as String,
    maskedContent: json['maskedContent'] as String?, hasPhoneNumber: json['hasPhoneNumber'] == true,
    hasPaymentRequest: json['hasPaymentRequest'] == true, isRead: json['isRead'] == true,
    createdAt: json['createdAt'] == null ? null : DateTime.tryParse(json['createdAt'].toString()),
    senderName: (json['sender'] as Map?)?['name'] as String?,
  );
}

class MessageHistoryPage {
  final List<ChatMessage> messages;
  final String? olderCursor;
  final String? afterCursor;
  final bool hasOlder;
  final bool hasMoreAfter;
  final String? mode;

  const MessageHistoryPage({
    required this.messages,
    this.olderCursor,
    this.afterCursor,
    this.hasOlder = false,
    this.hasMoreAfter = false,
    this.mode,
  });

  factory MessageHistoryPage.fromJson(Map<String, dynamic> json) {
    final page = json['page'] as Map?;
    final rawMessages = json['messages'] as List? ?? const [];
    return MessageHistoryPage(
      messages: rawMessages
          .map((item) => ChatMessage.fromJson(Map<String, dynamic>.from(item as Map)))
          .toList(),
      olderCursor: page?['olderCursor'] as String?,
      afterCursor: page?['afterCursor'] as String?,
      hasOlder: page?['hasOlder'] == true,
      hasMoreAfter: page?['hasMoreAfter'] == true,
      mode: page?['mode'] as String?,
    );
  }
}

List<ChatMessage> mergeChatMessages(
    List<ChatMessage> current, List<ChatMessage> incoming) {
  final byId = <int, ChatMessage>{};
  for (final message in [...current, ...incoming]) {
    byId[message.id] = message;
  }
  final result = byId.values.toList()
    ..sort((a, b) {
      final timeComparison = (b.createdAt ?? DateTime(0))
          .compareTo(a.createdAt ?? DateTime(0));
      return timeComparison != 0 ? timeComparison : b.id.compareTo(a.id);
    });
  return result;
}

class ContractView {
  final int id;
  final int freightId;
  final String status;
  final String paymentStatus;
  final double agreedPrice;
  final DateTime? deadline;
  final String? shipperName;
  final String? driverName;
  final String? pickupLocation;
  final String? deliveryLocation;
  ContractView({required this.id, required this.freightId, required this.status,
    required this.paymentStatus, required this.agreedPrice, this.deadline,
    this.shipperName, this.driverName, this.pickupLocation, this.deliveryLocation});
  factory ContractView.fromJson(Map<String, dynamic> j) => ContractView(
    id: (j['id'] as num).toInt(), freightId: (j['freightId'] as num).toInt(),
    status: (j['status'] ?? 'unknown') as String, paymentStatus: (j['paymentStatus'] ?? 'unknown') as String,
    agreedPrice: double.tryParse(j['agreedPrice'].toString()) ?? 0,
    deadline: j['deadline'] == null ? null : DateTime.tryParse(j['deadline'].toString()),
    shipperName: (j['shipper'] as Map?)?['name'] as String?, driverName: (j['driver'] as Map?)?['name'] as String?,
    pickupLocation: (j['freight'] as Map?)?['pickupLocation'] as String?, deliveryLocation: (j['freight'] as Map?)?['deliveryLocation'] as String?,
  );
}

class DisputeView {
  final int id;
  final int freightId;
  final int? bookingId;
  final String reason;
  final String description;
  final String status;
  final String? resolution;
  final double? refundAmount;
  final DateTime? createdAt;
  DisputeView({required this.id, required this.freightId, this.bookingId, required this.reason, required this.description, required this.status, this.resolution, this.refundAmount, this.createdAt});
  factory DisputeView.fromJson(Map<String, dynamic> j) => DisputeView(
    id: (j['id'] as num).toInt(), freightId: (j['freightId'] as num).toInt(),
    bookingId: (j['bookingId'] as num?)?.toInt(),
    reason: (j['reason'] ?? '') as String, description: (j['description'] ?? '') as String,
    status: (j['status'] ?? 'unknown') as String, resolution: j['resolution'] as String?,
    refundAmount: (j['refundAmount'] as num?)?.toDouble(),
    createdAt: j['createdAt'] == null ? null : DateTime.tryParse(j['createdAt'].toString()),
  );
}

// ── NearbyDriver ──────────────────────────────────────────────────────────

class NearbyDriver {
  final int driverId;
  final String name;
  final String truckType;
  final String plateNumber;
  final double rating;
  final int tripCount;
  final double? distanceKm;
  final String lastSeen;
  final String? vehicleCategory;
  final double? latitude;
  final double? longitude;

  NearbyDriver({
    required this.driverId,
    required this.name,
    required this.truckType,
    required this.plateNumber,
    required this.rating,
    required this.tripCount,
    this.distanceKm,
    required this.lastSeen,
    this.vehicleCategory,
    this.latitude,
    this.longitude,
  });

  factory NearbyDriver.fromJson(Map<String, dynamic> j) => NearbyDriver(
        driverId:        j['driver_id'] as int,
        name:            (j['driver_name'] ?? j['name'] ?? '') as String,
        truckType:       (j['vehicle_type'] ?? j['truck_type'] ?? '') as String,
        plateNumber:     (j['vehicle_plate'] ?? j['plate_number'] ?? '') as String,
        rating:          double.tryParse(j['rating'].toString()) ?? 0.0,
        tripCount:       (j['trip_count'] ?? 0) as int,
        distanceKm:      j['distance_km'] != null
            ? double.tryParse(j['distance_km'].toString())
            : null,
        lastSeen:        (j['last_seen'] ?? '') as String,
        vehicleCategory: j['vehicle_category'] as String?,
        latitude:        j['latitude'] != null
            ? double.tryParse(j['latitude'].toString())
            : null,
        longitude:       j['longitude'] != null
            ? double.tryParse(j['longitude'].toString())
            : null,
      );
}

// ── NearbyTruck ───────────────────────────────────────────────────────────

class NearbyTruck {
  final int vehicleId;
  final int? driverId;
  final String driverName;
  final String vehicleType;
  final String vehiclePlate;
  final String? vehicleCategory;
  final double latitude;
  final double longitude;
  final String? currentCity;
  final double rating;
  final double distanceKm;
  final String lastSeen;

  NearbyTruck({
    required this.vehicleId,
    this.driverId,
    required this.driverName,
    required this.vehicleType,
    required this.vehiclePlate,
    this.vehicleCategory,
    required this.latitude,
    required this.longitude,
    this.currentCity,
    required this.rating,
    required this.distanceKm,
    required this.lastSeen,
  });

  factory NearbyTruck.fromJson(Map<String, dynamic> j) => NearbyTruck(
        vehicleId:       j['vehicle_id'] as int,
        driverId:        j['driver_id'] as int?,
        driverName:      (j['driver_name'] ?? '') as String,
        vehicleType:     (j['vehicle_type'] ?? '') as String,
        vehiclePlate:    (j['vehicle_plate'] ?? '') as String,
        vehicleCategory: j['vehicle_category'] as String?,
        latitude:        double.tryParse(j['latitude'].toString()) ?? 0.0,
        longitude:       double.tryParse(j['longitude'].toString()) ?? 0.0,
        currentCity:     j['current_city'] as String?,
        rating:          double.tryParse(j['rating'].toString()) ?? 0.0,
        distanceKm:      double.tryParse(j['distance_km'].toString()) ?? 0.0,
        lastSeen:        (j['last_seen'] ?? '') as String,
      );
}

// ── RouteResult ───────────────────────────────────────────────────────────

class RouteStep {
  final String instruction;
  final int distanceM;
  final int durationS;
  final String maneuver;

  RouteStep({
    required this.instruction,
    required this.distanceM,
    required this.durationS,
    required this.maneuver,
  });

  factory RouteStep.fromJson(Map<String, dynamic> j) => RouteStep(
        instruction: (j['instruction'] ?? '') as String,
        distanceM:   (j['distance_m'] ?? 0) as int,
        durationS:   (j['duration_s'] ?? 0) as int,
        maneuver:    (j['maneuver'] ?? 'turn') as String,
      );

  String get distanceLabel {
    if (distanceM < 1000) return '$distanceM m';
    return '${(distanceM / 1000).toStringAsFixed(1)} km';
  }
}

class RouteResult {
  final double distanceKm;
  final int durationMin;
  /// Polyline as list of [lng, lat] pairs (OSRM order)
  final List<List<double>> polyline;
  final List<RouteStep> steps;
  final String source;

  RouteResult({
    required this.distanceKm,
    required this.durationMin,
    required this.polyline,
    required this.steps,
    required this.source,
  });

  factory RouteResult.fromJson(Map<String, dynamic> j) {
    final rawPolyline = j['polyline'] as List? ?? [];
    final polyline = rawPolyline
        .whereType<List>()
        .map((p) => p.whereType<num>().map((n) => n.toDouble()).toList())
        .where((p) => p.length >= 2)
        .toList();

    final rawSteps = j['steps'] as List? ?? [];
    final steps = rawSteps
        .whereType<Map<String, dynamic>>()
        .map(RouteStep.fromJson)
        .toList();

    return RouteResult(
      distanceKm:  double.tryParse(j['distance_km'].toString()) ?? 0.0,
      durationMin: (j['duration_min'] ?? 0) as int,
      polyline:    polyline,
      steps:       steps,
      source:      (j['source'] ?? 'unknown') as String,
    );
  }
}

// ── PlaceResult ───────────────────────────────────────────────────────────

class PlaceResult {
  final String name;
  final String shortName;
  final double lat;
  final double lng;
  final String type;

  PlaceResult({
    required this.name,
    required this.shortName,
    required this.lat,
    required this.lng,
    required this.type,
  });

  factory PlaceResult.fromJson(Map<String, dynamic> j) => PlaceResult(
        name:      (j['name'] ?? '') as String,
        shortName: (j['short_name'] ?? (j['name'] ?? '')) as String,
        lat:       double.tryParse(j['lat'].toString()) ?? 0.0,
        lng:       double.tryParse(j['lng'].toString()) ?? 0.0,
        type:      (j['type'] ?? '') as String,
      );
}

// ── TripLocation ──────────────────────────────────────────────────────────

class TripLocation {
  final double? currentLat;
  final double? currentLng;
  final String? currentCity;
  final DateTime? lastUpdatedAt;
  final int? minutesSinceUpdate;
  final List<Map<String, dynamic>> routeData;
  final String? destination;
  final double? destinationLat;
  final double? destinationLng;
  final double? pickupLat;
  final double? pickupLng;

  TripLocation({
    this.currentLat,
    this.currentLng,
    this.currentCity,
    this.lastUpdatedAt,
    this.minutesSinceUpdate,
    this.routeData = const [],
    this.destination,
    this.destinationLat,
    this.destinationLng,
    this.pickupLat,
    this.pickupLng,
  });

  bool get hasPosition => currentLat != null && currentLng != null;
  bool get hasDestination => destinationLat != null && destinationLng != null;

  factory TripLocation.fromJson(Map<String, dynamic> j) {
    final rawRoute = j['route_data'];
    final List<Map<String, dynamic>> route = rawRoute is List
        ? rawRoute
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList()
        : [];
    return TripLocation(
      currentLat: j['current_lat'] != null
          ? double.tryParse(j['current_lat'].toString())
          : null,
      currentLng: j['current_lng'] != null
          ? double.tryParse(j['current_lng'].toString())
          : null,
      currentCity:         j['current_city'] as String?,
      lastUpdatedAt:       j['last_updated_at'] != null
          ? DateTime.tryParse(j['last_updated_at'] as String)
          : null,
      minutesSinceUpdate:  j['minutes_since_update'] as int?,
      routeData:           route,
      destination:         j['destination'] as String?,
      destinationLat:      j['destination_lat'] != null
          ? double.tryParse(j['destination_lat'].toString())
          : null,
      destinationLng:      j['destination_lng'] != null
          ? double.tryParse(j['destination_lng'].toString())
          : null,
      pickupLat:           j['pickup_lat'] != null
          ? double.tryParse(j['pickup_lat'].toString())
          : null,
      pickupLng:           j['pickup_lng'] != null
          ? double.tryParse(j['pickup_lng'].toString())
          : null,
    );
  }
}
