class Lesson {
  final String nodeCode;
  final String nodeTitle;
  final int difficultyLevel;
  final int estimatedMinutes;
  final String intro;
  final String theory;
  final List<String> examples;
  final String tip;

  Lesson({
    required this.nodeCode,
    required this.nodeTitle,
    required this.difficultyLevel,
    required this.estimatedMinutes,
    required this.intro,
    required this.theory,
    required this.examples,
    required this.tip,
  });

  factory Lesson.fromJson(Map<String, dynamic> json) {
    return Lesson(
      nodeCode: json['node_code'] as String,
      nodeTitle: json['node_title'] as String,
      difficultyLevel: (json['difficulty_level'] as num).toInt(),
      estimatedMinutes: (json['estimated_minutes'] as num).toInt(),
      intro: json['intro'] as String,
      theory: json['theory'] as String,
      examples: (json['examples'] as List<dynamic>? ?? []).map((e) => e as String).toList(),
      tip: json['tip'] as String,
    );
  }
}
