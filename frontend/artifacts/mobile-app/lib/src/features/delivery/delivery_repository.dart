import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:dio/dio.dart';
import 'package:logger/logger.dart';

import '../../data/api/api_client.dart';
import '../dispatch/dispatch_assignment.dart';
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

  // ── Proof of Delivery ─────────────────────────────────────────────────────

  /// Submit proof of delivery as a multipart upload.
  /// [file] is the image or PDF file selected by the driver.
  Future<ProofOfDelivery> submitProof(
    int tripId,
    File file, {
    String? recipientName,
    String? note,
  }) async {
    _logger.d('DeliveryRepository.submitProof(tripId=$tripId, file=${file.path})');

    final fileName = file.path.split(Platform.pathSeparator).last;
    final formData = FormData.fromMap({
      'file': await MultipartFile.fromFile(file.path, filename: fileName),
      if (recipientName != null && recipientName.isNotEmpty)
        'recipientName': recipientName,
      if (note != null && note.isNotEmpty) 'note': note,
    });

    final data = await _api.postFormData<Map<String, dynamic>>(
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
