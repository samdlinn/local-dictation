# Third party notices

Local Dictation is an independent open-source project. It is not affiliated with Wispr Flow, Handy, Dictus, OpenWispr, or their authors.

The desktop foundation is [Handy](https://github.com/cjpais/Handy), Copyright 2025 CJ Pais, MIT licensed. The original MIT license is retained in LICENSE. Initial revision: `73cc8579cff74e97e08de9d86035a92e4335ff7f`.

The Android foundation is [Dictus Android](https://github.com/getdictus/dictus-android), Copyright 2026 PIVI Solutions, MIT licensed. Its license is retained in android/LICENSE. Initial revision: `2a8bfc11ca2a85f07ce8533652cd0f739c07fd0b`.

Local inference uses whisper.cpp / transcribe-cpp and llama.cpp. Their license notices are retained with their source or build dependencies. llama.cpp is pinned to `7d7f0287530bc8981690f361d8c1627dbae1c99a` for the desktop cleanup runtime.

The optional cleanup model is Qwen3 1.7B Q8_0, distributed by Qwen under Apache 2.0. The model is downloaded from a pinned revision, verified with SHA-256, and never committed to Git. Its license is bundled under licenses/QWEN-LICENSE.txt.

The microphone artwork in app-icon.svg was created for this project. Original upstream product names, icons, and mascots are not our branding. Retained upstream references identify provenance and contributors.

Additional dependency licenses are listed in package manifests and lockfiles. The Android app includes a generated open-source license screen.

Android local cleanup reuses the JNI inference module from OpenWispr revision `0bf53cad34dfbbf36685a83420cb1b9580ae92b4` (MIT; licenses/OPENWISPR-LICENSE.txt), including its vendored llama.cpp and notices. The JNI wrapper derives from Arm AI Chat; any original notices in the imported source are retained.
