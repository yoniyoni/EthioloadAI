import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';
import 'package:file_picker/file_picker.dart';
import '../../data/api/api_client.dart';
import '../../data/models/models.dart';
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

// ── Providers ─────────────────────────────────────────────────────────────

final fleetDriverDetailProvider = FutureProvider.autoDispose
    .family<Map<String, dynamic>, int>((ref, driverId) async {
  return ref.read(fleetRepositoryProvider).getDriverDetail(driverId);
});

final fleetDriverDocumentsProvider = FutureProvider.autoDispose
    .family<List<DriverDocument>, int>((ref, driverId) async {
  return ref.read(documentRepositoryProvider).listForDriver(driverId);
});

// ── Document type definitions (mirrors driver_documents_screen.dart) ──────

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

// ── Status helpers (mirrors driver_documents_screen.dart) ──────────────────

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

class FleetDriverDetailScreen extends ConsumerStatefulWidget {
  final int driverId;
  const FleetDriverDetailScreen({super.key, required this.driverId});

  @override
  ConsumerState<FleetDriverDetailScreen> createState() =>
      _FleetDriverDetailScreenState();
}

class _FleetDriverDetailScreenState
    extends ConsumerState<FleetDriverDetailScreen> {
  void _refreshAll() {
    ref.invalidate(fleetDriverDetailProvider(widget.driverId));
    ref.invalidate(fleetDriverDocumentsProvider(widget.driverId));
  }

  Future<void> _openEditSheet(Map<String, dynamic> detail) async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (_) => _EditDriverSheet(driverId: widget.driverId, detail: detail),
    );
  }

  @override
  Widget build(BuildContext context) {
    final detailAsync = ref.watch(fleetDriverDetailProvider(widget.driverId));

    return Scaffold(
      backgroundColor: _bg,
      appBar: AppBar(
        backgroundColor: Colors.green,
        elevation: 0,
        iconTheme: const IconThemeData(color: Colors.white),
        title: const Text(
          'Driver Details',
          style: TextStyle(
              color: Colors.white, fontWeight: FontWeight.w600, fontSize: 16),
        ),
        actions: [
          detailAsync.maybeWhen(
            data: (detail) => IconButton(
              icon: const Icon(Icons.edit_outlined, color: Colors.white),
              tooltip: 'Edit driver',
              onPressed: () => _openEditSheet(detail),
            ),
            orElse: () => const SizedBox.shrink(),
          ),
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white),
            tooltip: 'Refresh',
            onPressed: _refreshAll,
          ),
        ],
      ),
      body: RefreshIndicator(
        color: _amber,
        onRefresh: () async => _refreshAll(),
        child: detailAsync.when(
          data: (detail) =>
              _Body(driverId: widget.driverId, detail: detail),
          loading: () => ListView(
            children: const [
              SizedBox(height: 200),
              Center(child: CircularProgressIndicator()),
            ],
          ),
          error: (e, _) => _ErrorState(
            message: e.toString(),
            onRetry: () =>
                ref.invalidate(fleetDriverDetailProvider(widget.driverId)),
          ),
        ),
      ),
    );
  }
}

// ── Body ──────────────────────────────────────────────────────────────────

class _Body extends ConsumerWidget {
  final int driverId;
  final Map<String, dynamic> detail;
  const _Body({required this.driverId, required this.detail});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final docsAsync = ref.watch(fleetDriverDocumentsProvider(driverId));

    final fullName = detail['full_name']?.toString() ?? '—';
    final phone = detail['phone']?.toString();
    final email = detail['email']?.toString();
    final isVerified = detail['verification_status'] == true;
    final approvedCount =
        (detail['documents_approved'] as num?)?.toInt() ?? 0;
    final requiredCount = (detail['documents_required'] as num?)?.toInt() ??
        _requiredDocs.length;
    final assignedVehicle =
        detail['assigned_vehicle'] as Map<String, dynamic>?;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _DriverInfoCard(
          fullName: fullName,
          phone: (phone == null || phone.isEmpty) ? 'Not provided' : phone,
          email: (email == null || email.isEmpty) ? 'Not provided' : email,
          isVerified: isVerified,
        ),
        const SizedBox(height: 16),

