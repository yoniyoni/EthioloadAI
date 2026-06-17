import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:easy_localization/easy_localization.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../data/models/models.dart';
import '../../data/providers/data_providers.dart';
import '../../data/repositories/repositories.dart';
import '../shared/widgets/shared_widgets.dart';

const _purple = Color(0xFF7C3AED);
const _fleetBlue = Color(0xFF1E40AF);

class ShipperBidSelectionScreen extends ConsumerWidget {
  final int cargoId;
  const ShipperBidSelectionScreen({super.key, required this.cargoId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cargoAsync = ref.watch(singleCargoProvider(cargoId));
    final bidsAsync = ref.watch(bidsForCargoProvider(cargoId));

    return Scaffold(
      backgroundColor: kBackground,
      appBar: EthioAppBar(
        title: 'bid.driver_bids_title'.tr(),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh, color: Colors.white),
            tooltip: 'common.refresh'.tr(),
            onPressed: () => ref.invalidate(bidsForCargoProvider(cargoId)),
          ),
        ],
      ),
      body: RefreshIndicator(
        color: kAmber,
        onRefresh: () async {
          ref.invalidate(singleCargoProvider(cargoId));
          ref.invalidate(bidsForCargoProvider(cargoId));
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // ── Cargo header ──────────────────────────────────────────
            cargoAsync.when(
              data: (cargo) => _CargoHeaderCard(cargo: cargo),
              loading: () => const ShimmerBox(height: 100, radius: 12),
              error: (_, __) => const SizedBox.shrink(),
            ),
            const SizedBox(height: 16),

            // ── Bid count label ───────────────────────────────────────
            bidsAsync.when(
              data: (bids) {
                final pending = bids.where((b) => b.status == 'pending').toList();
                return Text(
                  'bid.pending_count'.tr(namedArgs: {'count': '${pending.length}'}),
                  style: GoogleFonts.inter(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: kTextPrimary,
                  ),
                );
              },
              loading: () => const SizedBox.shrink(),
              error: (_, __) => const SizedBox.shrink(),
            ),
            const SizedBox(height: 10),

            // ── Bid list ──────────────────────────────────────────────
            bidsAsync.when(
              data: (bids) {
                final pending = bids.where((b) => b.status == 'pending').toList();
                if (pending.isEmpty) {
                  return EmptyState(
                    icon: Icons.gavel_outlined,
                    title: 'bid.no_bids'.tr(),
                    subtitle: 'bid.no_bids_sub'.tr(),
                  );
                }
                return Column(
                  children: pending
                      .map((bid) => _BidCard(
                            bid: bid,
                            cargoId: cargoId,
                            onAccepted: () {
                              ref.invalidate(bidsForCargoProvider(cargoId));
                              ref.invalidate(bookingListProvider);
                              ref.invalidate(cargoListProvider);
                              context.go('/my-bookings');
                            },
                            onRejected: () =>
                                ref.invalidate(bidsForCargoProvider(cargoId)),
                          ))
                      .toList(),
                );
              },
              loading: () => Column(
                children: List.generate(
                    3, (_) => const ShimmerBox(height: 140, radius: 12)),
              ),
              error: (e, _) => _ErrorState(
                message: e.toString(),
                onRetry: () => ref.invalidate(bidsForCargoProvider(cargoId)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Cargo header card ─────────────────────────────────────────────────────

class _CargoHeaderCard extends StatelessWidget {
  final CargoRequest cargo;
  const _CargoHeaderCard({required this.cargo});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: kSurface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: kBorder, width: 0.5),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: kGreen.withValues(alpha: 0.07),
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.inventory_2_outlined, color: kGreen, size: 22),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('${cargo.pickupLocation} → ${cargo.destination}',
                    style: GoogleFonts.inter(
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                        color: kTextPrimary)),
                const SizedBox(height: 2),
                Text(
                    '${cargo.materialType}  ·  ${cargo.weight.toStringAsFixed(0)} t',
                    style: GoogleFonts.inter(fontSize: 12, color: kTextSecond)),
              ],
            ),
          ),
          _UrgencyBadge(level: cargo.urgencyLevel),
        ]),
        if (cargo.budget != null) ...[
          const SizedBox(height: 10),
          const Divider(height: 1, color: kBorder),
          const SizedBox(height: 10),
          Text('${'bid.shipper_budget'.tr()} ETB ${_fmt(cargo.budget!)}',
              style: GoogleFonts.inter(
                  fontSize: 12,
                  color: kAmber,
                  fontWeight: FontWeight.w600)),
        ],
      ]),
    );
  }
}

