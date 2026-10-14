import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';
import '../shared/widgets/shared_widgets.dart';
import '../shared/widgets/nearby_drivers_map_widget.dart';

/// Cargo detail screen.
/// - Drivers see cargo info + "Place Bid" button.
/// - Shippers see cargo info + "View Driver Bids" button.
class FreightDetailScreen extends ConsumerWidget {
  final int freightId;
  const FreightDetailScreen({required this.freightId, super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cargoAsync = ref.watch(singleCargoProvider(freightId));

    return cargoAsync.when(
      data: (cargo) => _DetailScaffold(cargo: cargo),
      loading: () => Scaffold(
        backgroundColor: kBackground,
        appBar: const EthioAppBar(title: 'Cargo Details'),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: const [
            ShimmerBox(height: 120, radius: 12),
            SizedBox(height: 12),
            ShimmerBox(height: 200, radius: 12),
            SizedBox(height: 12),
            ShimmerBox(height: 120, radius: 12),
          ],
        ),
      ),
      error: (e, _) => Scaffold(
        backgroundColor: kBackground,
        appBar: const EthioAppBar(title: 'Cargo Details'),
        body: Center(
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
                Text('Failed to load cargo',
                    style: GoogleFonts.inter(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: kTextPrimary)),
                const SizedBox(height: 6),
                Text(e.toString(),
                    textAlign: TextAlign.center,
                    style: GoogleFonts.inter(
                        fontSize: 13, color: kTextMuted)),
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
                  onPressed: () => ref.refresh(singleCargoProvider(freightId)),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// ── Main scaffold ──────────────────────────────────────────────────────────

class _DetailScaffold extends ConsumerWidget {
  final CargoRequest cargo;
  const _DetailScaffold({required this.cargo});

  Color get _statusColor {
    switch (cargo.status) {
      case 'matched':
        return kGreen;
      case 'completed':
        return kSuccess;
      default:
        return kAmber;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authNotifierProvider).user;
    final isOwner = user != null && (user.id == cargo.userId || user.isAdmin);
    final canViewNearbyVehicles = isOwner;
    final canViewApplications = isOwner || user?.isShipper == true || user?.isAdmin == true;

    return Scaffold(
      backgroundColor: kBackground,
      appBar: const EthioAppBar(title: 'Cargo Details'),
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // ── Header ────────────────────────────────────────────────
            Container(
              width: double.infinity,
              color: kGreenTint,
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Text(
                          '${cargo.pickupLocation} → ${cargo.destination}',
                          style: GoogleFonts.inter(
                              fontSize: 17,
                              fontWeight: FontWeight.bold,
                              color: kTextPrimary),
                        ),
                      ),
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 10, vertical: 5),
                        decoration: BoxDecoration(
                          color: _statusColor.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Text(
                          cargo.status.toUpperCase(),
                          style: GoogleFonts.inter(
                              fontWeight: FontWeight.bold,
                              color: _statusColor,
                              fontSize: 11),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  Row(children: [
                    Expanded(
                      child: _InfoTile(
                        label: 'Budget',
                        value: cargo.budget != null
                            ? 'ETB ${cargo.budget!.toStringAsFixed(0)}'
                            : 'Negotiable',
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _InfoTile(
                          label: 'Weight',
                          value: '${cargo.weight} t'),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: _InfoTile(
                          label: 'Urgency',
                          value: cargo.urgencyLevel),
                    ),
                  ]),
                ],
              ),
            ),

            // ── Details ────────────────────────────────────────────────
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Cargo Details',
                      style: GoogleFonts.inter(
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: kTextPrimary)),
                  const SizedBox(height: 10),
                  _DetailRow('Material', cargo.materialType),
                  _DetailRow('Weight', '${cargo.weight} tons'),
                  _DetailRow('Urgency', cargo.urgencyLevel),
                  _DetailRow('Status', cargo.status),
                  if (cargo.budget != null)
                    _DetailRow('Budget',
                        'ETB ${cargo.budget!.toStringAsFixed(2)}'),
                  if (cargo.createdAt != null)
                    _DetailRow('Posted',
                        cargo.createdAt!.toLocal().toString().split(' ').first),

                  const SizedBox(height: 20),
                  Text('Locations',
                      style: GoogleFonts.inter(
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: kTextPrimary)),
                  const SizedBox(height: 10),
                  _LocationCard(
                      icon: Icons.trip_origin_rounded,
                      title: 'Pickup',
                      address: cargo.pickupLocation,
                      color: kAmber),
                  if (canViewNearbyVehicles) ...[
                    const SizedBox(height: 16),
                    if (cargo.pickupLatitude != null &&
                        cargo.pickupLongitude != null)
                      NearbyDriversMapWidget(
                        key: const Key('freight-nearby-vehicles'),
                        pickupLat: cargo.pickupLatitude!,
                        pickupLng: cargo.pickupLongitude!,
                        pickupName: cargo.pickupLocation,
                      )
                    else
                      Container(
                        key: const Key('freight-nearby-vehicles-unavailable'),
                        width: double.infinity,
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: kSurface,
                          border: Border.all(color: kBorder),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: Text(
                          'Nearby vehicles are unavailable because this freight has no pickup coordinates.',
                          style: GoogleFonts.inter(
                            fontSize: 12,
                            color: kTextMuted,
                          ),
                        ),
                      ),
                  ],
                  const SizedBox(height: 10),
                  _LocationCard(
                      icon: Icons.location_on_rounded,
                      title: 'Destination',
                      address: cargo.destination,
                      color: kGreen),

                  // ── Applications (for shipper / owner / admin) ───────
                  if (canViewApplications) ...[
                    const SizedBox(height: 24),
                    _ShipperApplicationsSection(cargo: cargo),
                    const SizedBox(height: 16),
                    _ShipperCancelCargoAction(cargo: cargo),
                  ],

                  // ── Driver Apply / Your Bid action ───────────────────
                  if (user?.isDriver == true) ...[
                    const SizedBox(height: 24),
                    _DriverBidAction(cargo: cargo),
                  ],

                  const SizedBox(height: 32),
                ],
              ),
            ),
          ],
        ),
      ),
      bottomNavigationBar: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        child: OutlinedButton.icon(
          icon: const Icon(Icons.arrow_back_rounded, size: 16),
          label: Text('Back',
              style: GoogleFonts.inter(
                  fontSize: 14, fontWeight: FontWeight.w600)),
          style: OutlinedButton.styleFrom(
            foregroundColor: kGreen,
            side: const BorderSide(color: kBorder),
            padding: const EdgeInsets.symmetric(vertical: 14),
            shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12)),
          ),
          onPressed: () => context.pop(),
        ),
      ),
    );
  }
}

