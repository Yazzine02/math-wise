import 'package:go_router/go_router.dart';
import '../providers/auth_provider.dart';
import '../screens/login_screen.dart';
import '../screens/home_screen.dart'; // We will create a dummy screen for this next

class AppRouter {
  final AuthProvider authProvider;

  AppRouter(this.authProvider);

  late final GoRouter router = GoRouter(
    refreshListenable: authProvider,
    initialLocation: '/home',
    
    // The Auth Guard
    redirect: (context, state) {
      // Don't route until we've checked SharedPreferences
      if (!authProvider.isInitialized) return null;

      final bool isLoggedIn = authProvider.isAuthenticated;
      final bool isGoingToLogin = state.matchedLocation == '/login';

      if (!isLoggedIn && !isGoingToLogin) {
        return '/login'; // Kick to login
      }

      if (isLoggedIn && isGoingToLogin) {
        return '/home'; // Prevent logged-in users from seeing login screen
      }

      return null; 
    },
    
    routes: [
      GoRoute(
        path: '/login',
        name: 'login',
        builder: (context, state) => LoginScreen(),
      ),
      GoRoute(
        path: '/home',
        name: 'home',
        builder: (context, state) => const HomeScreen(),
      ),
    ],
  );
}