// ── Bid card ──────────────────────────────────────────────────────────────

class _BidCard extends ConsumerStatefulWidget {
  final Bid bid;
  final int cargoId;
  final VoidCallback onAccepted;
  final VoidCallback onRejected;

  const _BidCard({
    required this.bid,
    required this.cargoId,
    required this.onAccepted,
    required this.onRejected,
  });

  @override
  ConsumerState<_BidCard> createState() => _BidCardState();
}

class _BidCardState extends ConsumerState<_BidCard> {
  bool _accepting = false;
  bool _rejecting = false;
  bool _acceptingCounter = false;

  Future<void> _accept() async {
    setState(() => _accepting = true);
    try {
      await ref.read(bidRepositoryProvider).acceptBid(widget.bid.id);
      if (mounted) {
        await _showDriverContactDialog();
        widget.onAccepted();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
              'common.error_prefix'.tr(namedArgs: {'msg': e.toString()})),
          backgroundColor: kDanger,
        ));
      }
    } finally {
      if (mounted) setState(() => _accepting = false);
    }
  }

  Future<void> _showDriverContactDialog() async {
    final b = widget.bid;
    await showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: Row(children: [
          const Icon(Icons.check_circle_rounded, color: kGreen, size: 22),
          const SizedBox(width: 8),
          Text('bid.accepted_title'.tr(),
              style: GoogleFonts.inter(
                  color: kGreen,
                  fontSize: 17,
                  fontWeight: FontWeight.w600)),
        ]),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'bid.accepted_subtitle'.tr(),
              style: GoogleFonts.inter(fontSize: 13, color: kTextSecond),
            ),
            const SizedBox(height: 14),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: kBackground,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: kBorder),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (b.driverName != null) ...[
                    Row(children: [
                      const Icon(Icons.person_outline,
                          size: 16, color: kTextSecond),
                      const SizedBox(width: 8),
                      Text(b.driverName!,
                          style: GoogleFonts.inter(
                              fontWeight: FontWeight.w600,
                              fontSize: 14,
                              color: kTextPrimary)),
                    ]),
                    const SizedBox(height: 8),
                  ],
                  if (b.driverPhone != null)
                    Row(children: [
                      const Icon(Icons.phone_outlined, size: 16, color: kGreen),
                      const SizedBox(width: 8),
                      Text(b.driverPhone!,
                          style: GoogleFonts.inter(
                              fontSize: 16,
                              fontWeight: FontWeight.w700,
                              color: kGreen)),
                    ])
                  else
                    Text('bid.driver_phone_na'.tr(),
                        style:
                            GoogleFonts.inter(color: kTextSecond, fontSize: 13)),
                ],
              ),
            ),
          ],
        ),
        actions: [
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx),
            style: ElevatedButton.styleFrom(
              backgroundColor: kGreen,
              foregroundColor: Colors.white,
              elevation: 0,
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8)),
            ),
            child: Text('bid.go_to_bookings'.tr()),
          ),
        ],
      ),
    );
  }

  Future<void> _reject() async {
    setState(() => _rejecting = true);
    try {
      await ref.read(bidRepositoryProvider).rejectBid(widget.bid.id);
      if (mounted) widget.onRejected();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
              'common.error_prefix'.tr(namedArgs: {'msg': e.toString()})),
          backgroundColor: kDanger,
        ));
      }
    } finally {
      if (mounted) setState(() => _rejecting = false);
    }
  }

  Future<void> _acceptDriverCounter() async {
    setState(() => _acceptingCounter = true);
    try {
      await ref.read(bidRepositoryProvider).acceptCounter(widget.bid.id);
      if (mounted) {
        await _showDriverContactDialog();
        widget.onAccepted();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(
              'common.error_prefix'.tr(namedArgs: {'msg': e.toString()})),
          backgroundColor: kDanger,
        ));
      }
    } finally {
      if (mounted) setState(() => _acceptingCounter = false);
    }
  }

  void _openCounterDialog() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _CounterOfferSheet(
        bid: widget.bid,
        onSent: () {
          ref.invalidate(bidsForCargoProvider(widget.cargoId));
        },
      ),
    );
  }

  bool get _busy => _accepting || _rejecting || _acceptingCounter;

  @override
  Widget build(BuildContext context) {
    final b = widget.bid;
    final hasDriverCounter = b.needsShipperAction;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: kSurface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: hasDriverCounter
              ? _purple.withValues(alpha: 0.47)
              : b.isRecommended
                  ? kAmber.withValues(alpha: 0.47)
                  : kBorder,
          width: (hasDriverCounter || b.isRecommended) ? 1.5 : 0.5,
        ),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        // ── Driver row ─────────────────────────────────────────────
        Row(children: [
          CircleAvatar(
            radius: 22,
            backgroundColor: kGreen.withValues(alpha: 0.07),
            child: Text(
              _initials(b.driverName ?? 'D'),
              style: GoogleFonts.inter(
                  color: kGreen,
                  fontWeight: FontWeight.bold,
                  fontSize: 15),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(children: [
                  Text(b.driverName ?? 'Driver #${b.driverId}',
                      style: GoogleFonts.inter(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                          color: kTextPrimary)),
                  if (b.isRecommended) ...[
                    const SizedBox(width: 8),
                    _BestPriceBadge(),
                  ],
                ]),
                const SizedBox(height: 2),
                Row(children: [
                  if (b.driverRating != null) ...[
                    const Icon(Icons.star_rounded, size: 13, color: kAmber),
                    const SizedBox(width: 2),
                    Text(b.driverRating!.toStringAsFixed(1),
                        style: GoogleFonts.inter(
                            fontSize: 12, color: kTextSecond)),
                    const SizedBox(width: 8),
                  ],
                  Text(
                    'bid.trips'.tr(
                        namedArgs: {'count': '${b.driverTripCount ?? 0}'}),
                    style: GoogleFonts.inter(fontSize: 12, color: kTextSecond),
                  ),
                ]),
              ],
            ),
          ),
          Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Text(
              hasDriverCounter
                  ? 'bid.original'.tr()
                  : 'bid.bid_label'.tr(),
              style: GoogleFonts.inter(fontSize: 11, color: kTextSecond),
            ),
            Text('ETB ${_fmt(b.amount)}',
                style: GoogleFonts.inter(
                    fontSize: 17,
                    fontWeight: FontWeight.w600,
                    color: hasDriverCounter ? kTextSecond : kAmber,
                    decoration: hasDriverCounter
                        ? TextDecoration.lineThrough
                        : null)),
          ]),
        ]),

        // ── Driver's counter-offer banner ──────────────────────────
        if (hasDriverCounter && b.counterAmount != null) ...[
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: _purple.withValues(alpha: 0.04),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(
                  color: _purple.withValues(alpha: 0.24), width: 0.5),
            ),
            child: Row(children: [
              const Icon(Icons.swap_horiz_rounded, size: 16, color: _purple),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('bid.driver_counter_offer'.tr(),
                          style: GoogleFonts.inter(
                              fontSize: 11,
                              color: _purple,
                              fontWeight: FontWeight.w600)),
                      const SizedBox(height: 2),
                      Text('ETB ${_fmt(b.counterAmount!)}',
                          style: GoogleFonts.inter(
                              fontSize: 15,
                              fontWeight: FontWeight.w700,
                              color: _purple)),
                      if (b.counterNote != null && b.counterNote!.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.only(top: 3),
                          child: Text('"${b.counterNote}"',
                              style: GoogleFonts.inter(
                                  fontSize: 11,
                                  fontStyle: FontStyle.italic,
                                  color: kTextSecond)),
                        ),
                    ]),
              ),
            ]),
          ),
        ],

        // ── Your counter sent — waiting ────────────────────────────
        if (b.isCountered && b.counterBy == 'shipper') ...[
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: kAmber.withValues(alpha: 0.06),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(children: [
              const Icon(Icons.hourglass_empty_rounded, size: 14, color: kAmber),
              const SizedBox(width: 8),
              Text(
                'bid.your_counter_waiting'
                    .tr(namedArgs: {'amount': _fmt(b.counterAmount!)}),
                style: GoogleFonts.inter(fontSize: 12, color: kAmber),
              ),
            ]),
          ),
        ],

        // ── Vehicle info ───────────────────────────────────────────
        if (b.truckType != null || b.plateNumber != null) ...[
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
            decoration: BoxDecoration(
                color: kBackground, borderRadius: BorderRadius.circular(8)),
            child: Row(children: [
              const Icon(Icons.local_shipping_outlined,
                  size: 15, color: kTextSecond),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  [
                    if (b.truckType != null) b.truckType!,
                    if (b.plateNumber != null) b.plateNumber!,
                    if (b.vehicleCapacity != null)
                      '${b.vehicleCapacity!.toStringAsFixed(0)} t',
                  ].join('  ·  '),
                  style: GoogleFonts.inter(fontSize: 12, color: kTextSecond),
                ),
              ),
              if (b.distanceKm != null) ...[
                const SizedBox(width: 8),
                Row(children: [
                  const Icon(Icons.near_me_outlined,
                      size: 13, color: kTextSecond),
                  const SizedBox(width: 2),
                  Text('${b.distanceKm!.toStringAsFixed(0)} km',
                      style:
                          GoogleFonts.inter(fontSize: 11, color: kTextSecond)),
                ]),
              ],
            ]),
          ),
        ],

        // ── Bidder type badge ──────────────────────────────────────
        if (b.bidderType == 'fleet_owner') ...[
          const SizedBox(height: 6),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
            decoration: BoxDecoration(
              color: _fleetBlue.withValues(alpha: 0.05),
              borderRadius: BorderRadius.circular(6),
              border: Border.all(
                  color: _fleetBlue.withValues(alpha: 0.20), width: 0.5),
            ),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              const Icon(Icons.business_outlined,
                  size: 12, color: _fleetBlue),
              const SizedBox(width: 4),
              Text('common.fleet_owner'.tr(),
                  style: GoogleFonts.inter(
                      fontSize: 11,
                      color: _fleetBlue,
                      fontWeight: FontWeight.w600)),
            ]),
          ),
        ],

        if (b.note != null && b.note!.isNotEmpty) ...[
          const SizedBox(height: 8),
          Text('"${b.note}"',
              style: GoogleFonts.inter(
                  fontSize: 12,
                  fontStyle: FontStyle.italic,
                  color: kTextSecond)),
        ],

        const SizedBox(height: 14),
        const Divider(height: 1, color: kBorder),
        const SizedBox(height: 12),

        // ── Actions ───────────────────────────────────────────────
        if (hasDriverCounter) ...[
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _busy ? null : _acceptDriverCounter,
              style: ElevatedButton.styleFrom(
                backgroundColor: _purple,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8)),
                elevation: 0,
              ),
              child: _acceptingCounter
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : Text(
                      'bid.accept_amount'
                          .tr(namedArgs: {'amount': _fmt(b.counterAmount!)}),
                      style: GoogleFonts.inter(
                          fontSize: 13, fontWeight: FontWeight.w600)),
            ),
          ),
          const SizedBox(height: 8),
          Row(children: [
            Expanded(
              child: OutlinedButton(
                onPressed: _busy ? null : _openCounterDialog,
                style: OutlinedButton.styleFrom(
                  foregroundColor: kAmber,
                  side: const BorderSide(color: kAmber, width: 0.5),
                  padding: const EdgeInsets.symmetric(vertical: 11),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8)),
                ),
                child: Text('bid.counter_again'.tr(),
                    style: GoogleFonts.inter(
                        fontSize: 12, fontWeight: FontWeight.w600)),
              ),
            ),
            const SizedBox(width: 8),
            OutlinedButton(
              onPressed: _busy ? null : _reject,
              style: OutlinedButton.styleFrom(
                foregroundColor: kDanger,
                side: const BorderSide(color: kDanger, width: 0.5),
                padding: const EdgeInsets.symmetric(
                    vertical: 11, horizontal: 18),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8)),
              ),
              child: _rejecting
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: kDanger))
                  : Text('bid.reject'.tr(),
                      style: GoogleFonts.inter(fontSize: 12)),
            ),
          ]),
        ] else ...[
          Row(children: [
            Expanded(
              child: ElevatedButton(
                onPressed: _busy ? null : _accept,
                style: ElevatedButton.styleFrom(
                  backgroundColor: kGreen,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8)),
                  elevation: 0,
                ),
                child: _accepting
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white))
                    : Text('bid.accept'.tr(),
                        style: GoogleFonts.inter(
                            fontSize: 13, fontWeight: FontWeight.w600)),
              ),
            ),
            const SizedBox(width: 8),
            OutlinedButton(
              onPressed: _busy ? null : _openCounterDialog,
              style: OutlinedButton.styleFrom(
                foregroundColor: kAmber,
                side: const BorderSide(color: kAmber, width: 0.5),
                padding: const EdgeInsets.symmetric(
                    vertical: 12, horizontal: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8)),
              ),
              child: Text('bid.counter'.tr(),
                  style: GoogleFonts.inter(
                      fontSize: 13, fontWeight: FontWeight.w600)),
            ),
            const SizedBox(width: 8),
            OutlinedButton(
              onPressed: _busy ? null : _reject,
              style: OutlinedButton.styleFrom(
                foregroundColor: kDanger,
                side: const BorderSide(color: kDanger, width: 0.5),
                padding: const EdgeInsets.symmetric(
                    vertical: 12, horizontal: 14),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8)),
              ),
              child: _rejecting
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: kDanger))
                  : Text('bid.reject'.tr(),
                      style: GoogleFonts.inter(fontSize: 13)),
            ),
          ]),
        ],
      ]),
    );
  }
}

