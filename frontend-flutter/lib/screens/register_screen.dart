// lib/screens/register_screen.dart
//
// Redesigned registration. Uses the existing AuthService directly (matches
// the current branch's behaviour — token is stored, then we redirect to /home).

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../services/auth_service.dart';
import '../theme/app_theme.dart';
import '../widgets/app_widgets.dart';
import '../widgets/mw_wordmark.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});

  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _displayNameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _loading = false;

  @override
  void dispose() {
    _displayNameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _register() async {
    if (_displayNameController.text.isEmpty ||
        _emailController.text.isEmpty ||
        _passwordController.text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please fill in all fields'), backgroundColor: AppColors.diff3),
      );
      return;
    }

    setState(() => _loading = true);
    final error = await AuthService.register(
      _emailController.text.trim(),
      _passwordController.text.trim(),
      _displayNameController.text.trim(),
    );

    if (!mounted) return;
    setState(() => _loading = false);

    if (error == null) {
      context.go('/home');
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error), backgroundColor: AppColors.pink),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return MwScaffold(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(22, 24, 22, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const MwWordmark(size: 16),
                const Spacer(),
                Text('1 of 1', style: AppText.body(size: 11, weight: FontWeight.w600, color: AppColors.muted)),
              ],
            ),
            const SizedBox(height: 24),

            Text(
              'CREATE YOUR PROFILE',
              style: AppText.label(weight: FontWeight.w700, color: AppColors.lime, letterSpacing: 1.5),
            ),
            const SizedBox(height: 6),
            Text(
              'What should we',
              style: AppText.display(size: 28, weight: FontWeight.w800, color: AppColors.ink, letterSpacing: -0.8),
            ),
            Text(
              'call you?',
              style: AppText.display(size: 28, weight: FontWeight.w800, color: AppColors.lime, letterSpacing: -0.8),
            ),

            const SizedBox(height: 22),
            MwField(
              label: 'Display name',
              controller: _displayNameController,
              textCapitalization: TextCapitalization.words,
              textInputAction: TextInputAction.next,
            ),
            const SizedBox(height: 14),
            MwField(
              label: 'Email',
              controller: _emailController,
              keyboardType: TextInputType.emailAddress,
              textInputAction: TextInputAction.next,
            ),
            const SizedBox(height: 14),
            MwField(
              label: 'Password',
              controller: _passwordController,
              obscure: true,
              textInputAction: TextInputAction.done,
              onSubmitted: (_) => _register(),
            ),

            const SizedBox(height: 28),
            MwButton(label: 'Continue  →', onPressed: _register, loading: _loading),

            const SizedBox(height: 18),
            Center(
              child: Wrap(
                children: [
                  Text('Already have an account? ', style: AppText.body(size: 13, color: AppColors.muted)),
                  GestureDetector(
                    onTap: () => context.go('/login'),
                    child: Text('Sign in', style: AppText.body(size: 13, weight: FontWeight.w700, color: AppColors.lime)),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
