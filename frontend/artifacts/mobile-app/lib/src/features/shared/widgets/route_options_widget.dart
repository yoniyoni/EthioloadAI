import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:easy_localization/easy_localization.dart';
import '../../../data/models/models.dart';
import '../../../data/repositories/repositories.dart';

const _green  = Color(0xFF0F3D1A);
const _border = Color(0xFFE5E7EB);

/// Informational route card for cargo/freight detail screens.
/// Fetches an OSRM route and displays distance, duration, and via points.
/// Non-interactive — for information only (Feature 2).
class RouteOptionsWidget extends ConsumerStatefulWidget {
  final double fromLat;
  final double fromLng;
  final double toLat;
  final double toLng;
  final String fromName;
  final String toName;

  const RouteOptionsWidget({
    super.key,
    required this.fromLat,
    required this.fromLng,
    required this.toLat,
    required this.toLng,
    required this.fromName,
    required this.toName,
  });

  @override
  ConsumerState<RouteOptionsWidget> createState() => _RouteOptionsWidgetState();
}

class _RouteOptionsWidgetState extends ConsumerState<RouteOptionsWidget> {
  RouteResult? _route;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _fetchRoute());
  }

  Future<void> _fetchRoute() async {
    setState(() => _loading = true);
    try {
      final route = await ref.read(routingRepositoryProvider).getRoute(
        fromLat: widget.fromLat,
        fromLng: widget.fromLng,
        toLat:   widget.toLat,
        toLng:   widget.toLng,
      );
      if (mounted) setState(() { _route = route; _loading = false; });
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(top: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color:        Colors.white,
        borderRadius: BorderRadius.circular(12),
        border:       Border.all(color: _border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            const Icon(Icons.route_rounded, size: 16, color: _green),
            const SizedBox(width: 6),
            Text('navigation.best_route'.tr(),
                style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: _green)),
            const Spacer(),
            if (!_loading && _route != null && _route!.source != 'osrm')
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color:        Colors.orange.shade50,
                  borderRadius: BorderRadius.circular(4),
                  border:       Border.all(color: Colors.orange.shade200),
                ),
                child: Text('navigation.estimate'.tr(),
                    style: const TextStyle(
                        fontSize: 10, color: Colors.orange)),
              ),
          ]),
          const SizedBox(height: 10),

          if (_loading)
            const Center(
              child: Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: CircularProgressIndicator(strokeWidth: 2, color: _green),
              ),
            )
          else if (_route == null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 8),
              child: Text(
                'navigation.route_unavailable'.tr(),
                style: TextStyle(fontSize: 12, color: Colors.grey.shade500),
              ),
            )
          else ...[
            Row(children: [
              _RouteChip(
                icon:  Icons.straighten_rounded,
                value: '${_route!.distanceKm.toStringAsFixed(0)} km',
              ),
              const SizedBox(width: 10),
              _RouteChip(
                icon:  Icons.timer_outlined,
                value: '${_route!.durationMin} min',
              ),
            ]),
            const SizedBox(height: 8),
            Row(children: [
              const Icon(Icons.place_outlined, size: 12, color: Colors.grey),
              const SizedBox(width: 4),
              Expanded(
                child: Text(
                  '${widget.fromName} → ${widget.toName}',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 11, color: Colors.grey),
                ),
              ),
            ]),
          ],
        ],
      ),
    );
  }
}

class _RouteChip extends StatelessWidget {
  final IconData icon;
  final String value;
  const _RouteChip({required this.icon, required this.value});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
        decoration: BoxDecoration(
          color:        const Color(0xFF0F3D1A).withValues(alpha: 0.07),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          Icon(icon, size: 13, color: const Color(0xFF0F3D1A)),
          const SizedBox(width: 4),
          Text(value,
              style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: Color(0xFF0F3D1A))),
        ]),
      );
}
