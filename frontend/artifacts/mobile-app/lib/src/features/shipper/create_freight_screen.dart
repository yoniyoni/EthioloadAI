import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:easy_localization/easy_localization.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import '../../config/map_config.dart';
import '../../data/repositories/repositories.dart';
import '../../services/location_service.dart';
import '../shared/widgets/shared_widgets.dart';
import '../shared/widgets/cargo_route_map_widget.dart';
import '../shared/widgets/nearby_drivers_map_widget.dart';
import '../../utils/ethiopian_cities.dart';
import '../../utils/weight_format.dart';
import '../../utils/ethiopian_date.dart';

/// Canonical, UI-facing urgency tiers. Mirrors the backend's
/// App\Models\CargoRequest::URGENCY_LEVELS and the web app's
/// freight-new.tsx URGENCY_LEVELS.
const List<String> kUrgencyLevels = ['low', 'normal', 'high', 'express'];

/// Suggests an urgency tier from how soon the target date is. The shipper
/// can always override the suggestion via the urgency dropdown.
String suggestUrgency(String? dateStr) {
  if (dateStr == null || dateStr.isEmpty) return 'normal';
  final target = DateTime.tryParse(dateStr);
  if (target == null) return 'normal';
  final today = DateTime.now();
  final todayMidnight = DateTime(today.year, today.month, today.day);
  final targetMidnight = DateTime(target.year, target.month, target.day);
  final days = targetMidnight.difference(todayMidnight).inDays;
  if (days <= 3) return 'express';
  if (days <= 7) return 'high';
  if (days <= 14) return 'normal';
  return 'low';
}

class CreateFreightScreen extends ConsumerStatefulWidget {
  const CreateFreightScreen({super.key});

  @override
  ConsumerState<CreateFreightScreen> createState() =>
      _CreateFreightScreenState();
}

class _CreateFreightScreenState extends ConsumerState<CreateFreightScreen> {
  // ── Service type: null = selector, 'intercity', 'intracity' ──────────────
  String? _serviceType;

  // ── Intercity form state ──────────────────────────────────────────────────
  final _pageController = PageController();
  int _step = 0;

  String? pickupLocation;
  String? deliveryLocation;
  double? _deliveryLat;
  double? _deliveryLng;
  String? cargoType;
  String? weight;
  String? budget;
  String? deadline;
  String? description;
  String priceType = 'negotiable';
  String urgency = 'normal';
  bool _urgencyTouched = false;

  int? _priceMin;
  int? _priceMax;
  int? _priceDistKm;
  bool _priceLoading = false;
  String? _priceError;

  // ── Intracity form state ──────────────────────────────────────────────────
  final _intraPageController = PageController();
  int _intraStep = 0;

  String? _intraCity;
  final _pickupAreaCtrl = TextEditingController();
  final _dropoffAreaCtrl = TextEditingController();
  final _itemsDescCtrl = TextEditingController();
  String? _preferredDate;
  String? _vehicleTypeNeeded;
  String _intraUrgency = 'normal';
  bool _intraUrgencyTouched = false;

  // ── GPS pickup state (shared for both service types) ─────────────────────
  double? _pickupLat;
  double? _pickupLng;
  bool _gpsPickupMode = false;
  bool _detectingLocation = false;
  // Real reverse-geocoded place name (e.g. "Dabat") — used for display and
  // as the actual pickup_location/pickup_area text. Never overwritten with
  // the nearest known service city.
  String? _detectedPlaceName;
  // Nearest known service city (e.g. "Gondar") — an internal-only concept
  // needed for intracity's exact city-text driver matching. Kept separate
  // from _detectedPlaceName so it's never shown to the user as if it were
  // their actual location.
  String? _detectedServiceCity;
  double? _detectedServiceCityDistanceKm;

  // ── Static data ───────────────────────────────────────────────────────────

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

  static const _intracityCities = [
    'Addis Ababa',
    'Adama',
    'Adigrat',
    'Adwa',
    'Arba Minch',
    'Asela',
    'Assosa',
    'Axum',
    'Bahir Dar',
    'Bure',
    'Butajira',
    'Debre Birhan',
    'Debre Markos',
    'Debre Tabor',
    'Dessie',
    'Dilla',
    'Dire Dawa',
    'Finote Selam',
    'Gambela',
    'Gimbi',
    'Goba',
    'Gondar',
    'Harar',
    'Hawassa',
    'Hosaena',
    'Humera',
    'Injibara',
    'Jijiga',
    'Jimma',
    'Kombolcha',
    'Lalibela',
    'Mekele',
    'Metema',
    'Motta',
    'Nekemte',
    'Robe',
    'Shashamane',
    'Shire',
    'Welkite',
    'Woldia',
    'Woreta',
    'Yirgalem',
    'Addis Zemen',
    'Ambo',
  ];

  static const _vehicleTypesLight = ['pickup', 'minivan', 'bajaj'];

  static const _intercitySteps = ['Locations', 'Cargo', 'Budget', 'Review'];
  static const _intracitySteps = ['City & Areas', 'Details', 'Review'];

  @override
  void dispose() {
    _pageController.dispose();
    _intraPageController.dispose();
    _pickupAreaCtrl.dispose();
    _dropoffAreaCtrl.dispose();
    _itemsDescCtrl.dispose();
    super.dispose();
  }

  // ── GPS pickup detection ──────────────────────────────────────────────────

