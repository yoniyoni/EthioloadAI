import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:ethioload_ai/src/data/models/models.dart';
import 'package:ethioload_ai/src/data/providers/data_providers.dart';
import 'package:ethioload_ai/src/data/repositories/repositories.dart';
import 'package:ethioload_ai/src/features/shipper/freight_detail_screen.dart';

class _FakeAuthRepository implements AuthRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => null;
}

class _FakeAuthNotifier extends AuthNotifier {
  final User _user;
  _FakeAuthNotifier(this._user) : super(_FakeAuthRepository()) {
    state = AuthState(user: _user, token: 'fake-token');
  }
}

class _MockBidRepository implements BidRepository {
  int? placedCargoId;
  int? placedVehicleId;
  double? placedAmount;
  String? placedNote;
  int? acceptedBidId;

  @override
  Future<Bid> place({
    required int cargoId,
    required int vehicleId,
    required double amount,
    String? note,
  }) async {
    placedCargoId = cargoId;
    placedVehicleId = vehicleId;
    placedAmount = amount;
    placedNote = note;
    return Bid(
      id: 999,
      cargoRequestId: cargoId,
      driverId: 7,
      vehicleId: vehicleId,
      amount: amount,
      note: note,
      status: 'pending',
      cargoPriceType: 'fixed',
    );
  }

