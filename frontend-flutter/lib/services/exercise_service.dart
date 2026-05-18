import 'dart:convert';
import '../models/exercise.dart';
import '../models/ai_feedback.dart';
import 'api_client.dart';

class ExerciseService {
  /// If [nodeCode] is provided, fetches an exercise specifically from that knowledge
  /// node (used after a lesson). Otherwise the backend serves an adaptive exercise.
  static Future<Exercise> getNextExercise({String? nodeCode}) async {
    final path = (nodeCode == null || nodeCode.isEmpty)
        ? '/api/student/next-exercise'
        : '/api/student/next-exercise?node_code=${Uri.encodeQueryComponent(nodeCode)}';
    final response = await ApiClient.get(path);
    if (response.statusCode == 200) {
      return Exercise.fromJson(jsonDecode(response.body));
    }
    throw Exception('Failed to fetch next exercise: ${response.body}');
  }

  static Future<AiFeedback> submitAnswer({
    required String nodeCode,
    required String equation,
    required String correctAnswer,
    required String studentAnswer,
  }) async {
    final body = jsonEncode({
      'node_code': nodeCode,
      'equation': equation,
      'correct_answer': correctAnswer,
      'student_answer': studentAnswer,
    });
    final response = await ApiClient.post('/api/exercises/evaluate', body);
    if (response.statusCode == 200) {
      // Correctness is now server-authoritative (SymPy-backed). Don't compare
      // strings client-side — that's what caused false negatives on
      // equivalent-but-differently-formatted answers like "1/2" vs "0.5".
      return AiFeedback.fromJson(jsonDecode(response.body));
    }
    throw Exception('Failed to submit answer: ${response.body}');
  }
}
