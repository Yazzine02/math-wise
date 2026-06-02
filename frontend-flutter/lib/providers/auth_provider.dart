import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../errors/api_exception.dart';
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

  /// Returns null on success. Returns the [ApiException] on failure so the
  /// caller (login screen) can switch on type — typically:
  ///   - [ValidationException]   → highlight specific input fields
  ///   - [UnauthorizedException] → "wrong email or password"
  ///   - [NetworkException]      → "you're offline"
  Future<ApiException?> login(String email, String password) async {
    try {
      await AuthService.login(email, password);
      final prefs = await SharedPreferences.getInstance();
      _displayName = prefs.getString('display_name') ?? '';
      _isAuthenticated = true;
      notifyListeners();
      return null;
    } on ApiException catch (e) {
      return e;
    }
  }

  /// Same contract as [login]. Conflict (email exists) arrives as a
  /// [ConflictException]; validation issues as [ValidationException].
  Future<ApiException?> register(
      String email, String password, String displayName) async {
    try {
      await AuthService.register(email, password, displayName);
      final prefs = await SharedPreferences.getInstance();
      _displayName =
          prefs.getString('display_name') ?? displayName;
      _isAuthenticated = true;
      notifyListeners();
      return null;
    } on ApiException catch (e) {
      return e;
    }
  }

  Future<void> logout() async {
    await AuthService.logout();
    _isAuthenticated = false;
    _displayName = '';
    notifyListeners();
  }
}