// ── Driver: Place Bid (negotiable) or Accept/Reject (fixed) ──────────────

class _DriverBidAction extends ConsumerWidget {
  final CargoRequest cargo;
  const _DriverBidAction({required this.cargo});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Check for driver's existing bid / application first
    final myBids = ref.watch(myBidsProvider).valueOrNull;
    Bid? existingBid;
    if (myBids != null) {
      final matches = myBids.where(
        (b) => b.cargoRequestId == cargo.id,
      );
      existingBid = matches.isEmpty ? null : matches.first;
    }

    if (existingBid != null) {
      return _DriverExistingBidCard(cargo: cargo, bid: existingBid);
    }

    if (cargo.isFixed) {
      return _FixedPriceDriverAction(cargo: cargo);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Interested in this cargo?',
            style: GoogleFonts.inter(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: kTextPrimary)),
        const SizedBox(height: 6),
        Text(
          cargo.budget != null
              ? 'Shipper budget: ETB ${cargo.budget!.toStringAsFixed(0)}'
              : 'Budget not specified — propose your own price.',
          style: GoogleFonts.inter(fontSize: 12, color: kTextMuted),
        ),
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            icon: const Icon(Icons.gavel_rounded, size: 18),
            label: Text('Place Bid',
                style: GoogleFonts.inter(
                    fontSize: 14, fontWeight: FontWeight.w600)),
            style: ElevatedButton.styleFrom(
              backgroundColor: kAmber,
              foregroundColor: kTextPrimary,
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
              elevation: 0,
            ),
            onPressed: () => showModalBottomSheet(
              context: context,
              useRootNavigator: true,
              isScrollControlled: true,
              shape: const RoundedRectangleBorder(
                  borderRadius:
                      BorderRadius.vertical(top: Radius.circular(20))),
              builder: (_) => _PlaceBidSheet(cargo: cargo),
            ),
          ),
        ),
      ],
    );
  }
}

// ── Driver Existing Bid Card (replaces apply form for driver who already bid)
class _DriverExistingBidCard extends ConsumerStatefulWidget {
  final CargoRequest cargo;
  final Bid bid;
  const _DriverExistingBidCard({required this.cargo, required this.bid});

  @override
  ConsumerState<_DriverExistingBidCard> createState() => _DriverExistingBidCardState();
}

class _DriverExistingBidCardState extends ConsumerState<_DriverExistingBidCard> {
  bool _accepting = false;
  bool _rejecting = false;

