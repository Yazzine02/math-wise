// lib/widgets/mw_wordmark.dart
//
// "Math Wise" wordmark — lime ∑ sigil + Sora display text. Used in headers.

import 'package:flutter/material.dart';
import '../theme/app_theme.dart';

class MwWordmark extends StatelessWidget {
  final double size;
  const MwWordmark({super.key, this.size = 18});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: size + 8, height: size + 8,
          decoration: BoxDecoration(
            color: AppColors.lime,
            borderRadius: BorderRadius.circular(8),
          ),
          alignment: Alignment.center,
          child: Text(
            '∑',
            style: AppText.display(size: size - 2, weight: FontWeight.w800, color: AppColors.bgDeep, letterSpacing: -0.5),
          ),
        ),
        const SizedBox(width: 8),
        Text(
          'Math Wise',
          style: AppText.display(size: size, weight: FontWeight.w800, color: AppColors.ink, letterSpacing: -0.6),
        ),
      ],
    );
  }
}
