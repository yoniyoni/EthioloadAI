import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:easy_localization/easy_localization.dart';
import 'package:google_fonts/google_fonts.dart';
import '../../data/providers/data_providers.dart';
import '../../data/repositories/repositories.dart';
import '../shared/widgets/shared_widgets.dart';

class CreateFreightScreen extends ConsumerStatefulWidget {
  const CreateFreightScreen({super.key});

  @override
  ConsumerState<CreateFreightScreen> createState() =>
      _CreateFreightScreenState();
}

class _CreateFreightScreenState extends ConsumerState<CreateFreightScreen> {
  final _pageController = PageController();
  int _step = 0;

  String? pickupLocation;
  String? deliveryLocation;
  String? cargoType;
  String? weight;
  String? budget;
  String? deadline;
  String? description;
  String priceType = 'negotiable';

  // AI price prediction state
  int? _priceMin;
  int? _priceMax;
  int? _priceDistKm;
  bool _priceLoading = false;
  String? _priceError;

  static const _cargoTypes = [
    'agricultural',
    'construction',
    'electronics',
    'fragile',
    'fuel',
    'general',
    'livestock',
    'machinery',
    'medical',
    'perishable',
    'textiles',
    'other',
  ];

  static const _cities = [
    'Addis Ababa',
    'Adama / Nazret',
    'Arba Minch',
    'Asella',
    'Assosa',
    'Axum',
    'Adigrat',
    'Bale Robe',
    'Bahir Dar',
    'Bishoftu',
    'Debre Birhan',
    'Debre Markos',
    'Dessie',
    'Dilla',
    'Dire Dawa',
    'Gambela',
    'Goba',
    'Gondar',
    'Harar',
    'Hawassa',
    'Jijiga',
    'Jimma',
    'Kebri Dahar',
    'Mekele',
    'Moyale',
    'Nekemte',
    'Shire / Endaselassie',
    'Shashemene',
    'Sodo / Wolaita',
    'Woldia',
    'Other',
  ];

  static const _steps = ['Locations', 'Cargo', 'Budget', 'Review'];

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _next() {
    if (_step < 3) {
      // When leaving the cargo-details step, kick off the AI price estimate
      if (_step == 1) _fetchPricePrediction();
      _pageController.nextPage(
          duration: const Duration(milliseconds: 250), curve: Curves.easeInOut);
    } else {
      _submit();
    }
  }

  void _back() {
    if (_step > 0) {
      _pageController.previousPage(
          duration: const Duration(milliseconds: 250), curve: Curves.easeInOut);
    }
  }

