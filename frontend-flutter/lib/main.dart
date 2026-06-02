// lib/main.dart — "Bold Playful (dark)" design + EN/FR localization.
//
//   • buildAppTheme() from `theme/app_theme.dart`
//   • dark-navy system UI overlay
//   • LocaleProvider drives MaterialApp.locale (EN/FR toggle, persisted)
//   • AppLocalizations supplies the localization delegates + supported locales

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import 'l10n/app_localizations.dart';
import 'providers/auth_provider.dart';
import 'providers/dashboard_signal.dart';
import 'providers/locale_provider.dart';
import 'routing/app_router.dart';
import 'theme/app_theme.dart';

void main() {
  // Make the system status bar transparent and use light icons so it sits
  // cleanly on top of the dark navy background.
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
    systemNavigationBarColor: AppColors.bg,
    systemNavigationBarIconBrightness: Brightness.light,
  ));
  runApp(const MathExpApp());
}

class MathExpApp extends StatelessWidget {
  const MathExpApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AuthProvider()),
        ChangeNotifierProvider(create: (_) => DashboardSignal()),
        ChangeNotifierProvider(create: (_) => LocaleProvider()),
        ProxyProvider<AuthProvider, AppRouter>(
          update: (context, authProvider, previous) => AppRouter(authProvider),
        ),
      ],
      child: Builder(
        builder: (context) {
          final goRouter = context.read<AppRouter>().router;
          // Re-build when the user flips the language so the whole app
          // re-localizes live, without a restart.
          final locale = context.watch<LocaleProvider>().locale;
          return MaterialApp.router(
            title: 'MathExp',
            debugShowCheckedModeBanner: false,
            theme: buildAppTheme(),
            locale: locale,
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            routerConfig: goRouter,
          );
        },
      ),
    );
  }
}
