class AiFeedback {
  final String weaknessNode;
  final String explanation;
  final bool isCorrect;

  AiFeedback({
    required this.weaknessNode,
    required this.explanation,
    required this.isCorrect,
  });

  /// Reads server-authoritative correctness from `is_correct` in the response.
  /// Spring Boot delegates the decision to FastAPI's SymPy-backed
  /// `/check-answer` endpoint, so this is the only place where correctness is
  /// determined — Flutter must not run its own string comparison.
  factory AiFeedback.fromJson(Map<String, dynamic> json) {
    return AiFeedback(
      weaknessNode: json['weakness_node'] as String? ?? '',
      explanation: json['explanation'] as String? ?? '',
      isCorrect: json['is_correct'] as bool? ?? false,
    );
  }
}
