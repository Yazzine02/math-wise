// lib/widgets/app_widgets.dart
//
// Shared widgets for every screen. Imported as a single file to keep imports
// tidy: `import '../widgets/app_widgets.dart';`
//
// Contains:
//   • MwScaffold          — full-bleed background, optional ambient glows
//   • MwButton            — primary / ghost CTA (full width, pill)
//   • MwField             — labelled text field with focused outline glow
//   • MwIconButton        — square 38×38 button used in screen headers
//   • MwDifficultyBadge   — Lvl 1-5 pill (lime → pink)
//   • MwPlayfulBlobs      — decorative violet + pink radial glows (optional)

import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../theme/app_theme.dart';

// ─────────────────────────────────────────────────────────────────────────
// Scaffold — every screen wraps content in MwScaffold so the bg + glows
// stay consistent without copying the same Stack into every file.
// ─────────────────────────────────────────────────────────────────────────
class MwScaffold extends StatelessWidget {
  final Widget child;
  final bool glows;
  final bool safeArea;

  const MwScaffold({super.key, required this.child, this.glows = true, this.safeArea = true});

  @override
  Widget build(BuildContext context) {
    final inner = safeArea ? SafeArea(child: child) : child;
    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: const SystemUiOverlayStyle(
        statusBarColor: Colors.transparent,
        statusBarIconBrightness: Brightness.light,
      ),
      child: Scaffold(
        body: Stack(
          children: [
            if (glows) const MwPlayfulBlobs(),
            inner,
          ],
        ),
      ),
    );
  }
}

class MwPlayfulBlobs extends StatelessWidget {
  const MwPlayfulBlobs({super.key});

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Stack(
        children: [
          Positioned(
            top: -60, left: -80, width: 260, height: 260,
            child: _Blob(color: AppColors.violet.withValues(alpha: 0.35), sigma: 70),
          ),
          Positioned(
            bottom: -100, right: -80, width: 260, height: 260,
            child: _Blob(color: AppColors.pink.withValues(alpha: 0.20), sigma: 80),
          ),
        ],
      ),
    );
  }
}

class _Blob extends StatelessWidget {
  final Color color;
  final double sigma;
  const _Blob({required this.color, required this.sigma});

