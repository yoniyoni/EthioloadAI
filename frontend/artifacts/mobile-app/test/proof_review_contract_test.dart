import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:ethioload_ai/src/data/api/api_client.dart';
import 'package:ethioload_ai/src/data/models/models.dart';
import 'package:ethioload_ai/src/data/providers/data_providers.dart';
import 'package:ethioload_ai/src/data/repositories/repositories.dart';
import 'package:ethioload_ai/src/features/delivery/delivery_repository.dart';
import 'package:ethioload_ai/src/features/delivery/proof_of_delivery.dart';
import 'package:ethioload_ai/src/features/delivery/shipper_proof_review_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:logger/logger.dart';

class _ReviewAdapter implements HttpClientAdapter {
  final List<RequestOptions> requests = [];
  int statusCode = 200;
  Uint8List responseBytes = Uint8List.fromList([0x25, 0x50, 0x44, 0x46]);
  Map<String, dynamic> response = _proofResponse('SUBMITTED');

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    if (options.responseType == ResponseType.bytes) {
      return ResponseBody.fromBytes(
        responseBytes,
        statusCode,
        headers: {
          Headers.contentTypeHeader: ['application/pdf'],
        },
      );
    }
    return ResponseBody.fromString(
      jsonEncode(
        statusCode == 200 ? response : {'message': _errorFor(statusCode)},
      ),
      statusCode,
      headers: {
        Headers.contentTypeHeader: ['application/json'],
      },
    );
  }

  String _errorFor(int status) => switch (status) {
        403 => 'Forbidden',
        422 => 'Invalid request',
        _ => 'Service unavailable',
      };

  @override
  void close({bool force = false}) {}
}

Map<String, dynamic> _proofResponse(String status) => {
      'id': 51,
      'tripId': 73,
      'bookingId': 41,
      'freightId': 9,
      'uploadedById': 12,
      'fileName': 'delivery.pdf',
      'contentType': 'application/pdf',
      'fileSize': 2048,
      'status': status,
      'uploadedAt': '2026-10-21T10:00:00',
      'createdAt': '2026-10-21T10:00:00',
      'updatedAt': '2026-10-21T10:00:00',
    };

ProofOfDelivery _proof(ProofStatus status) => ProofOfDelivery(
      id: 51,
      tripId: 73,
      bookingId: 41,
      freightId: 9,
      uploadedById: 12,
      fileName: 'delivery.pdf',
      contentType: 'application/pdf',
      fileSize: 2048,
      status: status,
      uploadedAt: DateTime(2026, 10, 21),
      createdAt: DateTime(2026, 10, 21),
      updatedAt: DateTime(2026, 10, 21),
    );

ApiClient _apiClient(_ReviewAdapter adapter) {
  final dio = Dio(
    BaseOptions(
      baseUrl: 'https://api.example.test/api',
      validateStatus: (status) => status != null && status < 600,
    ),
  )
    ..httpClientAdapter = adapter
    ..interceptors.add(
      ApiInterceptor(
        logger: Logger(level: Level.off),
        secureStorage: const FlutterSecureStorage(),
      ),
    );
  return ApiClient(
    dio: dio,
    logger: Logger(level: Level.off),
    secureStorage: const FlutterSecureStorage(),
  );
}

Map<String, dynamic> _requestBody(RequestOptions request) {
  final data = request.data;
  if (data is String) return Map<String, dynamic>.from(jsonDecode(data) as Map);
  return Map<String, dynamic>.from(data as Map);
}

class _FakeDeliveryRepository extends DeliveryRepository {
  _FakeDeliveryRepository()
      : super(
          api: ApiClient(
            dio: Dio(BaseOptions(baseUrl: 'https://api.example.test/api')),
            logger: Logger(level: Level.off),
            secureStorage: const FlutterSecureStorage(),
          ),
          logger: Logger(level: Level.off),
        );

  ProofOfDelivery proof = _proof(ProofStatus.submitted);
  Completer<ProofOfDelivery>? pendingProof;
  int proofReads = 0;
  int tripReads = 0;
  int reviewCalls = 0;
  int confirmCalls = 0;
  bool failProofRead = false;
  bool failReview = false;

  @override
  Future<ProofOfDelivery> getProofForTrip(int tripId) async {
    proofReads++;
    if (pendingProof != null) return pendingProof!.future;
    if (failProofRead) {
      throw ApiException(message: 'Forbidden', statusCode: 403);
    }
    return proof;
  }

