import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/auth_service.dart';

class AuthProvider extends ChangeNotifier {
  bool _isAuthenticated = false;
  bool _isInitialized = false; // Prevents routing before we check storage

  bool get isAuthenticated => _isAuthenticated;
  bool get isInitialized => _isInitialized;

  AuthProvider() {
    _checkExistingToken();
  }

  // Check SharedPreferences on app startup
  Future<void> _checkExistingToken() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('jwt_token');
    
    _isAuthenticated = token != null;
    _isInitialized = true;
    notifyListeners(); // Wakes up the router
  }

  // Wrapper for your static login method
  Future<String?> login(String email, String password) async {
    final error = await AuthService.login(email, password);
    if (error == null) {
      _isAuthenticated = true;
      notifyListeners(); // Tells the router to redirect to home
    }
    return error;
  }

  // Wrapper for your static logout method
  Future<void> logout() async {
    await AuthService.logout();
    _isAuthenticated = false;
    notifyListeners(); // Tells the router to kick user to login screen
  }
}