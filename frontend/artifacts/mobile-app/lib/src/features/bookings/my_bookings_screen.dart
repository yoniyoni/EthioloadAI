import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../data/api/api_client.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';
import '../shared/widgets/shared_widgets.dart';

/// Full booking lifecycle:
///
///  Shipper books cargo  →  booking: pending
///  Driver accepts       →  booking: accepted   (price summary shown; no payment yet)
///  Driver starts trip   →  trip: ongoing
///  Driver delivers      →  booking: delivered  (payment unlocked)
///  Shipper pays         →  booking: confirmed  (payment created)
class MyBookingsScreen extends ConsumerStatefulWidget {
  const MyBookingsScreen({super.key});

  @override
  ConsumerState<MyBookingsScreen> createState() => _MyBookingsScreenState();
}

class _MyBookingsScreenState extends ConsumerState<MyBookingsScreen> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    // Poll every 10 seconds so status changes from the other party are reflected
    _timer = Timer.periodic(const Duration(seconds: 10), (_) {
      if (mounted) ref.invalidate(bookingListProvider);
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final bookingsAsync = ref.watch(bookingListProvider);
    final user = ref.read(authNotifierProvider).user;
    final isDriver = user?.isDriver ?? false;

    return Scaffold(
      backgroundColor: kBackground,
      appBar: EthioAppBar(
        title: isDriver ? 'My Jobs' : 'My Bookings',
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded, color: Colors.white),
            tooltip: 'Refresh',
            onPressed: () => ref.invalidate(bookingListProvider),
          ),
        ],
      ),
      body: RefreshIndicator(
        color: kAmber,
        onRefresh: () async => ref.invalidate(bookingListProvider),
        child: bookingsAsync.when(
          data: (bookings) {
            if (bookings.isEmpty) {
              return CustomScrollView(
                slivers: [
                  SliverFillRemaining(
                    child: Center(
                      child: EmptyState(
                        icon: Icons.receipt_long_outlined,
                        title: isDriver ? 'No Jobs Yet' : 'No Bookings Yet',
                        subtitle: isDriver
                            ? 'Accept cargo requests to get started'
                            : 'Post cargo and book a vehicle to get started',
                      ),
                    ),
                  ),
                ],
              );
            }
            int sortPriority(Booking b) {
              if (b.isTripOngoing) return 0;
              if (b.bookingStatus == 'pending' ||
                  b.bookingStatus == 'accepted' ||
                  b.bookingStatus == 'delivered') return 1;
              return 2;
            }

            final sorted = [...bookings]
              ..sort((x, y) => sortPriority(x).compareTo(sortPriority(y)));

            final items = <Object>[];
            bool addedActiveHeader = false;
            bool addedPastHeader = false;
            for (final b in sorted) {
              final p = sortPriority(b);
              if (p <= 1 && !addedActiveHeader) {
                if (isDriver) items.add('Active Trip / የአሁን ጉዞ');
                addedActiveHeader = true;
              } else if (p == 2 && !addedPastHeader) {
                if (isDriver) items.add('Past Bookings / ያለፉ ጉዞዎች');
                addedPastHeader = true;
              }
              items.add(b);
            }

            return ListView.builder(
              padding: const EdgeInsets.all(16),
              itemCount: items.length,
              itemBuilder: (_, i) {
                final item = items[i];
                if (item is String) {
                  return Padding(
                    padding: const EdgeInsets.only(top: 4, bottom: 8),
                    child: Text(
                      item,
                      style: GoogleFonts.inter(
                        fontSize: 11,
                        fontWeight: FontWeight.w700,
                        color: kTextMuted,
                        letterSpacing: 0.5,
                      ),
                    ),
                  );
                }
                return Padding(
                  padding: const EdgeInsets.only(bottom: 12),
                  child: _BookingCard(
                    booking: item as Booking,
                    isDriver: isDriver,
                    onRefresh: () => ref.invalidate(bookingListProvider),
                  ),
                );
              },
            );
          },
          loading: () => ListView(
            padding: const EdgeInsets.all(16),
            children: List.generate(
              4,
              (_) => const Padding(
                padding: EdgeInsets.only(bottom: 12),
                child: ShimmerBox(height: 160, radius: 12),
              ),
            ),
          ),
          error: (e, _) => Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    width: 56,
                    height: 56,
                    decoration: BoxDecoration(
                      color: kDanger.withValues(alpha: 0.1),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.error_outline_rounded,
                        size: 28, color: kDanger),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'Something went wrong',
                    style: GoogleFonts.inter(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: kTextPrimary,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    e.toString(),
                    textAlign: TextAlign.center,
                    style: GoogleFonts.inter(fontSize: 13, color: kTextMuted),
                  ),
                  const SizedBox(height: 20),
                  ElevatedButton.icon(
                    icon: const Icon(Icons.refresh_rounded, size: 18),
                    label: Text('Retry',
                        style: GoogleFonts.inter(
                            fontSize: 14, fontWeight: FontWeight.w600)),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: kGreen,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(
                          horizontal: 24, vertical: 12),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10)),
                      elevation: 0,
                    ),
                    onPressed: () => ref.invalidate(bookingListProvider),
                  ),
                ],
              ),
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
  bool _tripStarted = false;
  int? _tripId;
  String? _prevBookingStatus;

  @override
  void initState() {
    super.initState();
    // Seed from booking data so trips started in previous sessions are recognized
    _tripStarted = widget.booking.hasTripStarted;
    _tripId = widget.booking.tripId;
    _prevBookingStatus = widget.booking.bookingStatus;
  }

  @override
  void didUpdateWidget(_BookingCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    // Re-sync when the booking is refreshed by the 10s poll
    if (oldWidget.booking.tripId != widget.booking.tripId ||
        oldWidget.booking.tripStatus != widget.booking.tripStatus) {
      _tripStarted = widget.booking.hasTripStarted;
      _tripId = widget.booking.tripId;
    }
    // Task 2: notify driver when their bid transitions pending → accepted
    final newStatus = widget.booking.bookingStatus;
    if (widget.isDriver && _prevBookingStatus == 'pending' && newStatus == 'accepted') {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
            'ጨረታዎ ተቀባይነት አግኝቷል! Your bid was accepted — prepare for pickup.',
            style: GoogleFonts.inter(),
          ),
          backgroundColor: kGreen,
          duration: const Duration(seconds: 5),
        ));
      });
    }
    if (_prevBookingStatus != newStatus) _prevBookingStatus = newStatus;
  }

  // ── Status helpers ────────────────────────────────────────────────────

  static const _statusColors = {
    'pending':   kAmber,
    'accepted':  kGreen,
    'delivered': kAmber,
    'confirmed': kSuccess,
    'completed': kSuccess,
  };

  static const _statusIcons = {
    'pending':   Icons.hourglass_empty_rounded,
    'accepted':  Icons.thumb_up_rounded,
    'delivered': Icons.local_shipping_rounded,
    'confirmed': Icons.check_circle_rounded,
    'completed': Icons.check_circle_rounded,
  };

  static const _statusLabels = {
    'pending':   'Pending driver acceptance',
    'accepted':  'Driver accepted — ready to start',
    'delivered': 'Cargo delivered — payment required',
    'confirmed': 'Payment received — booking complete',
    'completed': 'Completed',
  };

  Color get _color =>
      _statusColors[widget.booking.bookingStatus] ?? kTextMuted;

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
          backgroundColor: kDanger,
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
            backgroundColor: kGreen,
          ));
        }
      });

  /// Shipper pays — shows payment method picker then confirms booking
  Future<void> _pay() async {
    final method = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => const _PaymentMethodSheet(),
    );
    if (method == null || !mounted) return;

    await _run(() async {
      final payment = await ref.read(paymentRepositoryProvider).create(
            bookingId: widget.booking.id,
            amount: widget.booking.estimatedPrice,
            paymentMethod: method,
          );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
            'Payment of ETB ${payment.amount.toStringAsFixed(0)} confirmed via '
            '${_methodLabel(method)}.',
          ),
          backgroundColor: kSuccess,
          duration: const Duration(seconds: 4),
        ));
      }
    });
  }

  /// Driver starts the trip
  Future<void> _startTrip() async {
    setState(() => _busy = true);
    try {
      final trip = await ref.read(tripRepositoryProvider).start(widget.booking.id);
      if (mounted) {
        setState(() {
          _tripStarted = true;
          _tripId = trip.id;
        });
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Trip started! You are now on the way.'),
          backgroundColor: kGreen,
        ));
        widget.onRefresh();
      }
    } catch (e) {
      // 400 means trip already exists — treat as success
      if (e.toString().contains('400') ||
          e.toString().toLowerCase().contains('already exists')) {
        if (mounted) {
          setState(() => _tripStarted = true);
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('Trip is already in progress.'),
            backgroundColor: kGreen,
          ));
          widget.onRefresh();
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(e.toString()),
            backgroundColor: kDanger,
          ));
        }
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Driver completes the trip — then immediately asks for payment method
  Future<void> _completeTrip() async {
    if (_tripId == null) return;
    setState(() => _busy = true);
    try {
      await ref.read(tripRepositoryProvider).complete(_tripId!);
      if (!mounted) return;
      setState(() => _tripStarted = false);

      // Task 4: immediately prompt driver to record payment method
      final method = await showModalBottomSheet<String>(
        context: context,
        backgroundColor: Colors.transparent,
        isScrollControlled: true,
        builder: (_) => const _DriverPaymentSheet(),
      );

      if (!mounted) return;

      if (method != null) {
        try {
          final payment = await ref.read(paymentRepositoryProvider).create(
            bookingId: widget.booking.id,
            amount: widget.booking.estimatedPrice,
            paymentMethod: method,
          );
          final net = widget.booking.estimatedPrice -
              (widget.booking.commissionFee ??
                  widget.booking.estimatedPrice * 0.10);
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(SnackBar(
              content: Text(
                'ጉዞ ተጠናቋል! Delivery confirmed. You will receive ETB '
                '${net.toStringAsFixed(0)} via ${_methodLabel(payment.paymentMethod)}.',
                style: GoogleFonts.inter(),
              ),
              backgroundColor: kSuccess,
              duration: const Duration(seconds: 5),
            ));
          }
        } catch (e) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(SnackBar(
              content: Text(
                'Cargo delivered! Payment record failed: $e',
                style: GoogleFonts.inter(),
              ),
              backgroundColor: kAmber,
              duration: const Duration(seconds: 4),
            ));
          }
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('Cargo delivered! Payment will be confirmed by the shipper.'),
            backgroundColor: kSuccess,
          ));
        }
      }

      widget.onRefresh();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Error: $e'),
          backgroundColor: kDanger,
        ));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Driver records payment method from the 'delivered' state (if skipped earlier)
  Future<void> _recordPayment() async {
    final method = await showModalBottomSheet<String>(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (_) => const _DriverPaymentSheet(),
    );
    if (method == null || !mounted) return;
    await _run(() async {
      await ref.read(paymentRepositoryProvider).create(
        bookingId: widget.booking.id,
        amount: widget.booking.estimatedPrice,
        paymentMethod: method,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
            'Payment recorded via ${_methodLabel(method)}!',
            style: GoogleFonts.inter(),
          ),
          backgroundColor: kSuccess,
        ));
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final b = widget.booking;
    final status = b.bookingStatus;

    return Container(
      decoration: BoxDecoration(
        color: kSurface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: kBorder),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withAlpha(6),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // ── Status banner ──────────────────────────────────────────
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            decoration: BoxDecoration(
              color: _color.withValues(alpha: 0.08),
              borderRadius:
                  const BorderRadius.vertical(top: Radius.circular(12)),
            ),
            child: Row(children: [
              Icon(_statusIcons[status] ?? Icons.circle_outlined,
                  color: _color, size: 18),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  _statusLabels[status] ?? status,
                  style: GoogleFonts.inter(
                      fontSize: 12,
                      color: _color,
                      fontWeight: FontWeight.w600),
                ),
              ),
              if (b.isMultiStop) ...[
                Container(
                  margin: const EdgeInsets.only(right: 6),
                  padding:
                      const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
                  decoration: BoxDecoration(
                    color: kGreen,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text('Multi-Stop',
                      style: GoogleFonts.inter(
                          fontSize: 9,
                          color: Colors.white,
                          fontWeight: FontWeight.bold)),
                ),
              ],
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: _color,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  status.toUpperCase(),
                  style: GoogleFonts.inter(
                      fontSize: 10,
                      color: Colors.white,
                      fontWeight: FontWeight.bold),
                ),
              ),
            ]),
          ),

          // ── Details ────────────────────────────────────────────────
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Route — most important info for the driver
                if (b.pickupLocation != null && b.destination != null) ...[
                  Row(
                    children: [
                      const Icon(Icons.trip_origin_rounded,
                          color: kAmber, size: 14),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          b.pickupLocation!,
                          style: GoogleFonts.inter(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: kTextPrimary),
                        ),
                      ),
                    ],
                  ),
                  Padding(
                    padding: const EdgeInsets.only(left: 6),
                    child: Column(
                      children: List.generate(
                          3,
                          (_) => Text('│',
                              style: GoogleFonts.inter(
                                  color: kBorder, height: 0.8))),
                    ),
                  ),
                  Row(
                    children: [
                      const Icon(Icons.location_on_rounded,
                          color: kGreen, size: 14),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          b.destination!,
                          style: GoogleFonts.inter(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: kTextPrimary),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  // Cargo summary row
                  Wrap(
                    spacing: 8,
                    runSpacing: 4,
                    children: [
                      if (b.materialType != null)
                        _Chip(
                            icon: Icons.inventory_2_rounded,
                            label: b.materialType!,
                            color: kGreen),
                      if (b.weight != null)
                        _Chip(
                            icon: Icons.scale_rounded,
                            label: '${b.weight!.toStringAsFixed(0)} t',
                            color: kAmber),
                      if (b.urgencyLevel != null)
                        _Chip(
                            icon: Icons.speed_rounded,
                            label: b.urgencyLevel!,
                            color: b.urgencyLevel == 'high' ||
                                    b.urgencyLevel == 'express'
                                ? kDanger
                                : kTextMuted),
                    ],
                  ),
                  const Divider(height: 20, color: kBorder),
                ] else ...[
                  Text('Booking #${b.id}',
                      style: GoogleFonts.inter(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          color: kTextPrimary)),
                  const SizedBox(height: 10),
                ],
                _InfoRow('Booking #', '${b.id}'),
                _InfoRow('Price',
                    'ETB ${b.estimatedPrice.toStringAsFixed(0)}'),
                if (b.commissionFee != null) ...[
                  _InfoRow('Platform Fee (10%)',
                      'ETB ${b.commissionFee!.toStringAsFixed(0)}'),
                  _InfoRow(
                    'You Earn',
                    'ETB ${(b.estimatedPrice - b.commissionFee!).toStringAsFixed(0)}',
                  ),
                ],
                // Multi-stop: show shipper's stop info
                if (b.isMultiStop && b.myStopOrder != null) ...[
                  const SizedBox(height: 8),
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: kGreenTint,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(color: kBorder),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.place_rounded,
                            color: kGreen, size: 16),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Your stop: #${b.myStopOrder}'
                                '${b.myStopLocation != null ? ' · ${b.myStopLocation}' : ''}',
                                style: GoogleFonts.inter(
                                    fontSize: 12,
                                    fontWeight: FontWeight.w600,
                                    color: kGreen),
                              ),
                              if (b.myStopStatus != null)
                                Text(
                                  'Status: ${b.myStopStatus}',
                                  style: GoogleFonts.inter(
                                      fontSize: 11, color: kTextMuted),
                                ),
                            ],
                          ),
                        ),
                        Text(
                          '${b.tripCompletedStops}/${b.tripTotalStops} done',
                          style: GoogleFonts.inter(
                              fontSize: 11, color: kTextMuted),
                        ),
                      ],
                    ),
                  ),
                ],

                const SizedBox(height: 16),

                // ── Role-based action buttons ──────────────────────
                if (_busy)
                  Center(
                    child: Padding(
                      padding: const EdgeInsets.all(8),
                      child: CircularProgressIndicator(
                        color: kAmber,
                        strokeWidth: 2.5,
                      ),
                    ),
                  )
                else
                  _buildActions(status),
              ],
            ),
          ),
          // ── Backhaul recommendations (only on the currently ongoing trip) ──
          if (widget.isDriver &&
              widget.booking.isTripOngoing &&
              (_tripId ?? widget.booking.tripId) != null)
            _BackhaulSection(
              tripId: _tripId ?? widget.booking.tripId!,
              destination: widget.booking.destination ?? '',
            ),
        ],
      ),
    );
  }

  Widget _buildActions(String status) {
    final isDriver = widget.isDriver;
    final b = widget.booking;

    // ── DRIVER: pending → accept ─────────────────────────────────────────
    if (isDriver && status == 'pending') {
      return _ActionButton(
        label: 'Accept Job',
        icon: Icons.thumb_up_rounded,
        color: kGreen,
        onTap: _accept,
        subtitle: 'Payment is collected after delivery',
      );
    }

    // ── DRIVER: accepted → price breakdown + start/in-progress trip ─────
    if (isDriver && status == 'accepted') {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (b.shipperName != null || b.shipperPhone != null) ...[
            _ContactCard(
              icon: Icons.person_outline_rounded,
              name: b.shipperName ?? 'Shipper',
              phone: b.shipperPhone ?? '—',
              label: 'Shipper Contact',
              color: kGreen,
            ),
            const SizedBox(height: 10),
          ],
          _PriceSummaryCard(booking: b, forDriver: true),
          const SizedBox(height: 10),
          if (_tripStarted) ...[
            _InfoBanner(
              icon: Icons.local_shipping_rounded,
              message: 'Trip is in progress — deliver the cargo.',
              color: kGreen,
            ),
            const SizedBox(height: 10),
            if (b.isMultiStop && _tripId != null) ...[
              ElevatedButton.icon(
                icon: const Icon(Icons.map_outlined, size: 18),
                label: Text('View Active Trip',
                    style: GoogleFonts.inter(
                        fontSize: 14, fontWeight: FontWeight.bold)),
                style: ElevatedButton.styleFrom(
                  backgroundColor: kGreen,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10)),
                  elevation: 0,
                ),
                onPressed: () => context.push('/driver/active-trip/$_tripId'),
              ),
              const SizedBox(height: 8),
            ],
            ElevatedButton.icon(
              icon: const Icon(Icons.check_circle_rounded, size: 18),
              label: Text('Mark as Delivered',
                  style: GoogleFonts.inter(
                      fontSize: 14, fontWeight: FontWeight.bold)),
              style: ElevatedButton.styleFrom(
                backgroundColor: kSuccess,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10)),
                elevation: 0,
              ),
              onPressed: _tripId != null ? _completeTrip : null,
            ),
            const SizedBox(height: 4),
            Text(
              _tripId == null
                  ? 'Trip ID not yet available — pull to refresh'
                  : 'Confirm cargo has been delivered to the shipper',
              textAlign: TextAlign.center,
              style: GoogleFonts.inter(fontSize: 11, color: kTextMuted),
            ),
          ] else
            _ActionButton(
              label: 'Start Trip',
              icon: Icons.play_circle_rounded,
              color: kGreen,
              onTap: _startTrip,
              subtitle: 'Begin cargo delivery to the shipper',
            ),
        ],
      );
    }

    // ── DRIVER: delivered → record payment if skipped at delivery ────────
    if (isDriver && status == 'delivered') {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _InfoBanner(
            icon: Icons.local_shipping_rounded,
            message: 'Cargo delivered! Record how payment was made.',
            color: kAmber,
          ),
          const SizedBox(height: 8),
          ElevatedButton.icon(
            icon: const Icon(Icons.payment_rounded, size: 18),
            label: Text('Record Payment Method',
                style: GoogleFonts.inter(
                    fontSize: 14, fontWeight: FontWeight.w600)),
            style: ElevatedButton.styleFrom(
              backgroundColor: kAmber,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
              elevation: 0,
            ),
            onPressed: _recordPayment,
          ),
        ],
      );
    }

    // ── DRIVER: confirmed → booking complete ─────────────────────────────
    if (isDriver && (status == 'confirmed' || status == 'completed')) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (b.paymentMethod != null) ...[
            _PaymentMethodBadge(method: b.paymentMethod!),
            const SizedBox(height: 8),
          ],
          _InfoBanner(
            icon: Icons.celebration_rounded,
            message: 'Payment received — booking complete!',
            color: kSuccess,
          ),
        ],
      );
    }

    // ── SHIPPER: pending → waiting for driver ────────────────────────────
    if (!isDriver && status == 'pending') {
      return _InfoBanner(
        icon: Icons.schedule_rounded,
        message: 'Waiting for driver to accept this booking...',
        color: kAmber,
      );
    }

    // ── SHIPPER: accepted → price info; payment after delivery ───────────
    if (!isDriver && status == 'accepted') {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (b.driverName != null || b.driverPhone != null) ...[
            _ContactCard(
              icon: Icons.local_shipping_outlined,
              name: b.driverName ?? 'Driver',
              phone: b.driverPhone ?? '—',
              label: 'Your Driver',
              color: kGreen,
            ),
            const SizedBox(height: 10),
          ],
          _PriceSummaryCard(booking: b, forDriver: false),
          const SizedBox(height: 8),
          _InfoBanner(
            icon: Icons.local_shipping_outlined,
            message: 'Driver is on the way — payment is collected after delivery.',
            color: kAmber,
          ),
          if (b.isTripOngoing) ...[
            const SizedBox(height: 10),
            ElevatedButton.icon(
              icon: const Icon(Icons.gps_fixed_rounded, size: 18),
              label: Text(
                'Track Cargo / ጭነቱን ክትተል',
                style: GoogleFonts.inter(
                    fontSize: 14, fontWeight: FontWeight.w600),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: kGreen,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10)),
                elevation: 0,
              ),
              onPressed: () => context.push('/tracking/${b.cargoId}'),
            ),
          ],
        ],
      );
    }

    // ── SHIPPER: delivered → pay now ─────────────────────────────────────
    if (!isDriver && status == 'delivered') {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (b.driverName != null || b.driverPhone != null) ...[
            _ContactCard(
              icon: Icons.local_shipping_outlined,
              name: b.driverName ?? 'Driver',
              phone: b.driverPhone ?? '—',
              label: 'Your Driver',
              color: kGreen,
            ),
            const SizedBox(height: 10),
          ],
          _PriceSummaryCard(booking: b, forDriver: false),
          const SizedBox(height: 10),
          _ActionButton(
            label: 'Pay Now  ·  ETB ${b.estimatedPrice.toStringAsFixed(0)}',
            icon: Icons.payment_rounded,
            color: kSuccess,
            onTap: _pay,
            subtitle: 'Cargo has been delivered — choose payment method',
          ),
        ],
      );
    }

    // ── SHIPPER: confirmed → complete + rate driver ───────────────────────
    if (!isDriver && (status == 'confirmed' || status == 'completed')) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (b.driverName != null || b.driverPhone != null) ...[
            _ContactCard(
              icon: Icons.local_shipping_outlined,
              name: b.driverName ?? 'Driver',
              phone: b.driverPhone ?? '—',
              label: 'Your Driver',
              color: kGreen,
            ),
            const SizedBox(height: 8),
          ],
          if (b.paymentMethod != null) ...[
            _PaymentMethodBadge(method: b.paymentMethod!),
            const SizedBox(height: 8),
          ],
          _InfoBanner(
            icon: Icons.check_circle_rounded,
            message: 'Payment confirmed — thank you!',
            color: kSuccess,
          ),
          if (!widget.booking.hasRating) ...[
            const SizedBox(height: 10),
            ElevatedButton.icon(
              icon: const Icon(Icons.star_rounded, size: 18),
              label: Text('Rate the Driver',
                  style: GoogleFonts.inter(
                      fontSize: 14, fontWeight: FontWeight.w600)),
              style: ElevatedButton.styleFrom(
                backgroundColor: kAmber,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10)),
                elevation: 0,
              ),
              onPressed: () => _showRatingDialog(context),
            ),
          ] else
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(
                '✓ You have already rated this booking',
                textAlign: TextAlign.center,
                style: GoogleFonts.inter(fontSize: 12, color: kTextMuted),
              ),
            ),
        ],
      );
    }

    return const SizedBox.shrink();
  }

  // ── Rating dialog ──────────────────────────────────────────────────────

  void _showRatingDialog(BuildContext context) {
    int selectedStars = 5;
    final feedbackCtrl = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          shape:
              RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          title: Text(
            'Rate the Driver',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.bold, color: kTextPrimary),
          ),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'How was ${widget.booking.driverName ?? "the driver"}\'s service?',
                style: GoogleFonts.inter(fontSize: 13, color: kTextSecond),
              ),
              const SizedBox(height: 16),
              // Star selector
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(5, (i) {
                  final star = i + 1;
                  return GestureDetector(
                    onTap: () =>
                        setDialogState(() => selectedStars = star),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: Icon(
                        star <= selectedStars
                            ? Icons.star_rounded
                            : Icons.star_border_rounded,
                        color: kAmber,
                        size: 36,
                      ),
                    ),
                  );
                }),
              ),
              const SizedBox(height: 6),
              Text('$selectedStars / 5',
                  style: GoogleFonts.inter(
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                      color: kTextPrimary)),
              const SizedBox(height: 16),
              TextField(
                controller: feedbackCtrl,
                maxLines: 3,
                decoration: InputDecoration(
                  hintText: 'Optional feedback...',
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                    borderSide: const BorderSide(color: kBorder),
                  ),
                  focusedBorder: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(8),
                    borderSide:
                        const BorderSide(color: kGreen, width: 1.5),
                  ),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              style: TextButton.styleFrom(foregroundColor: kTextMuted),
              child: const Text('Cancel'),
            ),
            ElevatedButton.icon(
              icon: const Icon(Icons.send_rounded, size: 16),
              label: const Text('Submit'),
              style: ElevatedButton.styleFrom(
                backgroundColor: kAmber,
                foregroundColor: Colors.white,
                elevation: 0,
              ),
              onPressed: () async {
                Navigator.pop(ctx);
                final messenger = ScaffoldMessenger.of(context);
                setState(() => _busy = true);
                try {
                  await ref.read(ratingRepositoryProvider).submitRating(
                        bookingId: widget.booking.id,
                        rating: selectedStars,
                        feedback: feedbackCtrl.text,
                      );
                  if (mounted) {
                    messenger.showSnackBar(const SnackBar(
                      content: Text('Rating submitted! Thank you.'),
                      backgroundColor: kSuccess,
                    ));
                    widget.onRefresh();
                  }
                } catch (e) {
                  if (mounted) {
                    messenger.showSnackBar(SnackBar(
                      content: Text('Error: $e'),
                      backgroundColor: kDanger,
                    ));
                  }
                } finally {
                  if (mounted) setState(() => _busy = false);
                }
              },
            ),
          ],
        ),
      ),
    );
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
              style: GoogleFonts.inter(fontSize: 13, color: kTextMuted)),
          Text(value,
              style: GoogleFonts.inter(
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  color: kTextPrimary)),
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
              style: GoogleFonts.inter(
                  fontSize: 14, fontWeight: FontWeight.bold)),
          style: ElevatedButton.styleFrom(
            backgroundColor: color,
            foregroundColor: Colors.white,
            padding: const EdgeInsets.symmetric(vertical: 14),
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(10)),
            elevation: 0,
          ),
          onPressed: onTap,
        ),
        const SizedBox(height: 4),
        Text(subtitle,
            textAlign: TextAlign.center,
            style: GoogleFonts.inter(fontSize: 11, color: kTextMuted)),
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
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: color.withValues(alpha: 0.25)),
      ),
      child: Row(children: [
        Icon(icon, color: color, size: 18),
        const SizedBox(width: 10),
        Expanded(
          child: Text(message,
              style: GoogleFonts.inter(fontSize: 13, color: color)),
        ),
      ]),
    );
  }
}

