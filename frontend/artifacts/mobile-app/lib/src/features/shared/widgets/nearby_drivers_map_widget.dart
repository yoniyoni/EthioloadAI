import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../config/map_config.dart';
import '../../../data/models/models.dart';
import '../../../data/repositories/repositories.dart';
import 'shared_widgets.dart';

/// Map widget displaying nearby available drivers around a pickup location.
/// Used by shippers during cargo creation to visualize available trucks.
class NearbyDriversMapWidget extends ConsumerStatefulWidget {
  final double pickupLat;
  final double pickupLng;
  final String? pickupName;
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
  List<NearbyDriver> drivers = [];
  bool isLoading = true;
  String? errorMessage;

  @override
  void initState() {
    super.initState();
    _mapController = MapController();
    _loadNearbyDrivers();
  }

  Future<void> _loadNearbyDrivers() async {
    setState(() {
      isLoading = true;
      errorMessage = null;
    });

    try {
      final cargoRepository = ref.read(cargoRepositoryProvider);
      final nearbyDrivers = await cargoRepository.nearbyDriversByLocation(
        widget.pickupLat,
        widget.pickupLng,
        serviceType: widget.serviceType,
      );
      setState(() {
        drivers = nearbyDrivers;
        isLoading = false;
      });
    } catch (e) {
      setState(() {
        errorMessage = 'Failed to load nearby drivers: $e';
        isLoading = false;
      });
    }
  }

  @override
  void didUpdateWidget(NearbyDriversMapWidget oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.pickupLat != widget.pickupLat ||
        oldWidget.pickupLng != widget.pickupLng) {
      _loadNearbyDrivers();
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
                  const Text('Loading nearby drivers...'),
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
              : drivers.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.local_shipping,
                              color: Colors.grey[400], size: 40),
                          const SizedBox(height: 12),
                          const Text(
                            'No nearby drivers available',
                            style: TextStyle(color: Colors.grey),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            'Trucks matching your cargo will appear here',
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
                                // Driver location markers
                                ...drivers.map((driver) {
                                  if (driver.latitude == null ||
                                      driver.longitude == null) {
                                    return Marker(
                                      point: LatLng(0, 0),
                                      child: SizedBox.shrink(),
                                    );
                                  }
                                  return Marker(
                                    point: LatLng(
                                      driver.latitude!,
                                      driver.longitude!,
                                    ),
                                    width: 50,
                                    height: 50,
                                    child: GestureDetector(
                                      onTap: () {
                                        _showDriverInfo(context, driver);
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
                        // Driver info list (bottom sheet style)
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
                                          '${drivers.length} Trucks Available',
                                          style: const TextStyle(
                                            fontWeight: FontWeight.bold,
                                            fontSize: 14,
                                          ),
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 12),
                                    ...drivers.take(3).map((driver) {
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
                                                      driver.name,
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
                                                          '${driver.rating} • ${driver.distanceKm?.toStringAsFixed(1) ?? '?'} km',
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
                                    if (drivers.length > 3)
                                      Center(
                                        child: Text(
                                          '+${drivers.length - 3} more nearby',
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

  void _showDriverInfo(BuildContext context, NearbyDriver driver) {
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
                        driver.name,
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
                            '${driver.rating} (${driver.tripCount} trips)',
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
              driver.truckType,
              Icons.local_shipping,
            ),
            const SizedBox(height: 12),
            _buildInfoRow(
              'Plate Number',
              driver.plateNumber,
              Icons.directions_car,
            ),
            const SizedBox(height: 12),
            _buildInfoRow(
              'Distance',
              '${driver.distanceKm?.toStringAsFixed(1) ?? '?'} km',
              Icons.location_on,
            ),
            const SizedBox(height: 12),
            _buildInfoRow(
              'Last Seen',
              driver.lastSeen,
              Icons.access_time,
            ),
            if (driver.vehicleCategory != null) ...[
              const SizedBox(height: 12),
              _buildInfoRow(
                'Category',
                driver.vehicleCategory!,
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
