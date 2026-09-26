import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';

import 'delivery_repository.dart';
import 'proof_of_delivery.dart';

/// Phase 7 — Active Trip screen for the driver.
///
/// This screen is shown once the driver has accepted an assignment and a
/// booking has been created (Phase 6). It guides the driver through:
///
///   1. START TRIP  — sets trip to ONGOING, freight → IN_TRANSIT
///   2. MARK ARRIVED — transitions trip ONGOING → ARRIVED
///   3. UPLOAD PROOF — submits proof file (photo/PDF)
///   4. AWAITING VERIFICATION — shows proof status
///   5. COMPLETED — delivery done
///
/// Route: /driver/trips/:tripId/active
class ActiveTripScreen extends ConsumerStatefulWidget {
  final int tripId;
  const ActiveTripScreen({super.key, required this.tripId});

  @override
  ConsumerState<ActiveTripScreen> createState() => _ActiveTripScreenState();
}

class _ActiveTripScreenState extends ConsumerState<ActiveTripScreen> {
  String? _tripStatus;
  bool _loading = false;
  String? _errorMessage;
  ProofOfDelivery? _proof;
  File? _selectedFile;

  final _recipientController = TextEditingController();
  final _noteController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _tripStatus = 'ONGOING'; // initial assumed state
  }

  @override
  void dispose() {
    _recipientController.dispose();
    _noteController.dispose();
    super.dispose();
  }

  // ── Actions ───────────────────────────────────────────────────────────────

  Future<void> _startTrip() async {
    setState(() { _loading = true; _errorMessage = null; });
    try {
      final result = await ref.read(deliveryRepositoryProvider).startTrip(widget.tripId);
      setState(() { _tripStatus = result['tripStatus'] as String? ?? 'ONGOING'; });
      _showSnack('Trip started! Drive safely.');
    } catch (e) {
      setState(() { _errorMessage = e.toString(); });
    } finally {
      setState(() { _loading = false; });
    }
  }

  Future<void> _markArrived() async {
    setState(() { _loading = true; _errorMessage = null; });
    try {
      final result = await ref.read(deliveryRepositoryProvider).markArrived(widget.tripId);
      setState(() { _tripStatus = result['tripStatus'] as String? ?? 'ARRIVED'; });
      _showSnack('Arrival confirmed!');
    } catch (e) {
      setState(() { _errorMessage = e.toString(); });
    } finally {
      setState(() { _loading = false; });
    }
  }

  Future<void> _pickFile() async {
    final picker = ImagePicker();
    final picked = await picker.pickImage(source: ImageSource.camera, imageQuality: 80);
    if (picked != null) {
      setState(() { _selectedFile = File(picked.path); });
    }
  }

  Future<void> _submitProof() async {
    if (_selectedFile == null) {
      _showSnack('Please select or capture a proof photo first.');
      return;
    }
    setState(() { _loading = true; _errorMessage = null; });
    try {
      final proof = await ref.read(deliveryRepositoryProvider).submitProof(
        widget.tripId,
        _selectedFile!,
        recipientName: _recipientController.text.trim().isEmpty
            ? null
            : _recipientController.text.trim(),
        note: _noteController.text.trim().isEmpty
            ? null
            : _noteController.text.trim(),
      );
      setState(() {
        _proof = proof;
        _tripStatus = 'DELIVERY_PENDING_VERIFICATION';
      });
      _showSnack('Proof submitted! Awaiting shipper verification.');
    } catch (e) {
      setState(() { _errorMessage = e.toString(); });
    } finally {
      setState(() { _loading = false; });
    }
  }

  Future<void> _refreshProofStatus() async {
    setState(() { _loading = true; _errorMessage = null; });
    try {
      final proof = await ref.read(deliveryRepositoryProvider).getProofForTrip(widget.tripId);
      setState(() {
        _proof = proof;
        if (proof.isVerified) _tripStatus = 'COMPLETED';
        if (proof.isRejected) _tripStatus = 'ARRIVED'; // resubmit
      });
    } catch (e) {
      setState(() { _errorMessage = e.toString(); });
    } finally {
      setState(() { _loading = false; });
    }
  }

  // ── Build ─────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Active Trip'),
        actions: [
          if (_tripStatus == 'DELIVERY_PENDING_VERIFICATION')
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: _loading ? null : _refreshProofStatus,
              tooltip: 'Check proof status',
            ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : SingleChildScrollView(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _StatusBanner(status: _tripStatus ?? 'ONGOING'),
                  const SizedBox(height: 20),
                  if (_errorMessage != null)
                    _ErrorCard(message: _errorMessage!),
                  _buildActionSection(),
                ],
              ),
            ),
    );
  }

  Widget _buildActionSection() {
    switch (_tripStatus?.toUpperCase()) {
      case 'ONGOING':
        return _ActionCard(
          icon: Icons.play_circle_outline,
          title: 'Start Your Trip',
          subtitle: 'Confirm you are departing for the delivery.',
          buttonLabel: 'Start Trip',
          buttonColor: Colors.green,
          onPressed: _startTrip,
        );

      case 'ARRIVED':
        return _ProofUploadSection(
          selectedFile: _selectedFile,
          recipientController: _recipientController,
          noteController: _noteController,
          onPickFile: _pickFile,
          onSubmit: _submitProof,
          isRejected: _proof?.isRejected ?? false,
          rejectionReason: _proof?.rejectionReason,
        );

      case 'DELIVERY_PENDING_VERIFICATION':
        return _PendingVerificationCard(
          proof: _proof,
          onRefresh: _refreshProofStatus,
        );

      case 'COMPLETED':
        return _CompletedCard(
          onRate: () => context.push('/driver/trips/${widget.tripId}/rate'),
          onHome: () => context.go('/driver/assignments'),
        );

      default:
        return _ActionCard(
          icon: Icons.drive_eta,
          title: 'En Route',
          subtitle: 'You are currently travelling to the destination.',
          buttonLabel: 'Mark Arrived',
          buttonColor: Colors.orange,
          onPressed: _markArrived,
        );
    }
  }

  void _showSnack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }
}

