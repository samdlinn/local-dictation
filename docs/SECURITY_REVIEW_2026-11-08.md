# Local Dictation: first security and bug review

Reviewed November 8, 2026, America/New_York. Reviewed source: `966f1579424d3f8d1c9d638f616b460ac5ba5abe`. Published alpha application source: `f556bd8b1636a7c41f07838b64b79348b1fdc3d9`; the later commits change documentation and publication tooling, and the application findings below also apply to that alpha.

Scope: the Handy desktop foundation, Dictus Android foundation, imported OpenWispr/Arm Android cleanup wrapper, our local cleanup integration, permissions, downloads, retention, and build/release configuration. Findings concern the snapshots imported into this product; they do not establish the state of the upstream projects' latest branches. Application fixes have not been applied as part of this review.

P1 means fix before everyday use of this privacy-focused product; P2 means a material privacy, reliability, or dependency issue; P3 denotes hardening or a smaller correctness issue. These priorities are project judgments, not CVSS scores or assertions of remote exploitability.

## Confirmed findings

### R1 — P1: Android saves raw dictation despite history being disabled

Inherited Dictus path. `DictationTranscriber.transcribe` unconditionally calls `recordInDebugLog(debugEntry(transcript.text, ...))` at [DictationTranscriber.kt:137](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/app/src/main/java/dev/pivisolutions/dictus/service/DictationTranscriber.kt#L137). [DictationService.kt:136](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/app/src/main/java/dev/pivisolutions/dictus/service/DictationService.kt#L136) wires this callback directly to a private file store without a history, consent, or debug-build condition. [TranscriptionDebugEntry.kt:31](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/app/src/main/java/dev/pivisolutions/dictus/debug/TranscriptionDebugEntry.kt#L31) serializes the full `raw` text. [TranscriptionDebugStore.kt:29](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/app/src/main/java/dev/pivisolutions/dictus/debug/TranscriptionDebugStore.kt#L29) appends it to `polish_events.jsonl`.

The nominal retention is seven days. Purging happens at the first append in a process and on reads, so older content can remain longer while the app is inactive or the same process keeps appending. This is app-private storage, not evidence of automatic cloud upload. It nevertheless contradicts the product's stated history-off behavior; the earlier user-facing description missed this separate file.

Fix: make recording text to diagnostics explicitly opt-in and off by default, or retain counts/timings only. Remove existing raw debug files when moving to the corrected privacy default. Test history-off, keyboard dictation, exports, process restarts, and deletion.

Evidence: complete source call-chain inspection; existing app-module tests explicitly exercise retaining these entries. Those app tests were not rerun locally during this review.

### R2 — P1: canceled desktop dictations can leave orphaned audio

Inherited Handy path. [actions.rs:743](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/src-tauri/src/actions.rs#L743) writes every nonempty recording to a WAV before transcription/output processing finishes. Cancellation exits at [actions.rs:784](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/src-tauri/src/actions.rs#L784), [actions.rs:815](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/src-tauri/src/actions.rs#L815), and [actions.rs:821](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/src-tauri/src/actions.rs#L821) do not remove the WAV. No history entry has been created at those points. History retention only enumerates database rows, so it cannot discover these orphaned files.

Trigger: record a phrase, stop recording, then cancel while transcription or optional cleanup is running after the WAV write finishes. The history list can be empty while the audio remains under the app's `recordings` directory, including with the zero-history default.

Fix: give the temporary recording an ownership guard that removes it on cancellation and failure, transferring ownership only when retention actually requires a saved recording. Avoid creating a permanent recording at all when retention is disabled. Include a carefully scoped orphan-recovery strategy for earlier alphas.

Evidence: source control-flow and retention-query inspection. The microphone/cancellation sequence still needs a physical app regression test; it was not driven against the user's microphone during this review.

### R3 — P2: Android cleanup logs input and output text to Logcat

Imported OpenWispr/Arm wrapper. [ai_chat.cpp:296](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/llm/src/main/cpp/ai_chat.cpp#L296) writes complete formatted messages at INFO level, including user messages and generated assistant text. The bundled Qwen model supplies a chat template, so the formatting path is used. [logging.h](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/llm/src/main/cpp/logging.h) retains INFO logs in release native builds.

The published APK's `libai-chat.so` contains the `Formatted and added %s message` logging literal. The lower-level DEBUG prompt literals are absent in that binary, but that does not remove the INFO-level exposure. This is system Logcat, separate from the application's Timber file logger. On ordinary Android devices arbitrary apps cannot simply read all Logcat; exposure includes authorized ADB, privileged diagnostics, and collected system reports.

Fix: never log message bodies or generated tokens at any level in distributed builds. Keep event names, lengths, and timings only. Validate with a unique synthetic phrase and a Logcat capture on a phone.

### R4 — P2: zero desktop history still leaves recoverable transcript bytes

Handy persistence combined with our zero-history default. [history.rs:294](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/src-tauri/src/managers/history.rs#L294) inserts the raw and cleaned transcript before deleting entries over the limit. [history.rs:254](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/src-tauri/src/managers/history.rs#L254) opens SQLite without enabling secure deletion; [history.rs:465](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/src-tauri/src/managers/history.rs#L465) uses ordinary `DELETE`.

A reproduction linked against the project's already-built bundled `rusqlite` library reported `secure_delete=0`. After inserting a synthetic transcript, deleting its row, and closing the database, the transcript remained readable in the database file bytes while the visible row count was zero. Recovering it requires access to that local database or a copy; this is not a remote access finding.

Fix: bypass transcript/database and persistent audio writes entirely for history-off dictations. For explicitly retained history, use appropriate deletion settings and document that OS backups/snapshots and storage media may retain earlier copies. SQLite describes the behavior of [secure_delete](https://www.sqlite.org/pragma.html#pragma_secure_delete); setting it alone does not make an already-written copy disappear from backups.

Evidence: executed synthetic-data reproduction using the project's compiled SQLite dependency, plus source inspection. No user transcripts or user databases were accessed.

### R5 — P2: failed Android model preparation leaks native resources

Imported wrapper plus our integration. [InferenceEngineImpl.kt:167](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/llm/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt#L167) loads the model before preparing its context. A preparation/system-prompt exception enters `State.Error`. Our `finally` calls `cleanUp`, but its [Error branch at line 297](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/llm/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt#L297) only changes the state to Initialized: it does not free native resources. The next load overwrites the global model pointer in [ai_chat.cpp:72](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/llm/src/main/cpp/ai_chat.cpp#L72).

A failure after allocation can therefore leak a large model/context, increasing memory pressure or causing a later process kill. The existing native `unload()` assumes fully initialized handles, so simply calling it from every error branch would need further care.

Fix: implement idempotent, partial-initialization-safe native cleanup; null freed handles; release allocated resources before resetting error state. Exercise load, prepare, and prompt failures with injected faults and repeated retries.

Evidence: source lifecycle inspection. Native allocation failures were not injected on a physical Android device.

### R6 — P2: Android's 45-second cleanup timeout is not a hard execution bound

Our [LocalCleanup.kt:86](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/app/src/main/java/dev/pivisolutions/dictus/localcleanup/LocalCleanup.kt#L86) wraps operations in `withTimeout`, but model loading and prompt processing enter synchronous JNI calls at [InferenceEngineImpl.kt:167](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/llm/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt#L167), [line 202](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/llm/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt#L202), and [line 231](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/llm/src/main/java/com/arm/aichat/internal/InferenceEngineImpl.kt#L231). The native wrapper has no decode abort callback, and `cleanUp` uses blocking dispatcher work.

Kotlin cancellation is [cooperative](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-timeout.html); it cannot terminate a synchronous native call. A slow or stuck load/prefill can delay fallback beyond 45 seconds, holding up the dictation result. This does not prove that normal dictations hang or that the UI always freezes.

Fix: add native cooperative cancellation for inference, move blocking cleanup off the main thread, and consider a worker process if a hard failure boundary is required. Test timeouts/cancellation under memory pressure and with long input on the OnePlus.

Evidence: source inspection and coroutine API semantics, not a measured phone timeout.

### R7 — P2: dependency baselines contain published advisories

`bun audit --json` returned matches for 18 package names and 44 distinct advisory URLs. The scan includes development and transitive dependencies and sometimes multiple affected ranges for one advisory. These are not 44 demonstrated vulnerabilities in the installed app.

OSV queries covered 846 Rust registry package/version pairs from Cargo.lock and two pinned native-engine commits. Thirty Rust package/version pairs matched 42 advisory IDs, including duplicate alias records, unmaintained-package notices, and vulnerabilities whose affected APIs may not be used. There were no pending pagination tokens. The two native commit queries returned no matches; that is not a substitute for auditing/fuzzing native code, and it does not cover every vendored Android source file.

Examples requiring action or triage:

- Vite 6.4.1 is affected by [GHSA-p9ff-h696-f583](https://github.com/vitejs/vite/security/advisories/GHSA-p9ff-h696-f583). That issue exposes files when the development server is explicitly network-accessible; this project's normal config binds locally. Packaged production installers do not run the Vite dev server. This one advisory is fixed in 6.4.2, but the audit flags additional Vite advisories, so choose a version that clears the complete current scan.
- `h2` 0.4.13 matches [RUSTSEC-2026-0258](https://rustsec.org/advisories/RUSTSEC-2026-0258.html), a low-severity empty-DATA-frame memory-exhaustion issue, patched in 0.4.16. Downloader/network exposure needs runtime-specific triage.
- `webpki-roots` 1.0.8 and `webpki-root-certs` 1.0.6 match the [emSign distrust advisory](https://rustsec.org/advisories/RUSTSEC-2026-0381.html); the relevant 1.x root bundles should resolve to at least 1.0.10. The local dependency tree places `webpki-roots` through `hf-hub`'s `ureq` dependency. The application's reviewed downloader uses hf-hub's Tokio API, so this review does not claim that this synchronous TLS path is actively exercised.
- Other Rust matches include unmaintained GUI dependencies and API-specific soundness issues. An advisory match alone does not establish a reachable exploit.

Fix: refresh compatible lockfile versions, remove unused dependencies/features, triage remaining notices by production reachability and platform, and add recurring advisory checks to CI. Validate upgrades with the existing build and device tests.

## Additional hardening and correctness notes

- **Desktop webview — P3, defense in depth:** [tauri.conf.json:16](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/src-tauri/tauri.conf.json#L16) disables CSP and permits the asset protocol to read any matched path, including dotfiles. This increases the impact of a future frontend injection, but no working injection or remote file-read chain was demonstrated. Enable a restrictive [CSP](https://v2.tauri.app/security/csp/) and scope asset reads to the specific audio/resources the UI needs. Review the main and overlay windows' capabilities independently.
- **Android distribution — P3:** [build-apps.yml:85](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/.github/workflows/build-apps.yml#L85) publishes an `assembleDebug` artifact. Re-signing it does not change its debuggable build configuration. Use a properly signed release variant for ordinary users; authorized debugging access is a stronger exposure in a debug build. A paid certificate or developer account is not necessary for signing an Android release APK.
- **Android launcher label — P3:** [AndroidManifest.xml:21](https://github.com/samdlinn/local-dictation/blob/966f1579424d3f8d1c9d638f616b460ac5ba5abe/android/app/src/main/AndroidManifest.xml#L21) still hardcodes `Dictus`, despite the Local Dictation onboarding/keyboard branding. Use the product's own application-label resource so users can find the correct app.
- **Testing gap:** the build workflow runs Android `:core:testDebugUnitTest`, not the app-module diagnostic-store/transcriber tests or physical native cleanup tests. Compilation and core test success did not establish the retention/privacy behavior reviewed here.
- **AI output integrity:** overlap/numeric/negation heuristics are not proof of semantic equivalence. Reordering names or reversing who pays whom can preserve all words and digits. Review-before-send remains necessary; this is a documented design limitation, not a newly demonstrated arbitrary-code execution issue.

## Checks and limits

Executed: source inspection of the paths above, Bun advisory scan, OSV lockfile/commit queries, installed Rust dependency-tree tracing, binary inspection of the published Android native logging literal, and the synthetic bundled-SQLite deletion reproduction. Existing user data was not read or modified.

The reviewed cloud client is explicitly blocked; cleanup downloads use a fixed revision, expected length, and SHA-256; Android has app-private storage, backup exclusions, a non-exported dictation service and a keyboard service protected by BIND_INPUT_METHOD. These are useful controls, not a blanket security guarantee.

Remaining verification includes phone/device runtime behavior, network capture, Android transitive Maven dependency resolution, native parser fuzzing, OS permission flows, and a complete supply-chain audit. This review does not certify the application or claim that no other bugs exist.

Recommended fix order: R1 and R2, R3 and R4, Android native cleanup/cancellation, dependency updates, then permission/build hardening. Use synthetic, non-sensitive phrases while validating the current alpha.
