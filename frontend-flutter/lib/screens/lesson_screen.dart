import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../models/lesson.dart';
import '../services/course_service.dart';

class LessonScreen extends StatefulWidget {
  final String nodeCode;
  const LessonScreen({super.key, required this.nodeCode});

  @override
  State<LessonScreen> createState() => _LessonScreenState();
}

class _LessonScreenState extends State<LessonScreen> {
  late Future<Lesson> _lessonFuture;

  @override
  void initState() {
    super.initState();
    _lessonFuture = CourseService.getLesson(widget.nodeCode);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Lesson')),
      body: FutureBuilder<Lesson>(
        future: _lessonFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text('Failed to load lesson: ${snapshot.error}', textAlign: TextAlign.center),
              ),
            );
          }
          final lesson = snapshot.data!;
          return _LessonBody(lesson: lesson);
        },
      ),
    );
  }
}

class _LessonBody extends StatelessWidget {
  final Lesson lesson;
  const _LessonBody({required this.lesson});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Text(lesson.nodeTitle, style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Row(
            children: [
              Icon(Icons.access_time, size: 16, color: Colors.grey.shade600),
              const SizedBox(width: 4),
              Text('~${lesson.estimatedMinutes} min read',
                  style: TextStyle(color: Colors.grey.shade700, fontSize: 13)),
              const SizedBox(width: 16),
              Icon(Icons.trending_up, size: 16, color: Colors.grey.shade600),
              const SizedBox(width: 4),
              Text('Difficulty ${lesson.difficultyLevel}',
                  style: TextStyle(color: Colors.grey.shade700, fontSize: 13)),
            ],
          ),
          const SizedBox(height: 24),

          // Intro callout
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: theme.colorScheme.primaryContainer.withValues(alpha: 0.4),
              borderRadius: BorderRadius.circular(12),
              border: Border(left: BorderSide(color: theme.colorScheme.primary, width: 4)),
            ),
            child: Text(
              lesson.intro,
              style: theme.textTheme.bodyLarge?.copyWith(fontStyle: FontStyle.italic, height: 1.4),
            ),
          ),
          const SizedBox(height: 28),

          // Theory section
          _SectionTitle(icon: Icons.menu_book, title: 'The Concept'),
          const SizedBox(height: 8),
          Text(lesson.theory, style: theme.textTheme.bodyLarge?.copyWith(height: 1.5)),
          const SizedBox(height: 28),

          // Examples section
          _SectionTitle(icon: Icons.lightbulb_outline, title: 'Worked Examples'),
          const SizedBox(height: 12),
          ...List.generate(lesson.examples.length, (i) => _ExampleCard(index: i + 1, body: lesson.examples[i])),
          const SizedBox(height: 28),

          // Tip section
          _SectionTitle(icon: Icons.tips_and_updates, title: 'Pro Tip'),
          const SizedBox(height: 8),
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.amber.shade50,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.amber.shade200),
            ),
            child: Text(lesson.tip, style: theme.textTheme.bodyLarge?.copyWith(height: 1.5)),
          ),
          const SizedBox(height: 36),

          // Practice CTA
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              icon: const Icon(Icons.play_arrow),
              label: Text('Practice ${lesson.nodeTitle}'),
              style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 16)),
              onPressed: () => context.push('/exercise?nodeCode=${lesson.nodeCode}'),
            ),
          ),
          const SizedBox(height: 16),
        ],
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final IconData icon;
  final String title;
  const _SectionTitle({required this.icon, required this.title});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 22, color: Theme.of(context).colorScheme.primary),
        const SizedBox(width: 8),
        Text(
          title,
          style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
        ),
      ],
    );
  }
}

class _ExampleCard extends StatelessWidget {
  final int index;
  final String body;
  const _ExampleCard({required this.index, required this.body});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.grey.shade100,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.grey.shade300),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.primary,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(
              'Example $index',
              style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold),
            ),
          ),
          const SizedBox(height: 10),
          Text(
            body,
            style: const TextStyle(fontFamily: 'monospace', height: 1.5, fontSize: 13.5),
          ),
        ],
      ),
    );
  }
}