// ── Sub-widgets ───────────────────────────────────────────────────────────────

class _StatusBanner extends StatelessWidget {
  final String status;
  const _StatusBanner({required this.status});

  @override
  Widget build(BuildContext context) {
    final (label, color) = switch (status.toUpperCase()) {
      'ONGOING'                         => ('In Transit', Colors.blue),
      'ARRIVED'                         => ('Arrived at Destination', Colors.orange),
      'DELIVERY_PENDING_VERIFICATION'   => ('Proof Awaiting Verification', Colors.purple),
      'COMPLETED'                       => ('Delivery Completed ✓', Colors.green),
      _                                 => ('Active Trip', Colors.grey),
    };

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withOpacity(0.4)),
      ),
      child: Row(
        children: [
          Icon(Icons.local_shipping, color: color, size: 28),
          const SizedBox(width: 12),
          Text(label,
              style: TextStyle(color: color, fontSize: 16, fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
}

class _ActionCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final String buttonLabel;
  final Color buttonColor;
  final VoidCallback onPressed;

  const _ActionCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.buttonLabel,
    required this.buttonColor,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Icon(icon, size: 48, color: buttonColor),
            const SizedBox(height: 12),
            Text(title,
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Text(subtitle, textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.grey)),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: onPressed,
                style: FilledButton.styleFrom(backgroundColor: buttonColor),
                child: Text(buttonLabel),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ProofUploadSection extends StatelessWidget {
  final File? selectedFile;
  final TextEditingController recipientController;
  final TextEditingController noteController;
  final VoidCallback onPickFile;
  final VoidCallback onSubmit;
  final bool isRejected;
  final String? rejectionReason;

  const _ProofUploadSection({
    required this.selectedFile,
    required this.recipientController,
    required this.noteController,
    required this.onPickFile,
    required this.onSubmit,
    required this.isRejected,
    this.rejectionReason,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (isRejected) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.red.shade50,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.red.shade200),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Proof Rejected',
                        style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold)),
                    if (rejectionReason != null)
                      Text('Reason: $rejectionReason',
                          style: const TextStyle(color: Colors.red)),
                    const Text('Please re-upload a new proof.',
                        style: TextStyle(color: Colors.red)),
                  ],
                ),
              ),
              const SizedBox(height: 16),
            ],
            const Text('Submit Proof of Delivery',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: onPickFile,
              icon: const Icon(Icons.camera_alt),
              label: Text(selectedFile != null
                  ? 'Photo selected ✓'
                  : 'Take Photo / Select File'),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: recipientController,
              decoration: const InputDecoration(
                labelText: 'Recipient Name (optional)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: noteController,
              maxLines: 3,
              decoration: const InputDecoration(
                labelText: 'Delivery Note (optional)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: selectedFile != null ? onSubmit : null,
                icon: const Icon(Icons.upload),
                label: const Text('Submit Proof'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PendingVerificationCard extends StatelessWidget {
  final ProofOfDelivery? proof;
  final VoidCallback onRefresh;

  const _PendingVerificationCard({this.proof, required this.onRefresh});

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Colors.purple.shade50,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            const Icon(Icons.hourglass_top, size: 48, color: Colors.purple),
            const SizedBox(height: 12),
            const Text('Proof Submitted',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Text('Submitted: ${proof?.uploadedAt.toLocal().toString().substring(0, 16) ?? "—"}'),
            const SizedBox(height: 8),
            const Text('The shipper is reviewing your proof of delivery.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.grey)),
            const SizedBox(height: 20),
            OutlinedButton.icon(
              onPressed: onRefresh,
              icon: const Icon(Icons.refresh),
              label: const Text('Check Status'),
            ),
          ],
        ),
      ),
    );
  }
}

class _CompletedCard extends StatelessWidget {
  final VoidCallback onRate;
  final VoidCallback onHome;

  const _CompletedCard({required this.onRate, required this.onHome});

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Colors.green.shade50,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            const Icon(Icons.check_circle, size: 64, color: Colors.green),
            const SizedBox(height: 12),
            const Text('Delivery Complete!',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            const Text('Your delivery has been verified. Great job!',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.grey)),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: onRate,
                    icon: const Icon(Icons.star_outline),
                    label: const Text('Rate Shipper'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton.icon(
                    onPressed: onHome,
                    icon: const Icon(Icons.home),
                    label: const Text('Home'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ErrorCard extends StatelessWidget {
  final String message;
  const _ErrorCard({required this.message});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.red.shade50,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.red.shade200),
      ),
      child: Text(message, style: const TextStyle(color: Colors.red)),
    );
  }
}
