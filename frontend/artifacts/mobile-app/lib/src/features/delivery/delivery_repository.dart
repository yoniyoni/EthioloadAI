import 'dart:typed_data';

import 'package:image_picker/image_picker.dart' show XFile;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:dio/dio.dart';
import 'package:logger/logger.dart';

import '../../data/api/api_client.dart';
import 'proof_of_delivery.dart';

/// Phase 7 — Repository for driver delivery operations.
///
/// Endpoints:
///   POST /trips/{tripId}/start                    — start trip
///   POST /trips/{tripId}/arrive                   — mark arrival
///   POST /proof-of-delivery/trips/{tripId}/proof  — submit proof
///   GET  /proof-of-delivery/trips/{tripId}/proof  — get proof status
class DeliveryRepository {
  final ApiClient _api;
  final Logger _logger;

  DeliveryRepository({required ApiClient api, required Logger logger})
      : _api = api,
        _logger = logger;

  // ── Trip actions ──────────────────────────────────────────────────────────

  /// Start the trip. Trip must be in ONGOING status with startTime not yet set.
  /// Returns the updated trip response (as raw map — use TripStatus from it).
  Future<Map<String, dynamic>> startTrip(int tripId) async {
    _logger.d('DeliveryRepository.startTrip($tripId)');
    return await _api.post<Map<String, dynamic>>('/trips/$tripId/start');
  }

  /// Mark arrival at destination (ONGOING → ARRIVED).
  Future<Map<String, dynamic>> markArrived(int tripId) async {
    _logger.d('DeliveryRepository.markArrived($tripId)');
    return await _api.post<Map<String, dynamic>>('/trips/$tripId/arrive');
  }

  /// Confirm delivery as the shipper associated with the trip.
  Future<Map<String, dynamic>> confirmDelivery(int tripId) async {
    _logger.d('DeliveryRepository.confirmDelivery($tripId)');
    return await _api
        .post<Map<String, dynamic>>('/trips/$tripId/confirm-delivery');
  }

  Future<Map<String, dynamic>> getTrip(int tripId) =>
      _api.get<Map<String, dynamic>>('/trips/$tripId');

  /// Resume the booking's trip, including after a restart, or create it once.
  Future<int> openBookingTrip(int bookingId) async {
    for (var page = 0;; page++) {
      final result = await _api.get<Map<String, dynamic>>('/trips',
          queryParameters: {'page': page, 'size': 100});
      for (final trip in result['content'] as List) {
        if (trip['booking']?['id'] == bookingId) return trip['id'] as int;
      }
      if (result['last'] == true) break;
    }
    final trip = await _api
        .post<Map<String, dynamic>>('/trips', data: {'bookingId': bookingId});
    return trip['id'] as int;
  }

  // ── Proof of Delivery ─────────────────────────────────────────────────────

  /// Submit proof of delivery as a multipart upload.
  /// [file] is the image or PDF file selected by the driver.
  Future<ProofOfDelivery> submitProof(
    int tripId,
    XFile file, {
    String? recipientName,
    String? note,
  }) async {
    _logger.d('DeliveryRepository.submitProof(tripId=$tripId)');

    const types = {
      'jpg': 'image/jpeg',
      'jpeg': 'image/jpeg',
      'png': 'image/png',
      'webp': 'image/webp',
      'pdf': 'application/pdf'
    };
    final mime = types[file.name.split('.').last.toLowerCase()] ??
        (types.containsValue(file.mimeType) ? file.mimeType : null);
    if (mime == null)
      throw ApiException(message: 'Choose a JPEG, PNG, WebP or PDF file.');
    final fileName = types.containsKey(file.name.split('.').last.toLowerCase())
        ? file.name
        : 'proof.${types.entries.firstWhere((e) => e.value == mime).key}';
    if (await file.length() > 10 * 1024 * 1024)
      throw ApiException(message: 'Maximum proof size is 10 MB.');
    final formData = FormData.fromMap({
      'file': MultipartFile.fromBytes(await file.readAsBytes(),
          filename: fileName, contentType: DioMediaType.parse(mime)),
      if (recipientName != null && recipientName.isNotEmpty)
        'recipientName': recipientName,
      if (note != null && note.isNotEmpty) 'note': note,
    });

    final data = await _api.upload(
      '/proof-of-delivery/trips/$tripId/proof',
      formData,
    );
    return ProofOfDelivery.fromJson(data);
  }

  /// Get current proof of delivery status for a trip.
  Future<ProofOfDelivery> getProofForTrip(int tripId) async {
    _logger.d('DeliveryRepository.getProofForTrip($tripId)');
    final data = await _api.get<Map<String, dynamic>>(
      '/proof-of-delivery/trips/$tripId/proof',
    );
    return ProofOfDelivery.fromJson(data);
  }

  /// Get proof by its ID.
  Future<ProofOfDelivery> getProofById(int proofId) async {
    _logger.d('DeliveryRepository.getProofById($proofId)');
    final data = await _api.get<Map<String, dynamic>>(
      '/proof-of-delivery/$proofId',
    );
    return ProofOfDelivery.fromJson(data);
  }

  /// Shipper verifies or rejects a submitted proof of delivery.
  Future<ProofOfDelivery> reviewProof(
    int proofId, {
    required bool verify,
    String? rejectionReason,
  }) async {
    final reason = rejectionReason?.trim();
    if (!verify && (reason == null || reason.isEmpty)) {
      throw ApiException(message: 'A rejection reason is required.');
    }
    if (!verify && reason!.length > 1000) {
      throw ApiException(
          message: 'Rejection reason must not exceed 1000 characters.');
    }

    final data = await _api.post<Map<String, dynamic>>(
      '/proof-of-delivery/$proofId/verify',
      data: {
        'action': verify ? 'VERIFY' : 'REJECT',
        if (!verify) 'rejectionReason': reason,
      },
    );
    return ProofOfDelivery.fromJson(data);
  }

  /// Download proof bytes through the authenticated API client.
  Future<Uint8List> downloadProof(int proofId) =>
      _api.getBytes('/proof-of-delivery/$proofId/download');
}

// ── Riverpod providers ────────────────────────────────────────────────────────

final deliveryRepositoryProvider = Provider<DeliveryRepository>((ref) {
  final api = ref.watch(apiClientProvider);
  return DeliveryRepository(api: api, logger: Logger());
});

/// Watches proof status for a specific trip. Refreshes on demand.
final proofStatusProvider =
    FutureProvider.family<ProofOfDelivery, int>((ref, tripId) async {
  final repo = ref.watch(deliveryRepositoryProvider);
  return repo.getProofForTrip(tripId);
});