  Future<void> _acceptCounter() async {
    setState(() => _accepting = true);
    try {
      await ref.read(bidRepositoryProvider).acceptCounter(widget.bid.id);
      if (mounted) {
        ref.invalidate(myBidsProvider);
        ref.invalidate(bookingListProvider);
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Counter-offer accepted!'),
          backgroundColor: kGreen,
        ));
        context.go('/my-bookings');
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Failed: $e'),
          backgroundColor: kDanger,
        ));
      }
    } finally {
      if (mounted) setState(() => _accepting = false);
    }
  }

  Future<void> _rejectCounter() async {
    setState(() => _rejecting = true);
    try {
      await ref.read(bidRepositoryProvider).rejectBid(widget.bid.id);
      if (mounted) {
        ref.invalidate(myBidsProvider);
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Bid declined'),
        ));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Failed: $e'),
          backgroundColor: kDanger,
        ));
      }
    } finally {
      if (mounted) setState(() => _rejecting = false);
    }
  }

  void _openCounterBack() {
    final ctrl = TextEditingController();
    final noteCtrl = TextEditingController();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 20,
          bottom: MediaQuery.of(ctx).viewInsets.bottom + 24,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Counter Back',
                style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.bold, color: kTextPrimary)),
            const SizedBox(height: 12),
            TextField(
              controller: ctrl,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(
                labelText: 'Your Counter Amount (ETB)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: noteCtrl,
              decoration: const InputDecoration(
                labelText: 'Note (optional)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: kAmber,
                  foregroundColor: kTextPrimary,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
                onPressed: () async {
                  final amt = double.tryParse(ctrl.text.trim());
                  if (amt == null || amt <= 0) return;
                  Navigator.of(ctx).pop();
                  try {
                    await ref.read(bidRepositoryProvider).counterBid(
                      widget.bid.id,
                      counterAmount: amt,
                      counterNote: noteCtrl.text.trim().isEmpty ? null : noteCtrl.text.trim(),
                    );
                    if (mounted) {
                      ref.invalidate(myBidsProvider);
                      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
                        content: Text('Counter-offer sent!'),
                        backgroundColor: kGreen,
                      ));
                    }
                  } catch (e) {
                    if (mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                        content: Text('Failed: $e'),
                        backgroundColor: kDanger,
                      ));
                    }
                  }
                },
                child: const Text('Send Counter-Offer', style: TextStyle(fontWeight: FontWeight.bold)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final b = widget.bid;
    final isShipperCounter = b.isCountered && b.counterBy == 'shipper';

    Color statusColor = kAmber;
    if (b.status == 'accepted') statusColor = kGreen;
    if (b.status == 'rejected') statusColor = kDanger;
    if (b.isCountered) statusColor = const Color(0xFF7C3AED);

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: statusColor.withValues(alpha: 0.4), width: 1.5),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header row: Your Bid badge + Status badge
          Row(
            children: [
              const Icon(Icons.assignment_turned_in_outlined, size: 18, color: kGreen),
              const SizedBox(width: 8),
              Text(
                widget.cargo.isFixed ? 'Your Application' : 'Your Bid',
                style: GoogleFonts.inter(
                  fontSize: 15,
                  fontWeight: FontWeight.bold,
                  color: kTextPrimary,
                ),
              ),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: statusColor.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: statusColor.withValues(alpha: 0.4), width: 0.5),
                ),
                child: Text(
                  b.status.toUpperCase(),
                  style: GoogleFonts.inter(
                    fontSize: 11,
                    fontWeight: FontWeight.w700,
                    color: statusColor,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Price row
          Row(
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    widget.cargo.isFixed ? 'Fixed Price' : 'Proposed Amount',
                    style: GoogleFonts.inter(fontSize: 11, color: kTextMuted),
                  ),
                  Text(
                    'ETB ${b.amount.toStringAsFixed(0)}',
                    style: GoogleFonts.inter(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: isShipperCounter ? kTextMuted : kTextPrimary,
                      decoration: isShipperCounter ? TextDecoration.lineThrough : null,
                    ),
                  ),
                ],
              ),
              if (b.counterAmount != null) ...[
                const SizedBox(width: 12),
                const Icon(Icons.arrow_forward_rounded, size: 16, color: kTextMuted),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      b.counterBy == 'shipper' ? 'Shipper Counter' : 'Your Counter',
                      style: GoogleFonts.inter(fontSize: 11, color: kTextMuted),
                    ),
                    Text(
                      'ETB ${b.counterAmount!.toStringAsFixed(0)}',
                      style: GoogleFonts.inter(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: b.counterBy == 'shipper' ? const Color(0xFF7C3AED) : kAmber,
                      ),
                    ),
                  ],
                ),
              ],
            ],
          ),

          // Vehicle info
          if (b.plateNumber != null || b.truckType != null) ...[
            const SizedBox(height: 10),
            Row(
              children: [
                if (b.plateNumber != null) ...[
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: Colors.grey.shade100,
                      borderRadius: BorderRadius.circular(4),
                      border: Border.all(color: kBorder),
                    ),
                    child: Text(
                      b.plateNumber!,
                      style: GoogleFonts.inter(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: kTextPrimary,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                if (b.truckType != null)
                  Text(
                    b.truckType!.replaceAll('_', ' ').toUpperCase(),
                    style: GoogleFonts.inter(fontSize: 12, color: kTextMuted),
                  ),
              ],
            ),
          ],

          // Bid note
          if (b.note != null && b.note!.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              '"${b.note}"',
              style: GoogleFonts.inter(
                fontSize: 12,
                fontStyle: FontStyle.italic,
                color: kTextMuted,
              ),
            ),
          ],

          // Dynamic state banners
          if (b.status == 'pending') ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                color: kAmber.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: kAmber.withValues(alpha: 0.4), width: 0.5),
              ),
              child: Row(
                children: [
                  const Icon(Icons.access_time_rounded, size: 16, color: kAmber),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      widget.cargo.isFixed
                          ? 'Application sent to shipper. Waiting for shipper response.'
                          : 'Waiting for shipper response',
                      style: GoogleFonts.inter(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: const Color(0xFF92400E),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
          if (b.status == 'pending' && !widget.cargo.isFixed) ...[
            const SizedBox(height: 10),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                icon: const Icon(Icons.edit_outlined, size: 16),
                label: Text('Edit Bid', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600)),
                style: OutlinedButton.styleFrom(
                  foregroundColor: kAmber,
                  side: const BorderSide(color: kAmber),
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
                onPressed: () => showModalBottomSheet(
                  context: context,
                  isScrollControlled: true,
                  shape: const RoundedRectangleBorder(
                    borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
                  ),
                  builder: (_) => _PlaceBidSheet(cargo: widget.cargo, existingBid: b),
                ),
              ),
            ),
          ],

          if (isShipperCounter) ...[
            const SizedBox(height: 12),
            if (b.counterNote != null && b.counterNote!.isNotEmpty) ...[
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: const Color(0xFF7C3AED).withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.format_quote_rounded, size: 14, color: Color(0xFF7C3AED)),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        b.counterNote!,
                        style: GoogleFonts.inter(fontSize: 12, fontStyle: FontStyle.italic, color: kTextPrimary),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),
            ],
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: (_accepting || _rejecting) ? null : _acceptCounter,
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF7C3AED),
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
                child: _accepting
                    ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : Text('Accept Shipper\'s ETB ${b.counterAmount!.toStringAsFixed(0)}',
                        style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600)),
              ),
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: (_accepting || _rejecting) ? null : _openCounterBack,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: kAmber,
                      side: const BorderSide(color: kAmber),
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    ),
                    child: Text('Counter Back', style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600)),
                  ),
                ),
                const SizedBox(width: 8),
                OutlinedButton(
                  onPressed: (_accepting || _rejecting) ? null : _rejectCounter,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: kDanger,
                    side: const BorderSide(color: kDanger),
                    padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 16),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                  child: _rejecting
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: kDanger))
                      : Text('Decline', style: GoogleFonts.inter(fontSize: 12)),
                ),
              ],
            ),
          ],

          if (b.isCountered && b.counterBy == 'driver') ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: kAmber.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: kAmber.withValues(alpha: 0.4), width: 0.5),
              ),
              child: Row(
                children: [
                  const Icon(Icons.schedule_rounded, size: 16, color: kAmber),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Counter-offer sent to shipper. Waiting for shipper response.',
                      style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w500, color: const Color(0xFF92400E)),
                    ),
                  ),
                ],
              ),
            ),
          ],

          if (b.status == 'accepted') ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: kGreen.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: kGreen.withValues(alpha: 0.4), width: 0.5),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.check_circle_rounded, size: 16, color: kGreen),
                      const SizedBox(width: 6),
                      Text('Bid Accepted!',
                          style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.bold, color: kGreen)),
                    ],
                  ),
                  if (b.shipperName != null || b.shipperPhone != null) ...[
                    const SizedBox(height: 6),
                    Text(
                      'Shipper: ${b.shipperName ?? ''} ${b.shipperPhone != null ? '(${b.shipperPhone})' : ''}',
                      style: GoogleFonts.inter(fontSize: 12, color: kTextPrimary),
                    ),
                  ],
                ],
              ),
            ),
          ],

          if (b.status == 'rejected') ...[
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: kDanger.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: kDanger.withValues(alpha: 0.4), width: 0.5),
              ),
              child: Row(
                children: [
                  const Icon(Icons.cancel_outlined, size: 16, color: kDanger),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'This bid was declined by the shipper.',
                      style: GoogleFonts.inter(fontSize: 12, color: kDanger),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

// ── Driver: Accept / Reject fixed-price cargo ─────────────────────────────

class _FixedPriceDriverAction extends StatelessWidget {
  final CargoRequest cargo;
  const _FixedPriceDriverAction({required this.cargo});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: kBackground,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: kBorder),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            const Icon(Icons.lock_outline_rounded, size: 18, color: kGreen),
            const SizedBox(width: 8),
            Text('Fixed Price Cargo',
                style: GoogleFonts.inter(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: kTextPrimary)),
          ]),
          const SizedBox(height: 6),
          Text(
            cargo.budget != null
                ? 'This freight is listed with a fixed price of ETB ${cargo.budget!.toStringAsFixed(0)}. '
                  'Bidding and counter-offers are not available on fixed-price loads.'
                : 'This freight is listed at a fixed price. Bidding and counter-offers are not available on fixed-price loads.',
            style: GoogleFonts.inter(fontSize: 12, color: kTextMuted),
          ),
          const SizedBox(height: 14),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              icon: const Icon(Icons.check_circle_outline_rounded, size: 18),
              label: Text('Apply at Fixed Price',
                  style: GoogleFonts.inter(fontSize: 14, fontWeight: FontWeight.w600)),
              style: ElevatedButton.styleFrom(
                backgroundColor: kGreen,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 13),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                elevation: 0,
              ),
              onPressed: () {
                debugPrint('>>> [FreightDetail] Apply at Fixed Price pressed for cargo ${cargo.id}');
                showModalBottomSheet(
                  context: context,
                  useRootNavigator: true,
                  isScrollControlled: true,
                  shape: const RoundedRectangleBorder(
                    borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
                  ),
                  builder: (_) => _PlaceBidSheet(cargo: cargo),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

// ── Shipper: Applications Section ─────────────────────────────────────────

class _ShipperApplicationsSection extends ConsumerStatefulWidget {
  final CargoRequest cargo;
  const _ShipperApplicationsSection({required this.cargo});

  @override
  ConsumerState<_ShipperApplicationsSection> createState() =>
      _ShipperApplicationsSectionState();
}

class _ShipperApplicationsSectionState
    extends ConsumerState<_ShipperApplicationsSection> {
  int _page = 0;

  void _refreshBids() {
    final previousPage = _page;
    setState(() => _page = 0);
    ref.invalidate(bidsForCargoPageProvider((widget.cargo.id, previousPage)));
    ref.invalidate(bidsForCargoPageProvider((widget.cargo.id, 0)));
  }

  @override
  Widget build(BuildContext context) {
    final bidsAsync = ref.watch(
        bidsForCargoPageProvider((widget.cargo.id, _page)));

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.people_alt_rounded, size: 20, color: kGreen),
            const SizedBox(width: 8),
            Text(
              bidsAsync.maybeWhen(
                data: (bidPage) =>
                    'Applications (${bidPage.pendingCount} pending or countered)',
                orElse: () => 'Applications',
              ),
              style: GoogleFonts.inter(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: kTextPrimary,
              ),
            ),
            const Spacer(),
            IconButton(
              icon: const Icon(Icons.refresh_rounded, size: 18, color: kTextMuted),
              tooltip: 'Refresh bids',
              onPressed: _refreshBids,
            ),
          ],
        ),
        const SizedBox(height: 10),
        bidsAsync.when(
          data: (bidPage) {
            if (bidPage.content.isEmpty && _page == 0) {
              return Container(
                width: double.infinity,
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: kBorder),
                ),
                child: Column(
                  children: [
                    const Icon(Icons.gavel_outlined, size: 36, color: kTextMuted),
                    const SizedBox(height: 10),
                    Text(
                      'No applications yet',
                      style: GoogleFonts.inter(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: kTextPrimary,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      'Driver bids will appear here once submitted.',
                      style: GoogleFonts.inter(fontSize: 12, color: kTextMuted),
                    ),
                  ],
                ),
              );
            }
            return Column(
              children: [
                ...bidPage.content.map(
                  (bid) => _ShipperApplicationCard(
                        bid: bid,
                        cargo: widget.cargo,
                        onUpdated: _refreshBids,
                      ),
                ),
                if (bidPage.hasNext)
                  TextButton(
                    onPressed: () => setState(
                        () => _page = bidPage.nextPage ?? _page + 1),
                    child: const Text('Next page of applications'),
                  ),
                if (_page > 0)
                  TextButton(
                    onPressed: () => setState(() => _page--),
                    child: const Text('Previous page'),
                  ),
              ],
            );
          },
          loading: () => Column(
            children: List.generate(
              2,
              (_) => const Padding(
                padding: EdgeInsets.only(bottom: 12),
                child: ShimmerBox(height: 120, radius: 12),
              ),
            ),
          ),
          error: (e, _) => Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: kDanger.withValues(alpha: 0.05),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: kDanger.withValues(alpha: 0.2)),
            ),
            child: Column(
              children: [
                Text(
                  'Failed to load applications: $e',
                  style: GoogleFonts.inter(fontSize: 12, color: kDanger),
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                TextButton.icon(
                  onPressed: _refreshBids,
                  icon: const Icon(Icons.refresh_rounded, size: 16, color: kGreen),
                  label: Text('Retry', style: GoogleFonts.inter(fontSize: 13, color: kGreen)),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

// ── Single Application Card for Shipper Review ───────────────────────────

class _ShipperApplicationCard extends ConsumerStatefulWidget {
  final Bid bid;
  final CargoRequest cargo;
  final VoidCallback onUpdated;
  const _ShipperApplicationCard({
    required this.bid,
    required this.cargo,
    required this.onUpdated,
  });

  @override
  ConsumerState<_ShipperApplicationCard> createState() => _ShipperApplicationCardState();
}

class _ShipperApplicationCardState extends ConsumerState<_ShipperApplicationCard> {
  bool _accepting = false;
  bool _rejecting = false;
  bool _countering = false;

  Future<void> _acceptBid() async {
    setState(() => _accepting = true);
    try {
      await ref.read(bidRepositoryProvider).acceptBid(widget.bid.id);
      if (mounted) {
        widget.onUpdated();
        ref.invalidate(singleCargoProvider(widget.cargo.id));
        ref.invalidate(bookingListProvider);
        ref.invalidate(cargoListProvider);
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Bid accepted! Booking created.'),
          backgroundColor: kGreen,
        ));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Failed to accept bid: $e'),
          backgroundColor: kDanger,
        ));
      }
    } finally {
      if (mounted) setState(() => _accepting = false);
    }
  }

  Future<void> _acceptCounter() async {
    setState(() => _countering = true);
    try {
      await ref.read(bidRepositoryProvider).acceptCounter(widget.bid.id);
      if (mounted) {
        widget.onUpdated();
        ref.invalidate(singleCargoProvider(widget.cargo.id));
        ref.invalidate(bookingListProvider);
        ref.invalidate(cargoListProvider);
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Driver counter-offer accepted! Booking created.'),
          backgroundColor: kGreen,
        ));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Failed to accept counter: $e'),
          backgroundColor: kDanger,
        ));
      }
    } finally {
      if (mounted) setState(() => _countering = false);
    }
  }

  Future<void> _rejectBid() async {
    setState(() => _rejecting = true);
    try {
      await ref.read(bidRepositoryProvider).rejectBid(widget.bid.id);
      if (mounted) {
        widget.onUpdated();
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Bid rejected.'),
        ));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Failed to reject: $e'),
          backgroundColor: kDanger,
        ));
      }
    } finally {
      if (mounted) setState(() => _rejecting = false);
    }
  }

  void _openCounterSheet() {
    final ctrl = TextEditingController();
    final noteCtrl = TextEditingController();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => Padding(
        padding: EdgeInsets.only(
          left: 20,
          right: 20,
          top: 20,
          bottom: MediaQuery.of(ctx).viewInsets.bottom + 24,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Counter-Offer to Driver',
                style: GoogleFonts.inter(fontSize: 16, fontWeight: FontWeight.bold, color: kTextPrimary)),
            const SizedBox(height: 6),
            Text(
              'Driver\'s proposed price: ETB ${widget.bid.amount.toStringAsFixed(0)}',
              style: GoogleFonts.inter(fontSize: 12, color: kTextMuted),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: ctrl,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              decoration: const InputDecoration(
                labelText: 'Your Counter Price (ETB)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 10),
            TextField(
              controller: noteCtrl,
              decoration: const InputDecoration(
                labelText: 'Note to Driver (optional)',
                border: OutlineInputBorder(),
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: kAmber,
                  foregroundColor: kTextPrimary,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                ),
                onPressed: () async {
                  final amt = double.tryParse(ctrl.text.trim());
                  if (amt == null || amt <= 0) return;
                  Navigator.of(ctx).pop();
                  try {
                    await ref.read(bidRepositoryProvider).counterBid(
                      widget.bid.id,
                      counterAmount: amt,
                      counterNote: noteCtrl.text.trim().isEmpty ? null : noteCtrl.text.trim(),
                    );
                    if (mounted) {
                      widget.onUpdated();
                      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
                        content: Text('Counter-offer sent to driver!'),
                        backgroundColor: kGreen,
                      ));
                    }
                  } catch (e) {
                    if (mounted) {
                      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
                        content: Text('Failed: $e'),
                        backgroundColor: kDanger,
                      ));
                    }
                  }
                },
                child: const Text('Send Counter-Offer', style: TextStyle(fontWeight: FontWeight.bold)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final b = widget.bid;
    final isFixedPrice = widget.cargo.isFixed;
    final hasDriverCounter = b.isCountered && b.counterBy == 'driver';
    final hasShipperCounter = b.isCountered && b.counterBy == 'shipper';
    final canManageBids = widget.cargo.status.toLowerCase() == 'posted' ||
        widget.cargo.status.toLowerCase() == 'pending' ||
        widget.cargo.status.toLowerCase() == 'open';

    Color statusColor = kAmber;
    if (b.status == 'accepted') statusColor = kGreen;
    if (b.status == 'rejected') statusColor = kDanger;
    if (b.isCountered) statusColor = const Color(0xFF7C3AED);

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: b.status == 'accepted'
              ? kGreen.withValues(alpha: 0.5)
              : hasDriverCounter
                  ? const Color(0xFF7C3AED).withValues(alpha: 0.5)
                  : kBorder,
          width: (b.status == 'accepted' || hasDriverCounter) ? 1.5 : 0.8,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 6,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Driver Name + Price + Status
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              CircleAvatar(
                radius: 20,
                backgroundColor: kGreen.withValues(alpha: 0.1),
                child: Text(
                  (b.driverName != null && b.driverName!.isNotEmpty)
                      ? b.driverName![0].toUpperCase()
                      : 'D',
                  style: GoogleFonts.inter(
                    color: kGreen,
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            b.driverName ?? 'Driver #${b.driverId}',
                            style: GoogleFonts.inter(
                              fontSize: 14,
                              fontWeight: FontWeight.bold,
                              color: kTextPrimary,
                            ),
                          ),
                        ),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: statusColor.withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(12),
                            border: Border.all(color: statusColor.withValues(alpha: 0.3), width: 0.5),
                          ),
                          child: Text(
                            b.status.toUpperCase(),
                            style: GoogleFonts.inter(
                              fontSize: 10,
                              fontWeight: FontWeight.bold,
                              color: statusColor,
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        Text(
                          'ETB ${b.amount.toStringAsFixed(0)}',
                          style: GoogleFonts.inter(
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                            color: hasDriverCounter ? kTextMuted : kTextPrimary,
                            decoration: hasDriverCounter ? TextDecoration.lineThrough : null,
                          ),
                        ),
                        if (b.plateNumber != null) ...[
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1.5),
                            decoration: BoxDecoration(
                              color: Colors.grey.shade100,
                              borderRadius: BorderRadius.circular(4),
                              border: Border.all(color: kBorder),
                            ),
                            child: Text(
                              b.plateNumber!,
                              style: GoogleFonts.inter(fontSize: 11, fontWeight: FontWeight.w600, color: kTextPrimary),
                            ),
                          ),
                        ],
                        if (b.truckType != null) ...[
                          const SizedBox(width: 6),
                          Text(
                            b.truckType!.replaceAll('_', ' ').toUpperCase(),
                            style: GoogleFonts.inter(fontSize: 11, color: kTextMuted),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),

          // Message note
          if (b.note != null && b.note!.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              '"${b.note}"',
              style: GoogleFonts.inter(fontSize: 12, fontStyle: FontStyle.italic, color: kTextMuted),
            ),
          ],

          // Counter-offer state banner
          if (hasDriverCounter && b.counterAmount != null) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: const Color(0xFF7C3AED).withValues(alpha: 0.07),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: const Color(0xFF7C3AED).withValues(alpha: 0.3), width: 0.5),
              ),
              child: Row(
                children: [
                  const Icon(Icons.swap_horiz_rounded, size: 16, color: Color(0xFF7C3AED)),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Driver Counter: ETB ${b.counterAmount!.toStringAsFixed(0)}${b.counterNote != null ? ' · "${b.counterNote}"' : ''}',
                      style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: const Color(0xFF7C3AED)),
                    ),
                  ),
                ],
              ),
            ),
          ],

          if (hasShipperCounter && b.counterAmount != null) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: kAmber.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: kAmber.withValues(alpha: 0.3), width: 0.5),
              ),
              child: Row(
                children: [
                  const Icon(Icons.hourglass_empty_rounded, size: 14, color: kAmber),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Your Counter: ETB ${b.counterAmount!.toStringAsFixed(0)} · Waiting for driver response',
                      style: GoogleFonts.inter(fontSize: 12, color: const Color(0xFF92400E)),
                    ),
                  ),
                ],
              ),
            ),
          ],

          // Accepted driver contact banner
          if (b.status == 'accepted') ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: kGreen.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: kGreen.withValues(alpha: 0.3), width: 0.5),
              ),
              child: Row(
                children: [
                  const Icon(Icons.check_circle_rounded, size: 16, color: kGreen),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Price agreed — Driver: ${b.driverPhone ?? b.driverName ?? 'Contact shared'}',
                      style: GoogleFonts.inter(fontSize: 12, fontWeight: FontWeight.w600, color: kGreen),
                    ),
                  ),
                ],
              ),
            ),
          ],

          // Action Buttons
          if (canManageBids && b.status == 'pending') ...[
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton(
                    onPressed: (_accepting || _rejecting) ? null : _acceptBid,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: kGreen,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                      elevation: 0,
                    ),
                    child: _accepting
                        ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                        : Text('Accept', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600)),
                  ),
                ),
                if (!isFixedPrice) ...[
                  const SizedBox(width: 8),
                  OutlinedButton(
                    onPressed: (_accepting || _rejecting) ? null : _openCounterSheet,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: kAmber,
                      side: const BorderSide(color: kAmber),
                      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    ),
                    child: Text('Counter', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600)),
                  ),
                ],
                const SizedBox(width: 8),
                OutlinedButton(
                  onPressed: (_accepting || _rejecting) ? null : _rejectBid,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: kDanger,
                    side: const BorderSide(color: kDanger),
                    padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                  child: _rejecting
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: kDanger))
                      : Text('Reject', style: GoogleFonts.inter(fontSize: 13)),
                ),
              ],
            ),
          ],

          if (canManageBids && hasDriverCounter) ...[
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton(
                    onPressed: (_countering || _rejecting) ? null : _acceptCounter,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: const Color(0xFF7C3AED),
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                      elevation: 0,
                    ),
                    child: _countering
                        ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                        : Text('Accept Counter', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600)),
                  ),
                ),
                if (!isFixedPrice) ...[
                  const SizedBox(width: 8),
                  OutlinedButton(
                    onPressed: (_countering || _rejecting) ? null : _openCounterSheet,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: kAmber,
                      side: const BorderSide(color: kAmber),
                      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                    ),
                    child: Text('Counter', style: GoogleFonts.inter(fontSize: 13, fontWeight: FontWeight.w600)),
                  ),
                ],
                const SizedBox(width: 8),
                OutlinedButton(
                  onPressed: (_countering || _rejecting) ? null : _rejectBid,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: kDanger,
                    side: const BorderSide(color: kDanger),
                    padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                  child: _rejecting
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: kDanger))
                      : Text('Reject', style: GoogleFonts.inter(fontSize: 13)),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

