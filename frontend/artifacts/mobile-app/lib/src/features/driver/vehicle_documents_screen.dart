import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:file_picker/file_picker.dart';
import '../../data/models/models.dart';
import '../../data/providers/data_providers.dart';
import '../../data/repositories/repositories.dart';

// ── Palette (mirrors driver_documents_screen.dart) ─────────────────────────
const _green   = Color(0xFF0F3D1A);
const _amber   = Color(0xFFF59E0B);
const _bg      = Color(0xFFF8FBF8);
const _border  = Color(0xFFE5E7EB);
const _textPri = Color(0xFF111827);
const _textSec = Color(0xFF6B7280);
const _danger  = Color(0xFFEF4444);
const _success = Color(0xFF059669);

// ── Document type definitions (English only) ────────────────────────────────

class _VDocInfo {
  final String   type;
  final String   label;
  final IconData icon;
  const _VDocInfo(this.type, this.label, this.icon);
}

const _requiredVehicleDocs = [
  _VDocInfo('registration_certificate',    'Vehicle Registration Certificate',        Icons.description_outlined),
  _VDocInfo('insurance_certificate',       'Insurance Certificate',                   Icons.shield_outlined),
  _VDocInfo('roadworthiness_certificate',  'Roadworthiness / Inspection Certificate', Icons.fact_check_outlined),
  _VDocInfo('ownership_document',          'Ownership Document',                      Icons.article_outlined),
];

// ── Status helpers ────────────────────────────────────────────────────────

Color _statusColor(String? status) => switch (status) {
      'approved' => _success,
      'rejected' => _danger,
      'pending'  => _amber,
      _          => _textSec,
    };

IconData _statusIcon(String? status) => switch (status) {
      'approved' => Icons.check_circle_rounded,
      'rejected' => Icons.cancel_rounded,
      'pending'  => Icons.hourglass_top_rounded,
      _          => Icons.upload_file_outlined,
    };

String _statusLabel(String? status) => switch (status) {
      'approved' => 'Approved',
      'rejected' => 'Rejected',
      'pending'  => 'Under review',
      _          => 'Not uploaded',
    };

String _formatDate(DateTime? dt) {
  if (dt == null) return '';
  return '${dt.day}/${dt.month}/${dt.year}';
}

// ── Screen ────────────────────────────────────────────────────────────────

class VehicleDocumentsScreen extends ConsumerWidget {
  final int     vehicleId;
  final String? plateNumber;

  const VehicleDocumentsScreen({
    super.key,
    required this.vehicleId,
    this.plateNumber,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final docsAsync = ref.watch(vehicleDocumentsProvider(vehicleId));

    return Scaffold(
      backgroundColor: _bg,
      appBar: AppBar(
        backgroundColor: _green,
        elevation: 0,
        iconTheme: const IconThemeData(color: Colors.white),
        title: Text(
          plateNumber != null && plateNumber!.isNotEmpty
              ? 'Vehicle Documents — $plateNumber'
              : 'Vehicle Documents',
          style: const TextStyle(
              color: Colors.white, fontWeight: FontWeight.w600, fontSize: 16),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white),
            tooltip: 'Refresh',
            onPressed: () =>
                ref.invalidate(vehicleDocumentsProvider(vehicleId)),
          ),
        ],
      ),
      body: docsAsync.when(
        data:    (docs) => _Body(vehicleId: vehicleId, docs: docs),
        loading: () => const _LoadingSkeleton(),
        error:   (e, _) => _ErrorState(
          message: e.toString(),
          onRetry: () => ref.invalidate(vehicleDocumentsProvider(vehicleId)),
        ),
      ),
    );
  }
}

// ── Body ──────────────────────────────────────────────────────────────────

class _Body extends ConsumerWidget {
  final int                   vehicleId;
  final List<VehicleDocument> docs;
  const _Body({required this.vehicleId, required this.docs});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final uploaded = {for (final d in docs) d.documentType: d};
    final approvedCount = docs.where((d) => d.isApproved).length;
    final allApproved   = approvedCount >= _requiredVehicleDocs.length;
    final allUploaded   = uploaded.length >= _requiredVehicleDocs.length;

    return RefreshIndicator(
      color: _amber,
      onRefresh: () async =>
          ref.invalidate(vehicleDocumentsProvider(vehicleId)),
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // ── Progress + status banner ──────────────────────────────
          _ProgressBanner(
            allApproved:   allApproved,
            allUploaded:   allUploaded,
            approvedCount: approvedCount,
            total:         _requiredVehicleDocs.length,
          ),
          const SizedBox(height: 20),

          // ── Document cards ────────────────────────────────────────
          const Text(
            'Required Documents',
            style: TextStyle(
                fontSize: 15, fontWeight: FontWeight.w600, color: _textPri),
          ),
          const SizedBox(height: 10),

          ..._requiredVehicleDocs.map((entry) {
            final doc = uploaded[entry.type];
            return _VDocumentCard(
              vehicleId:    vehicleId,
              documentType: entry.type,
              label:        entry.label,
              icon:         entry.icon,
              uploaded:     doc,
              onUploaded: () =>
                  ref.invalidate(vehicleDocumentsProvider(vehicleId)),
            );
          }),

          const SizedBox(height: 32),
        ],
      ),
    );
  }
}

