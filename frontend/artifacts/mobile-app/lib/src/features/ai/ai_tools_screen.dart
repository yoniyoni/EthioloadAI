import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';

/// AiToolsScreen — exposes all 5 AI engine endpoints in one UI.
class AiToolsScreen extends ConsumerStatefulWidget {
  const AiToolsScreen({Key? key}) : super(key: key);

  @override
  ConsumerState<AiToolsScreen> createState() => _AiToolsScreenState();
}

class _AiToolsScreenState extends ConsumerState<AiToolsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabs;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('AI Tools'),
        elevation: 0,
        bottom: TabBar(
          controller: _tabs,
          tabs: const [
            Tab(text: 'Recommend'),
            Tab(text: 'Price'),
            Tab(text: 'Empty Return'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabs,
        children: const [
          _RecommendTab(),
          _PriceTab(),
          _EmptyReturnTab(),
        ],
      ),
    );
  }
}

// ── Tab 1: Truck Recommendation ───────────────────────────────────────────

class _RecommendTab extends ConsumerStatefulWidget {
  const _RecommendTab();

  @override
  ConsumerState<_RecommendTab> createState() => _RecommendTabState();
}

class _RecommendTabState extends ConsumerState<_RecommendTab> {
  final _pickupCtrl = TextEditingController();
  final _destCtrl = TextEditingController();
  final _weightCtrl = TextEditingController();
  String _material = 'general';
  String _urgency = 'normal';
  bool _loading = false;
  List<TruckRecommendation>? _results;
  String? _error;

  final _materials = [
    'perishable',
    'fragile',
    'electronics',
    'construction',
    'general'
  ];
  final _urgencies = ['low', 'normal', 'high', 'express'];

  Future<void> _submit() async {
    if (_pickupCtrl.text.isEmpty ||
        _destCtrl.text.isEmpty ||
        _weightCtrl.text.isEmpty) return;
    setState(() {
      _loading = true;
      _error = null;
      _results = null;
    });
    try {
      final trucks =
          await ref.read(aiRepositoryProvider).recommendTruck(
                pickupLocation: _pickupCtrl.text.trim(),
                destination: _destCtrl.text.trim(),
                weight: double.parse(_weightCtrl.text.trim()),
                materialType: _material,
                urgencyLevel: _urgency,
              );
      setState(() {
        _results = trucks;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Get AI-ranked truck recommendations',
              style: TextStyle(color: Colors.grey)),
          const SizedBox(height: 16),
          TextField(
            controller: _pickupCtrl,
            decoration: _dec('Pickup city', Icons.location_on),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _destCtrl,
            decoration: _dec('Destination city', Icons.flag),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _weightCtrl,
            keyboardType: TextInputType.number,
            decoration: _dec('Weight (tons)', Icons.scale),
          ),
          const SizedBox(height: 12),
          _DropdownRow(
            label: 'Material',
            value: _material,
            items: _materials,
            onChanged: (v) => setState(() => _material = v!),
          ),
          const SizedBox(height: 12),
          _DropdownRow(
            label: 'Urgency',
            value: _urgency,
            items: _urgencies,
            onChanged: (v) => setState(() => _urgency = v!),
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _loading ? null : _submit,
              child: _loading
                  ? const SizedBox(
                      height: 18,
                      width: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Text('Get Recommendations'),
            ),
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!,
                style: const TextStyle(color: Colors.red, fontSize: 13)),
          ],
          if (_results != null) ...[
            const SizedBox(height: 20),
            const Text('Results',
                style:
                    TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
            const SizedBox(height: 10),
            ..._results!.map((t) => _TruckCard(truck: t)),
          ],
        ],
      ),
    );
  }
}

// ── Tab 2: Price Prediction ───────────────────────────────────────────────

class _PriceTab extends ConsumerStatefulWidget {
  const _PriceTab();

  @override
  ConsumerState<_PriceTab> createState() => _PriceTabState();
}

class _PriceTabState extends ConsumerState<_PriceTab> {
  final _pickupCtrl = TextEditingController();
  final _destCtrl = TextEditingController();
  final _weightCtrl = TextEditingController();
  String _material = 'general';
  bool _loading = false;
  Map<String, dynamic>? _result;
  String? _error;

  final _materials = [
    'perishable',
    'fragile',
    'electronics',
    'construction',
    'general'
  ];

