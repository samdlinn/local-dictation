# Local Dictation Product Plan

Build one free dictation product for macOS, Windows, Linux, Android, and, where free installation is practical, iPhone. Press a shortcut or microphone button, speak naturally, and insert faithful, cleaned text into the app you are using. Speech recognition, cleanup, settings, and vocabulary run on the device.

The first release prioritizes the available M5 MacBook Air with 16 GB, OnePlus 15, and Windows 10 desktop. Linux remains a supported release target. iPhone is an optional track: attempt a free installation and useful local functionality, and defer it if Apple restrictions prevent that. Required spending is zero. Working name: Local Dictation; choose final branding before distribution.

# Product decisions

- No app account, subscription, usage quota, API key, hosted inference, or required purchase.
- After setup, dictation and cleanup work with the network disconnected.
- Package the inference engines inside the app. Users do not install Python, Ollama, LM Studio, Docker, or a separate model server.
- Recommend and download models automatically, with simple Fast and Accurate choices available later.
- Preserve the speaker's meaning, names, numbers, negations, and tone. Cleanup is conservative editing.
- Keep preferences, vocabulary, and optional history on each device. Export and import settings as a file; account-based synchronization is outside the first release.
- Use the existing hardware. No GPU purchase is required.
- Ship the product and its core features free and open source. Use original branding and retain dependency notices.
- Start with English dictation. Keep the engine interface multilingual, but validate each additional language before advertising support.

# Existing foundations and local processing

| Foundation | Role in our product | Local capability and remaining work |
| --- | --- | --- |
| Handy | Desktop app foundation for macOS, Windows, and Linux | Already supports local speech models, shortcuts, settings, and insertion. Its optional cleanup can use either local or cloud models. Integrate an embedded local cleanup engine so our users need no provider setup. |
| Dictus Android | Android app and keyboard foundation | Local Whisper and Parakeet transcription already exist. Add and validate our local cleanup behavior and settings. |
| Dictus iOS | Optional iPhone app and keyboard foundation | Local transcription exists. Advanced upstream modes use Apple Intelligence on supported newer iPhones. Our older-device path must not depend on Apple Intelligence. |
| OpenWispr | Reference for cleanup and Android insertion | Demonstrates local speech recognition, deterministic corrections, and optional llama.cpp cleanup on Mac and Android. Evaluate reusable components individually. |

These are reuse candidates, not dependencies on paid upstream subscriptions. Dictus Desktop is already a fork of Handy, so use Handy upstream as the desktop starting point rather than maintain both forks.

