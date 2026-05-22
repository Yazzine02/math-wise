import 'dart:convert';
import '../models/weakness_summary.dart';
import 'api_client.dart';

class ProgressService {
  static Future<WeaknessSummary> getWeaknesses() async {
    final response = await ApiClient.get('/api/student/progress');
    if (response.statusCode == 200) {
      return WeaknessSummary.fromJson(jsonDecode(response.body));
    }
    throw Exception('Failed to fetch progress: ${response.body}');
  }
}
