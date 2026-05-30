import 'package:flutter/foundation.dart';

/// A tiny app-wide "the dashboard's data just became stale" signal.
///
/// The home screen subscribes to this and re-fetches its weakness summary
/// whenever the signal fires. Any action that could change what the
/// dashboard shows — submitting an exercise answer, mastering a topic,
/// etc. — should call [invalidate] right after the underlying API call
/// returns.
///
/// Why this exists: a flutter widget below the top of the navigation
/// stack (e.g. the home screen while the user is on /exercise) doesn't
/// rebuild when the user pops back via the system back gesture — it was
/// kept alive the whole time, so initState never reruns. Without an
/// explicit invalidation signal the screen keeps showing stale data
/// until the user happens to hit a context.go() that re-mounts it.
class DashboardSignal extends ChangeNotifier {
  int _version = 0;

  /// Monotonically increasing counter; listeners don't need to read it,
  /// but it's useful for debugging "did the signal fire?".
  int get version => _version;

  /// Mark the dashboard as stale. All current listeners are notified.
  void invalidate() {
    _version++;
    notifyListeners();
  }
}
