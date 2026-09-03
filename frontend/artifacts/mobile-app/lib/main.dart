import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:easy_localization/easy_localization.dart';
import 'src/config/theme/app_theme.dart';
import 'src/config/routes/app_router.dart';
import 'src/data/api/api_client.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await EasyLocalization.ensureInitialized();
  // Must run before runApp() so kBaseUrl already reflects any saved
  // server-URL override by the time the first request goes out.
  await ServerConfig.load(const FlutterSecureStorage());
  // Firebase.initializeApp() — uncomment once google-services.json is added

  runApp(
    EasyLocalization(
      supportedLocales: const [
        Locale('en', 'US'),
        Locale('am', 'ET'),
      ],
      path: 'assets/languages',
      fallbackLocale: const Locale('en', 'US'),
      child: const ProviderScope(
        child: EthioLoadApp(),
      ),
    ),
  );
}

class EthioLoadApp extends ConsumerWidget {
  const EthioLoadApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // ref.read — router and theme are singletons, never need to rebuild MaterialApp
    final router = ref.read(appRouterProvider);
    final themes = ref.read(appThemeProvider);

    return MaterialApp.router(
      title: 'EthioLoad AI',
      localizationsDelegates: context.localizationDelegates,
      supportedLocales: context.supportedLocales,
      locale: context.locale,
      theme: themes.lightTheme,
      darkTheme: themes.darkTheme,
      themeMode: ThemeMode.light,
      routerConfig: router,
      debugShowCheckedModeBanner: false,
    );
  }
}