  Future<void> _detectPickupLocation() async {
    setState(() {
      _detectingLocation = true;
      _gpsPickupMode = false;
    });
    try {
      final pos = await LocationService.getCurrentPosition();
      if (pos == null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text('location.denied'.tr()),
            backgroundColor: kDanger,
          ));
          setState(() => _detectingLocation = false);
        }
        return;
      }

      // Two independent lookups: reverseGeocode gives the real place name
      // (e.g. "Dabat"); nearestCity gives the closest known *service* city
      // (e.g. "Gondar"), needed only for intracity's exact city-text driver
      // matching. Fetched concurrently; nearestCity is allowed to fail
      // without aborting (reverse geocoding is the one we actually need).
      final reverseFuture = ref
          .read(routingRepositoryProvider)
          .reverseGeocode(pos.latitude, pos.longitude);
      final nearestCityFuture = () async {
        try {
          return await ref
              .read(cargoRepositoryProvider)
              .nearestCity(pos.latitude, pos.longitude);
        } catch (_) {
          return null;
        }
      }();

      final reverseResult = await reverseFuture;
      final nearestCityResult = await nearestCityFuture;
      if (!mounted) return;

      final placeName = (reverseResult?['short_address'] as String?)?.trim();
      final serviceCity = nearestCityResult?.city;
      // If reverse geocoding also failed, fall back to the service city so
      // the field isn't left blank — but never silently pass the service
      // city off as the real place name when reverse geocoding succeeded.
      final displayName = (placeName != null && placeName.isNotEmpty)
          ? placeName
          : serviceCity;

      if (displayName == null) {
        throw Exception('Could not determine a place name for this location');
      }

      setState(() {
        _pickupLat = pos.latitude;
        _pickupLng = pos.longitude;
        _detectedPlaceName = displayName;
        _detectedServiceCity = serviceCity;
        _detectedServiceCityDistanceKm = nearestCityResult?.distanceKm;
        _gpsPickupMode = true;
        _detectingLocation = false;
        pickupLocation = displayName;
        // Intracity matching needs a known service city, not the free-text
        // place name — fall back to the place name only if no known city
        // could be resolved at all (keeps the field non-null for the form).
        _intraCity = serviceCity ?? displayName;
        if (_pickupAreaCtrl.text.isEmpty ||
            _pickupAreaCtrl.text.startsWith('Near ')) {
          _pickupAreaCtrl.text = 'Near $displayName (GPS location)';
        }
      });
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('location.unavailable'.tr()),
          backgroundColor: kDanger,
        ));
        setState(() => _detectingLocation = false);
      }
    }
  }

  void _clearGpsPickup() {
    setState(() {
      _pickupLat = null;
      _pickupLng = null;
      _gpsPickupMode = false;
      _detectedPlaceName = null;
      _detectedServiceCity = null;
      _detectedServiceCityDistanceKm = null;
    });
  }

  void _selectManualPickupLocation(String value) {
    setState(() {
      pickupLocation = value;
      _pickupLat = null;
      _pickupLng = null;
      _gpsPickupMode = false;
      _detectedPlaceName = null;
      _detectedServiceCity = null;
      _detectedServiceCityDistanceKm = null;
      if (_pickupAreaCtrl.text.isEmpty ||
          _pickupAreaCtrl.text.startsWith('Near ')) {
        _pickupAreaCtrl.text = value;
      }
    });
  }

  // ── Navigation ────────────────────────────────────────────────────────────

  void _next() {
    if (_serviceType == 'intracity') {
      if (_intraStep < 2) {
        _intraPageController.nextPage(
            duration: const Duration(milliseconds: 250),
            curve: Curves.easeInOut);
      } else {
        _submitIntracity();
      }
      return;
    }
    // Intercity
    if (_step < 3) {
      if (_step == 1) _fetchPricePrediction();
      _pageController.nextPage(
          duration: const Duration(milliseconds: 250), curve: Curves.easeInOut);
    } else {
      _submit();
    }
  }

  void _back() {
    if (_serviceType == 'intracity') {
      if (_intraStep > 0) {
        _intraPageController.previousPage(
            duration: const Duration(milliseconds: 250),
            curve: Curves.easeInOut);
      } else {
        setState(() => _serviceType = null);
      }
      return;
    }
    // Intercity
    if (_step == 0) {
      setState(() => _serviceType = null);
    } else {
      _pageController.previousPage(
          duration: const Duration(milliseconds: 250), curve: Curves.easeInOut);
    }
  }

  // ── Intercity helpers ─────────────────────────────────────────────────────

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
            // weight is entered in kuntal (the unit shippers/drivers think
            // in) — convert to tons here since that's what the AI pricing
            // service and the backend generally expect.
            weight: WeightFormat.kuntalToTons(double.tryParse(weight ?? '') ?? 100),
            urgencyLevel: urgency,
            materialType: cargoType ?? 'general',
            fromLat: _pickupLat,
            fromLng: _pickupLng,
            toLat: _deliveryLat,
            toLng: _deliveryLng,
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
            _priceError =
                'AI price service unavailable. Enter your budget manually.';
          });
        }
      }
    } catch (_) {
      if (mounted)
        setState(() {
          _priceLoading = false;
          _priceError = 'Could not fetch price estimate';
        });
    }
  }

  Future<void> _submit() async {
    if (pickupLocation == null ||
        deliveryLocation == null ||
        cargoType == null ||
        weight == null ||
        budget == null) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Please fill in all required fields',
            style: GoogleFonts.inter()),
        backgroundColor: kDanger,
      ));
      return;
    }
    final parsedWeightKuntal = double.tryParse(weight!);
    if (parsedWeightKuntal == null || parsedWeightKuntal <= 0) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Enter a valid weight greater than 0',
            style: GoogleFonts.inter()),
        backgroundColor: kDanger,
      ));
      return;
    }
    // The form collects weight in kuntal; the API expects tons.
    final parsedWeight = WeightFormat.kuntalToTons(parsedWeightKuntal);
    final parsedBudget = double.tryParse(budget!);
    if (parsedBudget == null || parsedBudget < 0) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Enter a valid budget',
            style: GoogleFonts.inter()),
        backgroundColor: kDanger,
      ));
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
            weight: parsedWeight,
            urgencyLevel: urgency,
            budget: parsedBudget,
            priceType: priceType,
            bidDeadline: deadline != null ? DateTime.tryParse(deadline!) : null,
            pickupLat: _pickupLat,
            pickupLng: _pickupLng,
          );
      if (mounted) Navigator.pop(context);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Cargo request created!', style: GoogleFonts.inter()),
          backgroundColor: kSuccess,
        ));
        await Future.delayed(const Duration(milliseconds: 800));
        if (mounted) context.go('/freight');
      }
    } catch (e) {
      if (mounted) Navigator.pop(context);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Error: $e', style: GoogleFonts.inter()),
          backgroundColor: kDanger,
        ));
      }
    }
  }

  // ── Intracity submit ──────────────────────────────────────────────────────

  Future<void> _submitIntracity() async {
    if (_intraCity == null ||
        _pickupAreaCtrl.text.isEmpty ||
        _dropoffAreaCtrl.text.isEmpty ||
        _itemsDescCtrl.text.isEmpty ||
        _preferredDate == null) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text('Please fill in all required fields',
            style: GoogleFonts.inter()),
        backgroundColor: kDanger,
      ));
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
      await ref.read(cargoRepositoryProvider).createIntracity(
            city: _intraCity!,
            pickupArea: _pickupAreaCtrl.text.trim(),
            dropoffArea: _dropoffAreaCtrl.text.trim(),
            itemsDescription: _itemsDescCtrl.text.trim(),
            preferredDate: DateTime.parse(_preferredDate!),
            vehicleTypeNeeded: _vehicleTypeNeeded,
            urgencyLevel: _intraUrgency,
            pickupLat: _pickupLat,
            pickupLng: _pickupLng,
          );
      if (mounted) Navigator.pop(context);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Moving request created!', style: GoogleFonts.inter()),
          backgroundColor: kSuccess,
        ));
        await Future.delayed(const Duration(milliseconds: 800));
        if (mounted) context.go('/freight');
      }
    } catch (e) {
      if (mounted) Navigator.pop(context);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('Error: $e', style: GoogleFonts.inter()),
          backgroundColor: kDanger,
        ));
      }
    }
  }

  // ── Build ─────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    // ── 0. Service type selector ───────────────────────────────────────────
    if (_serviceType == null) {
      return Scaffold(
        backgroundColor: kBackground,
        appBar: EthioAppBar(title: 'shipper.post_cargo'.tr()),
        body: _ServiceTypeSelector(
          onSelect: (type) => setState(() => _serviceType = type),
        ),
      );
    }

    final steps =
        _serviceType == 'intracity' ? _intracitySteps : _intercitySteps;
    final currentStep = _serviceType == 'intracity' ? _intraStep : _step;
    final isLast = _serviceType == 'intracity' ? _intraStep == 2 : _step == 3;

    return Scaffold(
      backgroundColor: kBackground,
      appBar: EthioAppBar(title: 'shipper.post_cargo'.tr()),
      body: Column(
        children: [
          _StepIndicator(current: currentStep, steps: steps),
          Divider(height: 1, color: kBorder),

          Expanded(
            child: _serviceType == 'intracity'
                ? PageView(
                    controller: _intraPageController,
                    physics: const NeverScrollableScrollPhysics(),
                    onPageChanged: (i) => setState(() => _intraStep = i),
                    children: [
                      _IntraStep1(
                        city: _intraCity,
                        pickupCtrl: _pickupAreaCtrl,
                        dropoffCtrl: _dropoffAreaCtrl,
                        cities: _intracityCities,
                        onCity: (v) => setState(() => _intraCity = v),
                        gpsPickupMode: _gpsPickupMode,
                        detectingLocation: _detectingLocation,
                        detectedPlaceName: _detectedPlaceName,
                        detectedServiceCity: _detectedServiceCity,
                        detectedServiceCityDistanceKm:
                            _detectedServiceCityDistanceKm,
                        gpsLat: _pickupLat,
                        gpsLng: _pickupLng,
                        onUseGps: _detectPickupLocation,
                        onClearGps: _clearGpsPickup,
                      ),
                      _IntraStep2(
                        descCtrl: _itemsDescCtrl,
                        preferredDate: _preferredDate,
                        vehicleType: _vehicleTypeNeeded,
                        vehicleTypes: _vehicleTypesLight,
                        urgency: _intraUrgency,
                        onDate: (v) => setState(() {
                          _preferredDate = v;
                          if (!_intraUrgencyTouched) {
                            _intraUrgency = suggestUrgency(v);
                          }
                        }),
                        onVehicleType: (v) =>
                            setState(() => _vehicleTypeNeeded = v),
                        onUrgency: (v) => setState(() {
                          _intraUrgency = v;
                          _intraUrgencyTouched = true;
                        }),
                      ),
                      _IntraStep3Review(
                        city: _intraCity,
                        pickupArea: _pickupAreaCtrl.text,
                        dropoffArea: _dropoffAreaCtrl.text,
                        itemsDesc: _itemsDescCtrl.text,
                        preferredDate: _preferredDate,
                        vehicleType: _vehicleTypeNeeded,
                        urgency: _intraUrgency,
                      ),
                    ],
                  )
                : PageView(
                    controller: _pageController,
                    physics: const NeverScrollableScrollPhysics(),
                    onPageChanged: (i) => setState(() => _step = i),
                    children: [
                      _Step1(
                        pickupLocation: pickupLocation,
                        deliveryLocation: deliveryLocation,
                        cities: _cities,
                        onPickup: _selectManualPickupLocation,
                        onDelivery: (v) => setState(() => deliveryLocation = v),
                        gpsPickupMode: _gpsPickupMode,
                        detectingLocation: _detectingLocation,
                        detectedPlaceName: _detectedPlaceName,
                        detectedServiceCity: _detectedServiceCity,
                        detectedServiceCityDistanceKm:
                            _detectedServiceCityDistanceKm,
                        onUseGps: _detectPickupLocation,
                        onClearGps: _clearGpsPickup,
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
                        urgency: urgency,
                        onBudget: (v) => setState(() => budget = v),
                        onDeadline: (v) => setState(() {
                          deadline = v;
                          if (!_urgencyTouched) urgency = suggestUrgency(v);
                        }),
                        onPriceType: (v) => setState(() => priceType = v),
                        onUrgency: (v) => setState(() {
                          urgency = v;
                          _urgencyTouched = true;
                          _fetchPricePrediction();
                        }),
                      ),
                      _Step4(
                        pickup: pickupLocation,
                        delivery: deliveryLocation,
                        pickupLat: _pickupLat ??
                            cityCoordinates(pickupLocation ?? '')?.latitude,
                        pickupLng: _pickupLng ??
                            cityCoordinates(pickupLocation ?? '')?.longitude,
                        destinationLat:
                            cityCoordinates(deliveryLocation ?? '')?.latitude,
                        destinationLng:
                            cityCoordinates(deliveryLocation ?? '')?.longitude,
                        cargo: cargoType,
                        weight: weight,
                        budget: budget,
                        deadline: deadline,
                        description: description,
                        priceType: priceType,
                        urgency: urgency,
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
                    child: Text(
                      currentStep == 0 ? 'Change Type' : 'Back',
                      style: GoogleFonts.inter(fontWeight: FontWeight.w600),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
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
                      isLast ? 'Submit' : 'Continue',
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

// ── Service type selector ─────────────────────────────────────────────────────

class _ServiceTypeSelector extends StatelessWidget {
  final ValueChanged<String> onSelect;
  const _ServiceTypeSelector({required this.onSelect});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const SizedBox(height: 8),
        Text('What are you moving?',
            style: GoogleFonts.inter(
                fontSize: 20,
                fontWeight: FontWeight.bold,
                color: kTextPrimary)),
        const SizedBox(height: 8),
        Text('Choose the type of service you need',
            style: GoogleFonts.inter(fontSize: 14, color: kTextSecond)),
        const SizedBox(height: 28),
        _ServiceCard(
          icon: Icons.local_shipping_rounded,
          title: 'Intercity Freight',
          subtitle: 'Transport cargo between cities',
          examples: 'e.g. Addis Ababa → Hawassa · Heavy loads',
          color: kGreen,
          onTap: () => onSelect('intercity'),
        ),
        const SizedBox(height: 16),
        _ServiceCard(
          icon: Icons.airport_shuttle_rounded,
          title: 'Intra-city Moving',
          subtitle: 'Move items within the same city',
          examples: 'e.g. Pickup, minivan, or bajaj within Addis',
          color: const Color(0xFF1E40AF),
          onTap: () => onSelect('intracity'),
        ),
        const SizedBox(height: 32),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
              color: kGreenTint,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: kGreen.withValues(alpha: 0.2))),
          child: Row(children: [
            Icon(Icons.info_outline, color: kGreen, size: 16),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                'Intercity uses heavy trucks. Intra-city uses pickups, minivans, and bajaj vehicles.',
                style: GoogleFonts.inter(fontSize: 12, color: kTextPrimary),
              ),
            ),
          ]),
        ),
      ],
    );
  }
}

