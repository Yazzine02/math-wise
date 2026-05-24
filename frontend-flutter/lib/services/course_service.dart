import 'dart:convert';
import '../models/course.dart';
import '../models/lesson.dart';
import 'api_client.dart';

class CourseService {
  static Future<List<Course>> listCourses() async {
    final response = await ApiClient.get('/api/courses');
    if (response.statusCode == 200) {
      final List<dynamic> body = jsonDecode(response.body);
      return body.map((j) => Course.fromJson(j as Map<String, dynamic>)).toList();
    }
    throw Exception('Failed to load courses: ${response.body}');
  }

  static Future<Lesson> getLesson(String nodeCode) async {
    final response = await ApiClient.get('/api/courses/$nodeCode');
    if (response.statusCode == 200) {
      return Lesson.fromJson(jsonDecode(response.body));
    }
    throw Exception('Failed to load lesson: ${response.body}');
  }
}
