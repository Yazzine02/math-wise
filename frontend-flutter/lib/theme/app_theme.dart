// lib/theme/app_theme.dart
//
// MathExp — design tokens + ThemeData for the "Bold Playful (dark)"
// direction. Centralises every colour, font and radius so screens stay
// consistent. To re-skin the app, edit values in [AppColors] / [AppRadii]
// — no screen file should reference Material defaults directly.

import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

/// Colour tokens. Names match the design system used in the HTML mockups.
class AppColors {
  AppColors._();

  // Surfaces
  static const Color bg       = Color(0xFF0F1226); // app background (navy)
  static const Color bgDeep   = Color(0xFF080A1A); // deepest surface (under cards)
  static const Color surface  = Color(0xFF1A1E38); // card background
  static const Color surfaceHi = Color(0xFF252A48); // raised / pressed
  static const Color line     = Color(0x14FFFFFF); // 8% white — hairline borders

  // Text
  static const Color ink      = Color(0xFFFFFFFF); // primary
  static const Color inkSoft  = Color(0xFFD7DAF0); // secondary
  static const Color muted    = Color(0xFF8B8FA8); // tertiary / labels

  // Accents
  static const Color lime     = Color(0xFFC8FF4A); // primary CTA / success
  static const Color limeDeep = Color(0xFF9DD126);
  static const Color pink     = Color(0xFFFF4A8B); // danger / wrong / streak
  static const Color violet   = Color(0xFF8B5CF6); // secondary accent
  static const Color cyan     = Color(0xFF4ADEFF); // tertiary accent

  // Difficulty palette (matches PlDiffBadge in the design mockups)
  static const Color diff1 = lime;
  static const Color diff2 = lime;
  static const Color diff3 = Color(0xFFFFB84A);
  static const Color diff4 = Color(0xFFFF8A4A);
  static const Color diff5 = pink;

  static Color diffColor(int level) {
    switch (level) {
      case 1: return diff1;
      case 2: return diff2;
      case 3: return diff3;
      case 4: return diff4;
      case 5: return diff5;
      default: return muted;
    }
  }
}

class AppRadii {
  AppRadii._();
  static const double sm = 10;
  static const double md = 14;
  static const double lg = 18;
  static const double xl = 22;
  static const double pill = 999;
}

class AppGaps {
  AppGaps._();
  static const double xs = 4;
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 18;
  static const double xl = 24;
  static const double xxl = 32;
}

/// Typography. Sora for display, Space Grotesk for body, JetBrains Mono for
/// numerics / code blocks. All wired through google_fonts so no asset bundle
/// is required.
class AppText {
  AppText._();

  static TextStyle display({double size = 28, FontWeight weight = FontWeight.w800, Color color = AppColors.ink, double letterSpacing = -0.8}) =>
      GoogleFonts.sora(fontSize: size, fontWeight: weight, color: color, letterSpacing: letterSpacing, height: 1.05);

  static TextStyle title({double size = 16, FontWeight weight = FontWeight.w800, Color color = AppColors.ink}) =>
      GoogleFonts.sora(fontSize: size, fontWeight: weight, color: color, letterSpacing: -0.3);

  static TextStyle body({double size = 14, FontWeight weight = FontWeight.w400, Color color = AppColors.inkSoft, double height = 1.5}) =>
      GoogleFonts.spaceGrotesk(fontSize: size, fontWeight: weight, color: color, height: height);

  static TextStyle label({double size = 11, FontWeight weight = FontWeight.w700, Color color = AppColors.muted, double letterSpacing = 1.2}) =>
      GoogleFonts.spaceGrotesk(fontSize: size, fontWeight: weight, color: color, letterSpacing: letterSpacing);

  static TextStyle mono({double size = 13, FontWeight weight = FontWeight.w500, Color color = AppColors.inkSoft, double height = 1.55}) =>
      GoogleFonts.jetBrainsMono(fontSize: size, fontWeight: weight, color: color, height: height);
}

/// The Material ThemeData consumed by MaterialApp.theme. Most screens use the
/// helpers in [AppText] / [AppColors] directly, but having this set means the
/// few Material widgets we *do* use (Scaffold, AppBar, dialogs, snackbars,
/// progress indicators) inherit the right look without per-screen overrides.
ThemeData buildAppTheme() {
  final base = ThemeData.dark(useMaterial3: true);
  return base.copyWith(
    scaffoldBackgroundColor: AppColors.bg,
    colorScheme: const ColorScheme.dark(
      brightness: Brightness.dark,
      primary: AppColors.lime,
      onPrimary: AppColors.bgDeep,
      secondary: AppColors.violet,
      onSecondary: AppColors.ink,
      tertiary: AppColors.cyan,
      surface: AppColors.surface,
      onSurface: AppColors.ink,
      error: AppColors.pink,
      onError: AppColors.bgDeep,
    ),
    textTheme: GoogleFonts.spaceGroteskTextTheme(base.textTheme).apply(
      bodyColor: AppColors.inkSoft,
      displayColor: AppColors.ink,
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: AppColors.bg,
      foregroundColor: AppColors.ink,
      elevation: 0,
      centerTitle: false,
    ),
    scrollbarTheme: ScrollbarThemeData(
      thumbColor: WidgetStateProperty.all(AppColors.line),
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(
      color: AppColors.lime,
    ),
    snackBarTheme: const SnackBarThemeData(
      backgroundColor: AppColors.surface,
      contentTextStyle: TextStyle(color: AppColors.ink),
      behavior: SnackBarBehavior.floating,
    ),
  );
}
