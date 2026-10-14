import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../config/map_config.dart';
import '../../../data/models/models.dart';
import '../../../data/repositories/repositories.dart';
import 'shared_widgets.dart';

/// Map widget displaying nearby available vehicles around a pickup location.
class NearbyDriversMapWidget extends ConsumerStatefulWidget {
  final double pickupLat;
  final double pickupLng;
  final String? pickupName;
  @Deprecated('The Spring nearby-vehicle endpoint does not filter by service type.')
  final String serviceType;
  final double height;
  final VoidCallback? onDriverSelected;

  const NearbyDriversMapWidget({
    required this.pickupLat,
    required this.pickupLng,
    this.pickupName,
    this.serviceType = 'intercity',
    this.height = 300,
    this.onDriverSelected,
    super.key,
  });

  @override
  ConsumerState<NearbyDriversMapWidget> createState() =>
      _NearbyDriversMapWidgetState();
}

class _NearbyDriversMapWidgetState
    extends ConsumerState<NearbyDriversMapWidget> {
  late MapController _mapController;
  List<Vehicle> vehicles = [];
  bool isLoading = true;
  String? errorMessage;

  @override
  void initState() {
    super.initState();
    _mapController = MapController();
    _loadNearbyVehicles();
  }

  Future<void> _loadNearbyVehicles() async {
    setState(() {
      isLoading = true;
      errorMessage = null;
    });

    try {
      final cargoRepository = ref.read(cargoRepositoryProvider);
      final nearbyVehicles = await cargoRepository.nearbyVehiclesByLocation(
        widget.pickupLat,
        widget.pickupLng,
      );
      if (!mounted) return;
      setState(() {
        vehicles = nearbyVehicles;
        isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        errorMessage = 'Failed to load nearby vehicles: $e';
        isLoading = false;
      });
    }
  }

  @override
  void didUpdateWidget(NearbyDriversMapWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.pickupLat != widget.pickupLat ||
        oldWidget.pickupLng != widget.pickupLng) {
      _loadNearbyVehicles();
    }
  }

  @override
  void dispose() {
    _mapController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: widget.height,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: kGreen.withOpacity(0.3)),
      ),
      child: isLoading
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const CircularProgressIndicator(
                      valueColor: AlwaysStoppedAnimation(kGreen)),
                  const SizedBox(height: 12),
                  const Text('Loading nearby vehicles...'),
                ],
              ),
            )
          : errorMessage != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.error_outline,
                            color: Colors.red, size: 32),
                        const SizedBox(height: 12),
                        Text(
                          errorMessage!,
                          textAlign: TextAlign.center,
                          style: const TextStyle(color: Colors.red),
                        ),
                      ],
                    ),
                  ),
                )
              : vehicles.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.local_shipping,
                              color: Colors.grey[400], size: 40),
                          const SizedBox(height: 12),
                          const Text(
                            'No nearby vehicles available',
                            style: TextStyle(color: Colors.grey),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            'Available trucks near this pickup will appear here',
                            style: TextStyle(
                              color: Colors.grey[500],
                              fontSize: 12,
                            ),
                          ),
                        ],
                      ),
                    )
                  : Stack(
                      children: [
                        FlutterMap(
                          mapController: _mapController,
                          options: MapOptions(
                            initialCenter: LatLng(
                              widget.pickupLat,
                              widget.pickupLng,
                            ),
                            initialZoom: 13,
                            minZoom: 5,
                            maxZoom: 18,
                          ),
                          children: [
                            TileLayer(
                              urlTemplate: kMapTileUrlTemplate,
                              userAgentPackageName: 'com.ethioloadai.app',
                            ),
                            MarkerLayer(
                              markers: [
                                // Pickup location marker
                                Marker(
                                  point: LatLng(
                                    widget.pickupLat,
                                    widget.pickupLng,
                                  ),
                                  width: 60,
                                  height: 60,
                                  child: Column(
                                    children: [
                                      Container(
                                        width: 40,
                                        height: 40,
                                        decoration: BoxDecoration(
                                          color: kGreen,
                                          shape: BoxShape.circle,
                                          boxShadow: [
                                            BoxShadow(
                                              color: kGreen.withOpacity(0.4),
                                              blurRadius: 8,
                                              spreadRadius: 2,
                                            ),
                                          ],
                                        ),
                                        child: const Icon(
                                          Icons.location_on,
                                          color: Colors.white,
                                          size: 24,
                                        ),
                                      ),
                                      const SizedBox(height: 4),
                                      Flexible(
                                        child: Container(
                                          padding: const EdgeInsets.symmetric(
                                            horizontal: 6,
                                            vertical: 2,
                                          ),
                                          decoration: BoxDecoration(
                                            color: kGreen,
                                            borderRadius:
                                                BorderRadius.circular(4),
                                          ),
                                          child: Text(
                                            widget.pickupName ?? 'Pickup',
                                            style: const TextStyle(
                                              color: Colors.white,
                                              fontSize: 10,
                                              fontWeight: FontWeight.bold,
                                            ),
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                // Spring returns vehicle locations, not driver profiles.
                                ...vehicles.where((vehicle) =>
                                    vehicle.latitude != null &&
                                    vehicle.longitude != null).map((vehicle) {
                                  return Marker(
                                    point: LatLng(
                                      vehicle.latitude!,
                                      vehicle.longitude!,
                                    ),
                                    width: 50,
                                    height: 50,
                                    child: GestureDetector(
                                      onTap: () {
                                        _showVehicleInfo(context, vehicle);
                                      },
                                      child: Column(
                                        children: [
                                          Container(
                                            width: 40,
                                            height: 40,
                                            decoration: BoxDecoration(
                                              color: Colors.blue,
                                              shape: BoxShape.circle,
                                              border: Border.all(
                                                color: Colors.white,
                                                width: 2,
                                              ),
                                              boxShadow: [
                                                BoxShadow(
                                                  color: Colors.blue
                                                      .withOpacity(0.4),
                                                  blurRadius: 8,
                                                  spreadRadius: 2,
                                                ),
                                              ],
                                            ),
                                            child: const Icon(
                                              Icons.directions_car,
                                              color: Colors.white,
                                              size: 20,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ),
                                  );
                                }).toList(),
                              ],
                            ),
                            kMapAttribution,
                          ],
                        ),
                        // Nearby vehicle list
                        Positioned(
                          bottom: 0,
                          left: 0,
                          right: 0,
                          child: Container(
                            decoration: BoxDecoration(
                              color: Colors.white,
                              borderRadius: const BorderRadius.only(
                                topLeft: Radius.circular(16),
                                topRight: Radius.circular(16),
                              ),
                              boxShadow: [
                                BoxShadow(
                                  color: Colors.black.withOpacity(0.1),
                                  blurRadius: 8,
                                  offset: const Offset(0, -2),
                                ),
                              ],
                            ),
                            child: SingleChildScrollView(
                              child: Padding(
                                padding: const EdgeInsets.all(16),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Row(
                                      children: [
                                        const Icon(Icons.local_shipping,
                                            color: kGreen, size: 20),
                                        const SizedBox(width: 8),
                                        Text(
                                          '${vehicles.length} Trucks Available',
                                          style: const TextStyle(
                                            fontWeight: FontWeight.bold,
                                            fontSize: 14,
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 12),
                                    ...vehicles.take(3).map((vehicle) {
                                      return Padding(
                                        padding:
                                            const EdgeInsets.only(bottom: 12),
                                        child: Container(
                                          padding: const EdgeInsets.all(12),
                                          decoration: BoxDecoration(
                                            border: Border.all(
                                              color:
                                                  Colors.grey.withOpacity(0.2),
                                            ),
                                            borderRadius:
                                                BorderRadius.circular(8),
                                          ),
                                          child: Row(
                                            children: [
                                              Container(
                                                width: 40,
                                                height: 40,
                                                decoration: BoxDecoration(
                                                  color:
                                                      kGreen.withOpacity(0.1),
                                                  shape: BoxShape.circle,
                                                ),
                                                child: const Icon(
                                                  Icons.directions_car,
                                                  color: kGreen,
                                                  size: 20,
                                                ),
                                              ),
                                              const SizedBox(width: 12),
                                              Expanded(
                                                child: Column(
                                                  crossAxisAlignment:
                                                      CrossAxisAlignment.start,
                                                  children: [
                                                    Text(
                                                      'Vehicle ${vehicle.plateNumber}',
                                                      style: const TextStyle(
                                                        fontWeight:
                                                            FontWeight.bold,
                                                        fontSize: 13,
                                                      ),
                                                      maxLines: 1,
                                                      overflow:
                                                          TextOverflow.ellipsis,
                                                    ),
                                                    const SizedBox(height: 4),
                                                    Row(
                                                      children: [
                                                        Icon(Icons.star,
                                                            size: 14,
                                                            color: Colors
                                                                .amber[600]),
                                                        const SizedBox(
                                                            width: 4),
                                                        Text(
                                                          '${vehicle.rating.toStringAsFixed(1)} vehicle rating • ${_distanceKm(vehicle)?.toStringAsFixed(1) ?? '?'} km',
                                                          style:
                                                              const TextStyle(
                                                            fontSize: 12,
                                                            color: Colors.grey,
                                                          ),
                                                        ),
                                                      ],
                                                    ),
                                                  ],
                                                ),
                                              ),
                                            ],
                                          ),
                                        ),
                                      );
                                    }).toList(),
                                    if (vehicles.length > 3)
                                      Center(
                                        child: Text(
                                          '+${vehicles.length - 3} more nearby',
                                          style: TextStyle(
                                            color: Colors.grey[600],
                                            fontSize: 12,
                                          ),
                                        ),
                                      ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
    );
  }

  double? _distanceKm(Vehicle vehicle) {
    final latitude = vehicle.latitude;
    final longitude = vehicle.longitude;
    if (latitude == null || longitude == null) return null;
    return const Distance().as(
      LengthUnit.Kilometer,
      LatLng(widget.pickupLat, widget.pickupLng),
      LatLng(latitude, longitude),
    );
  }

  void _showVehicleInfo(BuildContext context, Vehicle vehicle) {
    showModalBottomSheet(
      context: context,
      builder: (context) => Container(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Container(
                  width: 50,
                  height: 50,
                  decoration: BoxDecoration(
                    color: kGreen.withOpacity(0.1),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.directions_car, color: kGreen),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Vehicle ${vehicle.plateNumber}',
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Row(
                        children: [
                          Icon(Icons.star, size: 14, color: Colors.amber[600]),
                          const SizedBox(width: 4),
                          Text(
                            '${vehicle.rating.toStringAsFixed(1)} vehicle rating',
                            style: const TextStyle(color: Colors.grey),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            _buildInfoRow(
              'Vehicle Type',
              vehicle.truckType,
              Icons.local_shipping,
            ),
            const SizedBox(height: 12),
            _buildInfoRow(
              'Plate Number',
              vehicle.plateNumber,
              Icons.directions_car,
            ),
            const SizedBox(height: 12),
            _buildInfoRow(
              'Distance',
              '${_distanceKm(vehicle)?.toStringAsFixed(1) ?? '?'} km',
              Icons.location_on,
            ),
            const SizedBox(height: 12),
            _buildInfoRow(
              'Availability',
              vehicle.availabilityStatus,
              Icons.check_circle_outline,
            ),
            if (vehicle.vehicleCategory != null) ...[
              const SizedBox(height: 12),
              _buildInfoRow(
                'Category',
                vehicle.vehicleCategory!,
                Icons.category,
              ),
            ],
            const SizedBox(height: 20),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: () {
                  Navigator.pop(context);
                  widget.onDriverSelected?.call();
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: kGreen,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                ),
                child: const Text(
                  'Close',
                  style: TextStyle(color: Colors.white),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String value, IconData icon) {
    return Row(
      children: [
        Icon(icon, color: kGreen, size: 20),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                label,
                style: const TextStyle(
                  color: Colors.grey,
                  fontSize: 12,
                ),
              ),
              const SizedBox(height: 4),
              Text(
                value,
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 14,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