// ── Shipper: Cancel Cargo Action ──────────────────────────────────────────

class _ShipperCancelCargoAction extends ConsumerStatefulWidget {
  final CargoRequest cargo;
  const _ShipperCancelCargoAction({required this.cargo});

  @override
  ConsumerState<_ShipperCancelCargoAction> createState() => _ShipperCancelCargoActionState();
}

class _ShipperCancelCargoActionState extends ConsumerState<_ShipperCancelCargoAction> {
  bool _deleting = false;

  Future<void> _confirmDelete() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: kSurface,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Text('Cancel this cargo?',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w700, color: kTextPrimary)),
        content: Text(
          'This will permanently remove the cargo listing. '
          'Any drivers who bid will be notified that it was cancelled.',
          style: GoogleFonts.inter(fontSize: 13, color: kTextMuted),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: Text('Keep it',
                style: GoogleFonts.inter(color: kTextMuted)),
          ),
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            child: Text('Yes, cancel',
                style: GoogleFonts.inter(
                    color: kDanger, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) return;

    setState(() => _deleting = true);
    try {
      await ref.read(cargoRepositoryProvider).delete(widget.cargo.id);
      if (!mounted) return;
      ref.invalidate(cargoListProvider);
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Cargo cancelled. Bidders have been notified.'),
        backgroundColor: kGreen,
      ));
      context.go('/shipper');
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Failed to cancel: $e'),
        backgroundColor: kDanger,
      ));
    } finally {
      if (mounted) setState(() => _deleting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (widget.cargo.status.toLowerCase() == 'cancelled' || widget.cargo.status.toLowerCase() == 'completed') {
      return const SizedBox.shrink();
    }
    return SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
        icon: _deleting
            ? const SizedBox(
                width: 16,
                height: 16,
                child: CircularProgressIndicator(strokeWidth: 2, color: kDanger),
              )
            : const Icon(Icons.cancel_outlined, size: 18),
        label: Text('Cancel This Cargo',
            style: GoogleFonts.inter(
                fontSize: 14, fontWeight: FontWeight.w600)),
        style: OutlinedButton.styleFrom(
          foregroundColor: kDanger,
          side: const BorderSide(color: kDanger, width: 0.8),
          padding: const EdgeInsets.symmetric(vertical: 14),
          shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10)),
        ),
        onPressed: _deleting ? null : _confirmDelete,
      ),
    );
  }
}