  Future<void> _fetchPricePrediction() async {
    if (pickupLocation == null || deliveryLocation == null) return;
    setState(() {
      _priceLoading = true;
      _priceError = null;
    });
    try {
      final result = await ref.read(cargoRepositoryProvider).predictPrice(
            pickup: pickupLocation!,
            destination: deliveryLocation!,
            weight: double.tryParse(weight ?? '') ?? 10,
            urgencyLevel: 'normal',
            materialType: cargoType ?? 'general',
          );
      if (mounted) {
        if (result.min != null && result.max != null) {
          setState(() {
            _priceMin = result.min;
            _priceMax = result.max;
            _priceDistKm = result.distanceKm;
            _priceLoading = false;
          });
        } else {
          setState(() {
            _priceLoading = false;
            _priceError = 'AI price service unavailable. Enter your budget manually.';
          });
        }
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _priceLoading = false;
          _priceError = 'Could not fetch price estimate';
        });
      }
    }
  }

  Future<void> _submit() async {
    if (pickupLocation == null ||
        deliveryLocation == null ||
        cargoType == null ||
        weight == null ||
        budget == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
            content: Text('Please fill in all required fields',
                style: GoogleFonts.inter()),
            backgroundColor: kDanger),
      );
      return;
    }

    try {
      if (!mounted) return;
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (_) => Center(
            child: EthioCard(
                child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: CircularProgressIndicator(color: kGreen)))),
      );

      await ref.read(cargoRepositoryProvider).create(
            pickupLocation: pickupLocation!,
            destination: deliveryLocation!,
            materialType: cargoType!,
            weight: double.tryParse(weight!) ?? 0,
            urgencyLevel: 'normal',
            budget: double.tryParse(budget ?? ''),
            priceType: priceType,
          );

      if (mounted) Navigator.pop(context);

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text('Cargo request created!',
                  style: GoogleFonts.inter()),
              backgroundColor: kSuccess),
        );
        await Future.delayed(const Duration(milliseconds: 800));
        if (mounted) context.go('/freight');
      }
    } catch (e) {
      if (mounted) Navigator.pop(context);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text('Error: $e', style: GoogleFonts.inter()),
              backgroundColor: kDanger),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: kBackground,
      appBar: EthioAppBar(title: 'shipper.post_cargo'.tr()),
      body: Column(
        children: [
          _StepIndicator(current: _step, steps: _steps),
          Divider(height: 1, color: kBorder),

          Expanded(
            child: PageView(
              controller: _pageController,
              physics: const NeverScrollableScrollPhysics(),
              onPageChanged: (i) => setState(() => _step = i),
              children: [
                _Step1(
                  pickupLocation: pickupLocation,
                  deliveryLocation: deliveryLocation,
                  cities: _cities,
                  onPickup: (v) => setState(() => pickupLocation = v),
                  onDelivery: (v) => setState(() => deliveryLocation = v),
                ),
                _Step2(
                  cargoType: cargoType,
                  weight: weight,
                  description: description,
                  cargoTypes: _cargoTypes,
                  onType: (v) => setState(() => cargoType = v),
                  onWeight: (v) => setState(() => weight = v),
                  onDesc: (v) => setState(() => description = v),
                ),
                _Step3(
                  budget: budget,
                  deadline: deadline,
                  priceType: priceType,
                  priceMin: _priceMin,
                  priceMax: _priceMax,
                  priceDistKm: _priceDistKm,
                  priceLoading: _priceLoading,
                  priceError: _priceError,
                  onBudget: (v) => setState(() => budget = v),
                  onDeadline: (v) => setState(() => deadline = v),
                  onPriceType: (v) => setState(() => priceType = v),
                ),
                _Step4(
                  pickup: pickupLocation,
                  delivery: deliveryLocation,
                  cargo: cargoType,
                  weight: weight,
                  budget: budget,
                  deadline: deadline,
                  description: description,
                  priceType: priceType,
                ),
              ],
            ),
          ),

          // Navigation buttons
          Container(
            decoration: BoxDecoration(
              color: kSurface,
              border: Border(top: BorderSide(color: kBorder)),
            ),
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
            child: Row(
              children: [
                if (_step > 0) ...[
                  Expanded(
                    child: OutlinedButton(
                      onPressed: _back,
                      style: OutlinedButton.styleFrom(
                        foregroundColor: kGreen,
                        side: BorderSide(color: kGreen),
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10)),
                      ),
                      child: Text('Back',
                          style: GoogleFonts.inter(fontWeight: FontWeight.w600)),
                    ),
                  ),
                  const SizedBox(width: 12),
                ],
                Expanded(
                  child: ElevatedButton(
                    onPressed: _next,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: kGreen,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10)),
                    ),
                    child: Text(
                      _step == 3 ? 'Submit' : 'Continue',
                      style: GoogleFonts.inter(fontWeight: FontWeight.w600),
                    ),
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

// ── Step indicator ────────────────────────────────────────────────────────────

class _StepIndicator extends StatelessWidget {
  final int current;
  final List<String> steps;
  const _StepIndicator({required this.current, required this.steps});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: kSurface,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        children: List.generate(steps.length * 2 - 1, (i) {
          if (i.isOdd) {
            final stepIndex = i ~/ 2;
            return Expanded(
              child: Container(
                height: 2,
                color: stepIndex < current ? kGreen : kBorder,
              ),
            );
          }
          final stepIndex = i ~/ 2;
          final done = stepIndex < current;
          final active = stepIndex == current;
          return Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 30,
                height: 30,
                decoration: BoxDecoration(
                  color: done || active ? kGreen : kBorder,
                  shape: BoxShape.circle,
                ),
                child: Center(
                  child: done
                      ? const Icon(Icons.check, color: Colors.white, size: 16)
                      : Text(
                          '${stepIndex + 1}',
                          style: GoogleFonts.inter(
                              color: active ? Colors.white : kTextMuted,
                              fontWeight: FontWeight.bold,
                              fontSize: 13),
                        ),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                steps[stepIndex],
                style: GoogleFonts.inter(
                    fontSize: 10,
                    color: done || active ? kGreen : kTextMuted,
                    fontWeight: active ? FontWeight.bold : FontWeight.normal),
              ),
            ],
          );
        }),
      ),
    );
  }
}

