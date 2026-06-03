import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/repositories/repositories.dart';

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

  static const _cargoTypes = [
    'perishable',
    'fragile',
    'electronics',
    'construction',
    'general',
  ];

  static const _cities = [
    'Addis Ababa',
    'Adama',
    'Hawassa',
    'Dire Dawa',
    'Bahir Dar',
    'Gondar',
    'Mekele',
    'Jimma',
  ];

  static const _steps = [
    'Locations',
    'Cargo',
    'Budget',
    'Review',
  ];

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  void _next() {
    if (_step < 3) {
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

  Future<void> _submit() async {
    if (pickupLocation == null ||
        deliveryLocation == null ||
        cargoType == null ||
        weight == null ||
        budget == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
            content: Text('Please fill in all required fields'),
            backgroundColor: Colors.red),
      );
      return;
    }

    try {
      // Show loading
      if (!mounted) return;
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (_) => const Center(
            child: Card(
                child: Padding(
                    padding: EdgeInsets.all(24),
                    child: CircularProgressIndicator()))),
      );

      await ref.read(cargoRepositoryProvider).create(
            pickupLocation: pickupLocation!,
            destination: deliveryLocation!,
            materialType: cargoType!,
            weight: double.tryParse(weight!) ?? 0,
            urgencyLevel: 'normal',
            budget: double.tryParse(budget ?? ''),
          );

      if (mounted) Navigator.pop(context); // close dialog

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
              content: Text('Cargo request created!'),
              backgroundColor: Colors.green),
        );
        await Future.delayed(const Duration(milliseconds: 800));
        if (mounted) context.go('/freight');
      }
    } catch (e) {
      if (mounted) Navigator.pop(context);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
              content: Text('Error: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Post Cargo'), elevation: 0),
      body: Column(
        children: [
          // ── Step indicator (fixed height, no Expanded inside Column) ──
          _StepIndicator(current: _step, steps: _steps),
          const Divider(height: 1),

          // ── Page content ──────────────────────────────────────────────
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
                  onBudget: (v) => setState(() => budget = v),
                  onDeadline: (v) => setState(() => deadline = v),
                ),
                _Step4(
                  pickup: pickupLocation,
                  delivery: deliveryLocation,
                  cargo: cargoType,
                  weight: weight,
                  budget: budget,
                  deadline: deadline,
                  description: description,
                ),
              ],
            ),
          ),

          // ── Navigation buttons ────────────────────────────────────────
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                if (_step > 0) ...[
                  Expanded(
                    child: OutlinedButton(
                        onPressed: _back, child: const Text('Back')),
                  ),
                  const SizedBox(width: 12),
                ],
                Expanded(
                  child: ElevatedButton(
                    onPressed: _next,
                    style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.blue,
                        padding: const EdgeInsets.symmetric(vertical: 14)),
                    child: Text(_step == 3 ? 'Submit' : 'Continue'),
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

// ── Step indicator — fixed height, no unbounded flex ─────────────────────

class _StepIndicator extends StatelessWidget {
  final int current;
  final List<String> steps;
  const _StepIndicator({required this.current, required this.steps});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        children: List.generate(steps.length * 2 - 1, (i) {
          if (i.isOdd) {
            // connector line
            final stepIndex = i ~/ 2;
            return Expanded(
              child: Container(
                height: 2,
                color: stepIndex < current ? Colors.blue : Colors.grey[300],
              ),
            );
          }
          final stepIndex = i ~/ 2;
          final active = stepIndex <= current;
          return Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: active ? Colors.blue : Colors.grey[300],
                  shape: BoxShape.circle,
                ),
                child: Center(
                  child: Text(
                    '${stepIndex + 1}',
                    style: TextStyle(
                        color: active ? Colors.white : Colors.grey[700],
                        fontWeight: FontWeight.bold,
                        fontSize: 13),
                  ),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                steps[stepIndex],
                style: TextStyle(
                    fontSize: 10,
                    color: active ? Colors.blue : Colors.grey[500],
                    fontWeight:
                        active ? FontWeight.bold : FontWeight.normal),
              ),
            ],
          );
        }),
      ),
    );
  }
}

// ── Step 1: Locations ─────────────────────────────────────────────────────

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
        const Text('Pickup Location',
            style: TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        _CityPicker(
            selected: pickupLocation,
            cities: cities,
            hint: 'Select pickup city',
            onSelect: onPickup),
        const SizedBox(height: 20),
        const Text('Destination',
            style: TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        _CityPicker(
            selected: deliveryLocation,
            cities: cities,
            hint: 'Select destination city',
            onSelect: onDelivery),
        if (pickupLocation != null && deliveryLocation != null) ...[
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
                color: Colors.green[50],
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.green[200]!)),
            child: Row(children: [
              const Icon(Icons.route, color: Colors.green),
              const SizedBox(width: 8),
              Text('$pickupLocation → $deliveryLocation',
                  style: const TextStyle(
                      fontWeight: FontWeight.w600, color: Colors.green)),
            ]),
          ),
        ],
      ],
    );
  }
}

class _CityPicker extends StatelessWidget {
  final String? selected;
  final List<String> cities;
  final String hint;
  final ValueChanged<String> onSelect;

