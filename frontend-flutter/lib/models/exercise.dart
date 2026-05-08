class Exercise {
  final String id;
  final String nodeCode;
  final String nodeTitle;
  final String questionText;
  final String correctAnswer;
  final int difficultyLevel;

  Exercise({
    required this.id,
    required this.nodeCode,
    required this.nodeTitle,
    required this.questionText,
    required this.correctAnswer,
    required this.difficultyLevel,
  });

  factory Exercise.fromJson(Map<String, dynamic> json) {
    return Exercise(
      id: json['id'] as String,
      nodeCode: json['node_code'] as String,
      nodeTitle: json['node_title'] as String,
      questionText: json['question_text'] as String,
      correctAnswer: json['correct_answer'] as String,
      difficultyLevel: json['difficulty_level'] as int,
    );
  }
}