// ── Shared: dropdown with "Other" → text field ────────────────────────────────

class _DropdownWithOther extends StatefulWidget {
  final String? selected;
  final List<String> options;
  final String hint;
  final String otherHint;
  final IconData prefixIcon;
  final String Function(String) labelOf;
  final ValueChanged<String> onSelect;

  const _DropdownWithOther({
    required this.selected,
    required this.options,
    required this.hint,
    required this.otherHint,
    required this.prefixIcon,
    required this.labelOf,
    required this.onSelect,
  });

  @override
  State<_DropdownWithOther> createState() => _DropdownWithOtherState();
}

class _DropdownWithOtherState extends State<_DropdownWithOther> {
  late String? _dropdownValue;
  final _ctrl = TextEditingController();

  InputDecoration get _inputDeco => InputDecoration(
        filled: true,
        fillColor: kSurface,
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide(color: kBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(10),
          borderSide: BorderSide(color: kGreen, width: 1.5),
        ),
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
      );

  @override
  void initState() {
    super.initState();
    final sel = widget.selected;
    // "Other" sentinel is always lowercase 'other' in the cargo list,
    // but a city like 'Other' may also appear in the city list.
    // If the selected value exists verbatim in options → use it.
    // Otherwise it's a custom typed value → show "Other" + pre-fill text field.
    if (sel == null || widget.options.contains(sel)) {
      _dropdownValue = sel;
    } else {
      _dropdownValue = widget.options.last; // "Other" / "other"
      _ctrl.text = sel;
    }
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  bool get _isOther {
    final v = _dropdownValue;
    return v != null && v.toLowerCase() == 'other';
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        DropdownButtonFormField<String>(
          value: _dropdownValue,
          hint: Text(widget.hint,
              style: GoogleFonts.inter(color: kTextMuted)),
          isExpanded: true,
          decoration: _inputDeco.copyWith(
            prefixIcon: Icon(widget.prefixIcon, color: kGreen, size: 20),
          ),
          items: widget.options
              .map((o) => DropdownMenuItem(
                    value: o,
                    child: Text(widget.labelOf(o),
                        style: GoogleFonts.inter(fontSize: 14)),
                  ))
              .toList(),
          onChanged: (v) {
            setState(() => _dropdownValue = v);
            if (v != null && !_isOther) {
              widget.onSelect(v);
            } else if (_isOther && _ctrl.text.isNotEmpty) {
              widget.onSelect(_ctrl.text);
            }
          },
        ),
        if (_isOther) ...[
          const SizedBox(height: 8),
          TextFormField(
            controller: _ctrl,
            onChanged: (v) {
              if (v.isNotEmpty) widget.onSelect(v);
            },
            decoration: _inputDeco.copyWith(
              hintText: widget.otherHint,
              hintStyle: GoogleFonts.inter(color: kTextMuted),
              prefixIcon: Icon(Icons.edit_location_alt_outlined,
                  color: kGreen, size: 20),
            ),
            style: GoogleFonts.inter(fontSize: 14),
          ),
        ],
      ],
    );
  }
}

