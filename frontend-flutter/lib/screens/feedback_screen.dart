// lib/screens/feedback_screen.dart
//
// Result screen — correct or incorrect. Big banner, weakness chip and AI
// explanation when wrong; a celebratory block when right. The AI explanation
// text is server-supplied (kept in the language the model produced); the
// chrome and the weakness label are localized.

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../l10n/app_localizations.dart';
import '../l10n/l10n_helpers.dart';
import '../models/ai_feedback.dart';
import '../theme/app_theme.dart';
import '../widgets/app_widgets.dart';

class FeedbackScreen extends StatelessWidget {
  final AiFeedback feedback;
  const FeedbackScreen({super.key, required this.feedback});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final isCorrect = feedback.isCorrect;
    return MwScaffold(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                MwIconButton(icon: Icons.arrow_back_rounded, onPressed: () => context.pop()),
                const SizedBox(width: 10),
                Text(
                  l.result,
                  style: AppText.label(size: 11, weight: FontWeight.w700, color: AppColors.muted, letterSpacing: 1.2),
                ),
              ],
            ),
            const SizedBox(height: 18),

            if (isCorrect) const _CorrectBlock() else _WrongBlock(feedback: feedback),

            const Spacer(),
            MwButton(
              label: l.nextExercise,
              onPressed: () => context.pop(true),
            ),
            const SizedBox(height: 10),
            MwButton(
              label: l.backToDashboard,
              style: MwButtonStyle.ghost,
              onPressed: () => context.go('/home'),
            ),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
class _CorrectBlock extends StatelessWidget {
  const _CorrectBlock();

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Expanded(
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 96, height: 96,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: AppColors.lime.withValues(alpha: 0.18),
                border: Border.all(color: AppColors.lime, width: 2),
                boxShadow: [BoxShadow(color: AppColors.lime.withValues(alpha: 0.4), blurRadius: 30, spreadRadius: -4)],
              ),
              child: const Icon(Icons.check_rounded, color: AppColors.lime, size: 56),
            ),
            const SizedBox(height: 18),
            Text(
              l.correctKicker,
              style: AppText.label(size: 12, weight: FontWeight.w800, color: AppColors.lime, letterSpacing: 2),
            ),
            const SizedBox(height: 8),
            Text(
              l.correctTitle,
              style: AppText.display(size: 36, weight: FontWeight.w800, color: AppColors.ink, letterSpacing: -1.2),
            ),
            const SizedBox(height: 6),
            Text(
              l.correctSubtitle,
              style: AppText.body(size: 14, color: AppColors.muted),
            ),
          ],
        ),
      ),
    );
  }
}

class _WrongBlock extends StatelessWidget {
  final AiFeedback feedback;
  const _WrongBlock({required this.feedback});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Expanded(
      child: ListView(
        padding: EdgeInsets.zero,
        children: [
          // Banner
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: AppColors.pink.withValues(alpha: 0.10),
              borderRadius: BorderRadius.circular(AppRadii.lg),
              border: Border.all(color: AppColors.pink, width: 1.5),
            ),
            child: Row(
              children: [
                Container(
                  width: 44, height: 44,
                  decoration: const BoxDecoration(shape: BoxShape.circle, color: AppColors.pink),
                  child: const Icon(Icons.close_rounded, color: AppColors.bgDeep, size: 26),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(l.wrongTitle,
                          style: AppText.title(size: 18, weight: FontWeight.w800, color: AppColors.pink)),
                      const SizedBox(height: 2),
                      Text(l.wrongSubtitle,
                          style: AppText.body(size: 12, color: AppColors.pink.withValues(alpha: 0.85))),
                    ],
                  ),
                ),
              ],
            ),
          ),

          if (feedback.weaknessNode.isNotEmpty) ...[
            const SizedBox(height: 22),
            Text(
              l.weaknessLogged,
              style: AppText.label(size: 11, weight: FontWeight.w700, color: AppColors.muted, letterSpacing: 1.2),
            ),
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.centerLeft,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(AppRadii.pill),
                  gradient: const LinearGradient(colors: [AppColors.pink, AppColors.violet]),
                ),
                child: Text(
                  '◆  ${localizedNodeTitle(l, feedback.weaknessNode, feedback.weaknessNode)}',
                  style: AppText.title(size: 13, weight: FontWeight.w800, color: AppColors.ink),
                ),
              ),
            ),
          ],

          if (feedback.explanation.isNotEmpty) ...[
            const SizedBox(height: 22),
            Text(l.explanationHeader,
                style: AppText.title(size: 14, weight: FontWeight.w800, color: AppColors.ink)),
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(AppRadii.md),
                border: Border.all(color: AppColors.line),
              ),
              child: Text(feedback.explanation,
                  style: AppText.body(size: 13, color: AppColors.inkSoft, height: 1.6)),
            ),
          ],
        ],
      ),
    );
  }
}
