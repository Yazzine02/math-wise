import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../providers/locale_provider.dart';
import '../theme/app_theme.dart';

/// Compact EN / FR language switch. Reads the *effective* locale from
/// [Localizations] so it reflects the device default until the user overrides
/// it, then writes the choice through [LocaleProvider] (persisted).
class LanguageToggle extends StatelessWidget {
  const LanguageToggle({super.key});

  @override
  Widget build(BuildContext context) {
    final current = Localizations.localeOf(context).languageCode;
    final provider = context.read<LocaleProvider>();
    return Container(
      padding: const EdgeInsets.all(3),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadii.pill),
        border: Border.all(color: AppColors.line),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _Segment(
            label: 'EN',
            active: current == 'en',
            onTap: () => provider.setLocale(const Locale('en')),
          ),
          _Segment(
            label: 'FR',
            active: current == 'fr',
            onTap: () => provider.setLocale(const Locale('fr')),
          ),
        ],
      ),
    );
  }
}

class _Segment extends StatelessWidget {
  final String label;
  final bool active;
  final VoidCallback onTap;
  const _Segment({required this.label, required this.active, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 5),
        decoration: BoxDecoration(
          color: active ? AppColors.lime : Colors.transparent,
          borderRadius: BorderRadius.circular(AppRadii.pill),
        ),
        child: Text(
          label,
          style: AppText.label(
            size: 11,
            weight: FontWeight.w800,
            color: active ? AppColors.bgDeep : AppColors.muted,
            letterSpacing: 0.8,
          ),
        ),
      ),
    );
  }
}