        _ProgressBanner(approvedCount: approvedCount, total: requiredCount),
        const SizedBox(height: 16),

        _AssignedVehicleCard(vehicle: assignedVehicle),
        const SizedBox(height: 20),

        const Text(
          'Documents',
          style: TextStyle(
              fontSize: 15, fontWeight: FontWeight.w600, color: _textPri),
        ),
        const SizedBox(height: 10),

        docsAsync.when(
          data: (docs) {
            final uploaded = {for (final d in docs) d.documentType: d};
            return Column(
              children: _requiredDocs.map((entry) {
                final doc = uploaded[entry.type];
                return _DocumentCard(
                  driverId: driverId,
                  documentType: entry.type,
                  label: entry.label,
                  amharic: entry.amharic,
                  icon: entry.icon,
                  uploaded: doc,
                  onUploaded: () =>
                      ref.invalidate(fleetDriverDocumentsProvider(driverId)),
                );
              }).toList(),
            );
          },
          loading: () => const Padding(
            padding: EdgeInsets.symmetric(vertical: 24),
            child: Center(child: CircularProgressIndicator()),
          ),
          error: (e, _) => Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Text('Failed to load documents: $e',
                style: const TextStyle(color: _danger, fontSize: 12)),
          ),
        ),

        const SizedBox(height: 28),
        const Divider(color: _border),
        const SizedBox(height: 16),

        _DangerZone(driverId: driverId, fullName: fullName),

        const SizedBox(height: 32),
      ],
    );
  }
}

// ── Driver info card ─────────────────────────────────────────────────────

class _DriverInfoCard extends StatelessWidget {
  final String fullName;
  final String phone;
  final String email;
  final bool isVerified;

  const _DriverInfoCard({
    required this.fullName,
    required this.phone,
    required this.email,
    required this.isVerified,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _border, width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            const CircleAvatar(
              radius: 24,
              backgroundColor: Color(0xFF1976D2),
              child: Icon(Icons.person, color: Colors.white, size: 24),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(fullName,
                      style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          color: _textPri)),
                  const SizedBox(height: 4),
                  Container(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 10, vertical: 3),
                    decoration: BoxDecoration(
                      color: (isVerified ? _success : _textSec).withAlpha(20),
                      borderRadius: BorderRadius.circular(20),
                    ),
                    child: Text(
                      isVerified ? 'Verified' : 'Unverified',
                      style: TextStyle(
                        color: isVerified ? _success : _textSec,
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ]),
          const SizedBox(height: 14),
          const Divider(height: 1, color: _border),
          const SizedBox(height: 14),
          _InfoRow(icon: Icons.phone_outlined, label: 'Phone', value: phone),
          const SizedBox(height: 10),
          _InfoRow(icon: Icons.email_outlined, label: 'Email', value: email),
        ],
      ),
    );
  }
}

class _InfoRow extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  const _InfoRow({required this.icon, required this.label, required this.value});

  @override
  Widget build(BuildContext context) => Row(children: [
        Icon(icon, size: 16, color: _textSec),
        const SizedBox(width: 8),
        Text('$label: ', style: const TextStyle(fontSize: 12, color: _textSec)),
        Expanded(
          child: Text(value,
              style: const TextStyle(
                  fontSize: 13, color: _textPri, fontWeight: FontWeight.w500)),
        ),
      ]);
}

// ── Progress banner (simplified single-state version) ──────────────────────

class _ProgressBanner extends StatelessWidget {
  final int approvedCount;
  final int total;
  const _ProgressBanner({required this.approvedCount, required this.total});

  @override
  Widget build(BuildContext context) {
    final allApproved = total > 0 && approvedCount >= total;
    final color = allApproved ? _success : _amber;
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
            Icon(
                allApproved
                    ? Icons.verified_rounded
                    : Icons.hourglass_top_rounded,
                color: color,
                size: 26),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                allApproved
                    ? 'Verified — all documents approved'
                    : 'Verification pending',
                style: TextStyle(
                    fontSize: 14, fontWeight: FontWeight.w700, color: color),
              ),
            ),
          ]),
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: total > 0 ? approvedCount / total : 0,
              minHeight: 6,
              backgroundColor: color.withAlpha(30),
              valueColor: AlwaysStoppedAnimation<Color>(color),
            ),
          ),
          const SizedBox(height: 6),
          Text('$approvedCount / $total documents approved',
              style: TextStyle(
                  fontSize: 11, color: color, fontWeight: FontWeight.w500)),
        ],
      ),
    );
  }
}

