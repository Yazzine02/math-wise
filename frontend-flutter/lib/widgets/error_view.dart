import 'package:flutter/material.dart';
import '../errors/api_exception.dart';
import '../l10n/app_localizations.dart';
import '../l10n/l10n_helpers.dart';
import '../theme/app_theme.dart';
import 'app_widgets.dart';

/// Single error-state widget used by every screen.
///
/// Switches presentation based on the [ApiException] subclass:
///   • [NetworkException]             → cyan, "offline"
///   • [UnauthorizedException]
///     / [ForbiddenException]         → violet, "session ended"
///   • [ServerException]
///     / [ServiceUnavailableException]→ pink, "server problem"
///   • everything else                → pink, "something went wrong"
///
/// The heading is localized by kind; the body is localized from the error
/// envelope's `code` via [localizedErrorMessage] (falling back to the
/// server message). [ValidationException] is intentionally not given its own
/// visual — those are rendered field-by-field on the form.
class ErrorView extends StatelessWidget {
  final ApiException error;
  final VoidCallback? onRetry;

  const ErrorView({super.key, required this.error, this.onRetry});

  @override
  Widget build(BuildContext context) {
    final l = AppLocalizations.of(context)!;
    final flavor = _flavor();
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: flavor.color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(AppRadii.lg),
        border: Border.all(color: flavor.color.withValues(alpha: 0.4)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(flavor.icon, color: flavor.color, size: 22),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  _title(l, flavor.kind),
                  style: AppText.title(
                      size: 14, weight: FontWeight.w800, color: flavor.color),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            localizedErrorMessage(l, error),
            style: AppText.body(size: 13, color: AppColors.inkSoft, height: 1.45),
          ),
          if (onRetry != null) ...[
            const SizedBox(height: 14),
            MwButton(
              label: l.tryAgain,
              icon: Icons.refresh_rounded,
              onPressed: onRetry,
              style: MwButtonStyle.ghost,
            ),
          ],
        ],
      ),
    );
  }

  String _title(AppLocalizations l, _Kind kind) {
    switch (kind) {
      case _Kind.offline:
        return l.errorOfflineTitle;
      case _Kind.session:
        return l.errorSessionTitle;
      case _Kind.server:
        return l.errorServerTitle;
      case _Kind.generic:
        return l.errorGenericTitle;
    }
  }

  _ErrorFlavor _flavor() {
    if (error is NetworkException) {
      return const _ErrorFlavor(
          icon: Icons.wifi_off_rounded, color: AppColors.cyan, kind: _Kind.offline);
    }
    if (error is UnauthorizedException || error is ForbiddenException) {
      return const _ErrorFlavor(
          icon: Icons.lock_outline_rounded, color: AppColors.violet, kind: _Kind.session);
    }
    if (error is ServerException || error is ServiceUnavailableException) {
      return const _ErrorFlavor(
          icon: Icons.cloud_off_rounded, color: AppColors.pink, kind: _Kind.server);
    }
    return const _ErrorFlavor(
        icon: Icons.error_outline_rounded, color: AppColors.pink, kind: _Kind.generic);
  }
}

enum _Kind { offline, session, server, generic }

class _ErrorFlavor {
  final IconData icon;
  final Color color;
  final _Kind kind;
  const _ErrorFlavor({required this.icon, required this.color, required this.kind});
}
