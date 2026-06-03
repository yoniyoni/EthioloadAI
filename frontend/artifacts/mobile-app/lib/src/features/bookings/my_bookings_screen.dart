import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/api/api_client.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';

/// Full booking lifecycle:
///
///  Shipper books cargo  →  booking: pending
///  Driver accepts       →  booking: accepted
///  Shipper pays         →  booking: confirmed   (payment created)
///  Driver starts trip   →  trip: ongoing
///  Driver completes     →  booking: completed   (trip: completed)
class MyBookingsScreen extends ConsumerWidget {
  const MyBookingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bookingsAsync = ref.watch(bookingListProvider);
    final user = ref.read(authNotifierProvider).user;
    final isDriver = user?.isDriver ?? false;

    return Scaffold(
      appBar: AppBar(
        title: Text(isDriver ? 'Jobs' : 'My Bookings'),
        elevation: 0,
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.invalidate(bookingListProvider),
        child: bookingsAsync.when(
          data: (bookings) {
            if (bookings.isEmpty) {
              return Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.receipt_long,
                        size: 64, color: Colors.grey),
                    const SizedBox(height: 16),
                    Text(
                      isDriver
                          ? 'No jobs assigned yet.'
                          : 'No bookings yet.\nPost cargo and book a vehicle to get started.',
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Colors.grey, fontSize: 15),
                    ),
                  ],
                ),
              );
            }

            return ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: bookings.length,
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (_, i) => _BookingCard(
                booking: bookings[i],
                isDriver: isDriver,
                onRefresh: () => ref.invalidate(bookingListProvider),
              ),
            );
          },
          loading: () =>
              const Center(child: CircularProgressIndicator()),
          error: (e, _) => Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error, size: 48, color: Colors.red),
                const SizedBox(height: 12),
                Text(e.toString(), textAlign: TextAlign.center),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () => ref.invalidate(bookingListProvider),
                  child: const Text('Retry'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ── Booking card ──────────────────────────────────────────────────────────

class _BookingCard extends ConsumerStatefulWidget {
  final Booking booking;
  final bool isDriver;
  final VoidCallback onRefresh;

  const _BookingCard({
    required this.booking,
    required this.isDriver,
    required this.onRefresh,
  });

  @override
  ConsumerState<_BookingCard> createState() => _BookingCardState();
}

class _BookingCardState extends ConsumerState<_BookingCard> {
  bool _busy = false;

  // ── Status helpers ────────────────────────────────────────────────────

  static const _statusColors = {
    'pending': Colors.orange,
    'accepted': Colors.teal,
    'confirmed': Colors.blue,
    'completed': Colors.green,
  };

  static const _statusIcons = {
    'pending': Icons.hourglass_empty,
    'accepted': Icons.thumb_up_outlined,
    'confirmed': Icons.lock_outline,
    'completed': Icons.check_circle_outline,
  };

  static const _statusLabels = {
    'pending': 'Pending driver acceptance',
    'accepted': 'Driver accepted — waiting for payment',
    'confirmed': 'Paid & confirmed — driver can start',
    'completed': 'Completed',
  };

  Color get _color =>
      _statusColors[widget.booking.bookingStatus] ?? Colors.grey;

  // ── Actions ───────────────────────────────────────────────────────────

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
      if (mounted) widget.onRefresh();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(e.toString()),
          backgroundColor: Colors.red,
        ));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Driver accepts the booking
  Future<void> _accept() => _run(() async {
        await ref.read(apiClientProvider).patch<void>(
              '/bookings/${widget.booking.id}',
              data: {'booking_status': 'accepted'},
            );
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('Job accepted! Waiting for shipper payment.'),
            backgroundColor: Colors.teal,
          ));
        }
      });

  /// Shipper pays — booking moves to confirmed
  Future<void> _pay() => _run(() async {
        final payment =
            await ref.read(paymentRepositoryProvider).create(
                  bookingId: widget.booking.id,
                  amount: widget.booking.estimatedPrice,
                  paymentMethod: 'in_app',
                );
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(
              'Payment of ETB ${payment.amount.toStringAsFixed(0)} successful! '
              'Booking confirmed.',
            ),
            backgroundColor: Colors.green,
            duration: const Duration(seconds: 4),
          ));
        }
      });

  /// Driver starts the trip
  Future<void> _startTrip() => _run(() async {
        await ref
            .read(tripRepositoryProvider)
            .start(widget.booking.id);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('Trip started! Update your location as you go.'),
            backgroundColor: Colors.blue,
          ));
        }
      });

  // ── Build ─────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final b = widget.booking;
    final status = b.bookingStatus;

    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey[200]!),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withAlpha(8),
              blurRadius: 4,
              offset: const Offset(0, 2))
        ],
      ),
      child: Column(
        children: [
          // ── Status banner ──────────────────────────────────────────
          Container(
            width: double.infinity,
            padding:
                const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            decoration: BoxDecoration(
              color: _color.withAlpha(20),
              borderRadius:
                  const BorderRadius.vertical(top: Radius.circular(12)),
            ),
            child: Row(children: [
              Icon(_statusIcons[status] ?? Icons.circle,
                  color: _color, size: 18),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  _statusLabels[status] ?? status,
                  style: TextStyle(
                      fontSize: 12,
                      color: _color,
                      fontWeight: FontWeight.w600),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: _color,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  status.toUpperCase(),
                  style: const TextStyle(
                      fontSize: 10,
                      color: Colors.white,
                      fontWeight: FontWeight.bold),
                ),
              ),
            ]),
          ),

          // ── Details ────────────────────────────────────────────────
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Booking #${b.id}',
                    style: const TextStyle(
                        fontSize: 16, fontWeight: FontWeight.bold)),
                const SizedBox(height: 10),
                _InfoRow('Cargo #', '${b.cargoId}'),
                _InfoRow('Vehicle #', '${b.vehicleId}'),
                _InfoRow('Driver #', '${b.driverId}'),
                _InfoRow('Estimated Price',
                    'ETB ${b.estimatedPrice.toStringAsFixed(0)}'),
                if (b.commissionFee != null)
                  _InfoRow('Platform Fee (10%)',
                      'ETB ${b.commissionFee!.toStringAsFixed(0)}'),
                if (b.commissionFee != null)
                  _InfoRow(
                    'Driver Earns',
                    'ETB ${(b.estimatedPrice - b.commissionFee!).toStringAsFixed(0)}',
                  ),

                const SizedBox(height: 16),

                // ── Role-based action buttons ──────────────────────
                if (_busy)
                  const Center(
                      child: Padding(
                    padding: EdgeInsets.all(8),
                    child: CircularProgressIndicator(),
                  ))
                else
                  _buildActions(status),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildActions(String status) {
    final isDriver = widget.isDriver;

    // DRIVER: pending → accept
    if (isDriver && status == 'pending') {
      return _ActionButton(
        label: 'Accept Job',
        icon: Icons.thumb_up,
        color: Colors.teal,
        onTap: _accept,
        subtitle: 'Shipper will be notified to pay',
      );
    }

    // DRIVER: accepted → waiting for payment (no action)
    if (isDriver && status == 'accepted') {
      return _InfoBanner(
        icon: Icons.hourglass_top,
        message: 'Waiting for shipper to complete payment...',
        color: Colors.orange,
      );
    }

    // DRIVER: confirmed → start trip
    if (isDriver && status == 'confirmed') {
      return _ActionButton(
        label: 'Start Trip',
        icon: Icons.play_arrow,
        color: Colors.blue,
        onTap: _startTrip,
        subtitle: 'Cargo is paid — begin delivery',
      );
    }

    // SHIPPER: pending → waiting for driver
    if (!isDriver && status == 'pending') {
      return _InfoBanner(
        icon: Icons.schedule,
        message: 'Waiting for driver to accept this booking...',
        color: Colors.orange,
      );
    }

    // SHIPPER: accepted → pay now
    if (!isDriver && status == 'accepted') {
      return _ActionButton(
        label: 'Pay Now  ·  ETB ${widget.booking.estimatedPrice.toStringAsFixed(0)}',
        icon: Icons.payment,
        color: Colors.green,
        onTap: _pay,
        subtitle: 'In-app payment · secures the booking',
      );
    }

    // SHIPPER: confirmed → trip in progress
    if (!isDriver && status == 'confirmed') {
      return _InfoBanner(
        icon: Icons.local_shipping,
        message: 'Payment received — driver will start the trip.',
        color: Colors.blue,
      );
    }

    // Both: completed
    if (status == 'completed') {
      return _InfoBanner(
        icon: Icons.check_circle,
        message: 'Delivery completed successfully.',
        color: Colors.green,
      );
    }

    return const SizedBox.shrink();
  }
}

