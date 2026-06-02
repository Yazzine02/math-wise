import '../models/course.dart';
import '../models/lesson.dart';
import 'api_client.dart';

class CourseService {
  static Future<List<Course>> listCourses() async {
    final json = await ApiClient.getJson('/api/courses');
    return (json as List)
        .map((j) => Course.fromJson(j as Map<String, dynamic>))
        .toList();
  }

  static Future<Lesson> getLesson(String nodeCode) async {
    final json = await ApiClient.getJson('/api/courses/$nodeCode');
    return Lesson.fromJson(json as Map<String, dynamic>);
  }
}
