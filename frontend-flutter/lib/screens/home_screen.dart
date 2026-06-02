// lib/screens/home_screen.dart
//
// Dashboard. Matches the layout from the design mockups, with XP / streak /
// level deliberately OMITTED — those will be added when the XP feature is
// wired into the backend. Search for `XP_PLACEHOLDER` to find the spot
// where the level/streak chip should go later.

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../errors/api_exception.dart';
import '../l10n/app_localizations.dart';
import '../l10n/l10n_helpers.dart';
import '../providers/auth_provider.dart';
import '../providers/dashboard_signal.dart';
import '../models/weakness_summary.dart';
import '../services/progress_service.dart';
import '../theme/app_theme.dart';
import '../widgets/app_widgets.dart';
import '../widgets/error_view.dart';
import '../widgets/language_toggle.dart';
import '../widgets/mw_wordmark.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  WeaknessSummary? _summary;
  bool _loading = true;
  ApiException? _error;

  /// Held so we can unregister the listener cleanly in dispose. The
  /// signal fires from anywhere in the app (e.g. ExerciseScreen after a
  /// submit) and triggers a fresh _load — so the dashboard stays
  /// up-to-date even when the home screen was hidden under the
  /// navigation stack the whole time.
  late final DashboardSignal _dashboardSignal;

  @override
  void initState() {
    super.initState();
    _dashboardSignal = context.read<DashboardSignal>();
    _dashboardSignal.addListener(_onDashboardInvalidated);
    _load();
  }

  @override
  void dispose() {
    _dashboardSignal.removeListener(_onDashboardInvalidated);
    super.dispose();
  }

  void _onDashboardInvalidated() {
    if (mounted) _load();
  }

  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final s = await ProgressService.getWeaknesses();
      if (mounted) setState(() { _summary = s; _loading = false; });
    } on UnauthorizedException catch (_) {
      // Token expired / invalid. Bounce to /login via AuthProvider.
      if (mounted) await context.read<AuthProvider>().logout();
    } on ApiException catch (e) {
      if (mounted) setState(() { _error = e; _loading = false; });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final displayName = context.watch<AuthProvider>().displayName;
    return MwScaffold(
      child: RefreshIndicator(
        color: AppColors.lime,
        backgroundColor: AppColors.surface,
        onRefresh: _load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(20, 20, 20, 32),
          children: [
            // ─── Header ───────────────────────────────────────────────
            Row(
              children: [
                const MwWordmark(size: 16),
                const Spacer(),
                const LanguageToggle(),
                const SizedBox(width: 10),
                // XP_PLACEHOLDER — replace with a streak/XP chip once
                // the backend exposes them. For now a plain logout button.
                MwIconButton(
                  icon: Icons.logout_rounded,
                  tooltip: l.logout,
                  onPressed: () => context.read<AuthProvider>().logout(),
                ),
              ],
            ),
            const SizedBox(height: 22),

            // ─── Greeting ─────────────────────────────────────────────
            Text(
              displayName.isEmpty ? l.greetingNoName : l.greeting(displayName),
              style: AppText.display(size: 26, weight: FontWeight.w800, color: AppColors.ink, letterSpacing: -0.8),
            ),
            const SizedBox(height: 4),
            Text(
              l.homeSubtitle,
              style: AppText.body(size: 14, color: AppColors.muted),
            ),

            const SizedBox(height: 20),

            // ─── Primary CTA — adaptive practice ──────────────────────
            _PracticeCard(onTap: () => context.push('/exercise')),

            const SizedBox(height: 10),

            // ─── Secondary CTA — browse courses ──────────────────────
            _BrowseCoursesCard(onTap: () => context.push('/courses')),

            const SizedBox(height: 28),

            // ─── Weak areas ─────────────────────────────────────────
            Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Text(l.weakAreasTitle,
                    style: AppText.title(size: 15, weight: FontWeight.w800, color: AppColors.ink)),
                const Spacer(),
                Text(l.aiDiagnosed,
                    style: AppText.label(size: 10, weight: FontWeight.w700, color: AppColors.lime, letterSpacing: 1.2)),
              ],
            ),
            const SizedBox(height: 12),
            if (_loading)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 24),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_error != null)
              ErrorView(error: _error!, onRetry: _load)
            else if (_summary == null || _summary!.weaknesses.isEmpty)
              const _EmptyWeaknessBlock()
            else
              ..._summary!.weaknesses.asMap().entries.map((entry) => Padding(
                    padding: const EdgeInsets.only(bottom: 10),
                    child: _WeaknessTile(entry: entry.value, index: entry.key),
                  )),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