// ── Step 1: Locations ─────────────────────────────────────────────────────────

class _Step1 extends StatelessWidget {
  final String? pickupLocation;
  final String? deliveryLocation;
  final List<String> cities;
  final ValueChanged<String> onPickup;
  final ValueChanged<String> onDelivery;

  const _Step1({
    required this.pickupLocation,
    required this.deliveryLocation,
    required this.cities,
    required this.onPickup,
    required this.onDelivery,
  });

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Pickup Location',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        _DropdownWithOther(
          selected: pickupLocation,
          options: cities,
          hint: 'Select pickup city',
          otherHint: 'Enter city name (e.g. Mojo)',
          prefixIcon: Icons.trip_origin,
          labelOf: (c) => c,
          onSelect: onPickup,
        ),
        const SizedBox(height: 20),
        Text('Destination',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        _DropdownWithOther(
          selected: deliveryLocation,
          options: cities,
          hint: 'Select destination city',
          otherHint: 'Enter city name (e.g. Kombolcha)',
          prefixIcon: Icons.location_on_outlined,
          labelOf: (c) => c,
          onSelect: onDelivery,
        ),
        if (pickupLocation != null &&
            pickupLocation!.isNotEmpty &&
            deliveryLocation != null &&
            deliveryLocation!.isNotEmpty) ...[
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            decoration: BoxDecoration(
                color: kGreenTint,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: kGreen.withValues(alpha: 0.3))),
            child: Row(children: [
              Icon(Icons.route, color: kGreen, size: 18),
              const SizedBox(width: 8),
              Expanded(
                child: Text('$pickupLocation → $deliveryLocation',
                    style: GoogleFonts.inter(
                        fontWeight: FontWeight.w600, color: kGreen,
                        fontSize: 13)),
              ),
            ]),
          ),
        ],
      ],
    );
  }
}

// ── Step 2: Cargo details ─────────────────────────────────────────────────────

class _Step2 extends StatelessWidget {
  final String? cargoType;
  final String? weight;
  final String? description;
  final List<String> cargoTypes;
  final ValueChanged<String?> onType;
  final ValueChanged<String> onWeight;
  final ValueChanged<String> onDesc;

  const _Step2({
    required this.cargoType,
    required this.weight,
    required this.description,
    required this.cargoTypes,
    required this.onType,
    required this.onWeight,
    required this.onDesc,
  });

  InputDecoration _inputDeco({String? hint, String? suffix, Widget? prefix}) =>
      InputDecoration(
        hintText: hint,
        hintStyle: GoogleFonts.inter(color: kTextMuted),
        suffixText: suffix,
        prefixIcon: prefix,
        filled: true,
        fillColor: kSurface,
        enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: BorderSide(color: kBorder)),
        focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: BorderSide(color: kGreen, width: 1.5)),
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
      );

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Cargo Type',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        _DropdownWithOther(
          selected: cargoType,
          options: cargoTypes,
          hint: 'Select cargo type',
          otherHint: 'Describe cargo type (e.g. Chemicals)',
          prefixIcon: Icons.inventory_2_outlined,
          labelOf: (t) => t[0].toUpperCase() + t.substring(1),
          onSelect: (v) => onType(v),
        ),
        const SizedBox(height: 16),
        Text('Weight (tons)',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        TextFormField(
          initialValue: weight,
          keyboardType:
              const TextInputType.numberWithOptions(decimal: true),
          onChanged: onWeight,
          decoration: _inputDeco(hint: 'e.g. 20', suffix: 'tons'),
          style: GoogleFonts.inter(fontSize: 14),
        ),
        const SizedBox(height: 16),
        Text('Notes (optional)',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        TextFormField(
          initialValue: description,
          maxLines: 3,
          onChanged: onDesc,
          decoration: _inputDeco(hint: 'Special handling requirements...'),
          style: GoogleFonts.inter(fontSize: 14),
        ),
      ],
    );
  }
}

