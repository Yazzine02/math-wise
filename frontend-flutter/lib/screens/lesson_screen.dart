// lib/screens/lesson_screen.dart
//
// Lesson detail. Sections: intro callout · The concept · Worked examples
// (monospace JetBrains Mono cards) · Pro tip · Practice CTA.

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../errors/api_exception.dart';
import '../models/lesson.dart';
import '../providers/auth_provider.dart';
import '../services/course_service.dart';
import '../theme/app_theme.dart';
import '../widgets/app_widgets.dart';
import '../widgets/error_view.dart';

class LessonScreen extends StatefulWidget {
  final String nodeCode;
  const LessonScreen({super.key, required this.nodeCode});

  @override
  State<LessonScreen> createState() => _LessonScreenState();
}

class _LessonScreenState extends State<LessonScreen> {
  late Future<Lesson> _future;

  @override
  void initState() {
    super.initState();
    _future = CourseService.getLesson(widget.nodeCode);
  }

  void _reload() {
    setState(() => _future = CourseService.getLesson(widget.nodeCode));
  }

  @override
  Widget build(BuildContext context) {
    return MwScaffold(
      child: FutureBuilder<Lesson>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            final err = snap.error;
            if (err is UnauthorizedException) {
              WidgetsBinding.instance.addPostFrameCallback((_) {
                context.read<AuthProvider>().logout();
              });
              return const SizedBox.shrink();
            }
            return Padding(
              padding: const EdgeInsets.all(20),
              child: ErrorView(
                error: err is ApiException
                    ? err
                    : ApiException(message: err.toString()),
                onRetry: _reload,
              ),
            );
          }
          return _LessonBody(lesson: snap.data!);
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
    return ListView(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 32),
      children: [
        // ─── Top bar ─────────────────────────────────────────────────
        Row(
          children: [
            MwIconButton(icon: Icons.arrow_back_rounded, onPressed: () => context.pop()),
            const SizedBox(width: 10),
            Text(
              'LESSON',
              style: AppText.label(size: 11, weight: FontWeight.w700, color: AppColors.muted, letterSpacing: 1.2),
            ),
          ],
        ),
        const SizedBox(height: 14),

        // ─── Title + meta ───────────────────────────────────────────
        Text(
          lesson.nodeTitle,
          style: AppText.display(size: 26, weight: FontWeight.w800, color: AppColors.ink, letterSpacing: -0.8),
        ),
        const SizedBox(height: 10),
        Row(
          children: [
            MwDifficultyBadge(level: lesson.difficultyLevel),
            const SizedBox(width: 10),
            MwReadTime(minutes: lesson.estimatedMinutes),
          ],
        ),

        // ─── Intro callout ──────────────────────────────────────────
        const SizedBox(height: 18),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppColors.lime.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(AppRadii.md),
            border: const Border(left: BorderSide(color: AppColors.lime, width: 3)),
          ),
          child: Text(
            lesson.intro,
            style: AppText.body(size: 13, color: AppColors.inkSoft, height: 1.5)
                .copyWith(fontStyle: FontStyle.italic),
          ),
        ),

        // ─── The concept ────────────────────────────────────────────
        const SizedBox(height: 22),
        const _SectionHeader(icon: Icons.menu_book_rounded, title: 'The concept', tint: AppColors.lime),
        const SizedBox(height: 8),
        Text(lesson.theory, style: AppText.body(size: 13, color: AppColors.inkSoft, height: 1.6)),

        // ─── Worked examples ────────────────────────────────────────
        const SizedBox(height: 22),
        const _SectionHeader(icon: Icons.auto_awesome_outlined, title: 'Worked examples', tint: AppColors.lime),
        const SizedBox(height: 10),
        ...List.generate(lesson.examples.length, (i) => Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: _ExampleCard(index: i + 1, body: lesson.examples[i]),
            )),

        // ─── Pro tip ────────────────────────────────────────────────
        const SizedBox(height: 12),
        const _SectionHeader(icon: Icons.bolt_rounded, title: 'Pro tip', tint: AppColors.pink),
        const SizedBox(height: 8),
        Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppColors.pink.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(AppRadii.md),
            border: Border.all(color: AppColors.pink.withValues(alpha: 0.25)),
          ),
          child: Text(lesson.tip, style: AppText.body(size: 13, color: AppColors.inkSoft, height: 1.55)),
        ),

        // ─── Practice CTA ───────────────────────────────────────────
        const SizedBox(height: 24),
        MwButton(
          label: 'Practice  ${lesson.nodeTitle}',
          icon: Icons.play_arrow_rounded,
          onPressed: () => context.pushNamed('exercise', queryParameters: {'nodeCode': lesson.nodeCode}),
        ),
      ],
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final IconData icon;
  final String title;
  final Color tint;
  const _SectionHeader({required this.icon, required this.title, required this.tint});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, size: 18, color: tint),
        const SizedBox(width: 8),
        Text(title, style: AppText.title(size: 15, weight: FontWeight.w800, color: AppColors.ink)),
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
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.bgDeep,
        borderRadius: BorderRadius.circular(AppRadii.md),
        border: Border.all(color: AppColors.line),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
            decoration: BoxDecoration(
              color: AppColors.lime,
              borderRadius: BorderRadius.circular(6),
            ),
            child: Text(
              'EXAMPLE $index',
              style: AppText.title(size: 10, weight: FontWeight.w800, color: AppColors.bgDeep)
                  .copyWith(letterSpacing: 1),
            ),
          ),
          const SizedBox(height: 10),
          Text(body, style: AppText.mono(size: 12.5, color: AppColors.inkSoft, height: 1.6)),
        ],
      ),
    );
  }
}
