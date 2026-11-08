# Local Dictation alpha

An independent free dictation app built on Handy and Dictus, with optional local Qwen3 cleanup.

- Desktop builds: configurable dictation shortcuts, local speech models, vocabulary, clipboard insertion, and a local cleanup model manager.
- Android: offline keyboard dictation, vocabulary, and optional local cleanup under Settings.
- No app account, API key, paid inference, or automatic update service.
- Speech models download during setup. Optional AI cleanup is a separate free 1.83 GB download. Both run offline after setup.

This is an early alpha, not a stable release. Installer builds do not establish microphone, permission, paste, or physical Android compatibility. Review important text; cleanup can make mistakes and uses conservative rejection checks. iPhone is deferred.

Mac and Windows packages are not notarized/commercially signed and may display OS installation warnings. Android builds use a persistent development signing key for alpha updates; install only from this repository. Do not use alpha builds for sensitive password fields. Back up locally retained settings before replacing an existing alpha.

The source and licenses are public. Model files remain separate from Git. See README.md, PRIVACY.md, and THIRD_PARTY_NOTICES.md.
