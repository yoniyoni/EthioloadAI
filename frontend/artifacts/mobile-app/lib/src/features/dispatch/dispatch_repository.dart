import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:logger/logger.dart';

import '../../data/api/api_client.dart';
import 'dispatch_assignment.dart';

/// Phase 6 — Repository for driver dispatch assignment operations.
///
/// Talks to:
///   GET  /driver/assignments            — driver's inbox (newest first)
///   GET  /driver/assignments/{id}       — single assignment detail
///   POST /driver/assignments/{id}/accept — accept (creates Booking)
///   POST /driver/assignments/{id}/reject — reject (optional reason)
class DispatchRepository {
  final ApiClient _api;
  final Logger _logger;

  DispatchRepository({required ApiClient api, required Logger logger})
      : _api = api,
        _logger = logger;

  /// Returns the authenticated driver's assignment inbox, newest first.
  Future<List<DispatchAssignment>> getMyAssignments() async {
    _logger.d('DispatchRepository.getMyAssignments');
    final List<dynamic> data = await _api.get<List<dynamic>>(
      '/driver/assignments',
    );
    return data
        .map((json) => DispatchAssignment.fromJson(json as Map<String, dynamic>))
        .toList();
  }

  /// Returns a single assignment by ID.
  /// Throws [ApiException] with status 404 if it doesn't belong to the caller.
  Future<DispatchAssignment> getAssignment(int dispatchId) async {
    _logger.d('DispatchRepository.getAssignment($dispatchId)');
    final Map<String, dynamic> data =
        await _api.get<Map<String, dynamic>>('/driver/assignments/$dispatchId');
    return DispatchAssignment.fromJson(data);
  }

  /// Accepts a dispatch assignment.
  /// Atomically creates a Booking on the backend. Returns updated assignment.
  Future<DispatchAssignment> acceptAssignment(int dispatchId) async {
    _logger.d('DispatchRepository.acceptAssignment($dispatchId)');
    final Map<String, dynamic> data =
        await _api.post<Map<String, dynamic>>(
      '/driver/assignments/$dispatchId/accept',
    );
    return DispatchAssignment.fromJson(data);
  }

  /// Rejects a dispatch assignment with an optional reason.
  /// Releases driver + vehicle resources; carrier may redispatch.
  Future<DispatchAssignment> rejectAssignment(
    int dispatchId, {
    String? rejectionReason,
  }) async {
    _logger.d('DispatchRepository.rejectAssignment($dispatchId)');
    final body = rejectionReason != null && rejectionReason.isNotEmpty
        ? {'rejectionReason': rejectionReason}
        : <String, dynamic>{};
    final Map<String, dynamic> data =
        await _api.post<Map<String, dynamic>>(
      '/driver/assignments/$dispatchId/reject',
      data: body,
    );
    return DispatchAssignment.fromJson(data);
  }
}

// ── Riverpod providers ────────────────────────────────────────────────────────

/// Repository provider — depends on the shared [apiClientProvider].
final dispatchRepositoryProvider = Provider<DispatchRepository>((ref) {
  final api = ref.watch(apiClientProvider);
  return DispatchRepository(api: api, logger: Logger());
});

/// Async provider for the driver's assignment inbox.
final myAssignmentsProvider =
    FutureProvider.autoDispose<List<DispatchAssignment>>((ref) async {
  return ref.watch(dispatchRepositoryProvider).getMyAssignments();
});

/// Family provider for a single assignment detail.
final assignmentDetailProvider =
    FutureProvider.autoDispose.family<DispatchAssignment, int>((ref, id) async {
  return ref.watch(dispatchRepositoryProvider).getAssignment(id);
});