class _ServiceCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final String examples;
  final Color color;
  final VoidCallback onTap;

  const _ServiceCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.examples,
    required this.color,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          color: kSurface,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: color.withValues(alpha: 0.3), width: 1.5),
        ),
        child: Row(children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.08),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, color: color, size: 28),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title,
                    style: GoogleFonts.inter(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: kTextPrimary)),
                const SizedBox(height: 3),
                Text(subtitle,
                    style: GoogleFonts.inter(fontSize: 13, color: kTextSecond)),
                const SizedBox(height: 4),
                Text(examples,
                    style: GoogleFonts.inter(
                        fontSize: 11,
                        color: color,
                        fontWeight: FontWeight.w500)),
              ],
            ),
          ),
          Icon(Icons.chevron_right_rounded, color: color, size: 22),
        ]),
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

// ── GPS / Manual pickup mode toggle ──────────────────────────────────────────

class _PickupModeToggle extends StatelessWidget {
  final bool gpsMode;
  final bool loading;
  final VoidCallback onGps;
  final VoidCallback onManual;

  const _PickupModeToggle({
    required this.gpsMode,
    required this.loading,
    required this.onGps,
    required this.onManual,
  });

  @override
  Widget build(BuildContext context) {
    return Row(children: [
      Expanded(
        child: _ModeButton(
          icon: loading ? null : Icons.location_pin,
          label: loading
              ? 'location.detecting'.tr()
              : 'location.use_my_location'.tr(),
          selected: gpsMode || loading,
          loading: loading,
          onTap: (gpsMode || loading) ? null : onGps,
        ),
      ),
      const SizedBox(width: 10),
      Expanded(
        child: _ModeButton(
          icon: Icons.location_city_outlined,
          label: 'location.select_city'.tr(),
          selected: !gpsMode && !loading,
          loading: false,
          onTap: (!gpsMode && !loading) ? null : onManual,
        ),
      ),
    ]);
  }
}

