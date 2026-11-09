# Local Dictation 0.1.1 alpha 2

Privacy and reliability fixes for the first alpha. Free local dictation with optional on-device cleanup; no app account, API key, subscription, or hosted inference.

- History-off desktop dictations bypass audio and transcript storage. Pending recordings are deleted on cancellation/failure. Deletion uses SQLite secure deletion.
- The first launch removes unreferenced app-generated desktop recordings and rebuilds the database once to scrub previously deleted transcript bytes. Explicitly retained history and imported files are preserved; existing backups/snapshots are outside this cleanup.
- Android diagnostics keep metrics only. Upgrading removes the first alpha's raw diagnostic file and diagnostic cache exports. Native cleanup never logs message bodies or generated tokens.
- Android cleanup runs in a disposable private worker process, with raw-text fallback on worker error, crash, or deadline. Normal teardown handles partial native allocation safely.
- Android APKs now use a non-debuggable release build, the same persistent signing identity, and a higher version code so settings/models can survive the update.
- Dependencies were refreshed; the compatible GLib line includes the upstream security backport. Build gates check advisories, the backport's source checksum, privacy regressions, and APK signature/native-library alignment. Maintenance-only upstream notices remain explicitly tracked.
- Production desktop webviews now use CSP and recording-directory file access instead of unrestricted file access.

Quit the old desktop app before installing this update. On Android, install the new APK over the previous one. Use only packages from this repository's Releases page. The optional cleanup model remains a separate free 1.83 GB download; models process speech locally after setup.

This is an alpha. Automated checks do not establish microphone, shortcut, paste, or physical Android compatibility on your device. Review important text because local cleanup can make mistakes. Mac/Windows installation warnings may appear because these packages have no paid notarization or publisher certificate. iPhone remains deferred.
