import 'package:go_router/go_router.dart';
import '../providers/auth_provider.dart';
import '../screens/login_screen.dart';
import '../screens/home_screen.dart';
import '../screens/register_screen.dart';
import '../screens/exercise_screen.dart';
import '../screens/feedback_screen.dart';
import '../screens/courses_screen.dart';
import '../screens/lesson_screen.dart';
import '../models/ai_feedback.dart';

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
      final bool isGoingToRegister = state.matchedLocation == '/register';

      if (!isLoggedIn && !isGoingToLogin && !isGoingToRegister) {
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
        path: '/register',
        name: 'register',
        builder: (context, state) => const RegisterScreen(),
      ),
      GoRoute(
        path: '/home',
        name: 'home',
        builder: (context, state) => const HomeScreen(),
      ),
      GoRoute(
        path: '/exercise',
        name: 'exercise',
        builder: (context, state) => ExerciseScreen(
          nodeCode: state.uri.queryParameters['nodeCode'],
        ),
      ),
      GoRoute(
        path: '/feedback',
        name: 'feedback',
        builder: (context, state) => FeedbackScreen(feedback: state.extra as AiFeedback),
      ),
      GoRoute(
        path: '/courses',
        name: 'courses',
        builder: (context, state) => const CoursesScreen(),
      ),
      GoRoute(
        path: '/courses/:nodeCode',
        name: 'lesson',
        builder: (context, state) => LessonScreen(
          nodeCode: state.pathParameters['nodeCode']!,
        ),
      ),
    ],
  );
}