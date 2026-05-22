import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/auth_service.dart';

class AuthProvider extends ChangeNotifier {
  bool _isAuthenticated = false;
  bool _isInitialized = false;
  String _displayName = '';

  bool get isAuthenticated => _isAuthenticated;
  bool get isInitialized => _isInitialized;
  String get displayName => _displayName;

  AuthProvider() {
    _checkExistingToken();
  }

  Future<void> _checkExistingToken() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('jwt_token');
    _isAuthenticated = token != null;
    _displayName = prefs.getString('display_name') ?? '';
    _isInitialized = true;
    notifyListeners();
  }

  Future<String?> login(String email, String password) async {
    final error = await AuthService.login(email, password);
    if (error == null) {
      final prefs = await SharedPreferences.getInstance();
      _displayName = prefs.getString('display_name') ?? '';
      _isAuthenticated = true;
      notifyListeners();
    }
    return error;
  }

  Future<void> logout() async {
    await AuthService.logout();
    _isAuthenticated = false;
    _displayName = '';
    notifyListeners();
  }
}