class _ModeButton extends StatelessWidget {
  final IconData? icon;
  final String label;
  final bool selected;
  final bool loading;
  final VoidCallback? onTap;

  const _ModeButton({
    required this.icon,
    required this.label,
    required this.selected,
    required this.loading,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 180),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
        decoration: BoxDecoration(
          color: selected ? kGreen.withValues(alpha: 0.07) : kSurface,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: selected ? kGreen : kBorder,
            width: selected ? 1.5 : 1.0,
          ),
        ),
        child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          if (loading)
            SizedBox(
              width: 14,
              height: 14,
              child: CircularProgressIndicator(strokeWidth: 2, color: kGreen),
            )
          else if (icon != null)
            Icon(icon, size: 16, color: selected ? kGreen : kTextMuted),
          const SizedBox(width: 6),
          Flexible(
            child: Text(
              label,
              style: GoogleFonts.inter(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: selected ? kGreen : kTextSecond,
              ),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ]),
      ),
    );
  }
}

// ── GPS detected city badge ───────────────────────────────────────────────────

class _GpsDetectedBadge extends StatelessWidget {
  final String placeName;
  final String? serviceCity;
  final double? serviceCityDistanceKm;
  final VoidCallback onClear;

  const _GpsDetectedBadge({
    required this.placeName,
    required this.serviceCity,
    required this.serviceCityDistanceKm,
    required this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    // Only show the service-hub hint when it's actually informative — i.e.
    // it resolved to something different from the real place name. Never
    // implies the service city IS the user's location.
    final showServiceHub = serviceCity != null &&
        serviceCity != placeName &&
        serviceCityDistanceKm != null;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: kGreen.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: kGreen.withValues(alpha: 0.35)),
      ),
      child: Row(children: [
        const Icon(Icons.location_pin, color: Color(0xFF059669), size: 18),
        const SizedBox(width: 10),
        Expanded(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(
              placeName,
              style: GoogleFonts.inter(
                  fontWeight: FontWeight.bold, fontSize: 14, color: kGreen),
            ),
            if (showServiceHub)
              Text(
                'location.service_hub'.tr(namedArgs: {
                  'city': serviceCity!,
                  'km': serviceCityDistanceKm!.toStringAsFixed(1),
                }),
                style: GoogleFonts.inter(fontSize: 11, color: kTextSecond),
              ),
          ]),
        ),
        GestureDetector(
          onTap: onClear,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: kSurface,
              borderRadius: BorderRadius.circular(6),
              border: Border.all(color: kBorder),
            ),
            child: Text(
              'location.change'.tr(),
              style: GoogleFonts.inter(
                  fontSize: 11,
                  fontWeight: FontWeight.w600,
                  color: kTextSecond),
            ),
          ),
        ),
      ]),
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
    if (sel == null || widget.options.contains(sel)) {
      _dropdownValue = sel;
    } else {
      _dropdownValue = widget.options.last;
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
          initialValue: _dropdownValue,
          hint: Text(widget.hint, style: GoogleFonts.inter(color: kTextMuted)),
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

// ── Intercity Step 1: Locations ───────────────────────────────────────────────

class _Step1 extends StatelessWidget {
  final String? pickupLocation;
  final String? deliveryLocation;
  final List<String> cities;
  final ValueChanged<String> onPickup;
  final ValueChanged<String> onDelivery;
  final bool gpsPickupMode;
  final bool detectingLocation;
  final String? detectedPlaceName;
  final String? detectedServiceCity;
  final double? detectedServiceCityDistanceKm;
  final VoidCallback onUseGps;
  final VoidCallback onClearGps;

  const _Step1({
    required this.pickupLocation,
    required this.deliveryLocation,
    required this.cities,
    required this.onPickup,
    required this.onDelivery,
    required this.gpsPickupMode,
    required this.detectingLocation,
    required this.detectedPlaceName,
    required this.detectedServiceCity,
    required this.detectedServiceCityDistanceKm,
    required this.onUseGps,
    required this.onClearGps,
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

        // GPS / Manual toggle
        _PickupModeToggle(
          gpsMode: gpsPickupMode,
          loading: detectingLocation,
          onGps: onUseGps,
          onManual: onClearGps,
        ),
        const SizedBox(height: 12),

        // Show detected badge OR manual dropdown
        if (gpsPickupMode && detectedPlaceName != null)
          _GpsDetectedBadge(
            placeName: detectedPlaceName!,
            serviceCity: detectedServiceCity,
            serviceCityDistanceKm: detectedServiceCityDistanceKm,
            onClear: onClearGps,
          )
        else if (!detectingLocation) ...[
          _DropdownWithOther(
            selected: pickupLocation,
            options: cities,
            hint: 'location.select_city'.tr(),
            otherHint: 'Enter city name (e.g. Mojo)',
            prefixIcon: Icons.trip_origin,
            labelOf: (c) => c,
            onSelect: onPickup,
          ),
          const SizedBox(height: 10),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: kGreenTint,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: kGreen.withValues(alpha: 0.2)),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.info_outline, color: kGreen, size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Choose the pickup city manually if the cargo is in a different city. Nearby trucks will be matched around that pickup location.',
                    style: GoogleFonts.inter(fontSize: 12, color: kTextPrimary),
                  ),
                ),
              ],
            ),
          ),
        ],

        const SizedBox(height: 20),
        Text('Destination',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        _DropdownWithOther(
          selected: deliveryLocation,
          options: cities,
          hint: 'location.select_city'.tr(),
          otherHint: 'Enter city name (e.g. Mojo)',
          prefixIcon: Icons.flag_outlined,
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
                        fontWeight: FontWeight.w600,
                        color: kGreen,
                        fontSize: 13)),
              ),
            ]),
          ),
        ],
      ],
    );
  }
}

