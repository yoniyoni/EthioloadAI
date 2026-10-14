import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ethioload_ai/src/data/models/models.dart';
import 'package:ethioload_ai/src/data/providers/data_providers.dart';
import 'package:ethioload_ai/src/data/repositories/repositories.dart';
import 'package:ethioload_ai/src/features/shipper/freight_detail_screen.dart';

class _FakeAuthRepository implements AuthRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FakeAuthNotifier extends AuthNotifier {
  _FakeAuthNotifier(User user) : super(_FakeAuthRepository()) {
    state = AuthState(user: user, token: 'fake-test-token');
  }
}

class _FakeCargoRepository implements CargoRepository {
  _FakeCargoRepository(this.loadNearbyVehicles);

  final Future<List<Vehicle>> Function(double latitude, double longitude)
      loadNearbyVehicles;

  @override
  Future<List<Vehicle>> nearbyVehiclesByLocation(
    double latitude,
    double longitude, {
    double radiusKm = 10.0,
  }) =>
      loadNearbyVehicles(latitude, longitude);

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final cargo = CargoRequest(
    id: 42,
    userId: 7,
    pickupLocation: 'Addis Ababa, Bole',
    destination: 'Adama',
    materialType: 'grain',
    weight: 2,
    urgencyLevel: 'normal',
    status: 'posted',
    pickupLatitude: 8.98,
    pickupLongitude: 38.75,
  );

  User user({required int id, required String role}) => User(
        id: id,
        fullName: 'Test User',
        email: 'user@example.test',
        phone: '+251900000000',
        role: role,
      );

  Widget buildScreen({
    required User viewer,
    required Future<List<Vehicle>> Function(double, double) loadNearbyVehicles,
    CargoRequest? freight,
  }) {
    final screenCargo = freight ?? cargo;
    return ProviderScope(
      overrides: [
        singleCargoProvider(screenCargo.id)
            .overrideWith((ref) async => screenCargo),
        bidsForCargoPageProvider((screenCargo.id, 0)).overrideWith(
          (ref) async => const BidPage(
            content: [],
            page: 0,
            size: 20,
            hasNext: false,
            nextPage: null,
            recommendedBidId: null,
            pendingCount: 0,
          ),
        ),
        authNotifierProvider.overrideWith((ref) => _FakeAuthNotifier(viewer)),
        cargoRepositoryProvider.overrideWithValue(
          _FakeCargoRepository(loadNearbyVehicles),
        ),
      ],
      child: MaterialApp(
        home: FreightDetailScreen(freightId: screenCargo.id),
      ),
    );
  }

  testWidgets('freight owner sees nearby map at pickup coordinates',
      (tester) async {
    final pending = Completer<List<Vehicle>>();
    double? requestedLatitude;
    double? requestedLongitude;
    await tester.pumpWidget(buildScreen(
      viewer: user(id: cargo.userId, role: 'shipper'),
      loadNearbyVehicles: (latitude, longitude) {
        requestedLatitude = latitude;
        requestedLongitude = longitude;
        return pending.future;
      },
    ));
    await tester.pump();

    expect(find.byKey(const Key('freight-nearby-vehicles')), findsOneWidget);
    expect(find.text('Loading nearby vehicles...'), findsOneWidget);
    expect(requestedLatitude, cargo.pickupLatitude);
    expect(requestedLongitude, cargo.pickupLongitude);

    pending.complete(<Vehicle>[]);
    await tester.pumpAndSettle();
  });

  testWidgets('admin sees nearby map for freight they do not own',
      (tester) async {
    final pending = Completer<List<Vehicle>>();
    await tester.pumpWidget(buildScreen(
      viewer: user(id: 99, role: 'admin'),
      loadNearbyVehicles: (_, __) => pending.future,
    ));
    await tester.pump();

    expect(find.byKey(const Key('freight-nearby-vehicles')), findsOneWidget);
    pending.complete(<Vehicle>[]);
    await tester.pumpAndSettle();
  });

  testWidgets('non-owner shipper does not see nearby vehicles',
      (tester) async {
    await tester.pumpWidget(buildScreen(
      viewer: user(id: 99, role: 'shipper'),
      loadNearbyVehicles: (_, __) async => <Vehicle>[],
    ));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('freight-nearby-vehicles')), findsNothing);
    expect(find.byKey(const Key('freight-nearby-vehicles-unavailable')),
        findsNothing);
  });

  testWidgets('owner sees explanation when pickup coordinates are missing',
      (tester) async {
    final freightWithoutCoordinates = CargoRequest(
      id: cargo.id,
      userId: cargo.userId,
      pickupLocation: cargo.pickupLocation,
      destination: cargo.destination,
      materialType: cargo.materialType,
      weight: cargo.weight,
      urgencyLevel: cargo.urgencyLevel,
      status: cargo.status,
    );
    await tester.pumpWidget(buildScreen(
      viewer: user(id: cargo.userId, role: 'shipper'),
      freight: freightWithoutCoordinates,
      loadNearbyVehicles: (_, __) async => <Vehicle>[],
    ));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('freight-nearby-vehicles')), findsNothing);
    expect(
      find.text(
        'Nearby vehicles are unavailable because this freight has no pickup coordinates.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('owner sees explanation when only one pickup coordinate exists',
      (tester) async {
    final freightWithPartialCoordinates = CargoRequest(
      id: cargo.id,
      userId: cargo.userId,
      pickupLocation: cargo.pickupLocation,
      destination: cargo.destination,
      materialType: cargo.materialType,
      weight: cargo.weight,
      urgencyLevel: cargo.urgencyLevel,
      status: cargo.status,
      pickupLatitude: cargo.pickupLatitude,
    );
    await tester.pumpWidget(buildScreen(
      viewer: user(id: cargo.userId, role: 'shipper'),
      freight: freightWithPartialCoordinates,
      loadNearbyVehicles: (_, __) async => <Vehicle>[],
    ));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('freight-nearby-vehicles')), findsNothing);
    expect(
      find.text(
        'Nearby vehicles are unavailable because this freight has no pickup coordinates.',
      ),
      findsOneWidget,
    );
  });

  testWidgets('nearby map displays repository errors', (tester) async {
    await tester.pumpWidget(buildScreen(
      viewer: user(id: cargo.userId, role: 'shipper'),
      loadNearbyVehicles: (_, __) async => throw Exception('test failure'),
    ));
    await tester.pumpAndSettle();

    expect(find.textContaining('Failed to load nearby vehicles:'), findsOneWidget);
  });

  testWidgets('nearby map displays empty results', (tester) async {
    await tester.pumpWidget(buildScreen(
      viewer: user(id: cargo.userId, role: 'shipper'),
      loadNearbyVehicles: (_, __) async => <Vehicle>[],
    ));
    await tester.pumpAndSettle();

    expect(find.text('No nearby vehicles available'), findsOneWidget);
  });
}
