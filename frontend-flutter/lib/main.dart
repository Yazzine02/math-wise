// lib/main.dart — UPDATED for the "Bold Playful (dark)" design.
//
// Changes vs the feature/courses branch:
//   • Imports buildAppTheme() from `theme/app_theme.dart`
//   • Sets system UI overlay to dark navy
//   • Switches MaterialApp.theme → buildAppTheme()

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import 'providers/auth_provider.dart';
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
  runApp(const MathWiseApp());
}

class MathWiseApp extends StatelessWidget {
  const MathWiseApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AuthProvider()),
        ProxyProvider<AuthProvider, AppRouter>(
          update: (context, authProvider, previous) => AppRouter(authProvider),
        ),
      ],
      child: Builder(
        builder: (context) {
          final goRouter = context.read<AppRouter>().router;
          return MaterialApp.router(
            title: 'Math Wise',
            debugShowCheckedModeBanner: false,
            theme: buildAppTheme(),
            routerConfig: goRouter,
          );
        },
      ),
    );
  }
}