// ── Intercity Step 2: Cargo details ──────────────────────────────────────────

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
        Text('Weight (kuntal)',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        TextFormField(
          initialValue: weight,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          onChanged: onWeight,
          decoration: _inputDeco(hint: 'e.g. 200', suffix: 'kuntal'),
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

// ── Intercity Step 3: Budget & timeline ──────────────────────────────────────

class _Step3 extends StatelessWidget {
  final String? budget;
  final String? deadline;
  final String priceType;
  final int? priceMin;
  final int? priceMax;
  final int? priceDistKm;
  final bool priceLoading;
  final String? priceError;
  final String urgency;
  final ValueChanged<String> onBudget;
  final ValueChanged<String> onDeadline;
  final ValueChanged<String> onPriceType;
  final ValueChanged<String> onUrgency;

  const _Step3({
    required this.budget,
    required this.deadline,
    required this.priceType,
    required this.priceMin,
    required this.priceMax,
    required this.priceDistKm,
    required this.priceLoading,
    required this.priceError,
    required this.urgency,
    required this.onBudget,
    required this.onDeadline,
    required this.onPriceType,
    required this.onUrgency,
  });

  InputDecoration _inputDeco(
          {String? hint, String? prefix, Widget? prefixIcon}) =>
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
          )),
          const SizedBox(width: 10),
          Expanded(
              child: _PriceTypeOption(
            title: 'Fixed Price',
            subtitle: 'Drivers accept or reject',
            icon: Icons.price_check_rounded,
            selected: priceType == 'fixed',
            onTap: () => onPriceType('fixed'),
          )),
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
        Text('Bid Deadline / የጨረታ የመጨረሻ ቀን',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        TextFormField(
          readOnly: true,
          // The picker still works in Gregorian (no Ethiopian-calendar
          // picker widget exists yet — see EthiopianDate docs); what the
          // shipper actually reads back is the Ethiopian date. `deadline`
          // itself stays a plain ISO string for the API payload.
          initialValue:
              deadline != null ? EthiopianDate.display(DateTime.parse(deadline!)) : null,
          onTap: () async {
            final d = await showDatePicker(
              context: context,
              initialDate: DateTime.now().add(const Duration(days: 1)),
              firstDate: DateTime.now(),
              lastDate: DateTime.now().add(const Duration(days: 90)),
              builder: (ctx, child) => Theme(
                data: Theme.of(ctx)
                    .copyWith(colorScheme: ColorScheme.light(primary: kGreen)),
                child: child!,
              ),
            );
            if (d != null) onDeadline(d.toString().split(' ')[0]);
          },
          decoration: _inputDeco(
            hint: 'Tap to select date',
            prefixIcon:
                Icon(Icons.calendar_today_outlined, color: kGreen, size: 20),
          ),
          style: GoogleFonts.inter(fontSize: 14),
        ),
        const SizedBox(height: 16),
        Text('Urgency',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 4),
        Text(
          deadline != null
              ? 'Suggested from your deadline — change it if needed.'
              : 'Defaults to Normal until you pick a deadline above.',
          style: GoogleFonts.inter(fontSize: 12, color: kTextSecond),
        ),
        const SizedBox(height: 8),
        DropdownButtonFormField<String>(
          value: urgency,
          items: kUrgencyLevels
              .map((u) => DropdownMenuItem(
                    value: u,
                    child: Text(u[0].toUpperCase() + u.substring(1),
                        style: GoogleFonts.inter(fontSize: 14)),
                  ))
              .toList(),
          onChanged: (v) {
            if (v != null) onUrgency(v);
          },
          decoration: _inputDeco(
            prefixIcon:
                Icon(Icons.priority_high_rounded, color: kGreen, size: 20),
          ),
        ),
        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
              color: kGreenTint,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: kGreen.withValues(alpha: 0.25))),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
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
                    style: GoogleFonts.inter(fontSize: 13, color: kTextSecond)),
              ])
            else if (priceMin != null && priceMax != null)
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('ETB ${_fmt(priceMin!)} – ${_fmt(priceMax!)}',
                    style: GoogleFonts.inter(
                        fontSize: 22,
                        fontWeight: FontWeight.bold,
                        color: kGreen)),
                const SizedBox(height: 4),
                Text(
                  priceDistKm != null
                      ? 'Based on ~$priceDistKm km route and cargo weight'
                      : 'Based on route and cargo type',
                  style: GoogleFonts.inter(fontSize: 12, color: kTextSecond),
                ),
              ])
            else if (priceError != null)
              Text(priceError!,
                  style: GoogleFonts.inter(fontSize: 12, color: kDanger))
            else
              Text(
                  'Enter cargo details on the previous step to get a price estimate.',
                  style: GoogleFonts.inter(fontSize: 12, color: kTextSecond)),
          ]),
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
              color: selected ? kGreen : kBorder, width: selected ? 1.5 : 0.5),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
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
        ]),
      ),
    );
  }
}

