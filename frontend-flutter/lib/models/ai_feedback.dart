class AiFeedback {
  final String weaknessNode;
  final String explanation;
  final bool isCorrect;

  AiFeedback({
    required this.weaknessNode,
    required this.explanation,
    required this.isCorrect,
  });

  factory AiFeedback.fromJson(Map<String, dynamic> json, {required bool isCorrect}) {
    return AiFeedback(
      weaknessNode: json['weakness_node'] as String? ?? '',
      explanation: json['explanation'] as String? ?? '',
      isCorrect: isCorrect,
    );
  }
}
