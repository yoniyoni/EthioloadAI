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

  test('message history page parses cursors and keeps wrapper message list', () {
    final page = MessageHistoryPage.fromJson({
      'messages': [
        {
          'id': 7,
          'senderId': 10,
          'receiverId': 11,
          'freightId': 22,
          'content': 'latest',
          'createdAt': '2026-10-06T10:00:00.123456',
        },
      ],
      'page': {
        'mode': 'LATEST',
        'olderCursor': 'opaque-older',
        'afterCursor': 'opaque-after',
        'hasOlder': true,
      },
    });

    expect(page.messages.single.id, 7);
    expect(page.olderCursor, 'opaque-older');
    expect(page.afterCursor, 'opaque-after');
    expect(page.hasOlder, isTrue);
  });

  test('bid page parses global recommendation and pagination metadata', () {
    final page = BidPage.fromJson({
      'content': [
        {
          'id': 14,
          'freightId': 22,
          'driverId': 10,
          'vehicleId': 5,
          'amount': 900,
          'status': 'pending',
          'isRecommended': true,
        },
      ],
      'page': 0,
      'size': 20,
      'hasNext': true,
      'nextPage': 1,
      'recommendedBidId': 14,
      'pendingCount': 9,
    });

    expect(page.content.single.id, 14);
    expect(page.content.single.isRecommended, isTrue);
    expect(page.recommendedBidId, 14);
    expect(page.hasNext, isTrue);
    expect(page.nextPage, 1);
    expect(page.pendingCount, 9);
  });

  test('message merge removes duplicate IDs and preserves newest-first order', () {
    ChatMessage message(int id, String createdAt, String content) => ChatMessage(
          id: id,
          senderId: 10,
          receiverId: 11,
          freightId: 22,
          type: 'text',
          content: content,
          createdAt: DateTime.parse(createdAt),
        );

    final merged = mergeChatMessages(
      [
        message(3, '2026-10-06T10:00:00.123456', 'existing latest'),
        message(1, '2026-10-06T09:00:00.000000', 'oldest'),
      ],
      [
        message(3, '2026-10-06T10:00:00.123456', 'updated latest'),
        message(2, '2026-10-06T09:30:00.000000', 'middle'),
      ],
    );

    expect(merged.map((item) => item.id), [3, 2, 1]);
    expect(merged.first.content, 'updated latest');
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
