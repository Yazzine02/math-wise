import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';
import '../errors/api_exception.dart';

/// Single HTTP entry point for the rest of the app.
///
/// Prefer [getJson]/[postJson] for new code: they decode the JSON body on
/// success and throw a typed [ApiException] on failure (parsed from the
/// backend's uniform error envelope). The raw [get]/[post] helpers stay for
/// flows that need access to the [http.Response] object directly.
class ApiClient {
  /// Backend base URL. Overridable at build time without code changes:
  ///   flutter run --dart-define=API_BASE_URL=http://192.168.1.42:9090
  ///   flutter build apk --dart-define=API_BASE_URL=https://api.mathwise.app
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:9090',
  );

  /// Client-side request timeout. Backend has its own 60s read timeout to
  /// FastAPI (Phase 2); we give ourselves an extra grace period before
  /// declaring the whole request a [NetworkException].
  static const Duration _timeout = Duration(seconds: 75);

  static Future<Map<String, String>> _authHeaders() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('jwt_token') ?? '';
    return {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer $token',
    };
  }

  // ───── Raw HTTP — used by auth flow before/around login ─────

  static Future<http.Response> get(String path) async {
    final headers = await _authHeaders();
    return http.get(Uri.parse('$baseUrl$path'), headers: headers);
  }

  static Future<http.Response> post(String path, String body) async {
    final headers = await _authHeaders();
    return http.post(Uri.parse('$baseUrl$path'), headers: headers, body: body);
  }

  // ───── JSON helpers — preferred path for domain services ─────

  /// Authenticated GET. Returns the decoded JSON body on success.
  /// Throws [ApiException] (or one of its subclasses) on any failure:
  ///   - [NetworkException] for offline / DNS / socket / timeout
  ///   - [UnauthorizedException] for HTTP 401
  ///   - [ValidationException] for HTTP 400 with code VALIDATION_FAILED
  ///   - generic [ApiException] for other non-2xx
  static Future<dynamic> getJson(String path) async {
    return _wrap(() async => get(path).timeout(_timeout));
  }

  /// Authenticated POST. [body] may be a String (sent as-is) or any object
  /// that `jsonEncode` accepts. Same error semantics as [getJson].
  static Future<dynamic> postJson(String path, dynamic body) async {
    final encoded = body is String ? body : jsonEncode(body);
    return _wrap(() async => post(path, encoded).timeout(_timeout));
  }

  /// Common machinery: run an HTTP call, decode JSON on success, translate
  /// every failure mode into an [ApiException]. Centralised here so every
  /// caller sees the same error semantics without copying try/catch
  /// boilerplate.
  static Future<dynamic> _wrap(
      Future<http.Response> Function() requester) async {
    final http.Response response;
    try {
      response = await requester();
    } on SocketException {
      throw NetworkException();
    } on TimeoutException {
      throw NetworkException(message: 'Request timed out.');
    } on http.ClientException {
      throw NetworkException();
    }

    if (response.statusCode >= 200 && response.statusCode < 300) {
      if (response.body.isEmpty) return null;
      return jsonDecode(response.body);
    }
    throw ApiException.fromResponse(response);
  }
}
