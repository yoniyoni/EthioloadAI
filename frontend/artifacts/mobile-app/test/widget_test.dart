// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:ethioload_ai/src/data/models/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('notification model accepts Spring date casing and read state', () {
    final notification = AppNotification.fromJson({
      'id': 'n-1',
      'type': 'BID_RECEIVED',
      'data': {'message': 'A new bid arrived'},
      'readAt': null,
      'createdAt': '2026-09-05T10:15:00',
    });

    expect(notification.message, 'A new bid arrived');
    expect(notification.isRead, isFalse);
    expect(notification.createdAt.year, 2026);
  });

  test('message, contract, and dispute models map Spring DTOs', () {
    final message = ChatMessage.fromJson({
      'id': 4,
      'senderId': 10,
      'receiverId': 11,
      'freightId': 22,
      'type': 'text',
      'content': 'Ready for pickup',
      'isRead': true,
      'createdAt': '2026-09-05T10:15:00',
    });
    final contract = ContractView.fromJson({
      'id': 8,
      'freightId': 22,
      'status': 'ACTIVE',
      'paymentStatus': 'PENDING',
      'agreedPrice': 1250,
      'deadline': '2026-09-06',
      'freight': {
        'pickupLocation': 'Addis Ababa',
        'deliveryLocation': 'Adama',
      },
    });
    final dispute = DisputeView.fromJson({
      'id': 3,
      'freightId': 22,
      'bookingId': 7,
      'reason': 'Damaged cargo',
      'description': 'Package arrived damaged',
      'status': 'OPEN',
      'refundAmount': 50,
      'createdAt': '2026-09-05T10:15:00',
    });

    expect(message.content, 'Ready for pickup');
    expect(message.isRead, isTrue);
    expect(contract.agreedPrice, 1250);
    expect(contract.pickupLocation, 'Addis Ababa');
    expect(contract.deliveryLocation, 'Adama');
    expect(dispute.bookingId, 7);
    expect(dispute.refundAmount, 50);
  });

  test('bid model maps Spring DTO and normalizes casing', () {
    final bid = Bid.fromJson({
      'id': 15,
      'freightId': 4,
      'driverId': 9,
      'vehicleId': 4,
      'amount': 45000.0,
      'note': 'Available immediately',
      'status': 'COUNTERED',
      'counterAmount': 42000.0,
      'counterBy': 'SHIPPER',
      'counterNote': 'Special offer',
      'pickupLocation': 'Addis Ababa',
      'destination': 'Hawassa',
      'materialType': 'General',
      'weightTons': 10.0,
      'budget': 50000.0,
      'shipperName': 'Tigist Shipper',
    });

    expect(bid.id, 15);
    expect(bid.cargoRequestId, 4);
    expect(bid.driverId, 9);
    expect(bid.amount, 45000.0);
    expect(bid.status, 'countered');
    expect(bid.isCountered, isTrue);
    expect(bid.counterBy, 'shipper');
    expect(bid.needsDriverAction, isTrue);
    expect(bid.counterAmount, 42000.0);
    expect(bid.cargoPickup, 'Addis Ababa');
    expect(bid.cargoDestination, 'Hawassa');
    expect(bid.cargoMaterial, 'General');
    expect(bid.cargoWeight, 10.0);
    expect(bid.cargoBudget, 50000.0);
    expect(bid.shipperName, 'Tigist Shipper');
  });
}
