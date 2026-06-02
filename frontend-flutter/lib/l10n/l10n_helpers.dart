// Helpers that bridge server-supplied codes to localized UI strings.
//
// Scope note: in "Flutter UI only" French mode, server *content* (AI
// explanations, lesson theory/examples) stays in the language the backend
// produced. But the stable, enumerable bits — the 8 knowledge-node codes and
// the error envelope's machine-readable codes — can be mapped to localized
// labels here, entirely client-side, with no backend change.

import '../errors/api_exception.dart';
import 'app_localizations.dart';

/// Localized display title for a knowledge node, keyed by its stable
/// `node_code`. Falls back to [fallback] (the server-provided title) for any
/// code we don't recognise.
String localizedNodeTitle(AppLocalizations l, String code, String fallback) {
  switch (code) {
    case 'ARITH_ADDITION':
      return l.nodeArithAddition;
    case 'ARITH_SUBTRACTION':
      return l.nodeArithSubtraction;
    case 'ARITH_MULTIPLICATION':
      return l.nodeArithMultiplication;
    case 'ARITH_DIVISION':
      return l.nodeArithDivision;
    case 'FRACTIONS_SIMPLIFY':
      return l.nodeFractionsSimplify;
    case 'FRACTIONS_ADD_SUB':
      return l.nodeFractionsAddSub;
    case 'ALGEBRA_LINEAR':
      return l.nodeAlgebraLinear;
    case 'ALGEBRA_FACTORIZE':
      return l.nodeAlgebraFactorize;
    default:
      return fallback;
  }
}

/// Localized, user-facing message for an [ApiException], chosen from the
/// envelope's machine-readable `code`. Unknown codes fall back to the
/// server-provided message (which may be English — accepted in UI-only scope),
/// or a generic localized line if the server sent no message.
String localizedErrorMessage(AppLocalizations l, ApiException error) {
  if (error is NetworkException) return l.errMsgOffline;
  switch (error.code) {
    case 'INVALID_CREDENTIALS':
      return l.errMsgInvalidCredentials;
    case 'EMAIL_ALREADY_EXISTS':
      return l.errMsgEmailExists;
    case 'VALIDATION_FAILED':
      return l.errMsgValidation;
    case 'RESOURCE_MISSING':
      return l.errMsgNotFound;
    case 'AI_SERVICE_UNAVAILABLE':
    case 'AI_SERVICE_ERROR':
      return l.errMsgAiUnavailable;
    case 'INTERNAL_ERROR':
      return l.errMsgServer;
    default:
      return error.message.isNotEmpty ? error.message : l.errMsgGeneric;
  }
}
