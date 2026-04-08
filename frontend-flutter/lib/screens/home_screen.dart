import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../providers/auth_provider.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Math Wise Dashboard'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () {
              // Logging out will instantly trigger the router to kick you back to /login
              context.read<AuthProvider>().logout();
            },
          )
        ],
      ),
      body: const Center(
        child: Text('Welcome to Math Wise!'),
      ),
    );
  }
}