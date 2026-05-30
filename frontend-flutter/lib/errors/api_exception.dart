import 'dart:convert';
import 'package:http/http.dart' as http;

/// Base type for every API or network failure surfaced to the UI layer.
///
/// Parses the backend's [ErrorResponseDto] envelope:
/// ```json
/// {
///   "status": 400,
///   "code": "VALIDATION_FAILED",
///   "message": "Request validation failed",
///   "timestamp": "...",
///   "path": "/api/auth/register",
///   "fieldErrors": { "email": "Email must be a valid address" }
/// }
/// ```
///
/// Subclasses below let screens switch on type instead of parsing the
/// status code in every catch block.
class ApiException implements Exception {
  /// HTTP status code. `null` when we never reached the server.
  final int? status;

  /// Stable machine-readable code from the backend's envelope.
  /// One of: `INVALID_INPUT`, `VALIDATION_FAILED`, `EMAIL_ALREADY_EXISTS`,
  /// `INVALID_CREDENTIALS`, `RESOURCE_MISSING`, `AI_SERVICE_UNAVAILABLE`,
  /// `AI_SERVICE_ERROR`, `INTERNAL_ERROR`, …
  final String? code;

  /// Human-readable explanation. Safe to display verbatim.
  final String message;

  /// Per-field errors when [code] is `VALIDATION_FAILED`.
  /// Map of fieldName → first violation message.
  final Map<String, String>? fieldErrors;

  ApiException({
    required this.message,
    this.status,
    this.code,
    this.fieldErrors,
  });

  /// Build the right subclass from an HTTP error response.
  ///
  /// If the body isn't valid JSON we fall back to a generic [ApiException]
  /// with the raw status code — old endpoints that haven't been migrated to
  /// the envelope still produce a sensible error.
  factory ApiException.fromResponse(http.Response response) {
    final int status = response.statusCode;
    String? code;
    String? message;
    Map<String, String>? fieldErrors;

    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map) {
        code = decoded['code'] as String?;
        final m = decoded['message'];
        if (m is String) message = m;
        if (decoded['fieldErrors'] is Map) {
          fieldErrors = (decoded['fieldErrors'] as Map).map(
            (k, v) => MapEntry(k.toString(), v?.toString() ?? ''),
          );
        }
      }
    } catch (_) {
      // Not JSON — leave message null, fall back below.
    }
    message ??= 'Request failed (HTTP $status)';

    switch (status) {
      case 400:
        if (code == 'VALIDATION_FAILED') {
          return ValidationException(
            message: message,
            fieldErrors: fieldErrors ?? const {},
          );
        }
        return ApiException(status: status, code: code, message: message);
      case 401:
        return UnauthorizedException(message: message, code: code);
      case 403:
        return ForbiddenException(message: message, code: code);
      case 404:
        return NotFoundException(message: message, code: code);
      case 409:
        return ConflictException(message: message, code: code);
      case 502:
      case 503:
        return ServiceUnavailableException(
            message: message, code: code, status: status);
      default:
        if (status >= 500) {
          return ServerException(message: message, code: code, status: status);
        }
        return ApiException(status: status, code: code, message: message);
    }
  }

  @override
  String toString() => message;
}

/// No network: device offline, DNS failure, host unreachable, request
/// timed out. [status] and [code] are intentionally null — we never
/// reached the server, so there's no envelope to parse.
class NetworkException extends ApiException {
  NetworkException({
    String message = "You're offline. Check your connection and try again.",
  }) : super(message: message);
}

/// HTTP 401. Token expired or credentials wrong. Authenticated callers
/// should typically force a logout.
class UnauthorizedException extends ApiException {
  UnauthorizedException({required String message, String? code})
      : super(status: 401, code: code, message: message);
}

/// HTTP 403. Authenticated but not authorised for this resource.
class ForbiddenException extends ApiException {
  ForbiddenException({required String message, String? code})
      : super(status: 403, code: code, message: message);
}

class NotFoundException extends ApiException {
  NotFoundException({required String message, String? code})
      : super(status: 404, code: code, message: message);
}

/// HTTP 409. Used for "email already exists" on registration.
class ConflictException extends ApiException {
  ConflictException({required String message, String? code})
      : super(status: 409, code: code, message: message);
}

/// HTTP 400 + code `VALIDATION_FAILED`. Always carries a non-null
/// [fieldErrors] map so screens can highlight specific inputs.
class ValidationException extends ApiException {
  ValidationException({
    required String message,
    required Map<String, String> fieldErrors,
  }) : super(
            status: 400,
            code: 'VALIDATION_FAILED',
            message: message,
            fieldErrors: fieldErrors);
}

/// HTTP 5xx — something broke server-side.
class ServerException extends ApiException {
  ServerException({
    required String message,
    String? code,
    required int status,
  }) : super(status: status, code: code, message: message);
}

/// HTTP 502/503. Specifically the AI service path: distinguished from
/// other 5xx so the UI can say "AI tutor is taking a break" rather than
/// "something broke."
class ServiceUnavailableException extends ServerException {
  ServiceUnavailableException({
    required String message,
    String? code,
    required int status,
  }) : super(message: message, code: code, status: status);
}
