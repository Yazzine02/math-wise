// lib/screens/courses_screen.dart
//
// List of all available courses. Pulls from CourseService. Cards mirror the
// design mockups: title row + Lvl badge, 3-line intro clamp, min-read +
// "Start lesson →" footer.

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../models/course.dart';
import '../services/course_service.dart';
import '../theme/app_theme.dart';
import '../widgets/app_widgets.dart';

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
    setState(() => _coursesFuture = CourseService.listCourses());
    await _coursesFuture;
  }

  @override
  Widget build(BuildContext context) {
    return MwScaffold(
      child: RefreshIndicator(
        color: AppColors.lime,
        backgroundColor: AppColors.surface,
        onRefresh: _refresh,
        child: FutureBuilder<List<Course>>(
          future: _coursesFuture,
          builder: (context, snap) {
            return ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(20, 18, 20, 24),
              children: [
                // ─── Header ───────────────────────────────────────────
                Row(
                  children: [
                    MwIconButton(icon: Icons.arrow_back_rounded, onPressed: () => context.pop()),
                    const SizedBox(width: 10),
                    Text('Courses',
                        style: AppText.display(size: 22, weight: FontWeight.w800, color: AppColors.ink, letterSpacing: -0.6)),
                    const Spacer(),
                    if (snap.hasData)
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: AppColors.surface,
                          border: Border.all(color: AppColors.line),
                          borderRadius: BorderRadius.circular(AppRadii.pill),
                        ),
                        child: Text(
                          '${snap.data!.length} TOPICS',
                          style: AppText.label(size: 10, weight: FontWeight.w700, color: AppColors.muted, letterSpacing: 1.2),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  'Bite-sized lessons explaining each concept before you practice it.',
                  style: AppText.body(size: 12, color: AppColors.muted),
                ),
                const SizedBox(height: 18),

                // ─── Body ─────────────────────────────────────────────
                if (snap.connectionState == ConnectionState.waiting)
                  const Padding(
                    padding: EdgeInsets.symmetric(vertical: 40),
                    child: Center(child: CircularProgressIndicator()),
                  )
                else if (snap.hasError)
                  _ErrorBlock(message: '${snap.error}', onRetry: _refresh)
                else if ((snap.data ?? []).isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 40),
                    child: Center(
                      child: Text('No courses available yet.',
                          style: AppText.body(size: 14, color: AppColors.muted)),
                    ),
                  )
                else
                  ...snap.data!.map((c) => Padding(
                        padding: const EdgeInsets.only(bottom: 10),
                        child: _CourseCard(course: c),
                      )),
              ],
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
    return Material(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(AppRadii.lg),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadii.lg),
        onTap: () => context.push('/courses/${course.nodeCode}'),
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppRadii.lg),
            border: Border.all(color: AppColors.line),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      course.nodeTitle,
                      style: AppText.display(size: 17, weight: FontWeight.w800, color: AppColors.ink, letterSpacing: -0.4),
                    ),
                  ),
                  const SizedBox(width: 10),
                  MwDifficultyBadge(level: course.difficultyLevel),
                ],
              ),
              const SizedBox(height: 10),
              Text(
                course.intro,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: AppText.body(size: 12, color: AppColors.inkSoft, height: 1.5),
              ),
              const SizedBox(height: 12),
              const Divider(height: 1, color: AppColors.line),
              const SizedBox(height: 10),
              Row(
                children: [
                  MwReadTime(minutes: course.estimatedMinutes),
                  const Spacer(),
                  Text('Start lesson',
                      style: AppText.title(size: 12, weight: FontWeight.w800, color: AppColors.lime)),
                  const SizedBox(width: 4),
                  const Icon(Icons.arrow_forward, size: 14, color: AppColors.lime),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ErrorBlock extends StatelessWidget {
  final String message;
  final Future<void> Function() onRetry;
  const _ErrorBlock({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.pink.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(AppRadii.lg),
        border: Border.all(color: AppColors.pink.withValues(alpha: 0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Failed to load courses', style: AppText.title(size: 14, color: AppColors.pink)),
          const SizedBox(height: 6),
          Text(message, style: AppText.body(size: 12, color: AppColors.pink)),
          const SizedBox(height: 10),
          TextButton(
            onPressed: onRetry,
            style: TextButton.styleFrom(foregroundColor: AppColors.lime),
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }
}