// ── Assigned vehicle card ───────────────────────────────────────────────

class _AssignedVehicleCard extends StatelessWidget {
  final Map<String, dynamic>? vehicle;
  const _AssignedVehicleCard({required this.vehicle});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: _border, width: 0.5),
      ),
      child: Row(children: [
        Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            color: _green.withAlpha(18),
            borderRadius: BorderRadius.circular(8),
          ),
          child: const Icon(Icons.local_shipping_outlined,
              color: _green, size: 22),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Text('Assigned Vehicle',
                  style: TextStyle(
                      fontSize: 12,
                      color: _textSec,
                      fontWeight: FontWeight.w600)),
              const SizedBox(height: 2),
              Text(
                vehicle == null
                    ? 'No vehicle assigned'
                    : '${vehicle!['plate_number'] ?? '—'}  ·  ${vehicle!['truck_type'] ?? '—'}',
                style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: _textPri),
              ),
            ],
          ),
        ),
      ]),
    );
  }
}

// ── Document card (mirrors driver_documents_screen.dart's _DocumentCard) ──

class _DocumentCard extends ConsumerStatefulWidget {
  final int             driverId;
  final String          documentType;
  final String          label;
  final String          amharic;
  final IconData         icon;
  final DriverDocument?  uploaded;
  final VoidCallback     onUploaded;

