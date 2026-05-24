import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../models/course.dart';
import '../services/course_service.dart';

class CoursesScreen extends StatefulWidget {
  const CoursesScreen({super.key});

  @override
  State<CoursesScreen> createState() => _CoursesScreenState();
}

class _CoursesScreenState extends State<CoursesScreen> {
  late Future<List<Course>> _coursesFuture;

  @override
  void initState() {
    super.initState();
    _coursesFuture = CourseService.listCourses();
  }

  Future<void> _refresh() async {
    setState(() {
      _coursesFuture = CourseService.listCourses();
    });
    await _coursesFuture;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Courses')),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: FutureBuilder<List<Course>>(
          future: _coursesFuture,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              return ListView(
                children: [
                  const SizedBox(height: 200),
                  Center(
                    child: Padding(
                      padding: const EdgeInsets.all(24),
                      child: Column(
                        children: [
                          Text(
                            'Failed to load courses',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            '${snapshot.error}',
                            textAlign: TextAlign.center,
                            style: const TextStyle(color: Colors.red),
                          ),
                          const SizedBox(height: 16),
                          ElevatedButton(onPressed: _refresh, child: const Text('Retry')),
                        ],
                      ),
                    ),
                  ),
                ],
              );
            }
            final courses = snapshot.data ?? [];
            if (courses.isEmpty) {
              return const Center(child: Text('No courses available yet.'));
            }
            return ListView.separated(
              padding: const EdgeInsets.all(16),
              itemCount: courses.length,
              separatorBuilder: (_, __) => const SizedBox(height: 12),
              itemBuilder: (context, i) => _CourseCard(course: courses[i]),
            );
          },
        ),
      ),
    );
  }
}

class _CourseCard extends StatelessWidget {
  final Course course;
  const _CourseCard({required this.course});

  @override
  Widget build(BuildContext context) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () => context.push('/courses/${course.nodeCode}'),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      course.nodeTitle,
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                    ),
                  ),
                  _DifficultyBadge(level: course.difficultyLevel),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                course.intro,
                style: Theme.of(context).textTheme.bodyMedium,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Icon(Icons.access_time, size: 16, color: Colors.grey.shade600),
                  const SizedBox(width: 4),
                  Text(
                    '~${course.estimatedMinutes} min read',
                    style: TextStyle(color: Colors.grey.shade700, fontSize: 12),
                  ),
                  const Spacer(),
                  Text(
                    'Start lesson',
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.primary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  Icon(Icons.arrow_forward, size: 16, color: Theme.of(context).colorScheme.primary),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _DifficultyBadge extends StatelessWidget {
  final int level;
  const _DifficultyBadge({required this.level});

  @override
  Widget build(BuildContext context) {
    final colors = {1: Colors.green, 2: Colors.green, 3: Colors.orange, 4: Colors.deepOrange, 5: Colors.red};
    final color = colors[level] ?? Colors.grey;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color),
      ),
      child: Text(
        'Lvl $level',
        style: TextStyle(color: color, fontSize: 11, fontWeight: FontWeight.bold),
      ),
    );
  }
}