// ── Place bid bottom sheet ────────────────────────────────────────────────

class _PlaceBidSheet extends ConsumerStatefulWidget {
  final CargoRequest cargo;
  final Bid? existingBid;
  const _PlaceBidSheet({required this.cargo, this.existingBid});

  @override
  ConsumerState<_PlaceBidSheet> createState() => _PlaceBidSheetState();
}

class _PlaceBidSheetState extends ConsumerState<_PlaceBidSheet> {
  late final TextEditingController _amountCtrl;
  late final TextEditingController _noteCtrl;
  bool _submitting = false;

  @override
  void initState() {
    super.initState();
    final isFixed = widget.cargo.isFixed;
    _amountCtrl = TextEditingController(
      text: widget.existingBid != null
          ? widget.existingBid!.amount.toStringAsFixed(0)
          : (isFixed && widget.cargo.budget != null
              ? widget.cargo.budget!.toStringAsFixed(0)
              : ''),
    );
    _noteCtrl = TextEditingController(text: widget.existingBid?.note ?? '');
  }

  @override
  void dispose() {
    _amountCtrl.dispose();
    _noteCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final isFixed = widget.cargo.isFixed;
    final amount = isFixed
        ? (widget.cargo.budget ?? double.tryParse(_amountCtrl.text.trim()))
        : double.tryParse(_amountCtrl.text.trim());
    if (amount == null || amount <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Enter a valid bid amount in ETB.'),
        backgroundColor: kDanger,
      ));
      return;
    }

    setState(() => _submitting = true);
    try {
      if (widget.existingBid != null) {
        await ref.read(bidRepositoryProvider).update(
              widget.existingBid!.id,
              amount: amount,
              note: _noteCtrl.text.trim().isEmpty ? null : _noteCtrl.text.trim(),
            );
        if (mounted) {
          Navigator.of(context).pop();
          ref.invalidate(myBidsProvider);
          ref.invalidate(singleCargoProvider(widget.cargo.id));
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(isFixed ? 'Application updated!' : 'Bid updated!'),
            backgroundColor: kGreen,
          ));
        }
      } else {
        final vehicles = await ref.read(vehicleListProvider.future);
        if (!mounted) return;
        if (vehicles.isEmpty) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('Register a vehicle before placing a bid.'),
            backgroundColor: kDanger,
          ));
          setState(() => _submitting = false);
          return;
        }
        final approved = vehicles.where((v) => (v.approvalStatus ?? '').toUpperCase() == 'APPROVED').toList();
        if (approved.isEmpty && vehicles.any((v) => (v.approvalStatus ?? '').toUpperCase() != 'APPROVED')) {
          ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('Your vehicle is under review. An approved vehicle is required to place bids.'),
            backgroundColor: kAmber,
          ));
          setState(() => _submitting = false);
          return;
        }
        final selectedVehicle = approved.isNotEmpty ? approved.first : vehicles.first;

        await ref.read(bidRepositoryProvider).place(
              cargoId: widget.cargo.id,
              vehicleId: selectedVehicle.id,
              amount: amount,
              note: _noteCtrl.text.trim().isEmpty ? null : _noteCtrl.text.trim(),
            );
        if (mounted) {
          debugPrint('>>> [FreightDetail] _submit placing application for cargo ${widget.cargo.id} amount $amount');
          Navigator.of(context).pop();
          ref.invalidate(myBidsProvider);
          ref.invalidate(singleCargoProvider(widget.cargo.id));
          ref.invalidate(bidsForCargoPageProvider((widget.cargo.id, 0)));
          ref.invalidate(cargoListWithMetaProvider);
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text(isFixed
                ? 'Application submitted! Wait for the shipper to accept.'
                : 'Bid placed! Wait for the shipper to accept.'),
            backgroundColor: kGreen,
          ));
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Failed: $e'),
          backgroundColor: kDanger,
        ));
      }
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  static InputDecoration _inputDeco({
    required String hint,
    String? prefixText,
  }) =>
      InputDecoration(
        hintText: hint,
        prefixText: prefixText,
        prefixStyle: GoogleFonts.inter(color: kAmber, fontWeight: FontWeight.w600),
        hintStyle: GoogleFonts.inter(color: kTextMuted, fontSize: 14),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: kBorder),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: kBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: const BorderSide(color: kGreen, width: 1.5),
        ),
      );

  @override
  Widget build(BuildContext context) {
    final c = widget.cargo;
    final isFixed = c.isFixed;
    final bottom = MediaQuery.of(context).viewInsets.bottom;

    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(20, 20, 20, 20 + bottom),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
          Center(
            child: Container(
              width: 40,
              height: 4,
              decoration: BoxDecoration(
                color: kBorder,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
          ),
          const SizedBox(height: 16),
          Text(widget.existingBid != null
              ? (isFixed ? 'Your Application' : 'Edit Your Bid')
              : (isFixed ? 'Apply at Fixed Price' : 'Place Your Bid'),
              style: GoogleFonts.inter(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                  color: kTextPrimary)),
          const SizedBox(height: 4),
          Text(
            '${c.pickupLocation} → ${c.destination}  ·  ${c.weight.toStringAsFixed(0)} t  ·  ${c.materialType}',
            style: GoogleFonts.inter(fontSize: 13, color: kTextSecond),
          ),
          const SizedBox(height: 20),
          if (isFixed) ...[
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.grey.shade100,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: kBorder),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Fixed Price (Non-negotiable)',
                      style: GoogleFonts.inter(fontSize: 11, color: kTextMuted, fontWeight: FontWeight.w500)),
                  const SizedBox(height: 2),
                  Text(
                    'ETB ${c.budget?.toStringAsFixed(0) ?? _amountCtrl.text}',
                    style: GoogleFonts.inter(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: kTextPrimary,
                    ),
                  ),
                ],
              ),
            ),
          ] else ...[
            Text('Your Price (ETB)',
                style: GoogleFonts.inter(
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                    color: kTextPrimary)),
            const SizedBox(height: 6),
            TextField(
              controller: _amountCtrl,
              keyboardType:
                  const TextInputType.numberWithOptions(decimal: true),
              decoration: _inputDeco(hint: 'e.g. 15,000', prefixText: 'ETB  '),
            ),
          ],
          const SizedBox(height: 12),
          Text(isFixed ? 'Message to Shipper (optional)' : 'Note (optional)',
              style: GoogleFonts.inter(
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  color: kTextPrimary)),
          const SizedBox(height: 6),
          TextField(
            controller: _noteCtrl,
            maxLines: 2,
            decoration: _inputDeco(
                hint: isFixed
                    ? 'e.g. Available immediately, HOWO 15t truck'
                    : 'e.g. Available from tomorrow, HOWO 15t truck'),
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _submitting ? null : _submit,
              style: ElevatedButton.styleFrom(
                backgroundColor: kGreen,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10)),
                elevation: 0,
              ),
              child: _submitting
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : Text(widget.existingBid != null
                      ? (isFixed ? 'Update Application' : 'Update Bid')
                      : (isFixed ? 'Apply at Fixed Price' : 'Submit Bid'),
                      style: GoogleFonts.inter(
                          fontSize: 15, fontWeight: FontWeight.w600)),
            ),
          ),
        ],
      ),
    ),
  ),
);
  }
}

// ── Shared helper widgets ──────────────────────────────────────────────────

class _InfoTile extends StatelessWidget {
  final String label;
  final String value;
  const _InfoTile({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: GoogleFonts.inter(fontSize: 11, color: kTextMuted)),
        const SizedBox(height: 2),
        Text(value,
            style: GoogleFonts.inter(
                fontSize: 13,
                fontWeight: FontWeight.bold,
                color: kTextPrimary)),
      ],
    );
  }
}

class _DetailRow extends StatelessWidget {
  final String label;
  final String value;
  const _DetailRow(this.label, this.value);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
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

class _LocationCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String address;
  final Color color;

  const _LocationCard({
    required this.icon,
    required this.title,
    required this.address,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: kSurface,
        border: Border.all(color: kBorder),
        borderRadius: BorderRadius.circular(10),
      ),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
                color: color.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8)),
            child: Icon(icon, color: color, size: 18),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: GoogleFonts.inter(
                        fontSize: 11,
                        color: kTextMuted,
                        fontWeight: FontWeight.w500)),
                const SizedBox(height: 2),
                Text(address,
                    style: GoogleFonts.inter(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: kTextPrimary),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
