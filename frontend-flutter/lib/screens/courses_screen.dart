// lib/screens/courses_screen.dart
//
// List of all available courses. Pulls from CourseService. Cards mirror the
// design mockups: title row + Lvl badge, 3-line intro clamp, min-read +
// "Start lesson →" footer.

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../errors/api_exception.dart';
import '../l10n/app_localizations.dart';
import '../l10n/l10n_helpers.dart';
import '../models/course.dart';
import '../providers/auth_provider.dart';
import '../services/course_service.dart';
import '../theme/app_theme.dart';
import '../widgets/app_widgets.dart';
import '../widgets/error_view.dart';

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
    final l = AppLocalizations.of(context)!;
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
                    Text(l.coursesTitle,
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
                          l.topicsCount(snap.data!.length),
                          style: AppText.label(size: 10, weight: FontWeight.w700, color: AppColors.muted, letterSpacing: 1.2),
                        ),
                      ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  l.coursesSubtitle,
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
                  Builder(builder: (ctx) {
                    final err = snap.error;
                    if (err is UnauthorizedException) {
                      // Bounce to /login. Schedule for after build to avoid
                      // calling setState inside another widget's build.
                      WidgetsBinding.instance.addPostFrameCallback((_) {
                        ctx.read<AuthProvider>().logout();
                      });
                      return const SizedBox.shrink();
                    }
                    return ErrorView(
                      error: err is ApiException
                          ? err
                          : ApiException(message: err.toString()),
                      onRetry: _refresh,
                    );
                  })
                else if ((snap.data ?? []).isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 40),
                    child: Center(
                      child: Text(l.coursesEmpty,
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
    final l = AppLocalizations.of(context)!;
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
                      localizedNodeTitle(l, course.nodeCode, course.nodeTitle),
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
                  Text(l.startLesson,
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