// ── Intercity Step 4: Review ──────────────────────────────────────────────────

class _Step4 extends StatelessWidget {
  final String? pickup;
  final String? delivery;
  final double? pickupLat;
  final double? pickupLng;
  final double? destinationLat;
  final double? destinationLng;
  final String? cargo;
  final String? weight;
  final String? budget;
  final String? deadline;
  final String? description;
  final String priceType;
  final String urgency;

  const _Step4({
    required this.pickup,
    required this.delivery,
    this.pickupLat,
    this.pickupLng,
    this.destinationLat,
    this.destinationLng,
    required this.cargo,
    required this.weight,
    required this.budget,
    required this.deadline,
    required this.description,
    required this.priceType,
    required this.urgency,
  });

  @override
  Widget build(BuildContext context) {
    final hasCoords = pickupLat != null &&
        pickupLng != null &&
        destinationLat != null &&
        destinationLng != null;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Text('Review & Confirm',
            style: GoogleFonts.inter(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: kTextPrimary)),
        const SizedBox(height: 16),

        // ── Route map preview ────────────────────────────────────────
        if (hasCoords) ...[
          CargoRouteMapWidget(
            pickupLat: pickupLat!,
            pickupLng: pickupLng!,
            destinationLat: destinationLat!,
            destinationLng: destinationLng!,
            pickupName: pickup ?? '',
            destinationName: delivery ?? '',
            height: 220,
          ),
          const SizedBox(height: 12),
          // Route header row
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: kGreenTint,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: kGreen.withValues(alpha: 0.2)),
            ),
            child: Row(children: [
              Expanded(
                child: Text(
                  pickup ?? '—',
                  style: GoogleFonts.inter(
                      fontWeight: FontWeight.w600,
                      color: kTextPrimary,
                      fontSize: 13),
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                child:
                    Icon(Icons.arrow_forward_rounded, color: kAmber, size: 18),
              ),
              Expanded(
                child: Text(
                  delivery ?? '—',
                  textAlign: TextAlign.end,
                  style: GoogleFonts.inter(
                      fontWeight: FontWeight.w600,
                      color: kTextPrimary,
                      fontSize: 13),
                ),
              ),
            ]),
          ),
          const SizedBox(height: 20),

          // ── Nearby drivers map ───────────────────────────────────────
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Available Trucks Nearby',
                style: GoogleFonts.inter(
                  fontSize: 14,
                  fontWeight: FontWeight.bold,
                  color: kTextPrimary,
                ),
              ),
              TextButton.icon(
                onPressed: () => context.go(Uri(
                  path: '/nearby-trucks',
                  queryParameters: {
                    'lat': pickupLat.toString(),
                    'lng': pickupLng.toString(),
                    if (pickup != null) 'name': pickup!,
                  },
                ).toString()),
                icon: const Icon(Icons.travel_explore_outlined, size: 16),
                label: const Text('Radius search', style: TextStyle(fontSize: 12)),
                style: TextButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  foregroundColor: kGreen,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          NearbyDriversMapWidget(
            pickupLat: pickupLat!,
            pickupLng: pickupLng!,
            pickupName: pickup,
            serviceType: 'intercity',
            height: 250,
          ),
          const SizedBox(height: 20),
        ] else ...[
          RouteUnavailablePlaceholder(height: 160),
          const SizedBox(height: 16),
        ],

        _ReviewRow('Pickup', pickup ?? '—'),
        _ReviewRow('Destination', delivery ?? '—'),
        _ReviewRow('Cargo Type', cargo ?? '—'),
        _ReviewRow('Weight', weight != null ? '$weight kuntal' : '—'),
        _ReviewRow(
            'Pricing', priceType == 'fixed' ? 'Fixed Price' : 'Negotiable'),
        _ReviewRow('Budget', budget != null ? 'ETB $budget' : '—'),
        _ReviewRow(
            'Bid Deadline',
            deadline != null
                ? EthiopianDate.display(DateTime.parse(deadline!))
                : 'Not set'),
        _ReviewRow('Urgency', urgency[0].toUpperCase() + urgency.substring(1)),
        if (description != null && description!.isNotEmpty)
          _ReviewRow('Notes', description!),
        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
              color: kAmberLight,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: kAmber.withValues(alpha: 0.4))),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(Icons.info_outline, color: kAmber, size: 18),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                'Once submitted, your request will be visible to available drivers. Platform commission: 2%.',
                style: GoogleFonts.inter(fontSize: 12, color: kTextPrimary),
              ),
            ),
          ]),
        ),
      ],
    );
  }
}

