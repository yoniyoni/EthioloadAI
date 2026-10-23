import 'dart:typed_data';

import 'package:file_selector/file_selector.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../data/api/api_client.dart';
import '../../data/providers/data_providers.dart';
import 'delivery_repository.dart';
import 'proof_of_delivery.dart';

class ShipperProofReviewScreen extends ConsumerStatefulWidget {
  final int tripId;

  const ShipperProofReviewScreen({super.key, required this.tripId});

  @override
  ConsumerState<ShipperProofReviewScreen> createState() =>
      _ShipperProofReviewScreenState();
}

class _ShipperProofReviewScreenState
    extends ConsumerState<ShipperProofReviewScreen> {
  final _reasonController = TextEditingController();
  ProofOfDelivery? _proof;
  String? _tripStatus;
  String? _error;
  String? _refreshError;
  String? _fileError;
  Uint8List? _imageBytes;
  bool _loading = true;
  bool _submitting = false;
  bool _fileBusy = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _reasonController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    if (!(ref.read(authNotifierProvider).user?.isShipper ?? false)) {
      setState(() {
        _loading = false;
        _error = 'Only the shipment owner can view or review this proof.';
      });
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
      _refreshError = null;
      _fileError = null;
      _imageBytes = null;
    });

    final repository = ref.read(deliveryRepositoryProvider);
    try {
      final proof = await repository.getProofForTrip(widget.tripId);
      if (!mounted) return;
      setState(() {
        _proof = proof;
        _loading = false;
      });
      await _refreshTrip(repository);
      if (proof.contentType.startsWith('image/')) {
        await _loadImage(repository, proof);
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _error = _errorText(error);
          _loading = false;
        });
      }
    }
  }

  Future<void> _refreshTrip(DeliveryRepository repository) async {
    try {
      final trip = await repository.getTrip(widget.tripId);
      if (mounted) {
        setState(() {
          _tripStatus = (trip['tripStatus'] ?? trip['trip_status'])?.toString();
        });
      }
    } catch (error) {
      if (mounted) {
        setState(
          () => _refreshError =
              'Proof loaded, but trip status could not refresh: ${_errorText(error)}',
        );
      }
    }
  }

  Future<void> _loadImage(
    DeliveryRepository repository,
    ProofOfDelivery proof,
  ) async {
    try {
      final bytes = await repository.downloadProof(proof.id);
      if (mounted) setState(() => _imageBytes = bytes);
    } catch (error) {
      if (mounted) setState(() => _fileError = _errorText(error));
    }
  }

  String _errorText(Object error) {
    if (error is ApiException) {
      if (error.statusCode == 401 || error.statusCode == 403) {
        return 'You are not authorized to view or review this delivery proof.';
      }
      if (error.statusCode == 404) {
        return 'No proof of delivery was found for this trip.';
      }
      if (error.statusCode != null && error.statusCode! >= 500) {
        return 'The server could not complete this request. Please try again.';
      }
      return error.message;
    }
    return error.toString();
  }

  Future<void> _review({required bool verify}) async {
    final proof = _proof;
    if (_submitting || proof == null || !proof.canReview) return;

    final reason = _reasonController.text.trim();
    if (!verify && reason.isEmpty) {
      setState(() => _error = 'Enter a reason before rejecting this proof.');
      return;
    }
    if (!verify && reason.length > 1000) {
      setState(
        () => _error = 'Rejection reason must not exceed 1000 characters.',
      );
      return;
    }

    setState(() {
      _submitting = true;
      _error = null;
      _refreshError = null;
    });
    final repository = ref.read(deliveryRepositoryProvider);
    try {
      final updatedProof = await repository.reviewProof(
        proof.id,
        verify: verify,
        rejectionReason: verify ? null : reason,
      );
      if (!mounted) return;

      setState(() => _proof = updatedProof);
      ref.invalidate(proofStatusProvider(widget.tripId));
      ref.invalidate(tripProvider(widget.tripId));
      ref.invalidate(bookingListProvider);
      ref.invalidate(bookingPageProvider(0));
      ref.invalidate(singleBookingProvider(updatedProof.bookingId));

      try {
        final refreshedProof = await repository.getProofForTrip(widget.tripId);
        if (!mounted) return;
        setState(() => _proof = refreshedProof);
        await _refreshTrip(repository);
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              verify
                  ? 'Proof verified.'
                  : 'Proof rejected. The driver can submit a new proof.',
            ),
          ),
        );
      } catch (error) {
        if (mounted) {
          setState(
            () => _refreshError =
                'Decision saved, but proof or trip status could not refresh: ${_errorText(error)}',
          );
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text(
                'Decision saved, but the latest status could not be refreshed.',
              ),
            ),
          );
        }
      }
    } catch (error) {
      if (mounted) setState(() => _error = _errorText(error));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _openProofFile(ProofOfDelivery proof) async {
    if (_fileBusy) return;
    setState(() {
      _fileBusy = true;
      _fileError = null;
    });
    try {
      final bytes =
          await ref.read(deliveryRepositoryProvider).downloadProof(proof.id);
      if (proof.contentType.startsWith('image/')) {
        if (mounted) setState(() => _imageBytes = bytes);
        return;
      }

      final extension =
          proof.fileName.contains('.') ? proof.fileName.split('.').last : 'pdf';
      final location = await getSaveLocation(
        suggestedName: proof.fileName,
        acceptedTypeGroups: [
          XTypeGroup(label: 'Proof of delivery', extensions: [extension]),
        ],
      );
      if (location == null) return;

      await XFile.fromData(
        bytes,
        name: proof.fileName,
        mimeType: proof.contentType,
      ).saveTo(location.path);
      final opened = await launchUrl(
        Uri.file(location.path),
        mode: LaunchMode.externalApplication,
      );
      if (!opened && mounted) {
        setState(
          () => _fileError =
              'Proof was downloaded, but no application could open the file.',
        );
      }
    } catch (error) {
      if (mounted) setState(() => _fileError = _errorText(error));
    } finally {
      if (mounted) setState(() => _fileBusy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isShipper = ref.watch(authNotifierProvider).user?.isShipper ?? false;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Delivery Proof Review'),
        actions: [
          IconButton(
            tooltip: 'Refresh proof status',
            onPressed: _loading || _submitting ? null : _load,
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: !isShipper
          ? const Center(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: Text('Only the shipment owner can review this proof.'),
              ),
            )
          : _loading
              ? const Center(child: CircularProgressIndicator())
              : _error != null && _proof == null
                  ? _MessagePanel(message: _error!, onRetry: _load)
                  : _proof == null
                      ? const Center(child: Text('No proof is available.'))
                      : _buildProof(_proof!),
    );
  }

  Widget _buildProof(ProofOfDelivery proof) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text(
          'Trip #${proof.tripId}',
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Proof status: ${proof.status.label}'),
                const SizedBox(height: 6),
                Text('Trip status: ${_tripStatus ?? 'Unavailable'}'),
                const SizedBox(height: 6),
                Text('File: ${proof.fileName}'),
                if (proof.recipientName?.isNotEmpty ?? false)
                  Text('Received by: ${proof.recipientName}'),
                if (proof.note?.isNotEmpty ?? false)
                  Text('Driver note: ${proof.note}'),
                if (proof.rejectionReason?.isNotEmpty ?? false)
                  Text('Rejection reason: ${proof.rejectionReason}'),
                const SizedBox(height: 12),
                if (proof.contentType.startsWith('image/'))
                  if (_imageBytes != null)
                    ClipRRect(
                      borderRadius: BorderRadius.circular(8),
                      child: Image.memory(_imageBytes!, fit: BoxFit.contain),
                    )
                  else
                    const Center(child: CircularProgressIndicator()),
                if (proof.contentType == 'application/pdf')
                  OutlinedButton.icon(
                    onPressed: _fileBusy ? null : () => _openProofFile(proof),
                    icon: const Icon(Icons.picture_as_pdf_outlined),
                    label: Text(_fileBusy ? 'Downloading…' : 'Open PDF'),
                  ),
                if (_fileError != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      _fileError!,
                      style: const TextStyle(color: Colors.red),
                    ),
                  ),
              ],
            ),
          ),
        ),
        if (_refreshError != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              _refreshError!,
              style: const TextStyle(color: Colors.deepOrange),
            ),
          ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(_error!, style: const TextStyle(color: Colors.red)),
          ),
        if (proof.canReview) ...[
          TextField(
            controller: _reasonController,
            maxLength: 1000,
            maxLines: 3,
            enabled: !_submitting,
            decoration: const InputDecoration(
              labelText: 'Rejection reason (required to reject)',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: _submitting ? null : () => _review(verify: false),
                  child: const Text('Reject proof'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: FilledButton(
                  onPressed: _submitting ? null : () => _review(verify: true),
                  child: const Text('Verify proof'),
                ),
              ),
            ],
          ),
          if (_submitting)
            const Padding(
              padding: EdgeInsets.all(12),
              child: Center(child: CircularProgressIndicator()),
            ),
        ] else
          Text('This proof cannot be reviewed again (${proof.status.label}).'),
      ],
    );
  }
}

class _MessagePanel extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;

  const _MessagePanel({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(message, textAlign: TextAlign.center),
              const SizedBox(height: 12),
              OutlinedButton(onPressed: onRetry, child: const Text('Retry')),
            ],
          ),
        ),
      );
}