  Future<void> _submit() async {
    if (_pickupCtrl.text.isEmpty ||
        _destCtrl.text.isEmpty ||
        _weightCtrl.text.isEmpty) return;
    setState(() {
      _loading = true;
      _error = null;
      _result = null;
    });
    try {
      final data =
          await ref.read(aiRepositoryProvider).predictPrice(
                pickupLocation: _pickupCtrl.text.trim(),
                destination: _destCtrl.text.trim(),
                weight: double.parse(_weightCtrl.text.trim()),
                materialType: _material,
              );
      setState(() {
        _result = data;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Predict the fair price for a route',
              style: TextStyle(color: Colors.grey)),
          const SizedBox(height: 16),
          TextField(
              controller: _pickupCtrl,
              decoration: _dec('Pickup city', Icons.location_on)),
          const SizedBox(height: 12),
          TextField(
              controller: _destCtrl,
              decoration: _dec('Destination city', Icons.flag)),
          const SizedBox(height: 12),
          TextField(
            controller: _weightCtrl,
            keyboardType: TextInputType.number,
            decoration: _dec('Weight (tons)', Icons.scale),
          ),
          const SizedBox(height: 12),
          _DropdownRow(
            label: 'Material',
            value: _material,
            items: _materials,
            onChanged: (v) => setState(() => _material = v!),
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _loading ? null : _submit,
              child: _loading
                  ? const SizedBox(
                      height: 18,
                      width: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Text('Predict Price'),
            ),
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!,
                style: const TextStyle(color: Colors.red, fontSize: 13)),
          ],
          if (_result != null) ...[
            const SizedBox(height: 20),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.green[50],
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: Colors.green[200]!),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Predicted Price',
                      style: TextStyle(
                          fontSize: 13, color: Colors.black54)),
                  const SizedBox(height: 6),
                  Text(
                    'ETB ${(_result!['estimated_price'] ?? _result!['predicted_price'] ?? '—').toString()}',
                    style: const TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.bold,
                        color: Colors.green),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Confidence: ${((_result!['confidence'] ?? 0) * 100).toStringAsFixed(0)}%',
                    style: const TextStyle(fontSize: 12),
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

// ── Tab 3: Empty Return Risk ──────────────────────────────────────────────

class _EmptyReturnTab extends ConsumerStatefulWidget {
  const _EmptyReturnTab();

  @override
  ConsumerState<_EmptyReturnTab> createState() => _EmptyReturnTabState();
}

class _EmptyReturnTabState extends ConsumerState<_EmptyReturnTab> {
  final _destCtrl = TextEditingController();
  bool _loading = false;
  Map<String, dynamic>? _result;
  String? _error;

  Future<void> _submit() async {
    if (_destCtrl.text.isEmpty) return;
    setState(() {
      _loading = true;
      _error = null;
      _result = null;
    });
    try {
      final data = await ref
          .read(aiRepositoryProvider)
          .predictEmptyReturn(_destCtrl.text.trim());
      setState(() {
        _result = data;
        _loading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final risk = _result?['risk_level'] as String?;
    final riskColor = risk == 'Low'
        ? Colors.green
        : risk == 'Medium'
            ? Colors.orange
            : Colors.red;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Find out the probability of returning empty from a destination',
            style: TextStyle(color: Colors.grey),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _destCtrl,
            decoration: _dec('Destination city', Icons.flag),
          ),
          const SizedBox(height: 20),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: _loading ? null : _submit,
              child: _loading
                  ? const SizedBox(
                      height: 18,
                      width: 18,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Text('Check Risk'),
            ),
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!,
                style: const TextStyle(color: Colors.red, fontSize: 13)),
          ],
          if (_result != null) ...[
            const SizedBox(height: 20),
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: riskColor.withOpacity(0.07),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: riskColor.withOpacity(0.3)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(children: [
                    Icon(Icons.warning_amber_rounded, color: riskColor),
                    const SizedBox(width: 8),
                    Text(
                      'Risk: $risk',
                      style: TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.bold,
                          color: riskColor),
                    ),
                  ]),
                  const SizedBox(height: 10),
                  Text(
                    'Empty return probability: '
                    '${((_result!['empty_return_probability'] ?? 0) * 100).toStringAsFixed(0)}%',
                    style: const TextStyle(fontSize: 14),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    _result!['recommendation'] ?? '',
                    style:
                        TextStyle(fontSize: 13, color: Colors.grey[700]),
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

// ── Shared helpers ────────────────────────────────────────────────────────

InputDecoration _dec(String label, IconData icon) => InputDecoration(
      labelText: label,
      prefixIcon: Icon(icon),
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
      contentPadding:
          const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
    );

class _DropdownRow extends StatelessWidget {
  final String label;
  final String value;
  final List<String> items;
  final ValueChanged<String?> onChanged;

  const _DropdownRow({
    required this.label,
    required this.value,
    required this.items,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return DropdownButtonFormField<String>(
      value: value,
      decoration: InputDecoration(
        labelText: label,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
      ),
      items: items
          .map((e) => DropdownMenuItem(value: e, child: Text(e)))
          .toList(),
      onChanged: onChanged,
    );
  }
}

class _TruckCard extends StatelessWidget {
  final TruckRecommendation truck;
  const _TruckCard({required this.truck});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Colors.grey[200]!),
        boxShadow: [
          BoxShadow(
              color: Colors.black.withOpacity(0.04),
              blurRadius: 4,
              offset: const Offset(0, 2)),
        ],
      ),
      child: Row(
        children: [
          CircleAvatar(
            backgroundColor: Colors.blue[50],
            child: Text(
              truck.score.toStringAsFixed(2),
              style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.bold,
                  color: Colors.blue[700]),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(truck.driverName,
                    style: const TextStyle(
                        fontSize: 14, fontWeight: FontWeight.bold)),
                Text('${truck.plateNumber} • ${truck.capacity}t capacity',
                    style: const TextStyle(
                        fontSize: 12, color: Colors.black54)),
                Text('${truck.distanceKm} km away',
                    style:
                        const TextStyle(fontSize: 12, color: Colors.grey)),
              ],
            ),
          ),
          Column(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Text(
                'ETB ${truck.estimatedPrice}',
                style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    color: Colors.green),
              ),
              const Text('est. price',
                  style: TextStyle(fontSize: 10, color: Colors.grey)),
            ],
          ),
        ],
      ),
    );
  }
}
