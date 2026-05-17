class Course {
  final String nodeCode;
  final String nodeTitle;
  final String intro;
  final int difficultyLevel;
  final int estimatedMinutes;

  Course({
    required this.nodeCode,
    required this.nodeTitle,
    required this.intro,
    required this.difficultyLevel,
    required this.estimatedMinutes,
  });

  factory Course.fromJson(Map<String, dynamic> json) {
    return Course(
      nodeCode: json['node_code'] as String,
      nodeTitle: json['node_title'] as String,
      intro: json['intro'] as String,
      difficultyLevel: (json['difficulty_level'] as num).toInt(),
      estimatedMinutes: (json['estimated_minutes'] as num).toInt(),
    );
  }
}