// ── Step 3: Budget & timeline ─────────────────────────────────────────────────

class _Step3 extends StatelessWidget {
  final String? budget;
  final String? deadline;
  final String priceType;
  final int? priceMin;
  final int? priceMax;
  final int? priceDistKm;
  final bool priceLoading;
  final String? priceError;
  final ValueChanged<String> onBudget;
  final ValueChanged<String> onDeadline;
  final ValueChanged<String> onPriceType;

  const _Step3({
    required this.budget,
    required this.deadline,
    required this.priceType,
    required this.priceMin,
    required this.priceMax,
    required this.priceDistKm,
    required this.priceLoading,
    required this.priceError,
    required this.onBudget,
    required this.onDeadline,
    required this.onPriceType,
  });

  InputDecoration _inputDeco({String? hint, String? prefix, Widget? prefixIcon}) =>
      InputDecoration(
        hintText: hint,
        hintStyle: GoogleFonts.inter(color: kTextMuted),
        prefixText: prefix,
        prefixIcon: prefixIcon,
        filled: true,
        fillColor: kSurface,
        enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: BorderSide(color: kBorder)),
        focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: BorderSide(color: kGreen, width: 1.5)),
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
      );

  String _fmt(int v) {
    if (v >= 1000) {
      return v.toString().replaceAllMapped(
          RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]},');
    }
    return v.toString();
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Pricing Type',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        Row(children: [
          Expanded(
            child: _PriceTypeOption(
              title: 'Negotiable',
              subtitle: 'Drivers bid on your cargo',
              icon: Icons.gavel_rounded,
              selected: priceType == 'negotiable',
              onTap: () => onPriceType('negotiable'),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: _PriceTypeOption(
              title: 'Fixed Price',
              subtitle: 'Drivers accept or reject',
              icon: Icons.price_check_rounded,
              selected: priceType == 'fixed',
              onTap: () => onPriceType('fixed'),
            ),
          ),
        ]),
        const SizedBox(height: 16),
        Text(
          priceType == 'fixed'
              ? 'Budget (ETB) — required for fixed price'
              : 'Budget (ETB)',
          style: GoogleFonts.inter(
              fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        TextFormField(
          initialValue: budget,
          keyboardType: TextInputType.number,
          onChanged: onBudget,
          decoration: _inputDeco(hint: 'e.g. 15000', prefix: 'ETB '),
          style: GoogleFonts.inter(fontSize: 14),
        ),
        const SizedBox(height: 16),
        Text('Deadline',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        TextFormField(
          readOnly: true,
          initialValue: deadline,
          onTap: () async {
            final d = await showDatePicker(
              context: context,
              initialDate: DateTime.now().add(const Duration(days: 1)),
              firstDate: DateTime.now(),
              lastDate: DateTime.now().add(const Duration(days: 90)),
              builder: (ctx, child) => Theme(
                data: Theme.of(ctx).copyWith(
                  colorScheme: ColorScheme.light(primary: kGreen),
                ),
                child: child!,
              ),
            );
            if (d != null) onDeadline(d.toString().split(' ')[0]);
          },
          decoration: _inputDeco(
            hint: 'Tap to select date',
            prefixIcon: Icon(Icons.calendar_today_outlined,
                color: kGreen, size: 20),
          ),
          style: GoogleFonts.inter(fontSize: 14),
        ),
        const SizedBox(height: 20),

        // ── AI Price Suggestion card ──────────────────────────────────────
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
              color: kGreenTint,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: kGreen.withValues(alpha: 0.25))),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(children: [
                Icon(Icons.psychology_outlined, color: kGreen, size: 18),
                const SizedBox(width: 6),
                Text('AI Price Suggestion',
                    style: GoogleFonts.inter(
                        fontWeight: FontWeight.bold, color: kGreen)),
              ]),
              const SizedBox(height: 10),
              if (priceLoading)
                Row(children: [
                  SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: kGreen)),
                  const SizedBox(width: 10),
                  Text('Calculating route price...',
                      style: GoogleFonts.inter(
                          fontSize: 13, color: kTextSecond)),
                ])
              else if (priceMin != null && priceMax != null)
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'ETB ${_fmt(priceMin!)} – ${_fmt(priceMax!)}',
                      style: GoogleFonts.inter(
                          fontSize: 22,
                          fontWeight: FontWeight.bold,
                          color: kGreen),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      priceDistKm != null
                          ? 'Based on ~$priceDistKm km route and cargo weight'
                          : 'Based on route and cargo type',
                      style: GoogleFonts.inter(
                          fontSize: 12, color: kTextSecond),
                    ),
                  ],
                )
              else if (priceError != null)
                Text(priceError!,
                    style:
                        GoogleFonts.inter(fontSize: 12, color: kDanger))
              else
                Text(
                  'Enter cargo details on the previous step to get a price estimate.',
                  style: GoogleFonts.inter(
                      fontSize: 12, color: kTextSecond),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

// ── Price type option card ────────────────────────────────────────────────────

class _PriceTypeOption extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final bool selected;
  final VoidCallback onTap;

  const _PriceTypeOption({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: selected ? kGreen.withValues(alpha: 0.06) : kSurface,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: selected ? kGreen : kBorder,
            width: selected ? 1.5 : 0.5,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(icon, color: selected ? kGreen : kTextMuted, size: 20),
            const SizedBox(height: 6),
            Text(title,
                style: GoogleFonts.inter(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: selected ? kGreen : kTextPrimary)),
            const SizedBox(height: 2),
            Text(subtitle,
                style: GoogleFonts.inter(fontSize: 11, color: kTextMuted)),
          ],
        ),
      ),
    );
  }
}