// ── Progress banner ───────────────────────────────────────────────────────

const _amberBg     = Color(0xFFFAEEDA);
const _amberText   = Color(0xFF633806);
const _amberBorder = Color(0xFFF59E0B);

class _ProgressBanner extends StatelessWidget {
  final bool allApproved;
  final bool allUploaded;
  final int  approvedCount;
  final int  total;

  const _ProgressBanner({
    required this.allApproved,
    required this.allUploaded,
    required this.approvedCount,
    required this.total,
  });

  @override
  Widget build(BuildContext context) {
    // ── State 1: all approved → verified ────────────────────────────
    if (allApproved) {
      return Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: _success.withAlpha(18),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: _success.withAlpha(60), width: 0.5),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              const Icon(Icons.verified_rounded, color: _success, size: 28),
              const SizedBox(width: 12),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('✓  Vehicle Verified',
                        style: TextStyle(
                            fontSize: 14,
                            fontWeight: FontWeight.w700,
                            color: _success)),
                    SizedBox(height: 3),
                    Text(
                      'All documents approved. This vehicle can be assigned to jobs.',
                      style: TextStyle(fontSize: 12, color: _textSec),
                    ),
                  ],
                ),
              ),
            ]),
            const SizedBox(height: 12),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: const LinearProgressIndicator(
                value: 1.0,
                minHeight: 6,
                backgroundColor: Color(0x20059669),
                valueColor: AlwaysStoppedAnimation<Color>(_success),
              ),
            ),
            const SizedBox(height: 6),
            Text(
              '$approvedCount / $total documents approved',
              style: const TextStyle(
                  fontSize: 11, color: _success, fontWeight: FontWeight.w500),
            ),
          ],
        ),
      );
    }

    // ── State 2: all uploaded, waiting for admin approval ────────────
    if (allUploaded) {
      return Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: _amberBg,
          borderRadius: BorderRadius.circular(12),
          border: Border(
            left: const BorderSide(color: _amberBorder, width: 4),
            top:    BorderSide(color: _amberBorder.withAlpha(80), width: 0.5),
            right:  BorderSide(color: _amberBorder.withAlpha(80), width: 0.5),
            bottom: BorderSide(color: _amberBorder.withAlpha(80), width: 0.5),
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              const Icon(Icons.hourglass_top_rounded,
                  color: _amberText, size: 28),
              const SizedBox(width: 12),
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Waiting for approval',
                      style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: _amberText),
                    ),
                    SizedBox(height: 4),
                    Text(
                      'This vehicle will be verified automatically once an '
                      'admin approves all its documents.',
                      style: TextStyle(fontSize: 12, color: _amberText),
                    ),
                  ],
                ),
              ),
            ]),
            const SizedBox(height: 14),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: approvedCount / total,
                minHeight: 6,
                backgroundColor: _amberBorder.withAlpha(40),
                valueColor:
                    const AlwaysStoppedAnimation<Color>(_amberBorder),
              ),
            ),
            const SizedBox(height: 6),
            Text(
              '$approvedCount / $total documents approved',
              style: const TextStyle(
                  fontSize: 11,
                  color: _amberText,
                  fontWeight: FontWeight.w500),
            ),
          ],
        ),
      );
    }

    // ── State 3: documents still missing ────────────────────────────
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: _green.withAlpha(18),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _green.withAlpha(60), width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            const Icon(Icons.upload_file_outlined, color: _green, size: 28),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Upload Vehicle Documents',
                      style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: _green)),
                  const SizedBox(height: 3),
                  Text(
                    '$approvedCount of $total approved. '
                    'Upload the remaining to get this vehicle verified.',
                    style:
                        const TextStyle(fontSize: 12, color: _textSec),
                  ),
                ],
              ),
            ),
          ]),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: approvedCount / total,
              minHeight: 6,
              backgroundColor: _green.withAlpha(30),
              valueColor: const AlwaysStoppedAnimation<Color>(_green),
            ),
          ),
          const SizedBox(height: 6),
          Text(
            '$approvedCount / $total documents approved',
            style: const TextStyle(
                fontSize: 11, color: _green, fontWeight: FontWeight.w500),
          ),
        ],
      ),
    );
  }
}

// ── Document card ─────────────────────────────────────────────────────────

