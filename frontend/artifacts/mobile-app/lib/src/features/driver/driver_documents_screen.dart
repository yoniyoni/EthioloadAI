import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import '../../data/models/models.dart';
import '../../data/providers/data_providers.dart';
import '../../data/repositories/repositories.dart';

// ── Palette ───────────────────────────────────────────────────────────────
const _green   = Color(0xFF0F3D1A);
const _amber   = Color(0xFFF59E0B);
const _bg      = Color(0xFFF8FBF8);
const _border  = Color(0xFFE5E7EB);
const _textPri = Color(0xFF111827);
const _textSec = Color(0xFF6B7280);
const _danger  = Color(0xFFEF4444);
const _success = Color(0xFF059669);

// ── Document type definitions (English + Amharic) ─────────────────────────

class _DocInfo {
  final String   type;
  final String   label;
  final String   amharic;
  final IconData icon;
  const _DocInfo(this.type, this.label, this.amharic, this.icon);
}

const _requiredDocs = [
  _DocInfo('license',              "Driver's License",       'የመንጃ ፈቃድ',               Icons.badge_outlined),
  _DocInfo('national_id',          'National ID',             'ብሔራዊ መታወቂያ',            Icons.person_pin_outlined),
  _DocInfo('vehicle_registration', 'Vehicle Registration',    'የተሽከርካሪ ምዝገባ',           Icons.directions_car_outlined),
  _DocInfo('insurance',            'Insurance Certificate',   'ኢንሹራንስ ሰርተፍኬት',         Icons.shield_outlined),
  _DocInfo('tin',                  'TIN Certificate',         'የታክስ ምዝገባ ሰርተፍኬት',       Icons.receipt_long_outlined),
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

// Returns "English label / Amharic label" for status
String _statusLabel(String? status) => switch (status) {
      'approved' => 'Approved / ተቀባይነት አግኝቷል',
      'rejected' => 'Rejected / ተቀብዷل',
      'pending'  => 'Under review / በሂደት ላይ',
      _          => 'Not uploaded / አልተሰቀለም',
    };

String _formatDate(DateTime? dt) {
  if (dt == null) return '';
  return '${dt.day}/${dt.month}/${dt.year}';
}

// ── Screen ────────────────────────────────────────────────────────────────

class DriverDocumentsScreen extends ConsumerWidget {
  const DriverDocumentsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final docsAsync = ref.watch(driverDocumentsProvider);

    return Scaffold(
      backgroundColor: _bg,
      appBar: AppBar(
        backgroundColor: _green,
        elevation: 0,
        iconTheme: const IconThemeData(color: Colors.white),
        title: const Text(
          'My Documents / ሰነዶቼ',
          style: TextStyle(
              color: Colors.white, fontWeight: FontWeight.w600, fontSize: 16),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white),
            tooltip: 'Refresh',
            onPressed: () => ref.invalidate(driverDocumentsProvider),
          ),
        ],
      ),
      body: docsAsync.when(
        data:    (docs) => _Body(docs: docs),
        loading: () => const _LoadingSkeleton(),
        error:   (e, _) => _ErrorState(
          message: e.toString(),
          onRetry: () => ref.invalidate(driverDocumentsProvider),
        ),
      ),
    );
  }
}

// ── Body ──────────────────────────────────────────────────────────────────

class _Body extends ConsumerWidget {
  final List<DriverDocument> docs;
  const _Body({required this.docs});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final uploaded = {for (final d in docs) d.documentType: d};
    final approvedCount = docs.where((d) => d.isApproved).length;
    final allApproved   = approvedCount >= _requiredDocs.length;
    final anyPending    = docs.any((d) => d.isPending);

    return RefreshIndicator(
      color: _amber,
      onRefresh: () async => ref.invalidate(driverDocumentsProvider),
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // ── Progress + status banner ──────────────────────────────
          _ProgressBanner(
            allApproved:   allApproved,
            anyPending:    anyPending,
            approvedCount: approvedCount,
            total:         _requiredDocs.length,
          ),
          const SizedBox(height: 20),

          // ── Upload tips (shown until all approved) ────────────────
          if (!allApproved) ...[
            const _InfoCard(),
            const SizedBox(height: 20),
          ],

          // ── Document cards ────────────────────────────────────────
          const Text(
            'Required Documents / አስፈላጊ ሰነዶች',
            style: TextStyle(
                fontSize: 15, fontWeight: FontWeight.w600, color: _textPri),
          ),
          const SizedBox(height: 10),

          ..._requiredDocs.map((entry) {
            final doc = uploaded[entry.type];
            return _DocumentCard(
              documentType: entry.type,
              label:        entry.label,
              amharic:      entry.amharic,
              icon:         entry.icon,
              uploaded:     doc,
              onUploaded:   () => ref.invalidate(driverDocumentsProvider),
            );
          }),

          const SizedBox(height: 32),
        ],
      ),
    );
  }
}

// ── Progress banner ───────────────────────────────────────────────────────

class _ProgressBanner extends StatelessWidget {
  final bool allApproved;
  final bool anyPending;
  final int  approvedCount;
  final int  total;

  const _ProgressBanner({
    required this.allApproved,
    required this.anyPending,
    required this.approvedCount,
    required this.total,
  });

