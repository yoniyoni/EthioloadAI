import 'package:easy_localization/easy_localization.dart';
import 'package:ethioload_ai/src/data/models/models.dart';
import 'package:ethioload_ai/src/features/shared/widgets/place_search_field.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    SharedPreferences.setMockInitialValues({});
    await EasyLocalization.ensureInitialized();
  });

  testWidgets('disposing place search field does not call setState',
      (tester) async {
    await tester.pumpWidget(
      EasyLocalization(
        supportedLocales: const [Locale('en', 'US'), Locale('am', 'ET')],
        path: 'assets/languages',
        fallbackLocale: const Locale('en', 'US'),
        child: const ProviderScope(
          child: MaterialApp(
            home: Scaffold(
              body: PlaceSearchField(
                label: 'Location',
                onSelected: _ignorePlace,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.pumpWidget(const SizedBox.shrink());
    expect(tester.takeException(), isNull);
  });
}

void _ignorePlace(PlaceResult place) {}