class _VDocumentCard extends ConsumerStatefulWidget {
  final int              vehicleId;
  final String           documentType;
  final String           label;
  final IconData         icon;
  final VehicleDocument? uploaded;
  final VoidCallback     onUploaded;

  const _VDocumentCard({
    required this.vehicleId,
    required this.documentType,
    required this.label,
    required this.icon,
    required this.uploaded,
    required this.onUploaded,
  });

  @override
  ConsumerState<_VDocumentCard> createState() => _VDocumentCardState();
}

class _VDocumentCardState extends ConsumerState<_VDocumentCard> {
  bool _uploading = false;
  double _uploadProgress = 0;
  final _picker = ImagePicker();

  Future<void> _pickAndUpload() async {
    final choice = await _showSourceSheet();
    if (choice == null) return;

    Uint8List? fileBytes;
    String? fileName;

    if (choice == 'pdf') {
      final result = await FilePicker.platform.pickFiles(
        type:              FileType.custom,
        allowedExtensions: ['pdf'],
        withData:          true,
      );
      if (result == null || result.files.isEmpty) return;
      final picked = result.files.single;
      if (picked.bytes == null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('Could not read the selected PDF file'),
            backgroundColor: _danger,
          ));
        }
        return;
      }
      fileBytes = picked.bytes!;
      fileName = picked.name;
    } else {
      final source =
          choice == 'camera' ? ImageSource.camera : ImageSource.gallery;
      final XFile? file = await _picker.pickImage(
        source:       source,
        imageQuality: 85,
        maxWidth:     1920,
      );
      if (file == null) return;
      fileBytes = await file.readAsBytes();
      fileName = file.name;
    }

    setState(() { _uploading = true; _uploadProgress = 0; });
    try {
      await ref.read(documentRepositoryProvider).uploadForVehicle(
            vehicleId:    widget.vehicleId,
            documentType: widget.documentType,
            fileBytes:    fileBytes,
            fileName:     fileName,
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('${widget.label} uploaded — pending review'),
          backgroundColor: _green,
        ));
        widget.onUploaded();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Upload failed: $e'),
          backgroundColor: _danger,
        ));
      }
    } finally {
      if (mounted) setState(() { _uploading = false; _uploadProgress = 0; });
    }
  }

  /// Returns 'camera' | 'gallery' | 'pdf' | null (dismissed).
  Future<String?> _showSourceSheet() {
    return showModalBottomSheet<String>(
      context: context,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (_) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 36, height: 4,
                margin: const EdgeInsets.only(bottom: 12),
                decoration: BoxDecoration(
                    color: _border, borderRadius: BorderRadius.circular(2)),
              ),
              ListTile(
                leading: const Icon(Icons.camera_alt_outlined, color: _green),
                title: const Text('Take a photo'),
                onTap: () => Navigator.pop(context, 'camera'),
              ),
              ListTile(
                leading: const Icon(Icons.photo_library_outlined, color: _green),
                title: const Text('Choose from gallery'),
                onTap: () => Navigator.pop(context, 'gallery'),
              ),
              ListTile(
                leading: const Icon(Icons.picture_as_pdf_outlined, color: _green),
                title: const Text('Choose a PDF'),
                onTap: () => Navigator.pop(context, 'pdf'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final doc         = widget.uploaded;
    final isApproved  = doc?.isApproved ?? false;
    final statusColor = _statusColor(doc?.status);
    final statusIcon  = _statusIcon(doc?.status);
    final statusLbl   = _statusLabel(doc?.status);

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: doc == null ? _border : statusColor.withAlpha(80),
          width: 0.5,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            // Icon
            Container(
              width: 42, height: 42,
              decoration: BoxDecoration(
                color: (doc == null ? _textSec : statusColor).withAlpha(18),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Icon(widget.icon,
                  color: doc == null ? _textSec : statusColor, size: 22),
            ),
            const SizedBox(width: 12),

            // Label + status
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(widget.label,
                      style: const TextStyle(
                          fontSize: 14, fontWeight: FontWeight.w600, color: _textPri)),
                  const SizedBox(height: 4),
                  if (doc != null)
                    // Status badge pill
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 10, vertical: 3),
                      decoration: BoxDecoration(
                        color: statusColor.withAlpha(20),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(statusIcon, size: 11, color: statusColor),
                          const SizedBox(width: 4),
                          Text(statusLbl,
                              style: TextStyle(
                                  color: statusColor,
                                  fontSize: 11,
                                  fontWeight: FontWeight.w500)),
                        ],
                      ),
                    )
                  else
                    // "Not uploaded" pill
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 10, vertical: 3),
                      decoration: BoxDecoration(
                        color: _textSec.withAlpha(15),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: const Text(
                        'Not uploaded',
                        style: TextStyle(
                            color: _textSec,
                            fontSize: 11,
                            fontWeight: FontWeight.w500),
                      ),
                    ),
                ],
              ),
            ),

            // Upload / Replace button — hidden when approved
            if (_uploading)
              const SizedBox(
                width: 22, height: 22,
                child: CircularProgressIndicator(
                    strokeWidth: 2, color: _green))
            else if (!isApproved)
              _VUploadButton(isReplace: doc != null, onTap: _pickAndUpload),
          ]),

          // Upload progress bar
          if (_uploading) ...[
            const SizedBox(height: 10),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: _uploadProgress > 0 ? _uploadProgress : null,
                backgroundColor: _border,
                valueColor: const AlwaysStoppedAnimation<Color>(_amber),
                minHeight: 4,
              ),
            ),
            const SizedBox(height: 4),
            const Text('Uploading…',
                style: TextStyle(fontSize: 11, color: _textSec)),
          ],

          // Rejection reason
          if (doc != null && doc.isRejected && doc.rejectionReason != null) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: _danger.withAlpha(12),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: _danger.withAlpha(50), width: 0.5),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(Icons.error_outline, size: 15, color: _danger),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      'Reason: ${doc.rejectionReason}',
                      style: const TextStyle(fontSize: 12, color: _danger),
                    ),
                  ),
                ],
              ),
            ),
          ],

          // File name + upload date
          if (doc != null) ...[
            const SizedBox(height: 8),
            Text(
              '${doc.originalName}  ·  ${_formatDate(doc.createdAt)}',
              style: const TextStyle(fontSize: 11, color: _textSec),
            ),
          ],
        ],
      ),
    );
  }
}

