import 'dart:convert';
import '../models/exercise.dart';
import '../models/ai_feedback.dart';
import 'api_client.dart';

class ExerciseService {
  static Future<Exercise> getNextExercise() async {
    final response = await ApiClient.get('/api/student/next-exercise');
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
      final isCorrect = correctAnswer.trim().toLowerCase() == studentAnswer.trim().toLowerCase();
      return AiFeedback.fromJson(jsonDecode(response.body), isCorrect: isCorrect);
    }
    throw Exception('Failed to submit answer: ${response.body}');
  }
}
