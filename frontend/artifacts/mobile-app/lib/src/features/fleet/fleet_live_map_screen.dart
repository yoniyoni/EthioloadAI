import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';

import '../../data/repositories/repositories.dart';
import '../../services/spring_websocket_service.dart';

class FleetLiveMapScreen extends ConsumerStatefulWidget {
  const FleetLiveMapScreen({super.key});

  @override
  ConsumerState<FleetLiveMapScreen> createState() => _FleetLiveMapScreenState();
}

class _FleetLiveMapScreenState extends ConsumerState<FleetLiveMapScreen> {
  static const _initialCenter = LatLng(9.03, 38.75);

  final MapController _mapController = MapController();
  final Map<int, LatLng> _locations = {};
  final Map<int, String> _snapshotErrors = {};
  final Map<int, DateTime?> _locationTimes = {};
  final Map<int, SpringWebSocketService> _sockets = {};
  final Map<int, StreamSubscription<Map<String, dynamic>>> _subscriptions = {};
  final Map<int, String> _subscriptionErrors = {};

  List<Map<String, dynamic>> _vehicles = [];
  bool _loading = true;
  bool _mapReady = false;
  bool _didCenterOnVehicle = false;
  String? _loadError;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadFleet());
  }

  @override
  void dispose() {
    for (final subscription in _subscriptions.values) {
      unawaited(subscription.cancel());
    }
    for (final socket in _sockets.values) {
      unawaited(socket.dispose());
    }
    _mapController.dispose();
    super.dispose();
  }

  Future<void> _loadFleet() async {
    setState(() {
      _loading = true;
      _loadError = null;
      _snapshotErrors.clear();
      _locations.clear();
      _locationTimes.clear();
      _didCenterOnVehicle = false;
    });
    await _closeSubscriptions();

    try {
      final vehicles = await ref.read(fleetRepositoryProvider).listVehicles();
      if (!mounted) return;
      setState(() {
        _vehicles = vehicles;
        _loading = false;
      });

      for (final vehicle in vehicles) {
        final id = (vehicle['id'] as num?)?.toInt();
        if (id == null) continue;
        _subscribeToVehicle(id);
      }
      await Future.wait(vehicles.map(_loadVehicleSnapshot));
    } catch (error) {
      if (mounted) {
        setState(() {
          _loading = false;
          _loadError = error.toString();
        });
      }
    }
  }

  Future<void> _closeSubscriptions() async {
    final subscriptions = _subscriptions.values.toList();
    final sockets = _sockets.values.toList();
    _subscriptions.clear();
    _sockets.clear();
    await Future.wait(subscriptions.map((subscription) => subscription.cancel()));
    await Future.wait(sockets.map((socket) => socket.dispose()));
  }

  Future<void> _loadVehicleSnapshot(Map<String, dynamic> vehicle) async {
    final id = (vehicle['id'] as num?)?.toInt();
    if (id == null) return;
    try {
      final snapshot = await ref.read(vehicleRepositoryProvider).getLocationSnapshot(id);
      final latitude = _coordinate(snapshot['latitude']);
      final longitude = _coordinate(snapshot['longitude']);
      if (!mounted) return;
      setState(() {
        if (latitude != null && longitude != null) {
          _locations[id] = LatLng(latitude, longitude);
          _locationTimes[id] = DateTime.tryParse(
            snapshot['lastLocationAt']?.toString() ?? '',
          );
        }
        _snapshotErrors.remove(id);
      });
      _centerOnFirstLocation();
    } catch (error) {
      if (mounted) {
        setState(() => _snapshotErrors[id] = error.toString());
      }
    }
  }

  double? _coordinate(dynamic value) =>
      value == null ? null : double.tryParse(value.toString());

  void _subscribeToVehicle(int vehicleId) {
    final socket = SpringWebSocketService();
    _sockets[vehicleId] = socket;
    _subscriptions[vehicleId] = socket.messages.listen((message) {
      final type = message['type']?.toString();
      final data = message['data'];
      if (type == 'ERROR') {
        if (mounted) {
          setState(() => _subscriptionErrors[vehicleId] =
              data?.toString() ?? 'Subscription was rejected.');
        }
        return;
      }
      if (type != 'LOCATION_UPDATE' || data is! Map || !mounted) return;

      final payload = Map<String, dynamic>.from(data);
      if ((payload['vehicleId'] as num?)?.toInt() != vehicleId) return;
      final latitude = _coordinate(payload['latitude']);
      final longitude = _coordinate(payload['longitude']);
      if (latitude == null || longitude == null) return;

      setState(() {
        _locations[vehicleId] = LatLng(latitude, longitude);
        _locationTimes[vehicleId] = DateTime.tryParse(
          payload['timestamp']?.toString() ?? '',
        );
        _subscriptionErrors.remove(vehicleId);
      });
      _centerOnFirstLocation();
    });
    unawaited(_connectToVehicle(socket, vehicleId));
  }

  Future<void> _connectToVehicle(
    SpringWebSocketService socket,
    int vehicleId,
  ) async {
    try {
      await socket.connect(
        endpoint: '/ws/location',
        topic: 'vehicle.$vehicleId.location',
      );
    } on Exception catch (error) {
      if (mounted) {
        setState(() => _subscriptionErrors[vehicleId] = error.toString());
      }
    }
  }

  void _centerOnFirstLocation() {
    if (!_mapReady || _didCenterOnVehicle || _locations.isEmpty) return;
    _didCenterOnVehicle = true;
    _mapController.move(_locations.values.first, 8);
  }

  String _lastUpdated(int vehicleId) {
    final updatedAt = _locationTimes[vehicleId];
    if (updatedAt == null) return 'No location timestamp';
    return 'Updated ${MaterialLocalizations.of(context).formatTimeOfDay(
      TimeOfDay.fromDateTime(updatedAt.toLocal()),
    )}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Fleet Live Map'),
        actions: [
          IconButton(
            onPressed: _loading ? null : _loadFleet,
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh fleet locations',
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _loadError != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text('Could not load fleet vehicles: $_loadError'),
                        const SizedBox(height: 12),
                        FilledButton(
                          onPressed: _loadFleet,
                          child: const Text('Retry'),
                        ),
                      ],
                    ),
                  ),
                )
              : Column(
                  children: [
                    SizedBox(
                      height: 320,
                      child: FlutterMap(
                        mapController: _mapController,
                        options: MapOptions(
                          initialCenter: _initialCenter,
                          initialZoom: 6,
                          onMapReady: () {
                            _mapReady = true;
                            _centerOnFirstLocation();
                          },
                        ),
                        children: [
                          TileLayer(
                            urlTemplate:
                                'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                            userAgentPackageName: 'com.ethioload.ai',
                          ),
                          MarkerLayer(
                            markers: [
                              for (final vehicle in _vehicles)
                                if (_locationFor(vehicle) case final point?)
                                  Marker(
                                    point: point,
                                    width: 130,
                                    height: 54,
                                    child: Column(
                                      children: [
                                        const Icon(
                                          Icons.local_shipping,
                                          color: Colors.green,
                                          size: 30,
                                        ),
                                        Text(
                                          vehicle['plateNumber']?.toString() ??
                                              'Vehicle',
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: const TextStyle(
                                            backgroundColor: Colors.white,
                                            fontWeight: FontWeight.bold,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                            ],
                          ),
                        ],
                      ),
                    ),
                    Expanded(
                      child: _vehicles.isEmpty
                          ? const Center(child: Text('No vehicles in your fleet.'))
                          : ListView.builder(
                              itemCount: _vehicles.length,
                              itemBuilder: (context, index) {
                                final vehicle = _vehicles[index];
                                final id = (vehicle['id'] as num?)?.toInt();
                                final hasLocation =
                                    id != null && _locations.containsKey(id);
                                final error = id == null
                                    ? null
                                    : _snapshotErrors[id] ??
                                        _subscriptionErrors[id];
                                return ListTile(
                                  leading: Icon(
                                    Icons.local_shipping_outlined,
                                    color: hasLocation
                                        ? Colors.green
                                        : Colors.grey,
                                  ),
                                  title: Text(
                                    vehicle['plateNumber']?.toString() ??
                                        'Vehicle',
                                  ),
                                  subtitle: Text(
                                    error ??
                                        (hasLocation
                                            ? _lastUpdated(id)
                                            : 'No location reported yet'),
                                    maxLines: 2,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                  trailing: Text(
                                    vehicle['currentCity']?.toString() ?? '',
                                  ),
                                  onTap: hasLocation
                                      ? () => _mapController.move(
                                            _locations[id]!,
                                            12,
                                          )
                                      : null,
                                );
                              },
                            ),
                    ),
                  ],
                ),
    );
  }

  LatLng? _locationFor(Map<String, dynamic> vehicle) {
    final id = (vehicle['id'] as num?)?.toInt();
    return id == null ? null : _locations[id];
  }
}