class _PracticeCard extends StatelessWidget {
  final VoidCallback onTap;
  const _PracticeCard({required this.onTap});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Material(
      color: Colors.transparent,
      borderRadius: BorderRadius.circular(AppRadii.xl),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadii.xl),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppRadii.xl),
            gradient: const LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [AppColors.lime, AppColors.cyan],
            ),
          ),
          child: Stack(
            clipBehavior: Clip.hardEdge,
            children: [
              // Decorative oversized π
              Positioned(
                right: -10, bottom: -30,
                child: Opacity(
                  opacity: 0.18,
                  child: Text(
                    'π',
                    style: AppText.display(size: 120, weight: FontWeight.w800, color: AppColors.bgDeep, letterSpacing: -3),
                  ),
                ),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    l.practiceKicker,
                    style: AppText.label(size: 11, weight: FontWeight.w700, color: AppColors.bgDeep.withValues(alpha: 0.7), letterSpacing: 1.5),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    l.startPractice,
                    style: AppText.display(size: 22, weight: FontWeight.w800, color: AppColors.bgDeep, letterSpacing: -0.5),
                  ),
                  const SizedBox(height: 12),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    decoration: BoxDecoration(
                      color: AppColors.bgDeep,
                      borderRadius: BorderRadius.circular(AppRadii.pill),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.play_arrow_rounded, size: 16, color: AppColors.lime),
                        const SizedBox(width: 4),
                        Text(l.begin, style: AppText.title(size: 13, weight: FontWeight.w800, color: AppColors.lime)),
                      ],
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _BrowseCoursesCard extends StatelessWidget {
  final VoidCallback onTap;
  const _BrowseCoursesCard({required this.onTap});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Material(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(AppRadii.lg),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadii.lg),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppRadii.lg),
            border: Border.all(color: AppColors.line, width: 1.5),
          ),
          child: Row(
            children: [
              Container(
                width: 40, height: 40,
                decoration: BoxDecoration(
                  color: AppColors.bgDeep,
                  borderRadius: BorderRadius.circular(AppRadii.md),
                  border: Border.all(color: AppColors.violet, width: 1.5),
                ),
                alignment: Alignment.center,
                child: const Icon(Icons.menu_book_rounded, size: 18, color: AppColors.violet),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(l.browseCourses,
                        style: AppText.title(size: 14, weight: FontWeight.w700, color: AppColors.ink)),
                    const SizedBox(height: 2),
                    Text(l.browseCoursesSubtitle,
                        style: AppText.body(size: 11, color: AppColors.muted)),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: AppColors.muted, size: 22),
            ],
          ),
        ),
      ),
    );
  }
}

class _WeaknessTile extends StatelessWidget {
  final WeaknessEntry entry;
  final int index;
  const _WeaknessTile({required this.entry, required this.index});

  static const _tones = [AppColors.pink, AppColors.violet, AppColors.cyan];
  static const _glyphs = ['×', '½', '÷'];

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final tone = _tones[index % _tones.length];
    final glyph = _glyphs[index % _glyphs.length];
    return Material(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(AppRadii.lg),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadii.lg),
        onTap: () => context.pushNamed('exercise', queryParameters: {'nodeCode': entry.nodeCode}),
        child: Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppRadii.lg),
            border: Border.all(color: AppColors.line),
          ),
          child: Row(
            children: [
              Container(
                width: 44, height: 44,
                decoration: BoxDecoration(
                  color: tone,
                  borderRadius: BorderRadius.circular(AppRadii.md),
                  boxShadow: [BoxShadow(color: tone.withValues(alpha: 0.6), blurRadius: 18, spreadRadius: -4)],
                ),
                alignment: Alignment.center,
                child: Text(glyph,
                    style: AppText.display(size: 22, weight: FontWeight.w800, color: AppColors.bgDeep, letterSpacing: -0.5)),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(localizedNodeTitle(l, entry.nodeCode, entry.nodeTitle),
                        style: AppText.title(size: 14, weight: FontWeight.w700, color: AppColors.ink),
                        maxLines: 2, overflow: TextOverflow.ellipsis),
                    const SizedBox(height: 2),
                    Text(l.mistakeCount(entry.failureCount),
                        style: AppText.body(size: 11, color: AppColors.muted)),
                  ],
                ),
              ),
              const Icon(Icons.chevron_right, color: AppColors.muted, size: 20),
            ],
          ),
        ),
      ),
    );
  }
}

class _EmptyWeaknessBlock extends StatelessWidget {
  const _EmptyWeaknessBlock();

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppColors.lime.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(AppRadii.lg),
        border: Border.all(color: AppColors.lime.withValues(alpha: 0.4)),
      ),
      child: Row(
        children: [
          const Icon(Icons.auto_awesome_rounded, color: AppColors.lime, size: 26),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              l.noWeaknesses,
              style: AppText.body(size: 13, color: AppColors.inkSoft, height: 1.45),
            ),
          ),
        ],
      ),
    );
  }
}
