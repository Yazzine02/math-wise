import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Holds the user's chosen UI language and persists it across launches.
///
/// `locale == null` means "follow the device locale" — the default on a fresh
/// install. Once the user flips the in-app EN/FR toggle, the choice is saved to
/// SharedPreferences and restored on the next launch. Mirrors the
/// ChangeNotifier + SharedPreferences pattern used by [AuthProvider].
class LocaleProvider extends ChangeNotifier {
  static const _prefsKey = 'app_locale';

  Locale? _locale;
  Locale? get locale => _locale;

  LocaleProvider() {
    _load();
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    final code = prefs.getString(_prefsKey);
    if (code != null && code.isNotEmpty) {
      _locale = Locale(code);
      notifyListeners();
    }
  }

  /// Set and persist the language. No-op if unchanged.
  Future<void> setLocale(Locale locale) async {
    if (_locale?.languageCode == locale.languageCode) return;
    _locale = locale;
    notifyListeners();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefsKey, locale.languageCode);
  }
}