class _Chip extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  const _Chip(
      {required this.icon, required this.label, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.25)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 12, color: color),
          const SizedBox(width: 4),
          Text(label,
              style: GoogleFonts.inter(
                  fontSize: 11,
                  color: color,
                  fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

// ── Contact card ──────────────────────────────────────────────────────────

class _ContactCard extends StatelessWidget {
  final IconData icon;
  final String name;
  final String phone;
  final String label;
  final Color color;

  const _ContactCard({
    required this.icon,
    required this.name,
    required this.phone,
    required this.label,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: color.withValues(alpha: 0.2)),
      ),
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: color, size: 20),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label,
                    style: GoogleFonts.inter(
                        fontSize: 10,
                        color: kTextMuted,
                        fontWeight: FontWeight.w500)),
                const SizedBox(height: 2),
                Text(name,
                    style: GoogleFonts.inter(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: kTextPrimary)),
                const SizedBox(height: 2),
                GestureDetector(
                  onTap: () {
                    Clipboard.setData(ClipboardData(text: phone));
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                      content: Text('$phone copied to clipboard'),
                      duration: const Duration(seconds: 2),
                    ));
                  },
                  child: Row(
                    children: [
                      Icon(Icons.phone_rounded, size: 13, color: color),
                      const SizedBox(width: 4),
                      Text(phone,
                          style: GoogleFonts.inter(
                              fontSize: 13,
                              color: color,
                              fontWeight: FontWeight.w600)),
                      const SizedBox(width: 6),
                      Text('(tap to copy)',
                          style: GoogleFonts.inter(
                              fontSize: 10, color: kTextMuted)),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

// ── Backhaul recommendations section ──────────────────────────────────────
//
// Shows after a driver starts a trip. Polls GET /trips/{id}/backhaul-recommendations
// every 30 seconds and surfaces cargo available near the destination city.

class _BackhaulSection extends ConsumerStatefulWidget {
  final int tripId;
  final String destination;

  const _BackhaulSection({required this.tripId, required this.destination});

  @override
  ConsumerState<_BackhaulSection> createState() => _BackhaulSectionState();
}

class _BackhaulSectionState extends ConsumerState<_BackhaulSection> {
  Timer? _timer;
  // Locally dismissed IDs so cards disappear immediately without re-fetch
  final Set<int> _dismissed = {};

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 30), (_) {
      if (mounted) ref.invalidate(backhaulRecommendationsProvider(widget.tripId));
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _dismiss(int recId) async {
    setState(() => _dismissed.add(recId));
    try {
      await ref.read(tripRepositoryProvider).dismissRecommendation(recId);
    } catch (_) {
      // If the API call fails the card stays dismissed locally — no UX disruption
    }
  }

  static const _green = Color(0xFF0F3D1A);

  // English → Amharic display names
  static const Map<String, String> _amharic = {
    'Addis Ababa':  'አዲስ አበባ',
    'Addis Abeba':  'አዲስ አበባ',
    'Mekele':       'መቀሌ',
    'Gondar':       'ጎንደር',
    'Bahir Dar':    'ባሕር ዳር',
    'Dire Dawa':    'ድሬዳዋ',
    'Hawassa':      'ሐዋሳ',
    'Jimma':        'ጅማ',
    'Metema':       'መተማ',
    'Humera':       'ሑመራ',
    'Shire':        'ሽሬ',
    'Addis Zemen':  'አዲስ ዘመን',
    'Debre Tabor':  'ደብረ ታቦር',
    'Debre Markos': 'ደብረ ማርቆስ',
  };

  String _amharicName(String city) {
    for (final entry in _amharic.entries) {
      if (city.toLowerCase().contains(entry.key.toLowerCase())) {
        return entry.value;
      }
    }
    return city;
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(backhaulRecommendationsProvider(widget.tripId));
    final amharic = _amharicName(widget.destination);

    return async.when(
      loading: () => _skeleton(),
      error: (_, __) => const SizedBox.shrink(),
      data: (recs) {
        final visible = recs.where((r) => !_dismissed.contains(r.id)).toList();
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header
            Container(
              margin: const EdgeInsets.fromLTRB(0, 12, 0, 0),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              decoration: const BoxDecoration(
                color: _green,
                borderRadius: BorderRadius.only(
                  bottomLeft: Radius.circular(0),
                  bottomRight: Radius.circular(0),
                ),
              ),
              child: Row(children: [
                const Icon(Icons.sync_alt_rounded, color: Colors.white, size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Return loads near ${widget.destination}',
                        style: GoogleFonts.inter(
                            color: Colors.white,
                            fontSize: 13,
                            fontWeight: FontWeight.w700),
                      ),
                      Text(
                        'ወደ $amharic የሚሄዱ ጭነቶች',
                        style: GoogleFonts.inter(
                            color: Colors.white.withAlpha(180),
                            fontSize: 11),
                      ),
                    ],
                  ),
                ),
                Text(
                  '${visible.length} found',
                  style: GoogleFonts.inter(
                      color: Colors.white.withAlpha(200), fontSize: 11),
                ),
              ]),
            ),

            if (visible.isEmpty)
              Container(
                padding: const EdgeInsets.all(16),
                color: kGreenTint,
                child: Column(
                  children: [
                    Text(
                      'No return loads found near ${widget.destination} yet. We\'ll keep checking.',
                      textAlign: TextAlign.center,
                      style: GoogleFonts.inter(
                          fontSize: 12, color: kTextSecond),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'አሁን ወደ ${widget.destination} የሚሄዱ ጭነቶች አልተገኙም።',
                      textAlign: TextAlign.center,
                      style: GoogleFonts.inter(
                          fontSize: 11, color: kTextMuted),
                    ),
                  ],
                ),
              )
            else
              ...visible.map((rec) => _BackhaulCard(
                    rec: rec,
                    onDismiss: () => _dismiss(rec.id),
                    onViewBid: () =>
                        context.push('/freight/${rec.cargo.id}'),
                  )),

            const SizedBox(height: 8),
          ],
        );
      },
    );
  }

  Widget _skeleton() => Padding(
        padding: const EdgeInsets.fromLTRB(0, 12, 0, 8),
        child: Column(
          children: List.generate(
            2,
            (_) => const Padding(
              padding: EdgeInsets.only(bottom: 8),
              child: ShimmerBox(height: 80, radius: 8),
            ),
          ),
        ),
      );
}

