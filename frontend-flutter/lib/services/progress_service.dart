import '../models/weakness_summary.dart';
import 'api_client.dart';

class ProgressService {
  static Future<WeaknessSummary> getWeaknesses() async {
    final json = await ApiClient.getJson('/api/student/progress');
    return WeaknessSummary.fromJson(json as Map<String, dynamic>);
  }
}