  const _DocumentCard({
    required this.driverId,
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

    setState(() => _uploading = true);
    try {
      await ref.read(documentRepositoryProvider).uploadForDriver(
            driverId:     widget.driverId,
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
      if (mounted) setState(() => _uploading = false);
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
            if (_uploading)
              const SizedBox(
                width: 22, height: 22,
                child: CircularProgressIndicator(
                    strokeWidth: 2, color: _green))
            else if (!isApproved)
              _UploadButton(isReplace: doc != null, onTap: _pickAndUpload),
          ]),

          if (_uploading) ...[
            const SizedBox(height: 10),
            const ClipRRect(
              borderRadius: BorderRadius.all(Radius.circular(4)),
              child: LinearProgressIndicator(
                backgroundColor: _border,
                valueColor: AlwaysStoppedAnimation<Color>(_amber),
                minHeight: 4,
              ),
            ),
            const SizedBox(height: 4),
            const Text('Uploading…',
                style: TextStyle(fontSize: 11, color: _textSec)),
          ],

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

// ── Edit driver sheet ────────────────────────────────────────────────────

class _EditDriverSheet extends ConsumerStatefulWidget {
  final int driverId;
  final Map<String, dynamic> detail;
  const _EditDriverSheet({required this.driverId, required this.detail});

  @override
  ConsumerState<_EditDriverSheet> createState() => _EditDriverSheetState();
}

class _EditDriverSheetState extends ConsumerState<_EditDriverSheet> {
  late final TextEditingController _nameCtrl;
  late final TextEditingController _phoneCtrl;
  late final TextEditingController _emailCtrl;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _nameCtrl =
        TextEditingController(text: widget.detail['full_name']?.toString() ?? '');
    _phoneCtrl =
        TextEditingController(text: widget.detail['phone']?.toString() ?? '');
    _emailCtrl =
        TextEditingController(text: widget.detail['email']?.toString() ?? '');
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _phoneCtrl.dispose();
    _emailCtrl.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    final fullName = _nameCtrl.text.trim();
    final phone = _phoneCtrl.text.trim();
    final email = _emailCtrl.text.trim();
    if (fullName.isEmpty || phone.isEmpty) {
      setState(() => _error = 'Name and phone are required');
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ref.read(fleetRepositoryProvider).updateDriver(
            widget.driverId,
            fullName: fullName,
            phone: phone,
            email: email.isEmpty ? null : email,
          );
      if (mounted) {
        ref.invalidate(fleetDriverDetailProvider(widget.driverId));
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Driver details updated'),
          backgroundColor: _success,
        ));
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _saving = false;
          _error = e is ApiException ? e.message : 'Failed to update driver: $e';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Edit Driver',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            const Text(
              'Update this driver\'s name, phone, or email.',
              style: TextStyle(fontSize: 12, color: Colors.grey),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _nameCtrl,
              decoration: InputDecoration(
                labelText: 'Full Name',
                prefixIcon: const Icon(Icons.person_outline),
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _phoneCtrl,
              keyboardType: TextInputType.phone,
              decoration: InputDecoration(
                labelText: 'Phone',
                prefixIcon: const Icon(Icons.phone_outlined),
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _emailCtrl,
              keyboardType: TextInputType.emailAddress,
              decoration: InputDecoration(
                labelText: 'Email (optional)',
                prefixIcon: const Icon(Icons.email_outlined),
                border:
                    OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
              ),
            ),
            if (_error != null) ...[
              const SizedBox(height: 10),
              Text(_error!,
                  style: const TextStyle(color: _danger, fontSize: 12)),
            ],
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _saving ? null : _save,
                style: ElevatedButton.styleFrom(
                  backgroundColor: _green,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
                child: _saving
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white))
                    : const Text('Save Changes',
                        style: TextStyle(color: Colors.white)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Danger zone (remove from fleet / delete permanently) ──────────────────

class _DangerZone extends ConsumerWidget {
  final int driverId;
  final String fullName;
  const _DangerZone({required this.driverId, required this.fullName});

  Future<void> _removeFromFleet(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Remove from fleet?'),
        content: Text(
          '$fullName will be unlinked from your fleet. Their driver account '
          'and history are kept — you can re-add them later.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Remove', style: TextStyle(color: _danger)),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await ref.read(fleetRepositoryProvider).removeDriver(driverId);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('$fullName removed from fleet'),
          backgroundColor: _success,
        ));
        context.pop();
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
              e is ApiException ? e.message : 'Failed to remove driver: $e'),
          backgroundColor: _danger,
        ));
      }
    }
  }

  Future<void> _deletePermanently(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Delete driver permanently?'),
        content: Text(
          'This will permanently delete $fullName\'s account. This action is '
          'IRREVERSIBLE. If this driver has booking or trip history, deletion '
          'may not be possible — use "Remove from Fleet" instead in that case.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Delete Permanently',
                style: TextStyle(color: _danger)),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await ref.read(fleetRepositoryProvider).deleteDriverAccount(driverId);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('$fullName deleted permanently'),
          backgroundColor: _success,
        ));
        context.pop();
      }
    } catch (e) {
      final message =
          e is ApiException ? e.message : 'Failed to delete driver: $e';
      if (context.mounted) {
        await showDialog<void>(
          context: context,
          builder: (dialogContext) => AlertDialog(
            title: const Text('Cannot delete driver'),
            content: Text(message),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(dialogContext),
                child: const Text('OK'),
              ),
            ],
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text('Danger Zone',
            style: TextStyle(
                fontSize: 13, fontWeight: FontWeight.w700, color: _danger)),
        const SizedBox(height: 10),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            onPressed: () => _removeFromFleet(context, ref),
            icon: const Icon(Icons.remove_circle_outline, size: 18),
            label: const Text('Remove from Fleet'),
            style: OutlinedButton.styleFrom(
              foregroundColor: _textSec,
              side: const BorderSide(color: _border),
              padding: const EdgeInsets.symmetric(vertical: 12),
            ),
          ),
        ),
        const SizedBox(height: 10),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            onPressed: () => _deletePermanently(context, ref),
            icon: const Icon(Icons.delete_forever_outlined,
                size: 18, color: _danger),
            label: const Text('Delete Permanently',
                style: TextStyle(color: _danger)),
            style: OutlinedButton.styleFrom(
              foregroundColor: _danger,
              side: const BorderSide(color: _danger),
              padding: const EdgeInsets.symmetric(vertical: 12),
            ),
          ),
        ),
      ],
    );
  }
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
