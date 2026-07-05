import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';
import 'package:easy_localization/easy_localization.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';

const _green     = Color(0xFF0F3D1A);
const _greenFill = Color(0xFF1B5E20);
const _amber     = Color(0xFFF59E0B);
const _bg        = Color(0xFFF8FBF8);

/// Full-screen driver navigation screen.
/// Shows OSRM route polyline, turn-by-turn steps, and current position.
class NavigationScreen extends ConsumerStatefulWidget {
  final double destLat;
  final double destLng;
  final String destName;
  final double? originLat;
  final double? originLng;

  const NavigationScreen({
    super.key,
    required this.destLat,
    required this.destLng,
    required this.destName,
    this.originLat,
    this.originLng,
  });

  @override
  ConsumerState<NavigationScreen> createState() => _NavigationScreenState();
}

class _NavigationScreenState extends ConsumerState<NavigationScreen> {
  RouteResult? _route;
  bool _loading   = true;
  bool _showSteps = false;
  final MapController _mapCtrl = MapController();

  static const _defaultCenter = LatLng(9.0310, 38.7469);

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _fetchRoute());
  }

  Future<void> _fetchRoute() async {
    setState(() => _loading = true);
    try {
      final origin = await _resolveOrigin();
      if (origin == null) {
        if (mounted) setState(() => _loading = false);
        return;
      }
      final route = await ref.read(routingRepositoryProvider).getRoute(
        fromLat: origin.latitude,
        fromLng: origin.longitude,
        toLat:   widget.destLat,
        toLng:   widget.destLng,
      );
      if (mounted) {
        setState(() {
          _route   = route;
          _loading = false;
        });
        if (route != null && route.polyline.isNotEmpty) {
          final mid = route.polyline[route.polyline.length ~/ 2];
          _mapCtrl.move(LatLng(mid[1], mid[0]), 9.5);
        }
      }
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<LatLng?> _resolveOrigin() async {
    if (widget.originLat != null && widget.originLng != null) {
      return LatLng(widget.originLat!, widget.originLng!);
    }
    // Use Addis Ababa as fallback origin when position unknown
    return const LatLng(9.0320, 38.7469);
  }

  List<LatLng> get _routePoints => (_route?.polyline ?? [])
      .map((p) => LatLng(p[1], p[0])) // OSRM returns [lng,lat]
      .toList();

  @override
  Widget build(BuildContext context) {
    final dest = LatLng(widget.destLat, widget.destLng);

    return Scaffold(
      backgroundColor: _bg,
      appBar: AppBar(
        backgroundColor: _green,
        elevation: 0,
        iconTheme: const IconThemeData(color: Colors.white),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('navigation.navigate'.tr(),
                style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.w600,
                    fontSize: 16)),
            Text(
              widget.destName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: Colors.white70, fontSize: 12),
            ),
          ],
        ),
        actions: [
          if (_route != null)
            TextButton.icon(
              onPressed: () => setState(() => _showSteps = !_showSteps),
              icon: Icon(
                _showSteps ? Icons.map_outlined : Icons.list_rounded,
                color: Colors.white,
                size: 18,
              ),
              label: Text(
                _showSteps ? 'navigation.map'.tr() : 'navigation.steps'.tr(),
                style: const TextStyle(color: Colors.white, fontSize: 13),
              ),
            ),
          IconButton(
            icon: const Icon(Icons.refresh_rounded, color: Colors.white),
            onPressed: _fetchRoute,
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator(color: _green))
          : _showSteps && _route != null
              ? _StepsList(route: _route!, destName: widget.destName)
              : Stack(
                  children: [
                    FlutterMap(
                      mapController: _mapCtrl,
                      options: MapOptions(
                        initialCenter: _routePoints.isNotEmpty
                            ? _routePoints[_routePoints.length ~/ 2]
                            : dest,
                        initialZoom: 9.5,
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
                        if (_routePoints.length >= 2)
                          PolylineLayer(
                            polylines: [
                              Polyline(
                                points:      _routePoints,
                                strokeWidth: 4.5,
                                color:       _green.withValues(alpha: 0.85),
                              ),
                            ],
                          ),
                        MarkerLayer(markers: [
                          // Destination pin
                          Marker(
                            point:  dest,
                            width:  44,
                            height: 44,
                            child:  _DestPin(),
                          ),
                          // Origin pin (if provided)
                          if (widget.originLat != null && widget.originLng != null)
                            Marker(
                              point:  LatLng(widget.originLat!, widget.originLng!),
                              width:  44,
                              height: 44,
                              child:  _OriginPin(),
                            ),
                        ]),
                      ],
                    ),

                    // Route summary card at bottom
                    if (_route != null)
                      Positioned(
                        left: 12, right: 12, bottom: 20,
                        child: _RouteSummaryCard(route: _route!),
                      ),

                    // Recenter button
                    Positioned(
                      right: 12,
                      bottom: _route != null ? 110 : 20,
                      child: FloatingActionButton.small(
                        backgroundColor: Colors.white,
                        onPressed: () {
                          _mapCtrl.move(
                            _routePoints.isNotEmpty
                                ? _routePoints[_routePoints.length ~/ 2]
                                : dest,
                            9.5,
                          );
                        },
                        child: const Icon(Icons.my_location_rounded,
                            color: _green, size: 20),
                      ),
                    ),
                  ],
                ),
    );
  }
}