  const _CityPicker(
      {required this.selected,
      required this.cities,
      required this.hint,
      required this.onSelect});

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<String>(
      value: selected,
      hint: Text(hint),
      items: cities
          .map((c) => DropdownMenuItem(value: c, child: Text(c)))
          .toList(),
      onChanged: (v) { if (v != null) onSelect(v); },
      decoration: InputDecoration(
        prefixIcon: const Icon(Icons.location_on_outlined),
        border:
            OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
      ),
    );
  }
}

// ── Step 2: Cargo details ─────────────────────────────────────────────────

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

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text('Cargo Type',
            style: TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        DropdownButtonFormField<String>(
          value: cargoType,
          hint: const Text('Select type'),
          items: cargoTypes
              .map((t) => DropdownMenuItem(
                  value: t,
                  child: Text(t[0].toUpperCase() + t.substring(1))))
              .toList(),
          onChanged: onType,
          decoration: InputDecoration(
              border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8))),
        ),
        const SizedBox(height: 16),
        const Text('Weight (tons)',
            style: TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        TextFormField(
          initialValue: weight,
          keyboardType: TextInputType.number,
          onChanged: onWeight,
          decoration: InputDecoration(
            hintText: 'e.g. 20',
            suffixText: 'tons',
            border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8)),
          ),
        ),
        const SizedBox(height: 16),
        const Text('Notes (optional)',
            style: TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        TextFormField(
          initialValue: description,
          maxLines: 3,
          onChanged: onDesc,
          decoration: InputDecoration(
            hintText: 'Special handling requirements...',
            border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8)),
          ),
        ),
      ],
    );
  }
}

// ── Step 3: Budget & timeline ─────────────────────────────────────────────

class _Step3 extends StatelessWidget {
  final String? budget;
  final String? deadline;
  final ValueChanged<String> onBudget;
  final ValueChanged<String> onDeadline;

  const _Step3({
    required this.budget,
    required this.deadline,
    required this.onBudget,
    required this.onDeadline,
  });

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text('Budget (ETB)',
            style: TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        TextFormField(
          initialValue: budget,
          keyboardType: TextInputType.number,
          onChanged: onBudget,
          decoration: InputDecoration(
            hintText: 'e.g. 15000',
            prefixText: 'ETB ',
            border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8)),
          ),
        ),
        const SizedBox(height: 16),
        const Text('Deadline',
            style: TextStyle(fontWeight: FontWeight.bold)),
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
            );
            if (d != null) onDeadline(d.toString().split(' ')[0]);
          },
          decoration: InputDecoration(
            hintText: 'Tap to select date',
            prefixIcon: const Icon(Icons.calendar_today_outlined),
            border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(8)),
          ),
        ),
        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
              color: Colors.blue[50],
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: Colors.blue[200]!)),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Row(children: [
                Icon(Icons.psychology, color: Colors.blue, size: 18),
                SizedBox(width: 6),
                Text('AI Price Suggestion',
                    style: TextStyle(
                        fontWeight: FontWeight.bold, color: Colors.blue)),
              ]),
              const SizedBox(height: 6),
              const Text('ETB 8,000 – 12,000',
                  style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: Colors.green)),
              Text('Based on route and cargo type',
                  style:
                      TextStyle(fontSize: 12, color: Colors.grey[600])),
            ],
          ),
        ),
      ],
    );
  }
}

// ── Step 4: Review ────────────────────────────────────────────────────────

class _Step4 extends StatelessWidget {
  final String? pickup;
  final String? delivery;
  final String? cargo;
  final String? weight;
  final String? budget;
  final String? deadline;
  final String? description;

  const _Step4({
    required this.pickup,
    required this.delivery,
    required this.cargo,
    required this.weight,
    required this.budget,
    required this.deadline,
    required this.description,
  });

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text('Review & Confirm',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
        const SizedBox(height: 16),
        _ReviewRow('Pickup', pickup ?? '—'),
        _ReviewRow('Destination', delivery ?? '—'),
        _ReviewRow('Cargo Type', cargo ?? '—'),
        _ReviewRow('Weight', weight != null ? '$weight tons' : '—'),
        _ReviewRow('Budget', budget != null ? 'ETB $budget' : '—'),
        _ReviewRow('Deadline', deadline ?? 'Not set'),
        if (description != null && description!.isNotEmpty)
          _ReviewRow('Notes', description!),
        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
              color: Colors.amber[50],
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: Colors.amber[200]!)),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(Icons.info_outline, color: Colors.amber[700], size: 18),
              const SizedBox(width: 10),
              const Expanded(
                child: Text(
                  'Once submitted, your request will be visible to available drivers. Platform commission: 10%.',
                  style: TextStyle(fontSize: 12),
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
          border: Border.all(color: Colors.grey[200]!),
          borderRadius: BorderRadius.circular(8)),
      child: Row(
        children: [
          SizedBox(
            width: 90,
            child: Text(label,
                style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[600],
                    fontWeight: FontWeight.w500)),
          ),
          Expanded(
            child: Text(value,
                style: const TextStyle(
                    fontSize: 13, fontWeight: FontWeight.w600)),
          ),
        ],
      ),
    );
  }
}
