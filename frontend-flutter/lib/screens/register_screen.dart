// lib/screens/register_screen.dart
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../services/auth_service.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});

  @override
  _RegisterScreenState createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _displayNameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isLoading = false;

  void _handleRegister() async {
    // Basic validation
    if (_displayNameController.text.isEmpty || 
        _emailController.text.isEmpty || 
        _passwordController.text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please fill in all fields'), backgroundColor: Colors.orange),
      );
      return;
    }

    setState(() => _isLoading = true);
    
    // Call your static AuthService
    final error = await AuthService.register(
      _emailController.text.trim(),
      _passwordController.text.trim(),
      _displayNameController.text.trim(),
    );

    setState(() => _isLoading = false);

    // If the widget was unmounted during the network request, abort
    if (!mounted) return;

    if (error == null) {
      print("Registration successful! Token saved.");
      // Navigate to home since they are now authenticated
      context.go('/home'); 
    } else {
      // Show Spring Boot's error message (e.g., "Email is already registered")
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error), backgroundColor: Colors.red),
      );
    }
  }

  @override
  void dispose() {
    _displayNameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Create Math Wise Account')),
      body: SingleChildScrollView( // Prevents pixel overflow when keyboard pops up
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const SizedBox(height: 40),
            TextField(
              controller: _displayNameController,
              decoration: const InputDecoration(labelText: 'Display Name'),
              textCapitalization: TextCapitalization.words,
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _emailController,
              decoration: const InputDecoration(labelText: 'Email'),
              keyboardType: TextInputType.emailAddress,
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _passwordController,
              decoration: const InputDecoration(labelText: 'Password'),
              obscureText: true,
            ),
            const SizedBox(height: 32),
            _isLoading
                ? const CircularProgressIndicator()
                : ElevatedButton(
                    onPressed: _handleRegister,
                    child: const Text('Register'),
                  ),
            const SizedBox(height: 16),
            // Button to navigate back to login
            TextButton(
              onPressed: () => context.go('/login'),
              child: const Text("Already have an account? Login here"),
            ),
          ],
        ),
      ),
    );
  }
}