// ── Helper widgets ────────────────────────────────────────────────────────

class _InfoRow extends StatelessWidget {
  final String label;
  final String value;
  const _InfoRow(this.label, this.value);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label,
              style: TextStyle(fontSize: 13, color: Colors.grey[600])),
          Text(value,
              style: const TextStyle(
                  fontSize: 13, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  final String label;
  final IconData icon;
  final Color color;
  final VoidCallback onTap;
  final String subtitle;

  const _ActionButton({
    required this.label,
    required this.icon,
    required this.color,
    required this.onTap,
    required this.subtitle,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ElevatedButton.icon(
          icon: Icon(icon, size: 18),
          label: Text(label,
              style: const TextStyle(
                  fontSize: 14, fontWeight: FontWeight.bold)),
          style: ElevatedButton.styleFrom(
            backgroundColor: color,
            padding: const EdgeInsets.symmetric(vertical: 14),
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10)),
          ),
          onPressed: onTap,
        ),
        const SizedBox(height: 4),
        Text(subtitle,
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 11, color: Colors.grey[500])),
      ],
    );
  }
}

class _InfoBanner extends StatelessWidget {
  final IconData icon;
  final String message;
  final Color color;

  const _InfoBanner({
    required this.icon,
    required this.message,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withAlpha(20),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withAlpha(60)),
      ),
      child: Row(children: [
        Icon(icon, color: color, size: 18),
        const SizedBox(width: 10),
        Expanded(
          child: Text(message,
              style: TextStyle(fontSize: 13, color: color)),
        ),
      ]),
    );
  }
}
