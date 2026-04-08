import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'providers/auth_provider.dart';
import 'routing/app_router.dart';

void main() {
  runApp(const MathWiseApp());
}

class MathWiseApp extends StatelessWidget {
  const MathWiseApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        // 1. Provide the Auth State to the whole app
        ChangeNotifierProvider(create: (_) => AuthProvider()),
        
        // 2. Provide the Router, passing it the AuthProvider so it can guard routes
        ProxyProvider<AuthProvider, AppRouter>(
          update: (context, authProvider, previous) => AppRouter(authProvider),
        ),
      ],
      child: Builder(
        builder: (context) {
          // Grab the router
          final goRouter = context.read<AppRouter>().router;

          return MaterialApp.router(
            title: 'Math Wise',
            debugShowCheckedModeBanner: false,
            theme: ThemeData(
              colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
              useMaterial3: true,
            ),
            routerConfig: goRouter, // Use go_router
          );
        },
      ),
    );
  }
}