class _BackhaulCard extends StatelessWidget {
  final BackhaulRecommendation rec;
  final VoidCallback onDismiss;
  final VoidCallback onViewBid;

  const _BackhaulCard({
    required this.rec,
    required this.onDismiss,
    required this.onViewBid,
  });

  static const _amber = Color(0xFFF59E0B);

  Color _urgencyColor(String level) {
    switch (level.toLowerCase()) {
      case 'urgent':
      case 'express':
        return _amber;
      default:
        return kTextMuted;
    }
  }

  @override
  Widget build(BuildContext context) {
    final cargo = rec.cargo;
    final score = (rec.score * 100).round();

    return Container(
      decoration: const BoxDecoration(
        color: kSurface,
        border: Border(
          left: BorderSide(color: _amber, width: 3),
          top: BorderSide(color: kBorder, width: 0.5),
          right: BorderSide(color: kBorder, width: 0.5),
          bottom: BorderSide(color: kBorder, width: 0.5),
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 10),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '${cargo.pickupLocation} → ${cargo.destination}',
                      style: GoogleFonts.inter(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: kTextPrimary),
                    ),
                    const SizedBox(height: 3),
                    Row(children: [
                      Text(
                        '${cargo.materialType}  ·  ${cargo.weight.toStringAsFixed(0)} t',
                        style: GoogleFonts.inter(
                            fontSize: 11, color: kTextMuted),
                      ),
                      if (rec.distanceKm != null) ...[
                        Text('  ·  ',
                            style: GoogleFonts.inter(
                                fontSize: 11, color: kTextMuted)),
                        Text(
                          '${rec.distanceKm!.toStringAsFixed(1)} km away',
                          style: GoogleFonts.inter(
                              fontSize: 11, color: kTextMuted),
                        ),
                      ],
                    ]),
                    const SizedBox(height: 4),
                    // Urgency badge
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 7, vertical: 2),
                      decoration: BoxDecoration(
                        color: _urgencyColor(cargo.urgencyLevel)
                            .withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(
                        cargo.urgencyLevel,
                        style: GoogleFonts.inter(
                            fontSize: 10,
                            color: _urgencyColor(cargo.urgencyLevel),
                            fontWeight: FontWeight.w600),
                      ),
                    ),
                  ],
                ),
              ),
              // Dismiss button
              GestureDetector(
                onTap: onDismiss,
                child: const Padding(
                  padding: EdgeInsets.only(left: 8),
                  child: Icon(Icons.close_rounded,
                      size: 16, color: kTextMuted),
                ),
              ),
            ]),

            const SizedBox(height: 8),

            // Score bar
            Row(children: [
              Text('Match strength ',
                  style: GoogleFonts.inter(
                      fontSize: 10, color: kTextMuted)),
              Expanded(
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(4),
                  child: LinearProgressIndicator(
                    value: rec.score,
                    backgroundColor: kBorder,
                    color: kGreen,
                    minHeight: 5,
                  ),
                ),
              ),
              const SizedBox(width: 6),
              Text('$score%',
                  style: GoogleFonts.inter(
                      fontSize: 10,
                      fontWeight: FontWeight.w600,
                      color: kGreen)),
            ]),

            const SizedBox(height: 8),

            // CTA
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: onViewBid,
                style: ElevatedButton.styleFrom(
                  backgroundColor: _amber,
                  foregroundColor: kTextPrimary,
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8)),
                  elevation: 0,
                ),
                child: Text('View & Bid',
                    style: GoogleFonts.inter(
                        fontSize: 12, fontWeight: FontWeight.bold)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Payment helpers ───────────────────────────────────────────────────────

String _methodLabel(String method) {
  switch (method) {
    case 'telebirr':      return 'Telebirr';
    case 'cbe_birr':      return 'CBE Birr';
    case 'awash_bank':    return 'Awash Bank';
    case 'dashen_bank':   return 'Dashen Bank';
    case 'bank_transfer': return 'Other Bank';
    case 'cash':          return 'Cash';
    default:              return method;
  }
}

class _PaymentMethodBadge extends StatelessWidget {
  final String method;
  const _PaymentMethodBadge({required this.method});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: kGreen.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: kGreen.withValues(alpha: 0.25)),
      ),
      child: Row(children: [
        const Icon(Icons.check_circle_outline_rounded,
            size: 16, color: kGreen),
        const SizedBox(width: 8),
        Text('Payment method: ${_methodLabel(method)}',
            style: GoogleFonts.inter(
                fontSize: 13,
                color: kGreen,
                fontWeight: FontWeight.w600)),
      ]),
    );
  }
}

