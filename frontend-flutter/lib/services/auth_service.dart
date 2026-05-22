import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import 'api_client.dart';

class AuthService {
  // Single source of truth for the API base URL lives in ApiClient. Auth
  // endpoints can't use ApiClient.get/post directly because the JWT-injecting
  // helpers there assume the user is already authenticated.
  static const String _authBaseUrl = '${ApiClient.baseUrl}/api/auth';

  // --- LOGIN ---
  static Future<String?> login(String email, String password) async {
    try {
      final response = await http.post(
        Uri.parse('$_authBaseUrl/login'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'email': email,
          'password': password,
        }),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('jwt_token', data['token']);
        await prefs.setString('display_name', data['display_name'] ?? '');
        return null;
      } else {
        return response.body;
      }
    } catch (e) {
      return 'Network error: Could not connect to server.';
    }
  }

  // --- REGISTER ---
  static Future<String?> register(String email, String password, String displayName) async {
    try {
      final response = await http.post(
        Uri.parse('$_authBaseUrl/register'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'email': email,
          'password': password,
          'display_name': displayName,
        }),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('jwt_token', data['token']);
        await prefs.setString('display_name', data['display_name'] ?? displayName);
        return null;
      } else {
        return response.body;
      }
    } catch (e) {
      return 'Network error: Could not connect to server.';
    }
  }

  // --- LOGOUT ---
  static Future<void> logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('jwt_token');
    await prefs.remove('display_name');
  }
}
