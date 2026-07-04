import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';
import '../shared/widgets/shared_widgets.dart';

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
    final user = ref.read(authNotifierProvider).user;

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
                  const SizedBox(height: 10),
                  _LocationCard(
                      icon: Icons.location_on_rounded,
                      title: 'Destination',
                      address: cargo.destination,
                      color: kGreen),

                  // ── Role-based bid action ──────────────────────────
                  if (cargo.status == 'pending') ...[
                    const SizedBox(height: 24),
                    if (user?.isDriver == true)
                      _DriverBidAction(cargo: cargo)
                    else if (user?.isShipper == true)
                      _ShipperBidLink(cargo: cargo),
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
    if (cargo.priceType == 'fixed') {
      return _FixedPriceDriverAction(cargo: cargo);
    }

    // Negotiable: show existing bid or Place Bid button
    final myBids = ref.watch(myBidsProvider).valueOrNull;
    Bid? existingBid;
    if (myBids != null) {
      final matches = myBids.where(
        (b) => b.cargoRequestId == cargo.id &&
               (b.status == 'pending' || b.status == 'countered'),
      );
      existingBid = matches.isEmpty ? null : matches.first;
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
        if (existingBid != null)
          Text(
            'Your bid: ETB ${existingBid.amount.toStringAsFixed(0)} — ${existingBid.status}',
            style: GoogleFonts.inter(
                fontSize: 12, color: kAmber, fontWeight: FontWeight.w600),
          )
        else
          Text(
            cargo.budget != null
                ? 'Shipper budget: ETB ${cargo.budget!.toStringAsFixed(0)}'
                : 'Budget not specified — propose your own price.',
            style: GoogleFonts.inter(fontSize: 12, color: kTextMuted),
          ),
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: existingBid != null
              ? OutlinedButton.icon(
                  icon: const Icon(Icons.edit_outlined, size: 18),
                  label: Text('Edit Bid',
                      style: GoogleFonts.inter(
                          fontSize: 14, fontWeight: FontWeight.w600)),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: kAmber,
                    side: const BorderSide(color: kAmber),
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10)),
                  ),
                  onPressed: () => showModalBottomSheet(
                    context: context,
                    isScrollControlled: true,
                    shape: const RoundedRectangleBorder(
                        borderRadius:
                            BorderRadius.vertical(top: Radius.circular(20))),
                    builder: (_) =>
                        _PlaceBidSheet(cargo: cargo, existingBid: existingBid),
                  ),
                )
              : ElevatedButton.icon(
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

// ── Driver: Accept / Reject fixed-price cargo ─────────────────────────────

class _FixedPriceDriverAction extends ConsumerStatefulWidget {
  final CargoRequest cargo;
  const _FixedPriceDriverAction({required this.cargo});

  @override
  ConsumerState<_FixedPriceDriverAction> createState() =>
      _FixedPriceDriverActionState();
}

class _FixedPriceDriverActionState
    extends ConsumerState<_FixedPriceDriverAction> {
  bool _accepting = false;

  Future<void> _accept() async {
    setState(() => _accepting = true);
    try {
      await ref.read(cargoRepositoryProvider).acceptFixedPrice(widget.cargo.id);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text(
            'Offer accepted! The shipper will review and confirm a driver.',
          ),
          backgroundColor: kGreen,
        ));
        context.pop();
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

  @override
  Widget build(BuildContext context) {
    final c = widget.cargo;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Fixed Price Cargo',
            style: GoogleFonts.inter(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: kTextPrimary)),
        const SizedBox(height: 6),
        Text(
          c.budget != null
              ? 'Accept this job at ETB ${c.budget!.toStringAsFixed(0)}. '
                'The shipper will select a driver from all who accept.'
              : 'Accept this job. The shipper will select a driver from all who accept.',
          style: GoogleFonts.inter(fontSize: 12, color: kTextMuted),
        ),
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            icon: _accepting
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(
                        strokeWidth: 2, color: Colors.white))
                : const Icon(Icons.check_circle_outline_rounded, size: 18),
            label: Text(
              c.budget != null
                  ? 'Accept — ETB ${c.budget!.toStringAsFixed(0)}'
                  : 'Accept Fixed Price',
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
            onPressed: _accepting ? null : _accept,
          ),
        ),
        const SizedBox(height: 8),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            icon: const Icon(Icons.close_rounded, size: 18),
            label: Text('Not Interested',
                style: GoogleFonts.inter(
                    fontSize: 14, fontWeight: FontWeight.w600)),
            style: OutlinedButton.styleFrom(
              foregroundColor: kDanger,
              side: const BorderSide(color: kDanger, width: 0.5),
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
            ),
            onPressed: () => context.pop(),
          ),
        ),
      ],
    );
  }
}

