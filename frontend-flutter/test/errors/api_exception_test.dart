// test/errors/api_exception_test.dart
//
// Unit tests for the typed exception hierarchy introduced in Phase 6.
//
// What this file teaches:
//
// 1. flutter_test is the test framework for Dart code that doesn't need
//    Flutter widgets. ``test('description', () { ... })`` is the basic
//    unit. ``group('label', () { ... })`` lets you cluster related tests.
//
// 2. Dart's ``expect(actual, matcher)`` is the assertion API. Common
//    matchers: ``equals(x)``, ``isA<Type>()``, ``isNull``, ``contains(...)``.
//
// 3. ``http.Response`` is constructible directly — we don't need a real
//    HTTP server to test ``ApiException.fromResponse``. The test feeds
//    handmade Response objects and verifies the exception subtype the
//    factory returns.
//
// Run with: ``flutter test`` (from frontend-flutter/).

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:math_wise_frontend/errors/api_exception.dart';

void main() {
  group('ApiException.fromResponse', () {
    // ─────────────────────────────────────────────────────────────
    // Each branch of the status-code switch gets a test.
    // ─────────────────────────────────────────────────────────────

    test('401 → UnauthorizedException', () {
      final response = http.Response(
        '{"status":401,"code":"INVALID_CREDENTIALS","message":"Invalid email or password"}',
        401,
      );
      final exception = ApiException.fromResponse(response);

      expect(exception, isA<UnauthorizedException>());
      expect(exception.status, 401);
      expect(exception.code, 'INVALID_CREDENTIALS');
      expect(exception.message, 'Invalid email or password');
    });

    test('403 → ForbiddenException', () {
      final response = http.Response(
        '{"status":403,"code":"FORBIDDEN","message":"Nope"}',
        403,
      );
      expect(ApiException.fromResponse(response), isA<ForbiddenException>());
    });

    test('404 → NotFoundException', () {
      final response = http.Response(
        '{"status":404,"code":"RESOURCE_MISSING","message":"Not here"}',
        404,
      );
      expect(ApiException.fromResponse(response), isA<NotFoundException>());
    });

    test('409 → ConflictException with envelope fields', () {
      final response = http.Response(
        '{"status":409,"code":"EMAIL_ALREADY_EXISTS","message":"Email already exists"}',
        409,
      );
      final exception = ApiException.fromResponse(response);

      expect(exception, isA<ConflictException>());
      expect(exception.code, 'EMAIL_ALREADY_EXISTS');
    });

    test('400 + VALIDATION_FAILED → ValidationException with fieldErrors', () {
      final response = http.Response(
        '''
        {
          "status": 400,
          "code": "VALIDATION_FAILED",
          "message": "Request validation failed",
          "fieldErrors": {
            "email": "Email must be a valid address",
            "password": "Password must be between 8 and 128 characters"
          }
        }
        ''',
        400,
      );
      final exception = ApiException.fromResponse(response);

      expect(exception, isA<ValidationException>());
      expect(exception.fieldErrors, isNotNull);
      expect(exception.fieldErrors!['email'], 'Email must be a valid address');
      expect(
        exception.fieldErrors!['password'],
        'Password must be between 8 and 128 characters',
      );
    });

    test('400 without VALIDATION_FAILED code → generic ApiException, not ValidationException', () {
      // INVALID_INPUT (used by the GlobalExceptionHandler for plain
      // IllegalArgumentException) is also 400, but doesn't carry field
      // errors. It should NOT become a ValidationException.
      final response = http.Response(
        '{"status":400,"code":"INVALID_INPUT","message":"Bad nodeCode"}',
        400,
      );
      final exception = ApiException.fromResponse(response);

      expect(exception, isA<ApiException>());
      expect(exception, isNot(isA<ValidationException>()));
      expect(exception.code, 'INVALID_INPUT');
    });

    test('503 → ServiceUnavailableException', () {
      final response = http.Response(
        '{"status":503,"code":"AI_SERVICE_UNAVAILABLE","message":"AI service down"}',
        503,
      );
      expect(
        ApiException.fromResponse(response),
        isA<ServiceUnavailableException>(),
      );
    });

    test('502 → ServiceUnavailableException (extends ServerException)', () {
      final response = http.Response(
        '{"status":502,"code":"AI_SERVICE_ERROR","message":"Upstream error"}',
        502,
      );
      final exception = ApiException.fromResponse(response);
      expect(exception, isA<ServiceUnavailableException>());
      expect(exception, isA<ServerException>()); // parent class
    });

    test('500 → ServerException (not ServiceUnavailableException)', () {
      final response = http.Response(
        '{"status":500,"code":"INTERNAL_ERROR","message":"Something broke"}',
        500,
      );
      final exception = ApiException.fromResponse(response);
      expect(exception, isA<ServerException>());
      expect(exception, isNot(isA<ServiceUnavailableException>()));
    });

    test('arbitrary 4xx (e.g. 418) → generic ApiException', () {
      final response = http.Response('{"message":"I am a teapot"}', 418);
      final exception = ApiException.fromResponse(response);
      expect(exception.status, 418);
      expect(exception.message, 'I am a teapot');
    });

    test('non-JSON body falls back to a generic ApiException with derived message', () {
      // Some legacy or upstream system may return plain text.
      // The factory should not crash; it should degrade gracefully.
      final response = http.Response('garbage that is not JSON', 500);
      final exception = ApiException.fromResponse(response);

      expect(exception, isA<ServerException>());
      expect(exception.status, 500);
      // No structured message available, but message is non-empty.
      expect(exception.message, isNotEmpty);
    });
  });

  group('NetworkException', () {
    test('has null status and code (we never reached the server)', () {
      final exception = NetworkException();
      expect(exception.status, isNull);
      expect(exception.code, isNull);
      expect(exception.message, isNotEmpty);
    });

    test('custom message is preserved', () {
      final exception = NetworkException(message: 'Request timed out.');
      expect(exception.message, 'Request timed out.');
    });
  });
}