// ── Driver post-delivery payment sheet ───────────────────────────────────
//
// Shown to the driver immediately after "Mark as Delivered". Records how the
// shipper paid at the point of delivery (Ethiopian context: cash on delivery).

class _DriverPaymentSheet extends StatefulWidget {
  const _DriverPaymentSheet();

  @override
  State<_DriverPaymentSheet> createState() => _DriverPaymentSheetState();
}

class _DriverPaymentSheetState extends State<_DriverPaymentSheet> {
  String? _selected;

  static const _methods = [
    ('cash',          'Cash / ጥሬ ገንዘብ',     'Paid at delivery — most common in Ethiopia', Icons.payments_rounded),
    ('telebirr',      'Telebirr',             'Mobile transfer — instant',                   Icons.phone_android_rounded),
    ('cbe_birr',      'CBE Birr',             'Commercial Bank of Ethiopia',                 Icons.account_balance_rounded),
    ('awash_bank',    'Awash Bank',           'Bank transfer to Awash account',              Icons.account_balance_outlined),
    ('dashen_bank',   'Dashen Bank',          'Bank transfer to Dashen account',             Icons.account_balance_outlined),
    ('bank_transfer', 'Other Bank',           'Any other Ethiopian bank',                    Icons.account_balance_outlined),
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: kSurface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      padding: EdgeInsets.fromLTRB(
          20, 16, 20, MediaQuery.of(context).viewInsets.bottom + 32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                  color: kBorder, borderRadius: BorderRadius.circular(2)),
            ),
          ),
          const SizedBox(height: 16),
          Text(
            'How was payment made?',
            style: GoogleFonts.inter(
                fontSize: 16,
                fontWeight: FontWeight.w700,
                color: kTextPrimary),
          ),
          const SizedBox(height: 2),
          Text(
            'ክፍያ እንዴት ተፈጸመ?',
            style: GoogleFonts.inter(fontSize: 13, color: kTextSecond),
          ),
          const SizedBox(height: 16),
          ..._methods.map((m) {
            final (value, label, desc, icon) = m;
            final selected = _selected == value;
            return GestureDetector(
              onTap: () => setState(() => _selected = value),
              child: Container(
                margin: const EdgeInsets.only(bottom: 8),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: selected
                      ? kAmber.withValues(alpha: 0.06)
                      : kBackground,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: selected ? kAmber : kBorder,
                    width: selected ? 1.5 : 0.5,
                  ),
                ),
                child: Row(children: [
                  Container(
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                      color: (selected ? kAmber : kTextSecond)
                          .withValues(alpha: 0.10),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(icon,
                        size: 18,
                        color: selected ? kAmber : kTextSecond),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(label,
                            style: GoogleFonts.inter(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: kTextPrimary)),
                        Text(desc,
                            style: GoogleFonts.inter(
                                fontSize: 11, color: kTextSecond)),
                      ],
                    ),
                  ),
                  if (selected)
                    const Icon(Icons.check_circle_rounded,
                        color: kAmber, size: 18),
                ]),
              ),
            );
          }),
          const SizedBox(height: 8),
          Row(children: [
            TextButton(
              onPressed: () => Navigator.pop(context, null),
              style: TextButton.styleFrom(foregroundColor: kTextMuted),
              child: Text('Skip / ሰርዝ',
                  style: GoogleFonts.inter(fontSize: 13)),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: ElevatedButton(
                onPressed: _selected == null
                    ? null
                    : () => Navigator.pop(context, _selected),
                style: ElevatedButton.styleFrom(
                  backgroundColor: kAmber,
                  foregroundColor: Colors.white,
                  disabledBackgroundColor: kBorder,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10)),
                  elevation: 0,
                ),
                child: Text(
                  _selected == null
                      ? 'Confirm / አረጋግጥ'
                      : 'Confirm — ${_methodLabel(_selected!)}',
                  style: GoogleFonts.inter(
                      fontSize: 14, fontWeight: FontWeight.w700),
                ),
              ),
            ),
          ]),
        ],
      ),
    );
  }
}

