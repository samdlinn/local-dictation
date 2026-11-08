# Local processing

Local Dictation does not require an app account or API key. Speech is transcribed locally. Optional AI cleanup runs in the bundled native runtime with a downloaded model. The desktop cloud inference client rejects requests, including manually edited provider settings.

Network access is used to download speech and cleanup models. Cleanup downloads are pinned and SHA-256 verified. Automatic app update checks are disabled in this alpha; download updates manually from this project's Releases page. There are no intended analytics, remote crash reports, or hosted inference services.

Desktop recording history defaults to zero retained dictations. History can be enabled in settings and remains on the device. Android history is initially disabled. Audio may exist temporarily while processing, and desktop audio-file imports remain in local history until deleted. OS backups may include app data. Logs should not be shared without review.

The optional cleanup model is an editor, not a source of truth. It may make mistakes. Names, numbers, negations, and other important content should be reviewed. Rejected cleanup output falls back to transcription. A finite test set cannot guarantee faithful output for all speech.

This is an alpha. Network inspection and physical-device permission, microphone, and keyboard tests are release checks, not claims established by compilation alone.