// ── Intracity Step 1: City & Areas ────────────────────────────────────────────

class _IntraStep1 extends StatelessWidget {
  final String? city;
  final TextEditingController pickupCtrl;
  final TextEditingController dropoffCtrl;
  final List<String> cities;
  final ValueChanged<String> onCity;
  final bool gpsPickupMode;
  final bool detectingLocation;
  final String? detectedPlaceName;
  final String? detectedServiceCity;
  final double? detectedServiceCityDistanceKm;
  final double? gpsLat;
  final double? gpsLng;
  final VoidCallback onUseGps;
  final VoidCallback onClearGps;

  const _IntraStep1({
    required this.city,
    required this.pickupCtrl,
    required this.dropoffCtrl,
    required this.cities,
    required this.onCity,
    required this.gpsPickupMode,
    required this.detectingLocation,
    required this.detectedPlaceName,
    required this.detectedServiceCity,
    required this.detectedServiceCityDistanceKm,
    required this.gpsLat,
    required this.gpsLng,
    required this.onUseGps,
    required this.onClearGps,
  });

  InputDecoration _deco({String? hint, Widget? prefix}) => InputDecoration(
        hintText: hint,
        hintStyle: GoogleFonts.inter(color: kTextMuted),
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
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
              color: const Color(0xFF1E40AF).withValues(alpha: 0.06),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                  color: const Color(0xFF1E40AF).withValues(alpha: 0.2))),
          child: Row(children: [
            const Icon(Icons.airport_shuttle_rounded,
                color: Color(0xFF1E40AF), size: 18),
            const SizedBox(width: 8),
            Expanded(
                child: Text('Intra-city Moving · light vehicles only',
                    style: GoogleFonts.inter(
                        fontSize: 12,
                        color: const Color(0xFF1E40AF),
                        fontWeight: FontWeight.w600))),
          ]),
        ),
        const SizedBox(height: 20),

        // ── GPS / Manual pickup toggle ───────────────────────────────────
        Text('Pickup Location',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        _PickupModeToggle(
          gpsMode: gpsPickupMode,
          loading: detectingLocation,
          onGps: onUseGps,
          onManual: onClearGps,
        ),
        const SizedBox(height: 12),

        if (gpsPickupMode && detectedPlaceName != null) ...[
          _GpsDetectedBadge(
            placeName: detectedPlaceName!,
            serviceCity: detectedServiceCity,
            serviceCityDistanceKm: detectedServiceCityDistanceKm,
            onClear: onClearGps,
          ),
          const SizedBox(height: 14),
        ],

        // ── City dropdown (always shown, pre-filled in GPS mode) ─────────
        Text('City',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        DropdownButtonFormField<String>(
          initialValue: city,
          hint: Text('location.select_city'.tr(),
              style: GoogleFonts.inter(color: kTextMuted)),
          isExpanded: true,
          decoration: _deco(
              prefix:
                  Icon(Icons.location_city_outlined, color: kGreen, size: 20)),
          items: cities
              .map((c) => DropdownMenuItem(
                  value: c,
                  child: Text(c, style: GoogleFonts.inter(fontSize: 14))))
              .toList(),
          onChanged: (v) {
            if (v != null) onCity(v);
          },
        ),

        // ── Pickup area ──────────────────────────────────────────────────
        const SizedBox(height: 20),
        Text('Pickup Area / Neighborhood',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        TextField(
          controller: pickupCtrl,
          decoration: _deco(
            hint: gpsPickupMode
                ? 'Add a landmark (e.g. Near Piassa)'
                : 'e.g. Bole, Piazza, Kazanchis',
            prefix: Icon(Icons.trip_origin, color: kGreen, size: 20),
          ),
          style: GoogleFonts.inter(fontSize: 14),
        ),

        // ── Small map preview when GPS mode active ───────────────────────
        if (gpsPickupMode && gpsLat != null && gpsLng != null) ...[
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(10),
            child: SizedBox(
              height: 150,
              child: FlutterMap(
                options: MapOptions(
                  initialCenter: LatLng(gpsLat!, gpsLng!),
                  initialZoom: 14.0,
                  interactionOptions: const InteractionOptions(
                    flags: InteractiveFlag.none,
                  ),
                ),
                children: [
                  TileLayer(
                    urlTemplate: kMapTileUrlTemplate,
                    userAgentPackageName: 'com.ethioloadai.app',
                  ),
                  MarkerLayer(
                    markers: [
                      Marker(
                        point: LatLng(gpsLat!, gpsLng!),
                        width: 40,
                        height: 40,
                        child: const Icon(Icons.location_pin,
                            color: Color(0xFF059669), size: 40),
                      ),
                    ],
                  ),
                  kMapAttribution,
                ],
              ),
            ),
          ),
        ],

        // ── Dropoff area ─────────────────────────────────────────────────
        const SizedBox(height: 20),
        Text('Dropoff Area / Neighborhood',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        TextField(
          controller: dropoffCtrl,
          decoration: _deco(
            hint: 'e.g. CMC, Megenagna, Sarbet',
            prefix: Icon(Icons.location_on_outlined, color: kGreen, size: 20),
          ),
          style: GoogleFonts.inter(fontSize: 14),
        ),
        if (city != null &&
            pickupCtrl.text.isNotEmpty &&
            dropoffCtrl.text.isNotEmpty) ...[
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
                  child: Text(
                      '$city · ${pickupCtrl.text} → ${dropoffCtrl.text}',
                      style: GoogleFonts.inter(
                          fontWeight: FontWeight.w600,
                          color: kGreen,
                          fontSize: 13))),
            ]),
          ),
        ],
      ],
    );
  }
}

// ── Intracity Step 2: Details ─────────────────────────────────────────────────

