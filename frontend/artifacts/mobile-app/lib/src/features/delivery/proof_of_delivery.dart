/// Phase 7 — Proof of Delivery model for the Flutter driver app.
///
/// Maps to the backend ProofOfDeliveryResponse returned by:
///   POST /proof-of-delivery/trips/{tripId}/proof  — submit proof
///   GET  /proof-of-delivery/trips/{tripId}/proof  — get proof for trip
///   GET  /proof-of-delivery/{proofId}             — get by ID
///   POST /proof-of-delivery/{proofId}/verify      — shipper verifies/rejects
library;

class ProofOfDelivery {
  final int id;
  final int tripId;
  final int bookingId;
  final int freightId;
  final int uploadedById;
  final String fileName;
  final String contentType;
  final int fileSize;
  final String? recipientName;
  final String? note;
  final ProofStatus status;
  final int? reviewedById;
  final DateTime? reviewedAt;
  final String? rejectionReason;
  final DateTime uploadedAt;
  final DateTime createdAt;
  final DateTime updatedAt;

  const ProofOfDelivery({
    required this.id,
    required this.tripId,
    required this.bookingId,
    required this.freightId,
    required this.uploadedById,
    required this.fileName,
    required this.contentType,
    required this.fileSize,
    this.recipientName,
    this.note,
    required this.status,
    this.reviewedById,
    this.reviewedAt,
    this.rejectionReason,
    required this.uploadedAt,
    required this.createdAt,
    required this.updatedAt,
  });

  factory ProofOfDelivery.fromJson(Map<String, dynamic> json) {
    return ProofOfDelivery(
      id: json['id'] as int,
      tripId: json['tripId'] as int,
      bookingId: json['bookingId'] as int,
      freightId: json['freightId'] as int,
      uploadedById: json['uploadedById'] as int,
      fileName: json['fileName'] as String,
      contentType: json['contentType'] as String,
      fileSize: json['fileSize'] as int,
      recipientName: json['recipientName'] as String?,
      note: json['note'] as String?,
      status: ProofStatus.fromString(json['status'] as String),
      reviewedById: json['reviewedById'] as int?,
      reviewedAt: json['reviewedAt'] != null
          ? DateTime.parse(json['reviewedAt'] as String)
          : null,
      rejectionReason: json['rejectionReason'] as String?,
      uploadedAt: DateTime.parse(json['uploadedAt'] as String),
      createdAt: DateTime.parse(json['createdAt'] as String),
      updatedAt: DateTime.parse(json['updatedAt'] as String),
    );
  }

  bool get isSubmitted => status == ProofStatus.submitted;
  bool get isVerified => status == ProofStatus.verified;
  bool get isRejected => status == ProofStatus.rejected;
  bool get canReview => isSubmitted;
}

enum ProofStatus {
  submitted,
  verified,
  rejected,
  unknown;

  static ProofStatus fromString(String value) {
    switch (value.toUpperCase()) {
      case 'SUBMITTED':
        return ProofStatus.submitted;
      case 'VERIFIED':
        return ProofStatus.verified;
      case 'REJECTED':
        return ProofStatus.rejected;
      default:
        return ProofStatus.unknown;
    }
  }

  String get label {
    switch (this) {
      case ProofStatus.submitted:
        return 'Awaiting Verification';
      case ProofStatus.verified:
        return 'Verified';
      case ProofStatus.rejected:
        return 'Rejected';
      case ProofStatus.unknown:
        return 'Unknown status';
    }
  }
}