  @override
  Future<Booking> acceptBid(int bidId) async {
    acceptedBidId = bidId;
    return Booking.fromJson({
      'id': 501,
      'cargo_request_id': 42,
      'driver_id': 7,
      'status': 'confirmed',
      'booking_status': 'confirmed',
      'agreed_price': 7500.0,
      'pickup_location': 'Addis Ababa, Bole',
      'delivery_location': 'Adama',
      'pickupLocation': 'Addis Ababa, Bole',
      'deliveryLocation': 'Adama',
      'created_at': '2026-09-14T10:00:00',
    });
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => null;
}

void main() {
  group('Fixed-Price Model Unit Tests', () {
    test('CargoRequest detects uppercase FIXED and normalizes priceType', () {
      final cargo = CargoRequest.fromJson({
        'id': 42,
        'user_id': 5,
        'pickup_location': 'Addis Ababa, Bole',
        'destination': 'Adama',
        'material_type': 'electronics',
        'weight': 4.0,
        'urgency_level': 'high',
        'budget': 7500.0,
        'priceType': 'FIXED',
        'status': 'posted',
      });

      expect(cargo.priceType, 'fixed');
      expect(cargo.isFixed, isTrue);
      expect(cargo.budget, 7500.0);
    });

    test('CargoRequest parses fixed_price / price fallback correctly', () {
      final cargo = CargoRequest.fromJson({
        'id': 43,
        'userId': 5,
        'pickupLocation': 'Hawassa',
        'destination': 'Addis Ababa',
        'materialType': 'grain',
        'weightTons': 10,
        'urgencyLevel': 'normal',
        'fixedPrice': '8000',
        'price_type': 'fixed',
        'status': 'posted',
      });

      expect(cargo.isFixed, isTrue);
      expect(cargo.budget, 8000.0);
    });

    test('CargoRequest detects NEGOTIABLE correctly as isFixed == false', () {
      final cargo = CargoRequest.fromJson({
        'id': 44,
        'user_id': 5,
        'pickup_location': 'Addis Ababa',
        'destination': 'Bahir Dar',
        'material_type': 'general',
        'weight': 3.0,
        'urgency_level': 'normal',
        'budget': 6000.0,
        'priceType': 'NEGOTIABLE',
        'status': 'posted',
      });

      expect(cargo.priceType, 'negotiable');
      expect(cargo.isFixed, isFalse);
    });

    test('Bid maps fixed cargo type and normalizes status', () {
      final bid = Bid.fromJson({
        'id': 101,
        'cargo_request_id': 42,
        'driver_id': 7,
        'vehicle_id': 2,
        'amount': 7500.0,
        'note': 'Available with HOWO 15t',
        'status': 'PENDING',
        'priceType': 'FIXED',
        'cargo_budget': 7500.0,
      });

      expect(bid.id, 101);
      expect(bid.amount, 7500.0);
      expect(bid.status, 'pending');
      expect(bid.isFixedCargo, isTrue);
      expect(bid.note, 'Available with HOWO 15t');
    });
  });

  group('Fixed-Price Widget Flow Tests', () {
    final fixedCargo = CargoRequest.fromJson({
      'id': 42,
      'user_id': 5,
      'pickup_location': 'Addis Ababa, Bole',
      'destination': 'Adama',
      'material_type': 'electronics',
      'weight': 4.0,
      'urgency_level': 'high',
      'budget': 7500.0,
      'priceType': 'FIXED',
      'status': 'posted',
    });

    testWidgets('Driver opens fixed freight -> displays "Fixed Price Cargo" and "Apply at Fixed Price"',
        (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            singleCargoProvider(42).overrideWith((ref) async => fixedCargo),
            myBidsProvider.overrideWith((ref) async => <Bid>[]),
            bidsForCargoProvider(42).overrideWith((ref) async => <Bid>[]),
            authNotifierProvider.overrideWith((ref) => _FakeAuthNotifier(
                  User(
                    id: 7,
                    fullName: 'Bekele Driver',
                    email: 'bekele@driver.et',
                    phone: '0911000000',
                    role: 'driver',
                  ),
                )),
          ],
          child: const MaterialApp(
            home: FreightDetailScreen(freightId: 42),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Fixed Price Cargo'), findsOneWidget);
      expect(find.text('Apply at Fixed Price'), findsOneWidget);
      expect(find.textContaining('ETB 7500'), findsWidgets);
      expect(find.text('Place Bid'), findsNothing);
      expect(find.text('Interested in this cargo?'), findsNothing);
    });

    testWidgets('Driver taps "Apply at Fixed Price" -> opens modal sheet with read-only price and submits exact budget',
        (tester) async {
      tester.view.physicalSize = const Size(1200, 2400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final mockBidRepo = _MockBidRepository();
      final testVehicle = Vehicle(
        id: 2,
        userId: 7,
        truckType: 'HOWO_15T',
        plateNumber: '3-12345-ET',
        capacity: 15.0,
        currentCity: 'Addis Ababa',
        availabilityStatus: 'available',
        rating: 4.8,
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            singleCargoProvider(42).overrideWith((ref) async => fixedCargo),
            myBidsProvider.overrideWith((ref) async => <Bid>[]),
            bidsForCargoProvider(42).overrideWith((ref) async => <Bid>[]),
            vehicleListProvider.overrideWith((ref) async => [testVehicle]),
            bidRepositoryProvider.overrideWithValue(mockBidRepo),
            authNotifierProvider.overrideWith((ref) => _FakeAuthNotifier(
                  User(
                    id: 7,
                    fullName: 'Bekele Driver',
                    email: 'bekele@driver.et',
                    phone: '0911000000',
                    role: 'driver',
                  ),
                )),
          ],
          child: const MaterialApp(
            home: FreightDetailScreen(freightId: 42),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // Tap "Apply at Fixed Price" to open bottom sheet
      await tester.tap(find.text('Apply at Fixed Price'));
      await tester.pumpAndSettle();

      // Bottom sheet is now open
      expect(find.text('Fixed Price (Non-negotiable)'), findsOneWidget);
      expect(find.text('Message to Shipper (optional)'), findsOneWidget);

      // Verify no price text field is present (amount is read-only)
      expect(find.text('Your Price (ETB)'), findsNothing);

      // Enter optional message
      final noteFinder = find.byType(TextField);
      expect(noteFinder, findsOneWidget);
      await tester.enterText(noteFinder, 'Available immediately with HOWO truck');

      // Submit application: tap the bottom sheet "Apply at Fixed Price" button
      final submitButton = find.descendant(
        of: find.byType(BottomSheet),
        matching: find.widgetWithText(ElevatedButton, 'Apply at Fixed Price'),
      );
      expect(submitButton, findsOneWidget);
      await tester.tap(submitButton);
      await tester.pumpAndSettle();

      // Verify mock repo received exact budget and message
      expect(mockBidRepo.placedCargoId, 42);
      expect(mockBidRepo.placedVehicleId, 2);
      expect(mockBidRepo.placedAmount, 7500.0);
      expect(mockBidRepo.placedNote, 'Available immediately with HOWO truck');
    });

    testWidgets('Driver opens fixed freight with existing application -> displays "Your Application", "Fixed Price", and no "Edit Bid" button',
        (tester) async {
      final existingBid = Bid.fromJson({
        'id': 101,
        'cargo_request_id': 42,
        'driver_id': 7,
        'vehicle_id': 2,
        'amount': 7500.0,
        'note': 'Available immediately',
        'status': 'pending',
        'priceType': 'FIXED',
      });

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            singleCargoProvider(42).overrideWith((ref) async => fixedCargo),
            myBidsProvider.overrideWith((ref) async => [existingBid]),
            bidsForCargoProvider(42).overrideWith((ref) async => [existingBid]),
            authNotifierProvider.overrideWith((ref) => _FakeAuthNotifier(
                  User(
                    id: 7,
                    fullName: 'Bekele Driver',
                    email: 'bekele@driver.et',
                    phone: '0911000000',
                    role: 'driver',
                  ),
                )),
          ],
          child: const MaterialApp(
            home: FreightDetailScreen(freightId: 42),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Your Application'), findsOneWidget);
      expect(find.text('PENDING'), findsOneWidget);
      expect(find.text('Fixed Price'), findsOneWidget);
      expect(find.text('ETB 7500'), findsWidgets);
      expect(
        find.text('Application sent to shipper. Waiting for shipper response.'),
        findsOneWidget,
      );

      // Verify driver cannot edit price ("Edit Bid" is hidden for fixed freight)
      expect(find.text('Edit Bid'), findsNothing);
      expect(find.text('Place Bid'), findsNothing);
    });

    testWidgets('Shipper view on fixed freight -> displays "Accept" and "Reject", NO "Counter"',
        (tester) async {
      tester.view.physicalSize = const Size(1200, 2400);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(() {
        tester.view.resetPhysicalSize();
        tester.view.resetDevicePixelRatio();
      });

      final application = Bid.fromJson({
        'id': 101,
        'cargo_request_id': 42,
        'driver_id': 7,
        'driver_name': 'Bekele Driver',
        'driver_phone': '0911000000',
        'vehicle_id': 2,
        'amount': 7500.0,
        'note': 'Available immediately',
        'status': 'pending',
        'priceType': 'FIXED',
      });

      final mockBidRepo = _MockBidRepository();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            singleCargoProvider(42).overrideWith((ref) async => fixedCargo),
            bidsForCargoProvider(42).overrideWith((ref) async => [application]),
            bidRepositoryProvider.overrideWithValue(mockBidRepo),
            authNotifierProvider.overrideWith((ref) => _FakeAuthNotifier(
                  User(
                    id: 5,
                    fullName: 'Tigist Shipper',
                    email: 'tigist@shipper.et',
                    phone: '0912000000',
                    role: 'shipper',
                  ),
                )),
          ],
          child: const MaterialApp(
            home: FreightDetailScreen(freightId: 42),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('Bekele Driver'), findsOneWidget);
      expect(find.text('ETB 7500'), findsWidgets);

      // Shipper sees Accept and Reject buttons
      expect(find.text('Accept'), findsOneWidget);
      expect(find.text('Reject'), findsOneWidget);

      // Shipper must NOT see Counter button for fixed freight
      expect(find.text('Counter'), findsNothing);

      // Shipper taps Accept
      await tester.tap(find.text('Accept'));
      await tester.pumpAndSettle();

      // Verify acceptBid was called for bid 101
      expect(mockBidRepo.acceptedBidId, 101);
    });
  });
}
