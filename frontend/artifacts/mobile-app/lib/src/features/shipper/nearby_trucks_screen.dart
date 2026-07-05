import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';
import 'package:easy_localization/easy_localization.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';
import '../../services/location_service.dart';

const _green  = Color(0xFF0F3D1A);
const _amber  = Color(0xFFF59E0B);
const _bg     = Color(0xFFF8FBF8);
const _border = Color(0xFFE5E7EB);

/// Full-screen map showing available trucks near a location.
/// - Map with truck pins + radius circle
/// - Filter chip for vehicle category
/// - Bottom sheet list with driver details
/// - Auto-refreshes every 5 minutes (Feature 4)
class NearbyTrucksScreen extends ConsumerStatefulWidget {
  /// Center of the search radius. Defaults to GPS if null.
  final double? centerLat;
  final double? centerLng;
  final String? locationName;

  const NearbyTrucksScreen({
    super.key,
    this.centerLat,
    this.centerLng,
    this.locationName,
  });

  @override
  ConsumerState<NearbyTrucksScreen> createState() => _NearbyTrucksScreenState();
}

class _NearbyTrucksScreenState extends ConsumerState<NearbyTrucksScreen> {
  List<NearbyTruck> _trucks = [];
  bool _loading    = true;
  String? _category;         // null = all, 'light', 'heavy'
  double _radiusKm = 100;
  LatLng? _center;
  NearbyTruck? _selected;
  Timer? _refreshTimer;
  final MapController _mapCtrl = MapController();

  static const _defaultCenter = LatLng(9.0320, 38.7469);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _init());
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _init() async {
    // Resolve center: use provided coords or fall back to GPS
    if (widget.centerLat != null && widget.centerLng != null) {
      _center = LatLng(widget.centerLat!, widget.centerLng!);
    } else {
      final pos = await LocationService.getCurrentPosition();
      if (pos != null && mounted) {
        _center = LatLng(pos.latitude, pos.longitude);
      }
    }

    if (mounted) {
      _center ??= _defaultCenter;
      _mapCtrl.move(_center!, 9.0);
      await _load();
      _refreshTimer = Timer.periodic(
        const Duration(minutes: 5),
        (_) => _load(),
      );
    }
  }

  Future<void> _load() async {
    if (_center == null) return;
    setState(() => _loading = true);
    try {
      final trucks = await ref.read(routingRepositoryProvider).nearbyTrucks(
        lat:      _center!.latitude,
        lng:      _center!.longitude,
        radiusKm: _radiusKm,
        category: _category,
      );
      if (mounted) setState(() { _trucks = trucks; _loading = false; });
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final center = _center ?? _defaultCenter;

    return Scaffold(
      backgroundColor: _bg,
      appBar: AppBar(
        backgroundColor: _green,
        elevation: 0,
        iconTheme: const IconThemeData(color: Colors.white),
        title: Text(
          'tracking.nearby_trucks'.tr(),
          style: const TextStyle(
              color: Colors.white, fontWeight: FontWeight.w600),
        ),
        actions: [
          if (_loading)
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16),
              child: Center(
                child: SizedBox(
                  width: 18, height: 18,
                  child: CircularProgressIndicator(
                      strokeWidth: 2, color: Colors.white),
                ),
              ),
            )
          else
            IconButton(
              icon: const Icon(Icons.refresh_rounded, color: Colors.white),
              onPressed: _load,
            ),
        ],
      ),
      body: Column(
        children: [
          // ── Filter chips ───────────────────────────────────────────────
          _FilterBar(
            selected:   _category,
            radiusKm:   _radiusKm,
            onCategory: (c) {
              setState(() { _category = c; _selected = null; });
              _load();
            },
            onRadius:   (r) {
              setState(() { _radiusKm = r; _selected = null; });
              _load();
            },
          ),

          // ── Map ────────────────────────────────────────────────────────
          Expanded(
            flex: 5,
            child: Stack(
              children: [
                FlutterMap(
                  mapController: _mapCtrl,
                  options: MapOptions(
                    initialCenter: center,
                    initialZoom:   9.0,
                    onTap:         (_, __) => setState(() => _selected = null),
                    interactionOptions: const InteractionOptions(
                      flags: InteractiveFlag.all,
                    ),
                  ),
                  children: [
                    TileLayer(
                      urlTemplate:
                          'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                      userAgentPackageName: 'com.ethioloadai.app',
                    ),

                    // Radius circle
                    CircleLayer(
                      circles: [
                        CircleMarker(
                          point:      center,
                          radius:     _radiusKm * 1000,
                          useRadiusInMeter: true,
                          color:      _green.withValues(alpha: 0.06),
                          borderColor: _green.withValues(alpha: 0.35),
                          borderStrokeWidth: 1.5,
                        ),
                      ],
                    ),

                    // Center pin
                    MarkerLayer(markers: [
                      Marker(
                        point:  center,
                        width:  36,
                        height: 36,
                        child:  _CenterPin(),
                      ),
                    ]),

                    // Truck markers
                    MarkerLayer(
                      markers: _trucks.map((t) {
                        final isSelected = _selected?.vehicleId == t.vehicleId;
                        return Marker(
                          point:  LatLng(t.latitude, t.longitude),
                          width:  isSelected ? 56 : 44,
                          height: isSelected ? 56 : 44,
                          child:  GestureDetector(
                            onTap: () => setState(() => _selected = t),
                            child: _TruckPin(selected: isSelected),
                          ),
                        );
                      }).toList(),
                    ),
                  ],
                ),

                // Truck popup
                if (_selected != null)
                  Positioned(
                    top: 12, left: 16, right: 16,
                    child: _TruckPopup(
                      truck:    _selected!,
                      onClose:  () => setState(() => _selected = null),
                    ),
                  ),
              ],
            ),
          ),

          // ── Bottom list ────────────────────────────────────────────────
          Expanded(
            flex: 3,
            child: _TruckList(
              trucks:   _trucks,
              selected: _selected,
              onSelect: (t) {
                setState(() => _selected = t);
                _mapCtrl.move(LatLng(t.latitude, t.longitude), 12.0);
              },
            ),
          ),
        ],
      ),
    );
  }
}

