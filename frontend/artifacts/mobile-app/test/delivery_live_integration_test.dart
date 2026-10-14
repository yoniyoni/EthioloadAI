import 'dart:convert';
import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:image_picker/image_picker.dart';
import 'package:ethioload_ai/src/data/api/api_client.dart';
import 'package:ethioload_ai/src/data/repositories/repositories.dart';
import 'package:ethioload_ai/src/features/dispatch/dispatch_repository.dart';
import 'package:ethioload_ai/src/features/delivery/delivery_repository.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('real dispatch acceptance, trip resume, GPS, arrival and multipart proof', () async {
    HttpOverrides.global = null;
    FlutterSecureStorage.setMockInitialValues({});
    final file = File(const String.fromEnvironment('AUDIT_FIXTURES',
      defaultValue: '../../../local-run-checks/final-fixtures.json'));
    final fixture = jsonDecode(await file.readAsString()) as Map<String, dynamic>;
    final container = ProviderContainer();
    addTearDown(container.dispose);
    final api = container.read(apiClientProvider);
    await AuthRepository(api).login(identifier: fixture['users']['driver']['email'], password: fixture['password']);
    final dispatch = container.read(dispatchRepositoryProvider);
    final inbox = await dispatch.getMyAssignments();
    var assignment = inbox.singleWhere((item) => item.id == fixture['dispatchId']);
    if (assignment.bookingId == null) assignment = await dispatch.acceptAssignment(assignment.id);
    expect(assignment.bookingId, isNotNull);
    final delivery = container.read(deliveryRepositoryProvider);
    final tripId = await delivery.openBookingTrip(assignment.bookingId!);
    expect(await delivery.openBookingTrip(assignment.bookingId!), tripId);
    fixture['bookingId'] = assignment.bookingId;
    fixture['tripId'] = tripId;
    await file.writeAsString(jsonEncode(fixture));
    var trip = await delivery.getTrip(tripId);
    if (trip['startTime'] == null) trip = await delivery.startTrip(tripId);
    expect(trip['startTime'], isNotNull);
    if ((trip['tripStatus'] as String).toUpperCase() == 'ONGOING') {
      await api.post<dynamic>('/trips/$tripId/gps', data: {
        'latitude': 8.54, 'longitude': 39.27, 'speed': 0, 'accuracy': 5,
      });
      trip = await delivery.markArrived(tripId);
    }
    expect((trip['tripStatus'] as String).toUpperCase(), 'ARRIVED');
    final proof = await delivery.submitProof(tripId, XFile.fromData(
      base64Decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aB1sAAAAASUVORK5CYII='),
      name: 'delivery-proof.png', mimeType: 'image/png'), recipientName: 'Audit Recipient');
    fixture['proofId'] = proof.id;
    await file.writeAsString(jsonEncode(fixture));
    expect((await delivery.getProofForTrip(tripId)).id, proof.id);
    expect((await delivery.getTrip(tripId))['tripStatus'].toString().toUpperCase(), 'DELIVERY_PENDING_VERIFICATION');
  }, skip: !const bool.fromEnvironment('LIVE_DELIVERY'), timeout: const Timeout(Duration(minutes: 2)));
}
