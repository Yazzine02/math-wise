import 'package:flutter/material.dart';
import '../errors/api_exception.dart';
import '../theme/app_theme.dart';
import 'app_widgets.dart';

/// Single error-state widget used by every screen.
///
/// Switches presentation based on the [ApiException] subclass:
///   • [NetworkException]             → cyan, "You're offline"
///   • [UnauthorizedException]
///     / [ForbiddenException]         → violet, "Session ended"
///   • [ServerException]
///     / [ServiceUnavailableException]→ pink, "Server problem"
///   • everything else                → pink, "Something went wrong"
///
/// [ValidationException] is intentionally not given its own visual — those
/// errors should be rendered field-by-field on the form, not as a banner.
/// If the caller still hands a ValidationException to ErrorView, we fall
/// back to the generic flavour.
class ErrorView extends StatelessWidget {
  final ApiException error;
  final VoidCallback? onRetry;

  const ErrorView({super.key, required this.error, this.onRetry});

  @override
  Widget build(BuildContext context) {
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
                  flavor.title,
                  style: AppText.title(
                      size: 14, weight: FontWeight.w800, color: flavor.color),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            error.message,
            style: AppText.body(size: 13, color: AppColors.inkSoft, height: 1.45),
          ),
          if (onRetry != null) ...[
            const SizedBox(height: 14),
            MwButton(
              label: 'Try again',
              icon: Icons.refresh_rounded,
              onPressed: onRetry,
              style: MwButtonStyle.ghost,
            ),
          ],
        ],
      ),
    );
  }

  _ErrorFlavor _flavor() {
    if (error is NetworkException) {
      return const _ErrorFlavor(
        icon: Icons.wifi_off_rounded,
        title: "You're offline",
        color: AppColors.cyan,
      );
    }
    if (error is UnauthorizedException || error is ForbiddenException) {
      return const _ErrorFlavor(
        icon: Icons.lock_outline_rounded,
        title: 'Session ended',
        color: AppColors.violet,
      );
    }
    if (error is ServerException || error is ServiceUnavailableException) {
      return const _ErrorFlavor(
        icon: Icons.cloud_off_rounded,
        title: 'Server problem',
        color: AppColors.pink,
      );
    }
    return const _ErrorFlavor(
      icon: Icons.error_outline_rounded,
      title: 'Something went wrong',
      color: AppColors.pink,
    );
  }
}

class _ErrorFlavor {
  final IconData icon;
  final String title;
  final Color color;
  const _ErrorFlavor({
    required this.icon,
    required this.title,
    required this.color,
  });
}
