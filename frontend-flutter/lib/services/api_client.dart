import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class ApiClient {
  /// Backend base URL. Overridable at build time without code changes:
  ///   flutter run --dart-define=API_BASE_URL=http://192.168.1.42:9090
  ///   flutter build apk --dart-define=API_BASE_URL=https://api.mathwise.app
  ///
  /// The default targets the Android emulator's loopback alias for localhost.
  /// For iOS simulator use http://localhost:9090; for a physical device use
  /// your machine's LAN IP.
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:9090',
  );

  static Future<Map<String, String>> _authHeaders() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('jwt_token') ?? '';
    return {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer $token',
    };
  }

  static Future<http.Response> get(String path) async {
    final headers = await _authHeaders();
    return http.get(Uri.parse('$baseUrl$path'), headers: headers);
  }

  static Future<http.Response> post(String path, String body) async {
    final headers = await _authHeaders();
    return http.post(Uri.parse('$baseUrl$path'), headers: headers, body: body);
  }
}