[Handy source](https://github.com/cjpais/Handy), [Handy cleanup configuration and limitations](https://handy.computer/docs/post-processing), [Dictus Android](https://github.com/getdictus/dictus-android), [Dictus iOS](https://github.com/getdictus/dictus-ios), [OpenWispr](https://github.com/RohitAg13/openWispr).

# User experience

First launch has four steps: explain local processing, request microphone and insertion permissions, recommend a model with its download size, and offer a short dictation test. Downloads show progress, resume after interruptions, and can be retried. Provide offline model import for installations without internet.

Desktop users hold or toggle a configurable shortcut. On Apple keyboards, support Fn where available; offer a standard alternative shortcut everywhere. Show an obvious recording indicator and a cancel action. Insert text without submitting the message or executing a command. Preserve the clipboard and provide a recoverable copy if insertion fails.

Android users enable our keyboard and tap its microphone. Treat an overlay that works alongside another keyboard as a later convenience, because it requires additional permissions and testing. On iPhone, evaluate the companion app and keyboard together; provide record then copy as a simpler fallback if free signing permits only a standalone app.

Keep the default interface small: microphone, language, Fast or Accurate, cleanup toggle, personal dictionary, and settings. Put engine names and advanced tuning out of the normal dictation flow. Let users undo the last insertion where the receiving app supports it.

# Processing design

The local pipeline is:

Capture speech → detect silence → transcribe → apply vocabulary and conservative text normalization → optionally clean up with a local language model → check the result → insert text.

Use whisper.cpp as the initial portable speech engine. Evaluate Parakeet on platforms where it improves latency without complicating packaging. Use llama.cpp for an embedded cleanup model. Apple acceleration can improve performance, but Apple Intelligence is not a requirement for the product.

Use three app shells: Tauri with Rust and a TypeScript interface for desktop, Kotlin for Android, and Swift for optional iPhone support. Reuse existing platform code. Share a versioned settings schema, model catalog, cleanup rules and prompts, and conformance fixtures. Extract a shared native library only where it reduces maintenance; do not make a large cross-platform rewrite a prerequisite.

Speech and cleanup jobs run off the UI thread. Set memory and time budgets, cancel cleanly, and unload models after inactivity. On constrained devices, release the speech model before loading cleanup. Store model files once per app and verify downloads against pinned checksums.

# Model selection

| Purpose | Initial candidates | Selection rule |
| --- | --- | --- |
| Lightweight speech recognition | Whisper base or small, with English variants where appropriate | Use the smallest model that meets the accuracy target on the device. |
| Higher accuracy speech recognition | Quantized larger Whisper variants; Parakeet where supported | Offer only after measuring memory, startup, and end-to-end latency. |
| Local AI cleanup | Quantized Qwen3 0.6B and 1.7B as lightweight candidates; a larger candidate for capable desktops | Treat these as benchmark candidates. A model must preserve meaning and beat simple normalization before becoming a default. |

The Qwen3 candidates have Apache 2.0 model licenses. Whisper.cpp and llama.cpp provide local inference, so no model API bill is needed. Retain the applicable code and model notices in every package. The OpenWispr cleanup fine-tune is a research candidate only until its redistribution terms are established.

Model size alone does not establish cleanup quality. Handy documents unreliable instruction following with very small models. If no lightweight candidate meets the quality bar on a device, ship transcription with basic cleanup and clearly label AI cleanup unavailable there; never silently use the cloud.

[Whisper.cpp](https://github.com/ggml-org/whisper.cpp), [llama.cpp](https://github.com/ggml-org/llama.cpp), [Qwen3 0.6B](https://huggingface.co/Qwen/Qwen3-0.6B), [Qwen3 1.7B](https://huggingface.co/Qwen/Qwen3-1.7B), [Handy model limitations](https://handy.computer/docs/post-processing#limitations).

# Hardware and operating system targets

These are proposed engineering targets for our product, not verified minimum requirements or speed guarantees. Available memory, processor instructions, model format, audio length, and competing apps all affect performance.

| Device or platform | Initial target | Expected default |
| --- | --- | --- |
| M5 MacBook Air with 16 GB | Primary development and performance device | Local speech recognition and AI cleanup; choose models from measured results. |
| Other Macs | macOS 14 or later, Apple Silicon or Intel, 8 GB total RAM target; 16 GB preferred | Smaller models on limited machines; use Apple acceleration where supported. |
| Windows desktop | Windows 10 22H2 or Windows 11, initially x64, 8 GB RAM target; 16 GB preferred | CPU-capable path with optional GPU acceleration. Verify CPU instruction requirements before claiming compatibility. |
| Linux desktop | Initially Ubuntu 24.04 x64, 8 GB RAM target; 16 GB preferred | Test X11 plus GNOME and KDE Wayland explicitly. Expand the distribution list after validation. |
| OnePlus 15 | Primary Android test device | Local transcription and a lightweight cleanup candidate. Measure heat, battery use, and startup. |
| Other Android phones | Android 10 or later, initially arm64; 4 GB total RAM target for lightweight transcription, 6 to 8 GB preferred for AI cleanup | Automatic low-memory configuration, with AI cleanup conditional on testing. |
| Older iPhone | Initially iOS 17 or later, subject to actual model and free installation feasibility | Lightweight speech recognition first; local cleanup only if memory, latency, and installation allow it. |

Budget roughly 2 to 5 GB of free storage for the app and an initial speech-plus-cleanup model set. A transcription-only configuration should be smaller. Show the actual selected download size before downloading, and allow deleting optional models.

For scale, whisper.cpp documents a base model at 142 MiB on disk and approximately 388 MB model memory, and a small model at 466 MiB and approximately 852 MB model memory. Those figures exclude the rest of the app, cleanup, and OS overhead. They are not phone RAM requirements. [Whisper.cpp memory estimates](https://github.com/ggml-org/whisper.cpp#memory-usage).

# Faithful cleanup and failure behavior

The initial cleanup mode fixes punctuation, capitalization, obvious fillers, repetition, and explicit spoken corrections. It does not summarize, embellish, answer the dictated text, or apply instructions contained inside that text.

Example: “Send it Thursday, actually Friday morning” should become “Send it Friday morning.” A sentence such as “Do not send it Friday” must preserve the negation.

Use conservative checks for unexpected additions, large deletions, altered names or numbers, and malformed output. These checks reduce risk but cannot prove semantic equivalence. Keep the raw transcript available during the current session and provide a recoverable fallback when cleanup fails or times out. Offer Raw and Clean output, with a one-step way to recover the original. Dictation must remain usable when cleanup is disabled.

# Privacy and local settings

Do not send audio, transcripts, vocabulary, or diagnostics to a service. Disable analytics and remote crash reporting in the product. Make model downloads and optional update checks the only routine network paths, and document them in settings.

Record only during a visible user-initiated session. Stop capture on cancel, timeout, interruption, or app shutdown. Default to deleting temporary audio after processing; retain history only when the user enables it. Provide clear deletion controls for history, vocabulary, and downloaded models.

Store user data inside the platform's application storage with normal OS access controls. Explain that device backups may include that storage. Export settings only on explicit action; exclude audio and history unless explicitly selected. Diagnostics must be manually exportable and omit dictated content by default.

# Cost and installation

| Item | Required spending |
| --- | --- |
| App source, inference libraries, and selected downloadable models | $0 |
| Local dictation and cleanup | $0 in usage fees |
| Model training or paid datasets | Not required for the first release |
| Personal distribution | $0 using local files or available free release hosting |
| Desktop and Android installation | Free installer packages; no required store listing |
| iPhone distribution | Optional only; do not buy a developer membership for this plan |

The product uses existing hardware, electricity, storage, and download bandwidth. It has no required software or service payment. If free hosting reaches a limit, support direct file transfer and offline import rather than introduce a paid dependency.

Build a Mac app bundle, Windows installer, Linux packages, and Android APK. Without paid platform certificates, Mac or Windows may show installation warnings; do not promise a warning-free install under the zero-spend constraint. Consider free open-source signing programs where available, without making them a dependency.

For iPhone, try a free Xcode installation first. Apple says Personal Team provisioning expires after seven days and supports fewer capabilities than a paid membership. Verify the keyboard's required capabilities early. A standalone record-and-copy app may be more feasible than the full keyboard. If neither route is acceptable without payment, defer iPhone and continue the other platforms. Normal Apple or Google device/store accounts are separate from our app, which requires no account.

[Apple free developer account limitations](https://developer.apple.com/help/account/basics/about-your-developer-account).

# Delivery phases

| Phase | Work | Completion evidence |
| --- | --- | --- |
| 1 — Baseline and early feasibility | Build the chosen desktop and Android foundations on the Mac; establish a small real-speech test set; try free iPhone signing and confirm required capabilities. | Mac and OnePlus perform local transcription in airplane mode; iPhone has a documented free route or is explicitly deferred. |
| 2 — Local cleanup | Embed the cleanup runtime; compare lightweight models and simple normalization; implement budgets and recovery. | The same test phrases produce faithful output, and cleanup failure preserves usable transcription. |
| 3 — Unified product | Apply original branding, shared settings, dictionary behavior, onboarding, model management, and privacy defaults. | A new user installs without a terminal or provider key and completes a successful dictation. |
| 4 — Desktop portability | Produce and exercise Windows 10 and Linux installers; test shortcuts, paste, CPU fallback, and Wayland behavior. | Clean installations work on the declared support matrix; gaps are documented before release. |
| 5 — Optional iPhone | Implement the supported free installation route, local models, interruption handling, and keyboard or copy workflow. | A useful offline workflow runs on the actual iPhone without a required purchase; otherwise defer. |
| 6 — Release and maintenance | Package notices, repeat install and offline checks, document manual updates and recovery, and publish only when requested. | Reproducible packages, known limitations, and a clear support matrix. |

Start with a new codebase under original branding while preserving upstream history and notices. Pin the upstream revisions used for the initial builds. Bring in fixes from upstream in small reviewed updates. Build one release target at a time, but keep the shared behavior tests common to all platforms.

# Validation and release criteria

Use a consented, locally stored set of at least 50 short dictations covering normal messages, names, dates, numbers, negation, fillers, self-corrections, accented speech, background noise, silence, and technical terms. Include at least 20 phrases where changing a number, name, correction, or negation would materially change meaning. Score transcription errors separately from cleanup changes.

A release must meet these checks:

- No invented speech from silent test recordings and no material meaning changes in the curated cleanup test set. This is a finite release check, not a guarantee for arbitrary speech.
- End-to-end offline operation after model setup; no audio or text leaves the device during network inspection.
- Cancellation, microphone denial, interrupted downloads, model corruption, low memory, and cleanup timeout recover without losing the raw transcript.
- Insertion works in declared supported apps and never submits text automatically; a copy fallback remains available.
- Installation and first-run setup work on a clean account without a terminal, paid account, or API key.
- Report cold and warm latency, peak memory, and mobile battery behavior. Aim initially for cleaned text within 3 seconds on the M5 Mac and 5 seconds on the OnePlus after a 10-second dictation in a warm session; revise models or publish measured limits if these targets are missed.
- Test a 60-second dictation separately. Longer sessions may take longer and must show progress and remain cancellable.

Automated text checks supplement hands-on microphone, permission, keyboard, paste, and installer tests. Builds alone do not establish system-wide compatibility.

# Scope boundaries and next action

The first release provides dictation, conservative cleanup, vocabulary, local settings, and easy recovery. Cloud sync, meeting bots, voice assistants, app automation, paid model APIs, and custom model training are outside its scope.

Next action: create the development repository and pin the upstream foundations, then produce the first Mac and Android transcription builds and complete the free iPhone feasibility check. Proceed with zero required spending and keep iPhone optional if installation or hardware prevents an acceptable local experience.