// ── Counter-offer bottom sheet ────────────────────────────────────────────

class _CounterOfferSheet extends ConsumerStatefulWidget {
  final Bid bid;
  final VoidCallback onSent;
  const _CounterOfferSheet({required this.bid, required this.onSent});

  @override
  ConsumerState<_CounterOfferSheet> createState() =>
      _CounterOfferSheetState();
}

class _CounterOfferSheetState extends ConsumerState<_CounterOfferSheet> {
  final _amountCtrl = TextEditingController();
  final _noteCtrl = TextEditingController();
  bool _sending = false;

  @override
  void dispose() {
    _amountCtrl.dispose();
    _noteCtrl.dispose();
    super.dispose();
  }

  static InputDecoration _inputDeco(String label,
          {String? hint, Widget? prefixIcon}) =>
      InputDecoration(
        labelText: label,
        hintText: hint,
        prefixIcon: prefixIcon,
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
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

  Future<void> _send() async {
    final amountStr = _amountCtrl.text.trim();
    final amount = double.tryParse(amountStr);
    if (amount == null || amount <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('common.error'.tr()),
        backgroundColor: kDanger,
      ));
      return;
    }
    setState(() => _sending = true);
    try {
      await ref.read(bidRepositoryProvider).counterBid(
            widget.bid.id,
            counterAmount: amount,
            counterNote: _noteCtrl.text.trim().isEmpty
                ? null
                : _noteCtrl.text.trim(),
          );
      if (mounted) {
        Navigator.pop(context);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('common.success'.tr()),
          backgroundColor: kGreen,
        ));
        widget.onSent();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('common.error_prefix'
              .tr(namedArgs: {'msg': e.toString()})),
          backgroundColor: kDanger,
        ));
      }
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final b = widget.bid;
    return Padding(
      padding:
          EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: Container(
        decoration: const BoxDecoration(
          color: kSurface,
          borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        ),
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
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
            Text('bid.counter_title'.tr(),
                style: GoogleFonts.inter(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: kTextPrimary)),
            const SizedBox(height: 4),
            Text(
              'Driver bid: ETB ${_fmt(b.amount)}'
              '${b.counterAmount != null ? '  ·  Their counter: ETB ${_fmt(b.counterAmount!)}' : ''}',
              style: GoogleFonts.inter(fontSize: 12, color: kTextSecond),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _amountCtrl,
              keyboardType:
                  const TextInputType.numberWithOptions(decimal: true),
              decoration: _inputDeco(
                'bid.counter_amount_label'.tr(),
                prefixIcon: const Icon(Icons.payments_outlined),
              ),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _noteCtrl,
              maxLines: 2,
              decoration: _inputDeco(
                'bid.note_label'.tr(),
                hint: 'bid.note_hint'.tr(),
              ),
            ),
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: _sending ? null : _send,
                style: ElevatedButton.styleFrom(
                  backgroundColor: kAmber,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(10)),
                  elevation: 0,
                ),
                child: _sending
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white))
                    : Text('bid.send_counter'.tr(),
                        style: GoogleFonts.inter(
                            fontSize: 14, fontWeight: FontWeight.w700)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// ── Subwidgets ────────────────────────────────────────────────────────────

class _BestPriceBadge extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
      decoration: BoxDecoration(
        color: kGreenTint,
        borderRadius: BorderRadius.circular(20),
        border:
            Border.all(color: kGreen.withValues(alpha: 0.24), width: 0.5),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        const Icon(Icons.check_circle_rounded, size: 11, color: kGreen),
        const SizedBox(width: 3),
        Text('bid.best_price'.tr(),
            style: GoogleFonts.inter(
                fontSize: 10,
                color: kGreen,
                fontWeight: FontWeight.w700)),
      ]),
    );
  }
}