  @override
  Widget build(BuildContext context) {
    final Color color;
    final IconData icon;
    final String title;
    final String subtitle;

    if (allApproved) {
      color    = _success;
      icon     = Icons.verified_rounded;
      title    = '✓  Verified Driver / ተረጋጋጠ ሾፌር';
      subtitle = 'All documents approved. You can accept cargo and receive payments.';
    } else if (anyPending) {
      color    = _amber;
      icon     = Icons.hourglass_top_rounded;
      title    = 'Under Review / በሂደት ላይ';
      subtitle = '$approvedCount of $total approved. Admin is reviewing the rest.';
    } else {
      color    = _green;
      icon     = Icons.upload_file_outlined;
      title    = 'Upload Your Documents / ሰነዶችን ይስቀሉ';
      subtitle = '$approvedCount of $total approved. Upload the remaining to get verified.';
    }

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withAlpha(18),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withAlpha(60), width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Icon(icon, color: color, size: 28),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title,
                      style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: color)),
                  const SizedBox(height: 3),
                  Text(subtitle,
                      style: const TextStyle(fontSize: 12, color: _textSec)),
                ],
              ),
            ),
          ]),
          const SizedBox(height: 12),
          // Progress bar
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: approvedCount / total,
              minHeight: 6,
              backgroundColor: color.withAlpha(30),
              valueColor: AlwaysStoppedAnimation<Color>(color),
            ),
          ),
          const SizedBox(height: 6),
          Text(
            '$approvedCount / $total documents approved',
            style: TextStyle(
                fontSize: 11, color: color, fontWeight: FontWeight.w500),
          ),
        ],
      ),
    );
  }
}

// ── Info card ─────────────────────────────────────────────────────────────

class _InfoCard extends StatelessWidget {
  const _InfoCard();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _border, width: 0.5),
      ),
      child: const Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Icon(Icons.info_outline, size: 16, color: _textSec),
            SizedBox(width: 6),
            Text('Upload tips',
                style: TextStyle(
                    fontSize: 13, fontWeight: FontWeight.w600, color: _textPri)),
          ]),
          SizedBox(height: 8),
          _Tip('Take clear photos in good lighting — all text must be readable.'),
          _Tip('Accepted formats: JPG, PNG, or PDF (max 5 MB each).'),
          _Tip('Documents in Amharic are accepted / በአማርኛ ሰነዶች ተቀባይነት አላቸው።'),
          _Tip('Rejected documents show the reason — re-upload to fix.'),
        ],
      ),
    );
  }
}

class _Tip extends StatelessWidget {
  final String text;
  const _Tip(this.text);

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 4),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('• ', style: TextStyle(color: _textSec, fontSize: 12)),
            Expanded(
                child: Text(text,
                    style: const TextStyle(fontSize: 12, color: _textSec))),
          ],
        ),
      );
}

// ── Document card ─────────────────────────────────────────────────────────

class _DocumentCard extends ConsumerStatefulWidget {
  final String           documentType;
  final String           label;
  final String           amharic;
  final IconData         icon;
  final DriverDocument?  uploaded;
  final VoidCallback     onUploaded;

  const _DocumentCard({
    required this.documentType,
    required this.label,
    required this.amharic,
    required this.icon,
    required this.uploaded,
    required this.onUploaded,
  });

  @override
  ConsumerState<_DocumentCard> createState() => _DocumentCardState();
}

class _DocumentCardState extends ConsumerState<_DocumentCard> {
  bool _uploading = false;
  double _uploadProgress = 0;
  final _picker = ImagePicker();

  Future<void> _pickAndUpload() async {
    final source = await _showSourceSheet();
    if (source == null) return;

    final XFile? file = await _picker.pickImage(
      source:       source,
      imageQuality: 85,
      maxWidth:     1920,
    );
    if (file == null) return;

    setState(() { _uploading = true; _uploadProgress = 0; });
    try {
      await ref.read(documentRepositoryProvider).upload(
            documentType: widget.documentType,
            filePath:     file.path,
            fileName:     file.name,
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
              '${widget.label} uploaded — pending review / በሂደት ላይ ነው'),
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

  Future<ImageSource?> _showSourceSheet() {
    return showModalBottomSheet<ImageSource>(
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
                title: const Text('Take a photo / ፎቶ ያንሱ'),
                onTap: () => Navigator.pop(context, ImageSource.camera),
              ),
              ListTile(
                leading: const Icon(Icons.photo_library_outlined, color: _green),
                title: const Text('Choose from gallery / ከጋለሪ ይምረጡ'),
                onTap: () => Navigator.pop(context, ImageSource.gallery),
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
                  Text(widget.amharic,
                      style: const TextStyle(fontSize: 12, color: _textSec)),
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
                        'Not uploaded / አልተሰቀለም',
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
              _UploadButton(isReplace: doc != null, onTap: _pickAndUpload),
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
            const Text('Uploading… / በመስቀል ላይ…',
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

class _UploadButton extends StatelessWidget {
  final bool          isReplace;
  final VoidCallback  onTap;
  const _UploadButton({required this.isReplace, required this.onTap});

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
          _SkeletonBox(height: 80, radius: 12, opacity: _anim.value * 0.8),
          const SizedBox(height: 20),
          const SizedBox(height: 8),
          _SkeletonBox(height: 16, width: 180, radius: 4, opacity: _anim.value),
          const SizedBox(height: 12),
          ...List.generate(5, (_) => Padding(
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
              label: const Text('Try again / እንደገና ሞክር'),
              style: OutlinedButton.styleFrom(foregroundColor: _green),
            ),
          ],
        ),
      ),
    );
  }
}
