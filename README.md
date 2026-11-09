# Local Dictation

Free dictation with local speech recognition and optional on-device AI cleanup. No app account, API key, subscription, or hosted inference.

**Early alpha.** This is an independent project built on Handy and Dictus, with original branding and credit retained. It is not affiliated with Wispr Flow or the upstream projects. iPhone support is deferred while free installation is evaluated.

GitHub lists contributors from Handy’s inherited commit history. Those credits identify historical authors of the foundation, not a newly assembled Local Dictation team.

## Install an app

Download a package from [Releases](https://github.com/samdlinn/local-dictation/releases). You do not need to build from source.

| Platform | Package | Status |
| --- | --- | --- |
| Apple Silicon Mac | DMG | First local build produced; microphone and paste permissions require a device check |
| Other Mac builds | DMG for the architecture shown in its filename | Build pipeline |
| Windows 10 and 11 x64 | Setup EXE | Build pipeline; CPU inference initially |
| Ubuntu Linux x64 | DEB or AppImage | Build pipeline; Wayland integration varies by desktop |
| Android arm64, Android 10+ | APK | Build pipeline; physical-device validation pending |
| iPhone | None yet | Optional and deferred; no paid developer membership required by this project |

Mac and Windows may show installation warnings because the alpha has no paid notarization or publisher certificate. Install only assets from this repository. Android APKs use the same private development signing key across alphas so updates can preserve app data.

For the Windows 10 desktop, follow the [short Windows test guide](docs/WINDOWS_TEST.md) after the setup EXE is listed in the release.

### First launch

1. Install and open the app. Allow microphone access and the insertion or keyboard permissions requested by your operating system.
2. Download the recommended speech model.
3. Desktop: use the configurable shortcut shown in settings. Android: enable the Local Dictation keyboard, then tap its microphone.
4. Optional: open **Local cleanup** on desktop, or **Settings → Local cleanup** on Android. Download the free Qwen3 model (1.83 GB), then enable cleanup.
5. After model setup, dictation and cleanup run on the device with internet disconnected. Cleanup failures fall back to transcription. Always review important names, numbers, and negations.

All app settings and vocabulary are local. Desktop history defaults to zero retained dictations; Android history is initially disabled. File imports are retained locally until deleted. See [PRIVACY.md](PRIVACY.md).

## Requirements

Initial targets, to be validated rather than universal guarantees: 8 GB RAM on desktop, 16 GB preferred; a modern arm64 Android device, with 6–8 GB RAM preferred for local cleanup; roughly 3 GB free for the optional cleanup model and processing headroom, in addition to speech models. A dedicated GPU is not required. CPU instruction support and OS versions matter on older machines.

The primary test devices are an M5 MacBook Air with 16 GB and a OnePlus 15. The Windows 10 desktop and physical Android keyboard flow still need end-to-end tests. Small local models can miss corrections or change meaning; this release prioritizes preserving the original transcript when checks fail.

## Build from source

Normal users should download a release. Developers need Rust stable, Bun 1.4.4+, Python 3, CMake, and the [Tauri platform prerequisites](https://v2.tauri.app/start/prerequisites/).

```sh
git clone --recurse-submodules https://github.com/samdlinn/local-dictation.git
cd local-dictation
bun install --frozen-lockfile
python3 scripts/prepare-local-runtime.py
bun tauri build
```

The script builds a pinned llama.cpp completion executable without HTTPS or a listening server and stages it with the app. Speech models and the cleanup model are not committed to Git. The cleanup model is downloaded from a pinned revision and verified with SHA-256.

Android builds require JDK 17, Android SDK 36, NDK 27.2.12479018 and CMake 3.31.6 (plus the Whisper module's CMake 3.22.1):

```sh
cd android
./gradlew :app:assembleDebug :core:testDebugUnitTest
```

A local debug build uses your local Android signing identity. Before publishing, the release workflow re-signs its APK with this project's persistent development key held in GitHub Actions secrets, verifies the signature and 16 KB native-library ZIP alignment, and discards the temporary key file. The private key is never committed.

## Verify a change

```sh
python3 scripts/check-local-policy.py
bun run lint
bun run build
cargo fmt --manifest-path src-tauri/Cargo.toml -- --check
cargo clippy --manifest-path src-tauri/Cargo.toml
cargo test --manifest-path src-tauri/Cargo.toml local_cleanup::tests
```

The inherited desktop has additional tests and translation checks. Microphone, paste, permission, and mobile JNI tests need actual app sessions; compilation does not replace them.

## Repository layout

- `src/` and `src-tauri/`: desktop app, local speech processing, and embedded cleanup.
- `android/`: native keyboard app, speech engines, and local cleanup.
- `scripts/prepare-local-runtime.py`: pinned desktop cleanup engine build.
- `.github/workflows/build-apps.yml`: public desktop and Android builds on standard runners.
- `PRODUCT_PLAN.md`: product decisions and staged delivery.
- `THIRD_PARTY_NOTICES.md` and `licenses/`: upstream credits and model/library licenses.

## License and upstream work

MIT, with original notices retained. Desktop foundation: [Handy](https://github.com/cjpais/Handy), Copyright 2025 CJ Pais. Android foundation: [Dictus Android](https://github.com/getdictus/dictus-android), Copyright 2026 PIVI Solutions. Android local inference module: [OpenWispr](https://github.com/RohitAg13/openWispr) and its llama.cpp Android example ancestry. Qwen3 model: Apache 2.0. See [third-party notices](THIRD_PARTY_NOTICES.md) for pinned revisions and additional licenses.

The project is free software. Existing hardware, storage, electricity, and download bandwidth still apply. There is no required paid service, certificate, app-store listing, or inference bill.