class _IntraStep2 extends StatelessWidget {
  final TextEditingController descCtrl;
  final String? preferredDate;
  final String? vehicleType;
  final List<String> vehicleTypes;
  final String urgency;
  final ValueChanged<String> onDate;
  final ValueChanged<String?> onVehicleType;
  final ValueChanged<String> onUrgency;

  const _IntraStep2({
    required this.descCtrl,
    required this.preferredDate,
    required this.vehicleType,
    required this.vehicleTypes,
    required this.urgency,
    required this.onDate,
    required this.onVehicleType,
    required this.onUrgency,
  });

  InputDecoration _deco({String? hint, Widget? prefix}) => InputDecoration(
        hintText: hint,
        hintStyle: GoogleFonts.inter(color: kTextMuted),
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
        Text('What are you moving?',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        TextField(
          controller: descCtrl,
          maxLines: 4,
          decoration:
              _deco(hint: 'e.g. 2-bedroom furniture, boxes, appliances...'),
          style: GoogleFonts.inter(fontSize: 14),
        ),
        const SizedBox(height: 20),
        Text('Preferred Date',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        TextFormField(
          readOnly: true,
          onTap: () async {
            final d = await showDatePicker(
              context: context,
              initialDate: DateTime.now().add(const Duration(days: 1)),
              firstDate: DateTime.now(),
              lastDate: DateTime.now().add(const Duration(days: 90)),
              builder: (ctx, child) => Theme(
                data: Theme.of(ctx)
                    .copyWith(colorScheme: ColorScheme.light(primary: kGreen)),
                child: child!,
              ),
            );
            if (d != null) onDate(d.toString().split(' ')[0]);
          },
          decoration: _deco(
            hint: 'Tap to select moving date',
            prefix:
                Icon(Icons.calendar_today_outlined, color: kGreen, size: 20),
          ),
          // preferredDate stays a plain ISO string for the API payload; the
          // field shows the Ethiopian-calendar date, which is what the
          // shipper actually reads.
          controller: TextEditingController(
              text: preferredDate != null
                  ? EthiopianDate.display(DateTime.parse(preferredDate!))
                  : ''),
          style: GoogleFonts.inter(fontSize: 14),
        ),
        const SizedBox(height: 16),
        Text('Urgency',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 4),
        Text(
          preferredDate != null
              ? 'Suggested from your preferred date — change it if needed.'
              : 'Defaults to Normal until you pick a date above.',
          style: GoogleFonts.inter(fontSize: 12, color: kTextSecond),
        ),
        const SizedBox(height: 8),
        DropdownButtonFormField<String>(
          value: urgency,
          items: kUrgencyLevels
              .map((u) => DropdownMenuItem(
                    value: u,
                    child: Text(u[0].toUpperCase() + u.substring(1),
                        style: GoogleFonts.inter(fontSize: 14)),
                  ))
              .toList(),
          onChanged: (v) {
            if (v != null) onUrgency(v);
          },
          decoration: _deco(
              prefix:
                  Icon(Icons.priority_high_rounded, color: kGreen, size: 20)),
        ),
        const SizedBox(height: 20),
        Text('Vehicle Type Needed (optional)',
            style: GoogleFonts.inter(
                fontWeight: FontWeight.w600, color: kTextPrimary)),
        const SizedBox(height: 8),
        DropdownButtonFormField<String>(
          initialValue: vehicleType,
          hint: Text('Any available vehicle',
              style: GoogleFonts.inter(color: kTextMuted)),
          isExpanded: true,
          decoration: _deco(
              prefix: Icon(Icons.airport_shuttle_outlined,
                  color: kGreen, size: 20)),
          items: [
            const DropdownMenuItem<String>(
                value: null, child: Text('Any available vehicle')),
            ...vehicleTypes.map((t) => DropdownMenuItem(
                  value: t,
                  child: Text(t[0].toUpperCase() + t.substring(1),
                      style: GoogleFonts.inter(fontSize: 14)),
                )),
          ],
          onChanged: onVehicleType,
        ),
      ],
    );
  }
}

// ── Intracity Step 3: Review ──────────────────────────────────────────────────

class _IntraStep3Review extends StatelessWidget {
  final String? city;
  final String pickupArea;
  final String dropoffArea;
  final String itemsDesc;
  final String? preferredDate;
  final String? vehicleType;
  final String urgency;

  const _IntraStep3Review({
    required this.city,
    required this.pickupArea,
    required this.dropoffArea,
    required this.itemsDesc,
    required this.preferredDate,
    required this.vehicleType,
    required this.urgency,
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
        const SizedBox(height: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
          decoration: BoxDecoration(
              color: const Color(0xFF1E40AF).withValues(alpha: 0.06),
              borderRadius: BorderRadius.circular(8)),
          child: Text('Intra-city Moving',
              style: GoogleFonts.inter(
                  fontSize: 12,
                  color: const Color(0xFF1E40AF),
                  fontWeight: FontWeight.w600)),
        ),
        const SizedBox(height: 16),
        _ReviewRow('City', city ?? '—'),
        _ReviewRow('Pickup Area', pickupArea.isEmpty ? '—' : pickupArea),
        _ReviewRow('Dropoff Area', dropoffArea.isEmpty ? '—' : dropoffArea),
        _ReviewRow('Items', itemsDesc.isEmpty ? '—' : itemsDesc),
        _ReviewRow(
            'Preferred Date',
            preferredDate != null
                ? EthiopianDate.display(DateTime.parse(preferredDate!))
                : 'Not set'),
        _ReviewRow('Urgency', urgency[0].toUpperCase() + urgency.substring(1)),
        _ReviewRow(
            'Vehicle',
            vehicleType != null
                ? vehicleType![0].toUpperCase() + vehicleType!.substring(1)
                : 'Any available'),
        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
              color: kAmberLight,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: kAmber.withValues(alpha: 0.4))),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(Icons.info_outline, color: kAmber, size: 18),
            const SizedBox(width: 10),
            Expanded(
                child: Text(
              'Drivers in your city will bid on this request. No AI price for intra-city moves. Platform commission: 2%.',
              style: GoogleFonts.inter(fontSize: 12, color: kTextPrimary),
            )),
          ]),
        ),
      ],
    );
  }
}

// ── Shared review row ─────────────────────────────────────────────────────────

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
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: kTextPrimary)),
          ),
        ],
      ),
    );
  }
}