  @override
  Future<Map<String, dynamic>> getTrip(int tripId) async {
    tripReads++;
    return {'id': tripId, 'tripStatus': 'DELIVERY_PENDING_VERIFICATION'};
  }

  @override
  Future<ProofOfDelivery> reviewProof(
    int proofId, {
    required bool verify,
    String? rejectionReason,
  }) async {
    reviewCalls++;
    if (failReview) {
      throw ApiException(message: 'Service unavailable', statusCode: 503);
    }
    proof = _proof(verify ? ProofStatus.verified : ProofStatus.rejected);
    return proof;
  }

  @override
  Future<Map<String, dynamic>> confirmDelivery(int tripId) async {
    confirmCalls++;
    return {'id': tripId};
  }
}

class _TestAuthNotifier extends AuthNotifier {
  _TestAuthNotifier({bool isShipper = true})
      : super(
          AuthRepository(
            ApiClient(
              dio: Dio(BaseOptions(baseUrl: 'https://api.example.test/api')),
              logger: Logger(level: Level.off),
              secureStorage: const FlutterSecureStorage(),
            ),
          ),
        ) {
    state = AuthState(
      user: User(
        id: 7,
        fullName: 'Test Shipper',
        email: 'shipper@example.test',
        phone: '+251900000000',
        role: isShipper ? 'shipper' : 'driver',
      ),
      token: 'unit-test-token',
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late _ReviewAdapter adapter;
  late DeliveryRepository repository;

  setUp(() {
    FlutterSecureStorage.setMockInitialValues({'auth_token': 'proof-test-jwt'});
    adapter = _ReviewAdapter();
    repository = DeliveryRepository(
      api: _apiClient(adapter),
      logger: Logger(level: Level.off),
    );
  });

  test(
    'verification sends the exact Spring request body and endpoint',
    () async {
      final proof = await repository.reviewProof(51, verify: true);

      expect(adapter.requests.single.method, 'POST');
      expect(adapter.requests.single.path, '/proof-of-delivery/51/verify');
      expect(_requestBody(adapter.requests.single), {'action': 'VERIFY'});
      expect(proof.status, ProofStatus.submitted);
    },
  );

  test(
    'rejection trims reason and sends the exact Spring request body',
    () async {
      await repository.reviewProof(
        51,
        verify: false,
        rejectionReason: '  Missing signature  ',
      );

      expect(_requestBody(adapter.requests.single), {
        'action': 'REJECT',
        'rejectionReason': 'Missing signature',
      });
    },
  );

  test(
    'blank or overlong rejection reasons fail before making a request',
    () async {
      await expectLater(
        repository.reviewProof(51, verify: false, rejectionReason: '  '),
        throwsA(isA<ApiException>()),
      );
      await expectLater(
        repository.reviewProof(
          51,
          verify: false,
          rejectionReason: List.filled(1001, 'x').join(),
        ),
        throwsA(isA<ApiException>()),
      );
      expect(adapter.requests, isEmpty);
    },
  );

  test('only known SUBMITTED status can be reviewed', () {
    for (final status in [
      'SUBMITTED',
      'VERIFIED',
      'REJECTED',
      'UNKNOWN_NEW_STATE',
    ]) {
      final proof = ProofOfDelivery.fromJson(_proofResponse(status));
      expect(
        proof.canReview,
        status == 'SUBMITTED',
        reason: 'action eligibility for status $status',
      );
    }
    expect(
      ProofOfDelivery.fromJson(_proofResponse('UNKNOWN_NEW_STATE')).status,
      ProofStatus.unknown,
    );
  });

  test('validation, permission, and server errors remain API errors', () async {
    for (final status in [422, 403, 503]) {
      adapter.statusCode = status;
      await expectLater(
        repository.reviewProof(51, verify: true),
        throwsA(
          isA<ApiException>().having(
            (error) => error.statusCode,
            'statusCode',
            status,
          ),
        ),
      );
    }
  });

  test(
    'proof download uses authenticated route without putting token in URL',
    () async {
      final bytes = await repository.downloadProof(51);
      final request = adapter.requests.single;

      expect(bytes, adapter.responseBytes);
      expect(request.method, 'GET');
      expect(request.path, '/proof-of-delivery/51/download');
      expect(request.uri.path, '/api/proof-of-delivery/51/download');
      expect(request.headers['Authorization'], 'Bearer proof-test-jwt');
      expect(request.uri.query, isEmpty);
    },
  );

  test('401 proof download expires the stored session', () async {
    adapter.statusCode = 401;
    var cleared = false;
    final api = ApiClient(
      dio: _apiClient(adapter).dio,
      logger: Logger(level: Level.off),
      secureStorage: const FlutterSecureStorage(),
      onSessionCleared: () => cleared = true,
    );

    await expectLater(
      api.getBytes('/proof-of-delivery/51/download'),
      throwsA(
        isA<ApiException>().having(
          (error) => error.statusCode,
          'statusCode',
          401,
        ),
      ),
    );

    expect(cleared, isTrue);
    expect(await const FlutterSecureStorage().read(key: 'auth_token'), isNull);
  });

  testWidgets('screen shows loading then prevents actions for reviewed proof', (
    tester,
  ) async {
    final fake = _FakeDeliveryRepository();
    fake.pendingProof = Completer<ProofOfDelivery>();
    await tester.pumpWidget(_screenApp(fake));

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    fake.pendingProof!.complete(_proof(ProofStatus.verified));
    await tester.pumpAndSettle();

    expect(find.textContaining('Verified'), findsWidgets);
    expect(find.text('Verify proof'), findsNothing);
    expect(find.text('Reject proof'), findsNothing);
  });

  testWidgets('screen reports permission errors with retry action', (
    tester,
  ) async {
    final fake = _FakeDeliveryRepository()..failProofRead = true;
    await tester.pumpWidget(_screenApp(fake));
    await tester.pumpAndSettle();

    expect(find.textContaining('not authorized'), findsOneWidget);
    expect(find.text('Retry'), findsOneWidget);
  });

  testWidgets('failed review reports server error without showing success', (
    tester,
  ) async {
    final fake = _FakeDeliveryRepository()..failReview = true;
    await tester.pumpWidget(_screenApp(fake));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Verify proof'));
    await tester.pumpAndSettle();

    expect(fake.proof.status, ProofStatus.submitted);
    expect(
      find.text(
        'The server could not complete this request. Please try again.',
      ),
      findsOneWidget,
    );
    expect(find.text('Proof verified.'), findsNothing);
  });

  testWidgets(
    'successful review refreshes proof and trip and invalidates bookings',
    (tester) async {
      final fake = _FakeDeliveryRepository();
      var bookingPageLoads = 0;
      var singleBookingLoads = 0;
      final container = ProviderContainer(
        overrides: [
          authNotifierProvider.overrideWith((ref) => _TestAuthNotifier()),
          deliveryRepositoryProvider.overrideWithValue(fake),
          bookingPageProvider.overrideWith((ref, page) async {
            bookingPageLoads++;
            return const BookingPage(
              content: [],
              number: 0,
              size: 20,
              hasNext: false,
            );
          }),
          singleBookingProvider.overrideWith((ref, id) async {
            singleBookingLoads++;
            return Booking(
              id: id,
              cargoId: 9,
              vehicleId: 2,
              driverId: 12,
              bookingStatus: 'accepted',
              estimatedPrice: 100,
            );
          }),
        ],
      );
      addTearDown(container.dispose);
      final pageSubscription = container.listen(
        bookingPageProvider(0),
        (_, __) {},
      );
      final bookingSubscription = container.listen(
        singleBookingProvider(41),
        (_, __) {},
      );
      addTearDown(pageSubscription.close);
      addTearDown(bookingSubscription.close);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(home: ShipperProofReviewScreen(tripId: 73)),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Verify proof'), findsOneWidget);

      await tester.tap(find.text('Verify proof'));
      await tester.pumpAndSettle();

      expect(fake.reviewCalls, 1);
      expect(fake.proofReads, 2);
      expect(fake.tripReads, 2);
      expect(fake.confirmCalls, 0);
      expect(fake.proof.status, ProofStatus.verified);
      expect(bookingPageLoads, greaterThanOrEqualTo(2));
      expect(singleBookingLoads, greaterThanOrEqualTo(2));
      expect(
        find.text('This proof cannot be reviewed again (Verified).'),
        findsOneWidget,
      );
    },
  );
}

Widget _screenApp(_FakeDeliveryRepository repository) => ProviderScope(
      overrides: [
        authNotifierProvider.overrideWith((ref) => _TestAuthNotifier()),
        deliveryRepositoryProvider.overrideWithValue(repository),
      ],
      child: const MaterialApp(home: ShipperProofReviewScreen(tripId: 73)),
    );