// ── Upload button ─────────────────────────────────────────────────────────

class _VUploadButton extends StatelessWidget {
  final bool          isReplace;
  final VoidCallback  onTap;
  const _VUploadButton({required this.isReplace, required this.onTap});

  @override
  Widget build(BuildContext context) {
    if (isReplace) {
      return OutlinedButton(
        onPressed: onTap,
        style: OutlinedButton.styleFrom(
          foregroundColor: _green,
          side: const BorderSide(color: _green, width: 0.5),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        ),
        child: const Text('Replace',
            style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
      );
    }
    return ElevatedButton(
      onPressed: onTap,
      style: ElevatedButton.styleFrom(
        backgroundColor: _amber,
        foregroundColor: Colors.white,
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        elevation: 0,
      ),
      child: const Text('Upload',
          style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
    );
  }
}

// ── Loading skeleton ──────────────────────────────────────────────────────

class _LoadingSkeleton extends StatefulWidget {
  const _LoadingSkeleton();
  @override
  State<_LoadingSkeleton> createState() => _LoadingSkeletonState();
}

class _LoadingSkeletonState extends State<_LoadingSkeleton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctrl;
  late final Animation<double>   _anim;

  @override
  void initState() {
    super.initState();
    _ctrl = AnimationController(
        vsync: this, duration: const Duration(milliseconds: 900))
      ..repeat(reverse: true);
    _anim = Tween<double>(begin: 0.3, end: 0.7).animate(_ctrl);
  }

  @override
  void dispose() { _ctrl.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _anim,
      builder: (_, __) => ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _SkeletonBox(height: 88, radius: 12, opacity: _anim.value),
          const SizedBox(height: 20),
          const SizedBox(height: 8),
          _SkeletonBox(height: 16, width: 180, radius: 4, opacity: _anim.value),
          const SizedBox(height: 12),
          ...List.generate(4, (_) => Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: _SkeletonBox(height: 90, radius: 12, opacity: _anim.value),
          )),
        ],
      ),
    );
  }
}

class _SkeletonBox extends StatelessWidget {
  final double  height;
  final double? width;
  final double  radius;
  final double  opacity;

  const _SkeletonBox({
    required this.height,
    this.width,
    required this.radius,
    required this.opacity,
  });

  @override
  Widget build(BuildContext context) => Container(
        height: height,
        width:  width,
        decoration: BoxDecoration(
          color: Color.fromRGBO(0, 0, 0, opacity * 0.08),
          borderRadius: BorderRadius.circular(radius),
        ),
      );
}

// ── Error state ───────────────────────────────────────────────────────────

class _ErrorState extends StatelessWidget {
  final String   message;
  final VoidCallback onRetry;
  const _ErrorState({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.cloud_off_outlined, size: 52, color: _textSec),
            const SizedBox(height: 12),
            Text(message,
                textAlign: TextAlign.center,
                style: const TextStyle(color: _textSec, fontSize: 13)),
            const SizedBox(height: 20),
            OutlinedButton.icon(
              onPressed: onRetry,
              icon:  const Icon(Icons.refresh, size: 16),
              label: const Text('Try again'),
              style: OutlinedButton.styleFrom(foregroundColor: _green),
            ),
          ],
        ),
      ),
    );
  }
}