// ── Filter bar ────────────────────────────────────────────────────────────

class _FilterBar extends StatelessWidget {
  final String? selected;
  final double radiusKm;
  final void Function(String?) onCategory;
  final void Function(double) onRadius;

  const _FilterBar({
    required this.selected,
    required this.radiusKm,
    required this.onCategory,
    required this.onRadius,
  });

  @override
  Widget build(BuildContext context) {
    Widget chip(String label, String? value) => Padding(
      padding: const EdgeInsets.only(right: 6),
      child: FilterChip(
        label: Text(label, style: const TextStyle(fontSize: 12)),
        selected: selected == value,
        selectedColor: _green.withValues(alpha: 0.15),
        checkmarkColor: _green,
        onSelected: (_) => onCategory(selected == value ? null : value),
        side: BorderSide(
          color: selected == value ? _green : _border,
        ),
      ),
    );

    return Container(
      color: Colors.white,
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      child: Row(children: [
        chip('tracking.all_types'.tr(), null),
        chip('tracking.heavy'.tr(), 'heavy'),
        chip('tracking.light'.tr(), 'light'),
        const Spacer(),
        DropdownButton<double>(
          value:     radiusKm,
          underline: const SizedBox.shrink(),
          isDense:   true,
          style:     const TextStyle(fontSize: 12, color: Colors.black87),
          items:     [50.0, 100.0, 200.0, 500.0].map((r) => DropdownMenuItem(
            value: r,
            child: Text('${r.toInt()} km'),
          )).toList(),
          onChanged: (v) { if (v != null) onRadius(v); },
        ),
      ]),
    );
  }
}

// ── Truck list ────────────────────────────────────────────────────────────

class _TruckList extends StatelessWidget {
  final List<NearbyTruck> trucks;
  final NearbyTruck? selected;
  final void Function(NearbyTruck) onSelect;

