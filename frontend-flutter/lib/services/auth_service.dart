import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../errors/api_exception.dart';
import 'api_client.dart';

/// Authentication API — sits *outside* the JWT-injecting helpers in ApiClient
/// because the caller can't have a token yet.
///
/// Failure cases throw a typed [ApiException]. The most useful subclasses for
/// auth screens are:
///   - [ValidationException]      — 400 VALIDATION_FAILED with fieldErrors
///   - [ConflictException]        — 409 EMAIL_ALREADY_EXISTS on register
///   - [UnauthorizedException]    — 401 INVALID_CREDENTIALS on login
///   - [NetworkException]         — offline / timeout
class AuthService {
  static const String _authBaseUrl = '${ApiClient.baseUrl}/api/auth';
  static const Duration _timeout = Duration(seconds: 30);

  /// Logs in the student. On success, persists the JWT + display_name in
  /// SharedPreferences. Throws [ApiException] on any failure.
  static Future<void> login(String email, String password) async {
    final response = await _post('/login', {
      'email': email,
      'password': password,
    });
    final data = jsonDecode(response.body) as Map<String, dynamic>;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('jwt_token', data['token'] as String);
    await prefs.setString('display_name', (data['display_name'] as String?) ?? '');
  }

  /// Registers a new student.
  static Future<void> register(
      String email, String password, String displayName) async {
    final response = await _post('/register', {
      'email': email,
      'password': password,
      'display_name': displayName,
    });
    final data = jsonDecode(response.body) as Map<String, dynamic>;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('jwt_token', data['token'] as String);
    await prefs.setString(
        'display_name', (data['display_name'] as String?) ?? displayName);
  }

  static Future<void> logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('jwt_token');
    await prefs.remove('display_name');
  }

  /// Shared POST helper for the two unauthenticated auth endpoints. Same
  /// error semantics as `ApiClient._wrap` — network failures become
  /// [NetworkException], non-2xx becomes typed [ApiException].
  static Future<http.Response> _post(
      String path, Map<String, dynamic> body) async {
    final http.Response response;
    try {
      response = await http
          .post(
            Uri.parse('$_authBaseUrl$path'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode(body),
          )
          .timeout(_timeout);
    } on SocketException {
      throw NetworkException();
    } on TimeoutException {
      throw NetworkException(message: 'Request timed out.');
    } on http.ClientException {
      throw NetworkException();
    }

    if (response.statusCode >= 200 && response.statusCode < 300) {
      return response;
    }
    throw ApiException.fromResponse(response);
  }
}