class _UrgencyBadge extends StatelessWidget {
  final String level;
  const _UrgencyBadge({required this.level});

  @override
  Widget build(BuildContext context) {
    final Color color;
    switch (level.toLowerCase()) {
      case 'express':
        color = kDanger;
        break;
      case 'high':
        color = kAmber;
        break;
      default:
        color = kGreen;
    }
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(level,
          style: GoogleFonts.inter(
              fontSize: 10, color: color, fontWeight: FontWeight.w600)),
    );
  }
}

class _ErrorState extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;
  const _ErrorState({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(children: [
        const SizedBox(height: 32),
        const Icon(Icons.error_outline, size: 40, color: kDanger),
        const SizedBox(height: 8),
        Text(message,
            textAlign: TextAlign.center,
            style: GoogleFonts.inter(color: kTextSecond)),
        const SizedBox(height: 12),
        OutlinedButton.icon(
          onPressed: onRetry,
          icon: const Icon(Icons.refresh, size: 16),
          label: Text('common.retry'.tr()),
          style: OutlinedButton.styleFrom(foregroundColor: kGreen),
        ),
      ]),
    );
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────

String _initials(String name) {
  final parts = name.trim().split(' ');
  if (parts.length >= 2) return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
  return name.isNotEmpty ? name[0].toUpperCase() : 'D';
}

String _fmt(double v) => v
    .toStringAsFixed(0)
    .replaceAllMapped(
        RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]},');
