import '../models/exercise.dart';
import '../models/ai_feedback.dart';
import 'api_client.dart';

/// All errors are surfaced as `ApiException` (or one of its subclasses) so
/// callers can switch on type instead of parsing strings.
class ExerciseService {
  /// If [nodeCode] is provided, fetches an exercise specifically from that
  /// knowledge node (used after a lesson). Otherwise the backend serves an
  /// adaptive exercise based on the student's recent weaknesses.
  static Future<Exercise> getNextExercise({String? nodeCode}) async {
    final path = (nodeCode == null || nodeCode.isEmpty)
        ? '/api/student/next-exercise'
        : '/api/student/next-exercise?node_code=${Uri.encodeQueryComponent(nodeCode)}';
    final json = await ApiClient.getJson(path);
    return Exercise.fromJson(json as Map<String, dynamic>);
  }

  /// Submits the student's answer for evaluation. Correctness is
  /// server-authoritative (SymPy-backed); never compare strings on the
  /// client side.
  static Future<AiFeedback> submitAnswer({
    required String nodeCode,
    required String equation,
    required String correctAnswer,
    required String studentAnswer,
  }) async {
    final json = await ApiClient.postJson('/api/exercises/evaluate', {
      'node_code': nodeCode,
      'equation': equation,
      'correct_answer': correctAnswer,
      'student_answer': studentAnswer,
    });
    return AiFeedback.fromJson(json as Map<String, dynamic>);
  }
}
