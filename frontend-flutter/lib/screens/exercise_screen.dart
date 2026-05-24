// lib/screens/exercise_screen.dart
//
// Adaptive exercise. Matches the design mockup: top bar with home button,
// difficulty badge, big gradient question card, focused-glow answer card.
// Wiring unchanged from the feature/courses branch.

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../models/exercise.dart';
import '../models/ai_feedback.dart';
import '../services/exercise_service.dart';
import '../theme/app_theme.dart';
import '../widgets/app_widgets.dart';

class ExerciseScreen extends StatefulWidget {
  final String? nodeCode;
  const ExerciseScreen({super.key, this.nodeCode});

  @override
  State<ExerciseScreen> createState() => _ExerciseScreenState();
}

class _ExerciseScreenState extends State<ExerciseScreen> {
  Exercise? _exercise;
  bool _loading = true;
  bool _submitting = false;
  String? _error;
  final _answerController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _loadExercise();
  }

  @override
  void dispose() {
    _answerController.dispose();
    super.dispose();
  }

  Future<void> _loadExercise() async {
    setState(() { _loading = true; _error = null; });
    try {
      final ex = await ExerciseService.getNextExercise(nodeCode: widget.nodeCode);
      if (mounted) setState(() { _exercise = ex; _loading = false; });
    } catch (e) {
      if (mounted) setState(() { _error = e.toString(); _loading = false; });
    }
  }

  Future<void> _submit() async {
    final answer = _answerController.text.trim();
    if (answer.isEmpty || _exercise == null) return;
    setState(() { _submitting = true; _error = null; });
    try {
      final AiFeedback fb = await ExerciseService.submitAnswer(
        nodeCode: _exercise!.nodeCode,
        equation: _exercise!.questionText,
        correctAnswer: _exercise!.correctAnswer,
        studentAnswer: answer,
      );
      if (!mounted) return;
      _answerController.clear();
      final wantsNext = await context.pushNamed<bool>('feedback', extra: fb);
      if (!mounted) return;
      setState(() => _submitting = false);
      if (wantsNext == true) {
        await _loadExercise();
      }
    } catch (e) {
      if (mounted) setState(() { _error = e.toString(); _submitting = false; });
    }
  }

  @override
  Widget build(BuildContext context) {
    return MwScaffold(
      child: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? _ErrorBlock(error: _error!, onRetry: _loadExercise)
              : _body(),
    );
  }

  Widget _body() {
    final ex = _exercise!;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Top bar
          Row(
            children: [
              MwIconButton(icon: Icons.home_rounded, tooltip: 'Home', onPressed: () => context.go('/home')),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  ex.nodeTitle.toUpperCase(),
                  maxLines: 1, overflow: TextOverflow.ellipsis,
                  style: AppText.label(size: 11, weight: FontWeight.w700, color: AppColors.muted, letterSpacing: 1.2),
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),

          // Difficulty + XP badge (XP shown as a static design element — wire
          // to backend once the XP feature is integrated)
          Row(
            children: [
              MwDifficultyBadge(level: ex.difficultyLevel),
              const SizedBox(width: 8),
              Text(ex.nodeTitle, style: AppText.body(size: 12, color: AppColors.muted)),
            ],
          ),
          const SizedBox(height: 20),

          // Question card
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(AppRadii.xl),
              border: Border.all(color: AppColors.line),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('SOLVE',
                    style: AppText.label(size: 11, weight: FontWeight.w700, color: AppColors.muted, letterSpacing: 1.2)),
                const SizedBox(height: 8),
                ShaderMask(
                  shaderCallback: (rect) => const LinearGradient(
                    colors: [AppColors.lime, AppColors.cyan],
                  ).createShader(rect),
                  child: Text(
                    ex.questionText,
                    style: AppText.display(size: 26, weight: FontWeight.w800, color: Colors.white, letterSpacing: -0.8)
                        .copyWith(height: 1.15),
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 16),

          // Answer field
          MwField(
            label: 'Your answer',
            controller: _answerController,
            hint: 'Type your answer…',
            keyboardType: const TextInputType.numberWithOptions(decimal: true, signed: true),
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _submit(),
          ),

          const Spacer(),

          MwButton(
            label: _submitting ? 'Checking…' : 'Submit answer ✓',
            onPressed: _submitting ? null : _submit,
            loading: _submitting,
          ),
        ],
      ),
    );
  }
}

class _ErrorBlock extends StatelessWidget {
  final String error;
  final VoidCallback onRetry;
  const _ErrorBlock({required this.error, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(error, textAlign: TextAlign.center, style: AppText.body(color: AppColors.pink)),
            const SizedBox(height: 14),
            MwButton(label: 'Retry', onPressed: onRetry),
          ],
        ),
      ),
    );
  }
}