  @override
  Widget build(BuildContext context) {
    return ImageFiltered(
      imageFilter: ImageFilter.blur(sigmaX: sigma, sigmaY: sigma),
      child: Container(decoration: BoxDecoration(shape: BoxShape.circle, color: color)),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// Button — pill, full width by default. Two flavours: primary + ghost.
// ─────────────────────────────────────────────────────────────────────────
enum MwButtonStyle { primary, ghost }

class MwButton extends StatelessWidget {
  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;
  final MwButtonStyle style;
  final bool loading;
  final Color? color;

  const MwButton({
    super.key,
    required this.label,
    this.icon,
    this.onPressed,
    this.style = MwButtonStyle.primary,
    this.loading = false,
    this.color,
  });

  @override
  Widget build(BuildContext context) {
    final isPrimary = style == MwButtonStyle.primary;
    final fill = color ?? AppColors.lime;
    return SizedBox(
      width: double.infinity,
      height: 54,
      child: Material(
        color: isPrimary ? fill : Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadii.pill),
          side: isPrimary ? BorderSide.none : const BorderSide(color: AppColors.line, width: 1.5),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(AppRadii.pill),
          onTap: loading ? null : onPressed,
          child: Center(
            child: loading
                ? const SizedBox(
                    height: 22, width: 22,
                    child: CircularProgressIndicator(strokeWidth: 2.4, color: AppColors.bgDeep),
                  )
                : Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      if (icon != null) ...[
                        Icon(icon, size: 18, color: isPrimary ? AppColors.bgDeep : AppColors.ink),
                        const SizedBox(width: 8),
                      ],
                      Text(
                        label,
                        style: AppText.title(
                          size: 15,
                          weight: FontWeight.w800,
                          color: isPrimary ? AppColors.bgDeep : AppColors.ink,
                        ),
                      ),
                    ],
                  ),
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// Field — labelled text input. Outlined when unfocused, lime-glow when
// focused. Tracks focus internally so the caller doesn't need a FocusNode.
// ─────────────────────────────────────────────────────────────────────────
class MwField extends StatefulWidget {
  final String label;
  final TextEditingController? controller;
  final bool obscure;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final TextCapitalization textCapitalization;
  final void Function(String)? onSubmitted;
  final String? hint;

  const MwField({
    super.key,
    required this.label,
    this.controller,
    this.obscure = false,
    this.keyboardType,
    this.textInputAction,
    this.textCapitalization = TextCapitalization.none,
    this.onSubmitted,
    this.hint,
  });

  @override
  State<MwField> createState() => _MwFieldState();
}

class _MwFieldState extends State<MwField> {
  final FocusNode _node = FocusNode();
  bool _focused = false;

  @override
  void initState() {
    super.initState();
    _node.addListener(() => setState(() => _focused = _node.hasFocus));
  }

  @override
  void dispose() {
    _node.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(widget.label.toUpperCase(), style: AppText.label(letterSpacing: 1.2)),
        const SizedBox(height: 6),
        AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          decoration: BoxDecoration(
            color: _focused ? AppColors.lime.withValues(alpha: 0.06) : AppColors.surface,
            borderRadius: BorderRadius.circular(AppRadii.md),
            border: Border.all(
              color: _focused ? AppColors.lime : AppColors.line,
              width: 1.5,
            ),
            boxShadow: _focused
                ? [BoxShadow(color: AppColors.lime.withValues(alpha: 0.18), blurRadius: 0, spreadRadius: 4)]
                : null,
          ),
          child: TextField(
            controller: widget.controller,
            focusNode: _node,
            obscureText: widget.obscure,
            keyboardType: widget.keyboardType,
            textInputAction: widget.textInputAction,
            textCapitalization: widget.textCapitalization,
            onSubmitted: widget.onSubmitted,
            style: AppText.body(size: 15, weight: FontWeight.w500, color: AppColors.ink, height: 1.3),
            cursorColor: AppColors.lime,
            decoration: InputDecoration(
              hintText: widget.hint,
              hintStyle: AppText.body(size: 15, color: AppColors.muted),
              border: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            ),
          ),
        ),
      ],
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// Icon button — 38×38 square, used for back / home / logout in screen
// headers. Tap target stays 48×48 to meet accessibility minimums.
// ─────────────────────────────────────────────────────────────────────────
class MwIconButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback? onPressed;
  final String? tooltip;

  const MwIconButton({super.key, required this.icon, this.onPressed, this.tooltip});

  @override
  Widget build(BuildContext context) {
    final btn = SizedBox(
      width: 38, height: 38,
      child: Material(
        color: AppColors.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadii.md),
          side: const BorderSide(color: AppColors.line, width: 1),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(AppRadii.md),
          onTap: onPressed,
          child: Icon(icon, size: 18, color: AppColors.ink),
        ),
      ),
    );
    return tooltip == null ? btn : Tooltip(message: tooltip!, child: btn);
  }
}

// ─────────────────────────────────────────────────────────────────────────
// Difficulty badge — colour-coded pill (Lvl 1-5).
// ─────────────────────────────────────────────────────────────────────────
class MwDifficultyBadge extends StatelessWidget {
  final int level;
  const MwDifficultyBadge({super.key, required this.level});

  @override
  Widget build(BuildContext context) {
    final color = AppColors.diffColor(level);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(AppRadii.pill),
        border: Border.all(color: color, width: 1.5),
      ),
      child: Text(
        'Lvl $level',
        style: AppText.title(size: 11, weight: FontWeight.w800, color: color),
      ),
    );
  }
}

// Small "min read" pill used by course / lesson screens.
class MwReadTime extends StatelessWidget {
  final int minutes;
  const MwReadTime({super.key, required this.minutes});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(Icons.schedule, size: 12, color: AppColors.muted),
        const SizedBox(width: 4),
        Text('~$minutes min read', style: AppText.body(size: 11, weight: FontWeight.w600, color: AppColors.muted)),
      ],
    );
  }
}
