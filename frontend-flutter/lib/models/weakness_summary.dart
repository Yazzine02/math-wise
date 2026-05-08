class WeaknessSummary {
  final List<WeaknessEntry> weaknesses;

  WeaknessSummary({required this.weaknesses});

  factory WeaknessSummary.fromJson(Map<String, dynamic> json) {
    final list = (json['weaknesses'] as List<dynamic>? ?? []);
    return WeaknessSummary(
      weaknesses: list.map((e) => WeaknessEntry.fromJson(e as Map<String, dynamic>)).toList(),
    );
  }
}

class WeaknessEntry {
  final String nodeCode;
  final String nodeTitle;
  final int failureCount;

  WeaknessEntry({
    required this.nodeCode,
    required this.nodeTitle,
    required this.failureCount,
  });

  factory WeaknessEntry.fromJson(Map<String, dynamic> json) {
    return WeaknessEntry(
      nodeCode: json['node_code'] as String,
      nodeTitle: json['node_title'] as String,
      failureCount: (json['failure_count'] as num).toInt(),
    );
  }
}
