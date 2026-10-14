import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../delivery/delivery_repository.dart';

import 'dispatch_assignment.dart';
import 'dispatch_repository.dart';

/// Phase 6 — Driver assignment inbox screen.
///
/// Shows all dispatch assignments directed to the authenticated driver,
/// newest first. Provides a quick-action row for ASSIGNED items.
///
/// Route: /driver/assignments
class MyAssignmentsScreen extends ConsumerWidget {
  const MyAssignmentsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final assignmentsAsync = ref.watch(myAssignmentsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('My Assignments'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () => ref.invalidate(myAssignmentsProvider),
            tooltip: 'Refresh',
          ),
        ],
      ),
      body: assignmentsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => _ErrorView(
          message: err.toString(),
          onRetry: () => ref.invalidate(myAssignmentsProvider),
        ),
        data: (assignments) {
          if (assignments.isEmpty) {
            return const _EmptyView();
          }
          return RefreshIndicator(
            onRefresh: () async => ref.invalidate(myAssignmentsProvider),
            child: ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: assignments.length,
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (context, index) {
                return _AssignmentCard(
                  assignment: assignments[index],
                  onTap: () => context.push(
                    '/driver/assignments/${assignments[index].id}',
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }
}

// ── Assignment card ────────────────────────────────────────────────────────────

class _AssignmentCard extends StatelessWidget {
  final DispatchAssignment assignment;
  final VoidCallback onTap;

  const _AssignmentCard({required this.assignment, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final isPending = assignment.isPending;

    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Status banner
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              color: _statusColor(assignment.status, colorScheme),
              child: Row(
                children: [
                  Icon(_statusIcon(assignment.status), size: 16, color: Colors.white),
                  const SizedBox(width: 6),
                  Text(
                    assignment.status.displayName,
                    style: theme.textTheme.labelMedium?.copyWith(
                      color: Colors.white,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const Spacer(),
                  if (isPending)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.white24,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Text(
                        'ACTION REQUIRED',
                        style: TextStyle(
                          fontSize: 10,
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                ],
              ),
            ),

            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Freight ID
                  Text(
                    'Shipment #${assignment.freightId}',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 8),

                  // Agreed amount
                  Row(
                    children: [
                      const Icon(Icons.attach_money, size: 18, color: Colors.green),
                      const SizedBox(width: 4),
                      Text(
                        'ETB ${_formatAmount(assignment.agreedAmount)}',
                        style: theme.textTheme.bodyLarge?.copyWith(
                          color: Colors.green.shade700,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),

                  // Vehicle
                  Row(
                    children: [
                      const Icon(Icons.local_shipping_outlined, size: 16,
                          color: Colors.grey),
                      const SizedBox(width: 4),
                      Text(
                        'Vehicle #${assignment.vehicleId}',
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                  ),
                  const SizedBox(height: 4),

                  // Assigned date
                  Row(
                    children: [
                      const Icon(Icons.schedule, size: 16, color: Colors.grey),
                      const SizedBox(width: 4),
                      Text(
                        'Assigned ${_formatDate(assignment.assignedAt)}',
                        style: theme.textTheme.bodySmall,
                      ),
                    ],
                  ),

                  if (assignment.notes != null && assignment.notes!.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: colorScheme.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        assignment.notes!,
                        style: theme.textTheme.bodySmall,
                      ),
                    ),
                  ],

                  if (isPending) ...[
                    const SizedBox(height: 12),
                    Text(
                      'Tap to review and respond to this assignment.',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: colorScheme.primary,
                        fontStyle: FontStyle.italic,
                      ),
                    ),
                  ],

                  if (assignment.bookingId != null) ...[
                    const SizedBox(height: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.green.shade50,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: Colors.green.shade200),
                      ),
                      child: Text(
                        '✓ Booking #${assignment.bookingId} confirmed',
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: Colors.green.shade700,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Color _statusColor(DispatchStatus status, ColorScheme cs) {
    switch (status) {
      case DispatchStatus.assigned:        return Colors.orange.shade600;
      case DispatchStatus.driverAccepted:  return Colors.green.shade600;
      case DispatchStatus.driverRejected:  return Colors.red.shade600;
      case DispatchStatus.cancelled:
      case DispatchStatus.superseded:      return Colors.grey.shade500;
    }
  }

  IconData _statusIcon(DispatchStatus status) {
    switch (status) {
      case DispatchStatus.assigned:        return Icons.notifications_active;
      case DispatchStatus.driverAccepted:  return Icons.check_circle;
      case DispatchStatus.driverRejected:  return Icons.cancel;
      case DispatchStatus.cancelled:       return Icons.block;
      case DispatchStatus.superseded:      return Icons.swap_horiz;
    }
  }

  String _formatAmount(double amount) {
    return amount.toStringAsFixed(0).replaceAllMapped(
        RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]},');
  }

  String _formatDate(DateTime dt) {
    final now = DateTime.now();
    final diff = now.difference(dt);
    if (diff.inHours < 1) return '${diff.inMinutes}m ago';
    if (diff.inDays < 1) return '${diff.inHours}h ago';
    return '${dt.day}/${dt.month}/${dt.year}';
  }
}

// ── Assignment detail / accept / reject screen ────────────────────────────────

/// Phase 6 — Driver assignment detail screen.
///
/// Route: /driver/assignments/:id
class AssignmentDetailScreen extends ConsumerStatefulWidget {
  final int dispatchId;
  const AssignmentDetailScreen({super.key, required this.dispatchId});

  @override
  ConsumerState<AssignmentDetailScreen> createState() =>
      _AssignmentDetailScreenState();
}

class _AssignmentDetailScreenState
    extends ConsumerState<AssignmentDetailScreen> {
  bool _isLoading = false;
  String? _errorMessage;
  final _rejectReasonCtrl = TextEditingController();

  @override
  void dispose() {
    _rejectReasonCtrl.dispose();
    super.dispose();
  }

  Future<void> _accept(DispatchAssignment assignment) async {
    setState(() { _isLoading = true; _errorMessage = null; });
    try {
      await ref.read(dispatchRepositoryProvider).acceptAssignment(assignment.id);
      ref.invalidate(assignmentDetailProvider(assignment.id));
      ref.invalidate(myAssignmentsProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Assignment accepted — booking confirmed!'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } catch (e) {
      setState(() { _errorMessage = _parseError(e); });
    } finally {
      if (mounted) setState(() { _isLoading = false; });
    }
  }

  Future<void> _openTrip(int bookingId) async {
    setState(() { _isLoading = true; _errorMessage = null; });
    try {
      final tripId = await ref.read(deliveryRepositoryProvider).openBookingTrip(bookingId);
      if (mounted) await context.push('/driver/trips/$tripId/active');
    } catch (e) {
      if (mounted) setState(() => _errorMessage = _parseError(e));
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _confirmReject(DispatchAssignment assignment) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Reject Assignment'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Are you sure you want to reject this assignment?'),
            const SizedBox(height: 16),
            TextField(
              controller: _rejectReasonCtrl,
              decoration: const InputDecoration(
                labelText: 'Reason (optional)',
                border: OutlineInputBorder(),
              ),
              maxLines: 3,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Reject'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() { _isLoading = true; _errorMessage = null; });
    try {
      await ref.read(dispatchRepositoryProvider).rejectAssignment(
        assignment.id,
        rejectionReason: _rejectReasonCtrl.text.trim().isEmpty
            ? null
            : _rejectReasonCtrl.text.trim(),
      );
      ref.invalidate(assignmentDetailProvider(assignment.id));
      ref.invalidate(myAssignmentsProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Assignment rejected.')),
        );
        context.pop();
      }
    } catch (e) {
      setState(() { _errorMessage = _parseError(e); });
    } finally {
      if (mounted) setState(() { _isLoading = false; });
    }
  }

  @override
  Widget build(BuildContext context) {
    final assignmentAsync =
        ref.watch(assignmentDetailProvider(widget.dispatchId));
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Assignment Detail'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () =>
                ref.invalidate(assignmentDetailProvider(widget.dispatchId)),
          ),
        ],
      ),
      body: assignmentAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => _ErrorView(
          message: err.toString(),
          onRetry: () =>
              ref.invalidate(assignmentDetailProvider(widget.dispatchId)),
        ),
        data: (assignment) => SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Status chip
              _StatusChip(status: assignment.status),
              const SizedBox(height: 20),

              // Shipment details card
              _DetailSection(
                title: 'Shipment',
                children: [
                  _DetailRow(label: 'Freight ID', value: '#${assignment.freightId}'),
                  _DetailRow(label: 'Vehicle', value: '#${assignment.vehicleId}'),
                  _DetailRow(
                    label: 'Agreed Price',
                    value: 'ETB ${_formatAmount(assignment.agreedAmount)}',
                    valueStyle: TextStyle(
                      color: Colors.green.shade700,
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                    ),
                  ),
                  if (assignment.notes != null && assignment.notes!.isNotEmpty)
                    _DetailRow(label: 'Notes', value: assignment.notes!),
                ],
              ),
              const SizedBox(height: 16),

              // Timeline card
              _DetailSection(
                title: 'Timeline',
                children: [
                  _DetailRow(
                    label: 'Assigned',
                    value: assignment.assignedAt.toString().substring(0, 16),
                  ),
                  if (assignment.respondedAt != null)
                    _DetailRow(
                      label: 'Responded',
                      value: assignment.respondedAt!.toString().substring(0, 16),
                    ),
                  if (assignment.cancelledAt != null)
                    _DetailRow(
                      label: 'Cancelled',
                      value: assignment.cancelledAt!.toString().substring(0, 16),
                    ),
                ],
              ),

              if (assignment.rejectionReason != null) ...[
                const SizedBox(height: 16),
                _DetailSection(
                  title: 'Rejection',
                  children: [
                    _DetailRow(label: 'Reason', value: assignment.rejectionReason!),
                  ],
                ),
              ],

              if (assignment.bookingId != null) ...[
                const SizedBox(height: 16),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.green.shade50,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.green.shade200),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.check_circle, color: Colors.green),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              'Booking Confirmed',
                              style: theme.textTheme.titleSmall?.copyWith(
                                color: Colors.green.shade800,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            Text(
                              'Booking #${assignment.bookingId}',
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: Colors.green.shade700,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ],

              if (assignment.bookingId != null)
                FilledButton.icon(
                  onPressed: _isLoading ? null : () => _openTrip(assignment.bookingId!),
                  icon: const Icon(Icons.local_shipping),
                  label: const Text('Open Trip'),
                ),
              if (_errorMessage != null) ...[
                const SizedBox(height: 16),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.red.shade50,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.red.shade200),
                  ),
                  child: Text(
                    _errorMessage!,
                    style: TextStyle(color: Colors.red.shade700, fontSize: 14),
                  ),
                ),
              ],

              // Action buttons (only for ASSIGNED)
              if (assignment.isPending) ...[
                const SizedBox(height: 28),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        onPressed: _isLoading
                            ? null
                            : () => _confirmReject(assignment),
                        icon: const Icon(Icons.close, color: Colors.red),
                        label: const Text(
                          'Reject',
                          style: TextStyle(color: Colors.red),
                        ),
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: Colors.red),
                          padding: const EdgeInsets.symmetric(vertical: 14),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      flex: 2,
                      child: ElevatedButton.icon(
                        onPressed: _isLoading ? null : () => _accept(assignment),
                        icon: _isLoading
                            ? const SizedBox(
                                width: 16,
                                height: 16,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: Colors.white,
                                ),
                              )
                            : const Icon(Icons.check),
                        label: Text(_isLoading ? 'Processing…' : 'Accept Assignment'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.green.shade600,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(vertical: 14),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Text(
                  'Accepting confirms your responsibility for this shipment.\n'
                  'The agreed price (ETB ${_formatAmount(assignment.agreedAmount)}) '
                  'is fixed and cannot be changed.',
                  style: theme.textTheme.bodySmall?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                  textAlign: TextAlign.center,
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  String _formatAmount(double amount) {
    return amount.toStringAsFixed(0).replaceAllMapped(
        RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]},');
  }

  String _parseError(Object e) {
    return e.toString().replaceFirst('Exception: ', '');
  }
}

// ── Reusable sub-widgets ───────────────────────────────────────────────────────

class _StatusChip extends StatelessWidget {
  final DispatchStatus status;
  const _StatusChip({required this.status});

  @override
  Widget build(BuildContext context) {
    Color bg, fg;
    IconData icon;
    switch (status) {
      case DispatchStatus.assigned:
        bg = Colors.orange.shade100; fg = Colors.orange.shade800;
        icon = Icons.notifications_active;
        break;
      case DispatchStatus.driverAccepted:
        bg = Colors.green.shade100; fg = Colors.green.shade800;
        icon = Icons.check_circle;
        break;
      case DispatchStatus.driverRejected:
        bg = Colors.red.shade100; fg = Colors.red.shade800;
        icon = Icons.cancel;
        break;
      default:
        bg = Colors.grey.shade100; fg = Colors.grey.shade700;
        icon = Icons.block;
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(24),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 18, color: fg),
          const SizedBox(width: 6),
          Text(
            status.displayName,
            style: TextStyle(color: fg, fontWeight: FontWeight.w600),
          ),
        ],
      ),
    );
  }
}

class _DetailSection extends StatelessWidget {
  final String title;
  final List<Widget> children;
  const _DetailSection({required this.title, required this.children});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title,
            style: Theme.of(context)
                .textTheme
                .labelLarge
                ?.copyWith(color: Theme.of(context).colorScheme.primary)),
        const SizedBox(height: 8),
        Container(
          width: double.infinity,
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            border: Border.all(color: Colors.grey.shade200),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: children,
          ),
        ),
      ],
    );
  }
}

class _DetailRow extends StatelessWidget {
  final String label;
  final String value;
  final TextStyle? valueStyle;
  const _DetailRow({required this.label, required this.value, this.valueStyle});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 110,
            child: Text(
              label,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Colors.grey.shade600,
                  ),
            ),
          ),
          Expanded(
            child: Text(value, style: valueStyle),
          ),
        ],
      ),
    );
  }
}

class _EmptyView extends StatelessWidget {
  const _EmptyView();

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.inbox_outlined, size: 64, color: Colors.grey.shade400),
          const SizedBox(height: 16),
          Text(
            'No assignments yet',
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: Colors.grey.shade600,
                ),
          ),
          const SizedBox(height: 8),
          Text(
            'When a dispatcher assigns you a shipment,\nit will appear here.',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodySmall,
          ),
        ],
      ),
    );
  }
}

class _ErrorView extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;
  const _ErrorView({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 48, color: Colors.red),
            const SizedBox(height: 16),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            ElevatedButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ),
      ),
    );
  }
}