// ── Shipper: View Bids + Cancel Cargo ────────────────────────────────────

class _ShipperBidLink extends ConsumerStatefulWidget {
  final CargoRequest cargo;
  const _ShipperBidLink({required this.cargo});

  @override
  ConsumerState<_ShipperBidLink> createState() => _ShipperBidLinkState();
}

class _ShipperBidLinkState extends ConsumerState<_ShipperBidLink> {
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
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Incoming Bids',
            style: GoogleFonts.inter(
                fontSize: 14,
                fontWeight: FontWeight.bold,
                color: kTextPrimary)),
        const SizedBox(height: 6),
        Text(
          'Drivers can bid on your cargo. Review and accept the best offer.',
          style: GoogleFonts.inter(fontSize: 12, color: kTextMuted),
        ),
        const SizedBox(height: 12),
        SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            icon: const Icon(Icons.gavel_rounded, size: 18),
            label: Text('View Driver Bids',
                style: GoogleFonts.inter(
                    fontSize: 14, fontWeight: FontWeight.w600)),
            style: ElevatedButton.styleFrom(
              backgroundColor: kGreen,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(vertical: 14),
              shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10)),
              elevation: 0,
            ),
            onPressed: () => context.go('/cargo-bids/${widget.cargo.id}'),
          ),
        ),
        const SizedBox(height: 10),
        SizedBox(
          width: double.infinity,
          child: OutlinedButton.icon(
            icon: _deleting
                ? const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(
                        strokeWidth: 2, color: kDanger),
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
        ),
      ],
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
    _amountCtrl = TextEditingController(
      text: widget.existingBid != null
          ? widget.existingBid!.amount.toStringAsFixed(0)
          : '',
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
    final amount = double.tryParse(_amountCtrl.text.trim());
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
        await ref.read(bidRepositoryProvider).place(
              cargoId: widget.cargo.id,
              vehicleId: vehicles.first.id,
              amount: amount,
              note: _noteCtrl.text.trim().isEmpty ? null : _noteCtrl.text.trim(),
            );
      }
      if (mounted) {
        Navigator.of(context).pop();
        ref.invalidate(myBidsProvider);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text(widget.existingBid != null
              ? 'Bid updated!'
              : 'Bid placed! Wait for the shipper to accept.'),
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
    final bottom = MediaQuery.of(context).viewInsets.bottom;

    return Padding(
      padding: EdgeInsets.fromLTRB(20, 20, 20, 20 + bottom),
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
          Text(widget.existingBid != null ? 'Edit Your Bid' : 'Place Your Bid',
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
          const SizedBox(height: 12),
          Text('Note (optional)',
              style: GoogleFonts.inter(
                  fontSize: 13,
                  fontWeight: FontWeight.w500,
                  color: kTextPrimary)),
          const SizedBox(height: 6),
          TextField(
            controller: _noteCtrl,
            maxLines: 2,
            decoration: _inputDeco(
                hint: 'e.g. Available from tomorrow, HOWO 15t truck'),
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
                  : Text(widget.existingBid != null ? 'Update Bid' : 'Submit Bid',
                      style: GoogleFonts.inter(
                          fontSize: 15, fontWeight: FontWeight.w600)),
            ),
          ),
        ],
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
