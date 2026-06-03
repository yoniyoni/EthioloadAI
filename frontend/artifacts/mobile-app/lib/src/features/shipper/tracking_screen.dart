import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import '../../data/providers/data_providers.dart';
import '../../data/models/models.dart';
import '../../data/repositories/repositories.dart';

/// TrackingScreen
/// - For shippers: shows the current GPS position of the trip.
/// - For drivers: additionally streams their live location to the backend.
class TrackingScreen extends ConsumerStatefulWidget {
  final int freightId; // used as booking/trip lookup key
  const TrackingScreen({required this.freightId, Key? key}) : super(key: key);

  @override
  ConsumerState<TrackingScreen> createState() => _TrackingScreenState();
}

class _TrackingScreenState extends ConsumerState<TrackingScreen> {
  StreamSubscription<Position>? _locationSub;
  int? _tripId;
  double? _currentLat;
  double? _currentLng;
  String _lastUpdateText = 'Never';
  bool _isTracking = false;

  @override
  void dispose() {
    _locationSub?.cancel();
    super.dispose();
  }

  // ── Driver-side: start pushing GPS updates to the backend ──────────────
  Future<void> _startTracking(int tripId) async {
    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      _showSnack('Location services are disabled.');
      return;
    }
    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) {
        _showSnack('Location permission denied.');
        return;
      }
    }

    setState(() => _isTracking = true);

    _locationSub = Geolocator.getPositionStream(
      locationSettings: const LocationSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: 30, // push every 30 metres
      ),
    ).listen((pos) async {
      setState(() {
        _currentLat = pos.latitude;
        _currentLng = pos.longitude;
        _lastUpdateText = TimeOfDay.now().format(context);
      });
      try {
        await ref
            .read(tripRepositoryProvider)
            .updateLocation(tripId, pos.latitude, pos.longitude);
      } catch (_) {
        // silently fail — will retry on next position event
      }
    });
  }

  void _stopTracking() {
    _locationSub?.cancel();
    setState(() => _isTracking = false);
  }

  void _showSnack(String msg) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    final isDriver = ref.read(authNotifierProvider).user?.isDriver ?? false;

    // Try to load the booking for this cargo, then its trip
    final bookingAsync = ref.watch(bookingListProvider);

    return bookingAsync.when(
      loading: () => Scaffold(
        appBar: AppBar(title: const Text('Live Tracking')),
        body: const Center(child: CircularProgressIndicator()),
      ),
      error: (e, _) => Scaffold(
        appBar: AppBar(title: const Text('Live Tracking')),
        body: Center(child: Text('Error: $e')),
      ),
      data: (bookings) {
        // Find booking for this cargo request
        Booking? booking;
        try {
          booking = bookings.firstWhere((b) => b.cargoId == widget.freightId);
        } catch (_) {}

        return Scaffold(
          appBar: AppBar(
            title: const Text('Live Tracking'),
            elevation: 0,
            actions: [
              if (isDriver && booking != null)
                TextButton(
                  onPressed: _isTracking
                      ? _stopTracking
                      : () async {
                          // Start trip if needed, then track
                          if (_tripId == null) {
                            try {
                              final trip = await ref
                                  .read(tripRepositoryProvider)
                                  .start(booking!.id);
                              _tripId = trip.id;
                            } catch (_) {}
                          }
                          if (_tripId != null) _startTracking(_tripId!);
                        },
                  child: Text(
                    _isTracking ? 'Stop' : 'Start',
                    style: TextStyle(
                      color: _isTracking ? Colors.red : Colors.green,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
            ],
          ),
          body: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // ── Map placeholder ──────────────────────────────────
                Container(
                  width: double.infinity,
                  height: 260,
                  decoration: BoxDecoration(
                    color: Colors.grey[200],
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Stack(
                    children: [
                      const Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.map, size: 64, color: Colors.grey),
                            SizedBox(height: 8),
                            Text('Map view',
                                style: TextStyle(color: Colors.grey)),
                            Text(
                              'Add google_maps_flutter to render live map',
                              style:
                                  TextStyle(color: Colors.grey, fontSize: 11),
                            ),
                          ],
                        ),
                      ),
                      if (_currentLat != null)
                        Positioned(
                          bottom: 12,
                          left: 12,
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 10, vertical: 6),
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: BorderRadius.circular(8),
                              boxShadow: [
                                BoxShadow(
                                    color: Colors.black.withOpacity(0.1),
                                    blurRadius: 4)
                              ],
                            ),
                            child: Text(
                              '${_currentLat!.toStringAsFixed(5)}, '
                              '${_currentLng!.toStringAsFixed(5)}',
                              style: const TextStyle(fontSize: 12),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),

                const SizedBox(height: 20),

                // ── Status card ──────────────────────────────────────
                _StatusCard(
                  booking: booking,
                  isTracking: _isTracking,
                  lastUpdate: _lastUpdateText,
                  currentLat: _currentLat,
                  currentLng: _currentLng,
                ),

                const SizedBox(height: 20),

                // ── Driver controls ──────────────────────────────────
                if (isDriver && booking != null) ...[
                  const Text('Driver Controls',
                      style: TextStyle(
                          fontSize: 14, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton.icon(
                          icon: Icon(
                              _isTracking ? Icons.stop : Icons.play_arrow),
                          label: Text(_isTracking
                              ? 'Stop Tracking'
                              : 'Start Tracking'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor:
                                _isTracking ? Colors.red : Colors.green,
                            padding:
                                const EdgeInsets.symmetric(vertical: 14),
                          ),
                          onPressed: _isTracking
                              ? _stopTracking
                              : () async {
                                  if (_tripId == null) {
                                    try {
                                      final trip = await ref
                                          .read(tripRepositoryProvider)
                                          .start(booking!.id);
                                      _tripId = trip.id;
                                    } catch (e) {
                                      _showSnack('Could not start trip: $e');
                                      return;
                                    }
                                  }
                                  _startTracking(_tripId!);
                                },
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ElevatedButton.icon(
                          icon: const Icon(Icons.check_circle),
                          label: const Text('Complete Trip'),
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.blue,
                            padding:
                                const EdgeInsets.symmetric(vertical: 14),
                          ),
                          onPressed: _tripId == null
                              ? null
                              : () async {
                                  try {
                                    await ref
                                        .read(tripRepositoryProvider)
                                        .complete(_tripId!);
                                    _stopTracking();
                                    _showSnack('Trip completed!');
                                    ref.refresh(bookingListProvider);
                                  } catch (e) {
                                    _showSnack('Error: $e');
                                  }
                                },
                        ),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}

class _StatusCard extends StatelessWidget {
  final Booking? booking;
  final bool isTracking;
  final String lastUpdate;
  final double? currentLat;
  final double? currentLng;

  const _StatusCard({
    required this.booking,
    required this.isTracking,
    required this.lastUpdate,
    this.currentLat,
    this.currentLng,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.grey[50],
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey[200]!),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isTracking ? Colors.green : Colors.grey,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                isTracking ? 'Tracking active' : 'Not tracking',
                style: TextStyle(
                    fontWeight: FontWeight.bold,
                    color: isTracking ? Colors.green : Colors.grey[700]),
              ),
            ],
          ),
          const SizedBox(height: 12),
          _Row(label: 'Booking ID',
              value: booking != null ? '#${booking!.id}' : '—'),
          _Row(label: 'Status',
              value: booking?.bookingStatus ?? '—'),
          _Row(label: 'Last update', value: lastUpdate),
          if (currentLat != null)
            _Row(
                label: 'Current GPS',
                value:
                    '${currentLat!.toStringAsFixed(5)}, ${currentLng!.toStringAsFixed(5)}'),
        ],
      ),
    );
  }
}

class _Row extends StatelessWidget {
  final String label;
  final String value;
  const _Row({required this.label, required this.value});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 5),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label,
              style: TextStyle(fontSize: 13, color: Colors.grey[600])),
          Text(value,
              style: const TextStyle(
                  fontSize: 13, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}