// ── Step 4: Review ────────────────────────────────────────────────────────────

class _Step4 extends StatelessWidget {
  final String? pickup;
  final String? delivery;
  final String? cargo;
  final String? weight;
  final String? budget;
  final String? deadline;
  final String? description;
  final String priceType;

  const _Step4({
    required this.pickup,
    required this.delivery,
    required this.cargo,
    required this.weight,
    required this.budget,
    required this.deadline,
    required this.description,
    required this.priceType,
  });

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Review & Confirm',
            style: GoogleFonts.inter(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: kTextPrimary)),
        const SizedBox(height: 16),
        _ReviewRow('Pickup', pickup ?? '—'),
        _ReviewRow('Destination', delivery ?? '—'),
        _ReviewRow('Cargo Type', cargo ?? '—'),
        _ReviewRow('Weight', weight != null ? '$weight tons' : '—'),
        _ReviewRow('Pricing', priceType == 'fixed' ? 'Fixed Price' : 'Negotiable'),
        _ReviewRow('Budget', budget != null ? 'ETB $budget' : '—'),
        _ReviewRow('Deadline', deadline ?? 'Not set'),
        if (description != null && description!.isNotEmpty)
          _ReviewRow('Notes', description!),
        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
              color: kAmberLight,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: kAmber.withValues(alpha: 0.4))),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(Icons.info_outline, color: kAmber, size: 18),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  'Once submitted, your request will be visible to available drivers. Platform commission: 10%.',
                  style: GoogleFonts.inter(fontSize: 12, color: kTextPrimary),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}

class _ReviewRow extends StatelessWidget {
  final String label;
  final String value;
  const _ReviewRow(this.label, this.value);

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 12),
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
          color: kSurface,
          border: Border.all(color: kBorder),
          borderRadius: BorderRadius.circular(10)),
      child: Row(
        children: [
          SizedBox(
            width: 90,
            child: Text(label,
                style: GoogleFonts.inter(
                    fontSize: 12,
                    color: kTextSecond,
                    fontWeight: FontWeight.w500)),
          ),
          Expanded(
            child: Text(value,
                style: GoogleFonts.inter(
                    fontSize: 13, fontWeight: FontWeight.w600,
                    color: kTextPrimary)),
          ),
        ],
      ),
    );
  }
}
