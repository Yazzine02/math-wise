// lib/screens/login_screen.dart
//
// Redesigned for the "Bold Playful (dark)" direction.
// Consumes the typed ApiException hierarchy introduced in Phase 6 so that:
//   - ValidationException populates per-field error text on MwField
//   - Other failures render via the shared ErrorView widget (inline, not
//     as an ephemeral SnackBar that the user has to chase)

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../errors/api_exception.dart';
import '../providers/auth_provider.dart';
import '../theme/app_theme.dart';
import '../widgets/app_widgets.dart';
import '../widgets/error_view.dart';
import '../widgets/mw_wordmark.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _loading = false;

  // Last error from the auth call. ValidationException is destructured into
  // per-field error text on the MwFields; everything else renders as a
  // banner via ErrorView.
  ApiException? _error;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    final error = await context.read<AuthProvider>().login(
          _emailController.text.trim(),
          _passwordController.text.trim(),
        );

    if (!mounted) return;
    setState(() {
      _loading = false;
      _error = error;
    });

    if (error == null) {
      context.go('/home');
    }
  }

  @override
  Widget build(BuildContext context) {
    // Pluck field-level errors out of a ValidationException, if any. For all
    // other ApiException types fieldErrors is null and these stay empty.
    final fieldErrors = (_error is ValidationException)
        ? (_error as ValidationException).fieldErrors
        : const <String, String>{};

    // Banner is shown for non-validation errors only: validation is already
    // communicated per-field. Showing both would be noisy.
    final showBanner = _error != null && _error is! ValidationException;

    return MwScaffold(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(22, 24, 22, 24),
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: MediaQuery.of(context).size.height - 80),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const MwWordmark(size: 18),
              const SizedBox(height: 36),

              // Headline
              Text(
                'Sign in &\nlevel up',
                style: AppText.display(size: 32, weight: FontWeight.w800, color: AppColors.ink, letterSpacing: -1),
              ),
              ShaderMask(
                shaderCallback: (rect) => const LinearGradient(
                  colors: [AppColors.lime, AppColors.cyan],
                ).createShader(rect),
                child: Text(
                  'your math.',
                  style: AppText.display(size: 32, weight: FontWeight.w800, color: Colors.white, letterSpacing: -1),
                ),
              ),

              const SizedBox(height: 28),

              MwField(
                label: 'Email',
                controller: _emailController,
                keyboardType: TextInputType.emailAddress,
                textInputAction: TextInputAction.next,
                hint: 'you@school.edu',
                errorText: fieldErrors?['email'],
              ),
              const SizedBox(height: 14),
              MwField(
                label: 'Password',
                controller: _passwordController,
                obscure: true,
                textInputAction: TextInputAction.done,
                onSubmitted: (_) => _login(),
                errorText: fieldErrors?['password'],
              ),

              if (showBanner) ...[
                const SizedBox(height: 18),
                ErrorView(error: _error!),
              ],

              const SizedBox(height: 28),
              MwButton(
                label: _loading ? 'Signing in…' : "Let's go  →",
                onPressed: _login,
                loading: _loading,
              ),

              const SizedBox(height: 18),
              Center(
                child: Wrap(
                  children: [
                    Text('New player? ', style: AppText.body(size: 13, color: AppColors.muted)),
                    GestureDetector(
                      onTap: () => context.go('/register'),
                      child: Text(
                        'Create account',
                        style: AppText.body(size: 13, weight: FontWeight.w700, color: AppColors.lime),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
