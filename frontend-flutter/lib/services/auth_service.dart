import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class AuthService {
  // Need to use 10.0.2.2 instead of local host since it is a vm
  static const String baseUrl = 'http://10.0.2.2:9090/api/auth';

  // --- LOGIN ---
  static Future<String?> login(String email, String password) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/login'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'email': email,
          'password': password,
        }),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final token = data['token'];
        
        // Save the token 
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('jwt_token', token);
        
        return null; // Null means success
      } else {
        return response.body; // Returns the error message from Spring Boot
      }
    } catch (e) {
      return 'Network error: Could not connect to server.';
    }
  }

  // --- REGISTER ---
  static Future<String?> register(String email, String password, String displayName) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/register'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'email': email,
          'password': password,
          'display_name': displayName, // Matches @JsonProperty in Java
        }),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final token = data['token'];
        
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('jwt_token', token);
        
        return null; // Success
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
  }
}