// ── Payment method selection bottom sheet ────────────────────────────────

class _PaymentMethodSheet extends StatefulWidget {
  const _PaymentMethodSheet();

  @override
  State<_PaymentMethodSheet> createState() => _PaymentMethodSheetState();
}

class _PaymentMethodSheetState extends State<_PaymentMethodSheet> {
  String? _selected;

  static const _methods = [
    ('telebirr',      'Telebirr',    'Mobile money — instant transfer',              Icons.phone_android_rounded),
    ('cbe_birr',      'CBE Birr',    'Commercial Bank of Ethiopia mobile payment',   Icons.account_balance_rounded),
    ('bank_transfer', 'Other Bank',  'Awash, Abyssinia, Dashen or any other bank',   Icons.account_balance_outlined),
    ('cash',          'Cash',        'Pay on delivery or pickup',                    Icons.payments_rounded),
  ];

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: kSurface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 36,
              height: 4,
              decoration: BoxDecoration(
                color: kBorder,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text('Select Payment Method',
              style: GoogleFonts.inter(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: kTextPrimary)),
          const SizedBox(height: 4),
          Text('Choose how the payment will be made',
              style: GoogleFonts.inter(fontSize: 13, color: kTextSecond)),
          const SizedBox(height: 16),
          ..._methods.map((m) {
            final (value, label, desc, icon) = m;
            final selected = _selected == value;
            return GestureDetector(
              onTap: () => setState(() => _selected = value),
              child: Container(
                margin: const EdgeInsets.only(bottom: 10),
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: selected
                      ? kGreen.withValues(alpha: 0.06)
                      : kBackground,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(
                    color: selected ? kGreen : kBorder,
                    width: selected ? 1.5 : 0.5,
                  ),
                ),
                child: Row(children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: (selected ? kGreen : kTextSecond)
                          .withValues(alpha: 0.10),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(icon,
                        size: 20,
                        color: selected ? kGreen : kTextSecond),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(label,
                            style: GoogleFonts.inter(
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                                color: kTextPrimary)),
                        const SizedBox(height: 2),
                        Text(desc,
                            style: GoogleFonts.inter(
                                fontSize: 12, color: kTextSecond)),
                      ],
                    ),
                  ),
                  if (selected)
                    const Icon(Icons.check_circle_rounded,
                        color: kGreen, size: 20),
                ]),
              ),
            );
          }),
          const SizedBox(height: 8),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _selected == null
                  ? null
                  : () => Navigator.pop(context, _selected),
              style: ElevatedButton.styleFrom(
                backgroundColor: kSuccess,
                foregroundColor: Colors.white,
                disabledBackgroundColor: kBorder,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10)),
                elevation: 0,
              ),
              child: Text(
                _selected == null
                    ? 'Select a method to continue'
                    : 'Confirm Payment via ${_methodLabel(_selected!)}',
                style: GoogleFonts.inter(
                    fontSize: 14, fontWeight: FontWeight.w700),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

// ── Price summary card ────────────────────────────────────────────────────
//
// Shown to both driver (before trip start) and shipper (before/at payment).
// forDriver=true  → shows "You will receive" (agreed price minus 10%)
// forDriver=false → shows "Total to pay" (agreed price; commission deducted
//                   from driver's side, not added on top)

class _PriceSummaryCard extends StatelessWidget {
  final Booking booking;
  final bool forDriver;

  const _PriceSummaryCard({required this.booking, required this.forDriver});

  @override
  Widget build(BuildContext context) {
    final price      = booking.estimatedPrice;
    final commission = booking.commissionFee ?? (price * 0.10);
    final driverNet  = price - commission;

    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: kGreen.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: kGreen.withValues(alpha: 0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            const Icon(Icons.receipt_long_outlined, size: 15, color: kGreen),
            const SizedBox(width: 6),
            Text(
              'Payment Summary',
              style: GoogleFonts.inter(
                  fontSize: 12,
                  fontWeight: FontWeight.w700,
                  color: kGreen,
                  letterSpacing: 0.3),
            ),
          ]),
          const SizedBox(height: 10),
          _SummaryRow(
            label: 'Agreed price',
            value: 'ETB ${price.toStringAsFixed(0)}',
          ),
          const SizedBox(height: 6),
          _SummaryRow(
            label: 'Platform commission (10%)',
            value: '− ETB ${commission.toStringAsFixed(0)}',
            valueColor: kDanger,
          ),
          const Divider(height: 18, color: kBorder),
          _SummaryRow(
            label: forDriver ? 'You will receive' : 'Total to pay',
            value: 'ETB ${forDriver ? driverNet.toStringAsFixed(0) : price.toStringAsFixed(0)}',
            bold: true,
            valueColor: kGreen,
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: kAmber.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(6),
              border: Border.all(color: kAmber.withValues(alpha: 0.2)),
            ),
            child: Row(children: [
              const Icon(Icons.info_outline_rounded, size: 13, color: kAmber),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  forDriver
                      ? 'Payment is collected from the shipper after delivery'
                      : 'Commission is deducted from the driver — you pay the agreed amount',
                  style: GoogleFonts.inter(fontSize: 11, color: kAmber),
                ),
              ),
            ]),
          ),
        ],
      ),
    );
  }
}

class _SummaryRow extends StatelessWidget {
  final String label;
  final String value;
  final bool bold;
  final Color? valueColor;

  const _SummaryRow({
    required this.label,
    required this.value,
    this.bold = false,
    this.valueColor,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(
          label,
          style: GoogleFonts.inter(
            fontSize: 13,
            color: bold ? kTextPrimary : kTextSecond,
            fontWeight: bold ? FontWeight.w700 : FontWeight.w400,
          ),
        ),
        Text(
          value,
          style: GoogleFonts.inter(
            fontSize: bold ? 14 : 13,
            fontWeight: bold ? FontWeight.w800 : FontWeight.w600,
            color: valueColor ?? kTextPrimary,
          ),
        ),
      ],
    );
  }
}
