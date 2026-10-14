/// Phase 6 — Dispatch assignment model for the Flutter driver app.
///
/// Maps to the backend DispatchAssignmentResponse JSON returned by:
///   GET  /api/driver/assignments
///   GET  /api/driver/assignments/{id}
///   POST /api/driver/assignments/{id}/accept
///   POST /api/driver/assignments/{id}/reject
library;

class DispatchAssignment {
  final int id;
  final int freightId;
  final int carrierOfferId;
  final int carrierCompanyId;
  final int driverUserId;
  final int vehicleId;
  final int assignedBy;
  final DispatchStatus status;
  final double agreedAmount;
  final String? notes;
  final String? rejectionReason;
  final DateTime assignedAt;
  final DateTime? respondedAt;
  final DateTime? cancelledAt;
  final int? bookingId;
  final DateTime createdAt;
  final DateTime updatedAt;

  const DispatchAssignment({
    required this.id,
    required this.freightId,
    required this.carrierOfferId,
    required this.carrierCompanyId,
    required this.driverUserId,
    required this.vehicleId,
    required this.assignedBy,
    required this.status,
    required this.agreedAmount,
    this.notes,
    this.rejectionReason,
    required this.assignedAt,
    this.respondedAt,
    this.cancelledAt,
    this.bookingId,
    required this.createdAt,
    required this.updatedAt,
  });

  factory DispatchAssignment.fromJson(Map<String, dynamic> json) {
    return DispatchAssignment(
      id: json['id'] as int,
      freightId: json['freightId'] as int,
      carrierOfferId: json['carrierOfferId'] as int,
      carrierCompanyId: json['carrierCompanyId'] as int,
      driverUserId: json['driverUserId'] as int,
      vehicleId: json['vehicleId'] as int,
      assignedBy: json['assignedBy'] as int,
      status: DispatchStatus.fromString(json['status'] as String),
      agreedAmount: (json['agreedAmount'] as num).toDouble(),
      notes: json['notes'] as String?,
      rejectionReason: json['rejectionReason'] as String?,
      assignedAt: DateTime.parse(json['assignedAt'] as String),
      respondedAt: json['respondedAt'] != null
          ? DateTime.parse(json['respondedAt'] as String)
          : null,
      cancelledAt: json['cancelledAt'] != null
          ? DateTime.parse(json['cancelledAt'] as String)
          : null,
      bookingId: json['bookingId'] as int?,
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }

  bool get isPending => status == DispatchStatus.assigned;
  bool get isAccepted => status == DispatchStatus.driverAccepted;
  bool get isRejected => status == DispatchStatus.driverRejected;
  bool get isCancelled => status == DispatchStatus.cancelled;
  bool get isTerminal =>
      isAccepted || isRejected || isCancelled ||
      status == DispatchStatus.superseded;
}

/// Dispatch lifecycle status — mirrors backend [DispatchStatus] enum.
enum DispatchStatus {
  assigned,
  driverAccepted,
  driverRejected,
  cancelled,
  superseded;

  static DispatchStatus fromString(String value) {
    switch (value.toUpperCase()) {
      case 'ASSIGNED':        return DispatchStatus.assigned;
      case 'DRIVER_ACCEPTED': return DispatchStatus.driverAccepted;
      case 'DRIVER_REJECTED': return DispatchStatus.driverRejected;
      case 'CANCELLED':       return DispatchStatus.cancelled;
      case 'SUPERSEDED':      return DispatchStatus.superseded;
      default:                return DispatchStatus.assigned;
    }
  }

  String get displayName {
    switch (this) {
      case DispatchStatus.assigned:        return 'Pending Response';
      case DispatchStatus.driverAccepted:  return 'Accepted';
      case DispatchStatus.driverRejected:  return 'Rejected';
      case DispatchStatus.cancelled:       return 'Cancelled';
      case DispatchStatus.superseded:      return 'Superseded';
    }
  }
}