// ── Route summary card ────────────────────────────────────────────────────

class _RouteSummaryCard extends StatelessWidget {
  final RouteResult route;
  const _RouteSummaryCard({required this.route});

  @override
  Widget build(BuildContext context) {
    final isOsrm = route.source == 'osrm';
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(14),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.12),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(children: [
        _Stat(
          icon:  Icons.straighten_rounded,
          value: '${route.distanceKm.toStringAsFixed(0)} km',
          label: 'navigation.distance'.tr(),
        ),
        const _Divider(),
        _Stat(
          icon:  Icons.timer_outlined,
          value: '${route.durationMin} min',
          label: 'navigation.eta'.tr(),
        ),
        if (!isOsrm) ...[
          const _Divider(),
          const Expanded(
            child: Row(children: [
              Icon(Icons.info_outline, size: 13, color: Colors.orange),
              SizedBox(width: 4),
              Flexible(
                child: Text(
                  'Est.',
                  style: TextStyle(fontSize: 11, color: Colors.orange),
                ),
              ),
            ]),
          ),
        ],
      ]),
    );
  }
}

class _Stat extends StatelessWidget {
  final IconData icon;
  final String value;
  final String label;
  const _Stat({required this.icon, required this.value, required this.label});

  @override
  Widget build(BuildContext context) => Expanded(
        child: Row(children: [
          Icon(icon, size: 18, color: const Color(0xFF0F3D1A)),
          const SizedBox(width: 6),
          Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(value,
                  style: const TextStyle(
                      fontSize: 15, fontWeight: FontWeight.w700)),
              Text(label,
                  style: const TextStyle(fontSize: 11, color: Colors.grey)),
            ],
          ),
        ]),
      );
}

class _Divider extends StatelessWidget {
  const _Divider();
  @override
  Widget build(BuildContext context) => Container(
        height: 32,
        width: 1,
        margin: const EdgeInsets.symmetric(horizontal: 12),
        color: Colors.grey.shade200,
      );
}

// ── Turn-by-turn steps list ───────────────────────────────────────────────

class _StepsList extends StatelessWidget {
  final RouteResult route;
  final String destName;
  const _StepsList({required this.route, required this.destName});

  IconData _maneuverIcon(String maneuver) {
    return switch (maneuver) {
      'turn right'      => Icons.turn_right_rounded,
      'turn left'       => Icons.turn_left_rounded,
      'turn sharp right'=> Icons.turn_sharp_right_rounded,
      'turn sharp left' => Icons.turn_sharp_left_rounded,
      'turn slight right'=> Icons.turn_slight_right_rounded,
      'turn slight left' => Icons.turn_slight_left_rounded,
      'u turn'          => Icons.u_turn_right_rounded,
      'roundabout'      => Icons.rotate_right_rounded,
      'arrive'          => Icons.flag_rounded,
      _                 => Icons.straight_rounded,
    };
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          color: Colors.white,
          child: Row(children: [
            const Icon(Icons.flag_rounded, color: Color(0xFF0F3D1A), size: 18),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                destName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
              ),
            ),
            Text(
              '${route.distanceKm.toStringAsFixed(0)} km · ${route.durationMin} min',
              style: const TextStyle(fontSize: 12, color: Colors.grey),
            ),
          ]),
        ),
        Expanded(
          child: ListView.separated(
            itemCount: route.steps.length,
            separatorBuilder: (_, __) =>
                const Divider(height: 0, indent: 50),
            itemBuilder: (_, i) {
              final step = route.steps[i];
              return ListTile(
                leading: CircleAvatar(
                  radius: 18,
                  backgroundColor: const Color(0xFF0F3D1A).withValues(alpha: 0.1),
                  child: Icon(_maneuverIcon(step.maneuver),
                      color: const Color(0xFF0F3D1A), size: 18),
                ),
                title: Text(
                  step.instruction.isNotEmpty
                      ? step.instruction
                      : 'Continue',
                  style: const TextStyle(fontSize: 13),
                ),
                subtitle: Text(step.distanceLabel,
                    style: const TextStyle(
                        fontSize: 11, color: Colors.grey)),
                dense: true,
              );
            },
          ),
        ),
      ],
    );
  }
}

// ── Map pins ──────────────────────────────────────────────────────────────

class _DestPin extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Container(
        decoration: BoxDecoration(
          color:        _amber,
          shape:        BoxShape.circle,
          border:       Border.all(color: Colors.white, width: 2),
          boxShadow: [
            BoxShadow(
              color:      Colors.black.withValues(alpha: 0.25),
              blurRadius: 6,
              offset:     const Offset(0, 2),
            ),
          ],
        ),
        child: const Icon(Icons.flag_rounded, color: Colors.white, size: 22),
      );
}

class _OriginPin extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Container(
        decoration: BoxDecoration(
          color:        _greenFill,
          shape:        BoxShape.circle,
          border:       Border.all(color: Colors.white, width: 2),
          boxShadow: [
            BoxShadow(
              color:      Colors.black.withValues(alpha: 0.25),
              blurRadius: 6,
              offset:     const Offset(0, 2),
            ),
          ],
        ),
        child: const Icon(Icons.local_shipping_rounded,
            color: Colors.white, size: 20),
      );
}