  const _TruckList({
    required this.trucks,
    required this.selected,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    if (trucks.isEmpty) {
      return Center(
        child: Text(
          'tracking.no_nearby_trucks'.tr(),
          style: TextStyle(color: Colors.grey.shade500, fontSize: 13),
        ),
      );
    }

    return ListView.separated(
      itemCount: trucks.length,
      separatorBuilder: (_, __) =>
          const Divider(height: 0, indent: 16, endIndent: 16),
      itemBuilder: (_, i) {
        final t          = trucks[i];
        final isSelected = selected?.vehicleId == t.vehicleId;

        return InkWell(
          onTap: () => onSelect(t),
          child: Container(
            color: isSelected
                ? _green.withValues(alpha: 0.05)
                : Colors.transparent,
            padding:
                const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            child: Row(children: [
              CircleAvatar(
                radius: 20,
                backgroundColor: _green.withValues(alpha: 0.1),
                child: const Icon(Icons.local_shipping_rounded,
                    color: _green, size: 18),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(t.driverName,
                        style: const TextStyle(
                            fontSize: 13, fontWeight: FontWeight.w600)),
                    Text(
                      '${t.vehicleType} · ${t.vehiclePlate}',
                      style: const TextStyle(
                          fontSize: 11, color: Colors.grey),
                    ),
                  ],
                ),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    '${t.distanceKm.toStringAsFixed(1)} km',
                    style: const TextStyle(
                        fontSize: 12, fontWeight: FontWeight.w600),
                  ),
                  Row(mainAxisSize: MainAxisSize.min, children: [
                    const Icon(Icons.star_rounded,
                        color: _amber, size: 12),
                    const SizedBox(width: 2),
                    Text(t.rating.toStringAsFixed(1),
                        style: const TextStyle(fontSize: 11)),
                  ]),
                ],
              ),
            ]),
          ),
        );
      },
    );
  }
}

// ── Popup card ────────────────────────────────────────────────────────────

class _TruckPopup extends StatelessWidget {
  final NearbyTruck truck;
  final VoidCallback onClose;

  const _TruckPopup({required this.truck, required this.onClose});

  @override
  Widget build(BuildContext context) {
    return Material(
      elevation: 4,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 10, 10, 10),
        child: Row(children: [
          const Icon(Icons.local_shipping_rounded, color: _green, size: 22),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(truck.driverName,
                    style: const TextStyle(
                        fontSize: 13, fontWeight: FontWeight.w700)),
                Text(
                  '${truck.vehicleType} · ${truck.vehiclePlate} · '
                  '${truck.distanceKm.toStringAsFixed(1)} km',
                  style: const TextStyle(fontSize: 11, color: Colors.grey),
                ),
                if (truck.currentCity != null)
                  Text(truck.currentCity!,
                      style: const TextStyle(
                          fontSize: 11, color: Colors.grey)),
              ],
            ),
          ),
          Row(mainAxisSize: MainAxisSize.min, children: [
            const Icon(Icons.star_rounded, color: _amber, size: 14),
            const SizedBox(width: 2),
            Text(truck.rating.toStringAsFixed(1),
                style: const TextStyle(
                    fontSize: 12, fontWeight: FontWeight.w600)),
          ]),
          const SizedBox(width: 6),
          IconButton(
            icon: const Icon(Icons.close, size: 18, color: Colors.grey),
            onPressed: onClose,
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(),
          ),
        ]),
      ),
    );
  }
}

// ── Map pins ──────────────────────────────────────────────────────────────

class _TruckPin extends StatelessWidget {
  final bool selected;
  const _TruckPin({required this.selected});

  @override
  Widget build(BuildContext context) => Container(
        decoration: BoxDecoration(
          color:       selected ? _amber : _green,
          shape:       BoxShape.circle,
          border:      Border.all(color: Colors.white, width: 2),
          boxShadow: [
            BoxShadow(
              color:      Colors.black.withValues(alpha: 0.2),
              blurRadius: 6,
              offset:     const Offset(0, 2),
            ),
          ],
        ),
        child: Icon(
          Icons.local_shipping_rounded,
          color: Colors.white,
          size:  selected ? 26 : 20,
        ),
      );
}

class _CenterPin extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Container(
        decoration: BoxDecoration(
          color:       Colors.white,
          shape:       BoxShape.circle,
          border:      Border.all(color: _green, width: 2.5),
          boxShadow: [
            BoxShadow(
              color:      Colors.black.withValues(alpha: 0.15),
              blurRadius: 4,
            ),
          ],
        ),
        child: const Icon(Icons.my_location_rounded,
            color: _green, size: 18),
      );
}
