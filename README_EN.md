# Muse Cyrene

An unofficial Android fork of [Muse v2.6.1](https://github.com/Zer0Qing/Muse/tree/v2.6.1), maintained by kaiyuechen626. Original author Mozi, GPL notices and upstream attribution are retained. Cyrene changes are GPL-3.0-or-later; upstream does not endorse this release.

[Download 0.2.0 ARM64 APK](https://github.com/kaiyuechen626/Muse-Cyrene/releases/download/cyrene-v0.2.0/Muse-Cyrene-0.2.0-arm64-v8a.apk) | [Release and source attachments](https://github.com/kaiyuechen626/Muse-Cyrene/releases/tag/cyrene-v0.2.0)

Android 8.0+, version 0.2.0 / 271, package io.zer0.muse.cyrene. Same signing certificate as public 0.1.0 / 261 and accepted 0.1.9 / 270; do not uninstall to bypass a signature mismatch. No speech models, recordings, personal voices or API keys are bundled.

Adds embedded local role-pack speech, optional faster synthesis, sentence grouping and prefetch during playback. DeepSeek chat, auto-read, background reading, pause/resume/stop, system TTS and existing application tools remain. Local role synthesis is offline; chat and other online services still use configured network providers.

0.2.0 preserves accepted 0.1.9 business code and reuses its Galaxy S25 functional acceptance. The final 0.2.0 APK was not newly installed. Release build, 27 selected tests, signing, assets, JNI callback and upgrade prerequisites were checked; this is not a full regression or all-device certification. Long-text waits, inherited NDJSON limits and an inherited image library lacking 16KB ELF alignment remain. Bluetooth, calls, x86_64 and whole-app regression are outside this acceptance scope.

See BUILDING_CYRENE.md, releases/0.2.0.md and release/PUBLICATION.md. Original 0.1.0 license materials accompany the release, with added speech notices and upstream source directions. Existing Junrar / ML Kit licensing questions remain unresolved, as previously disclosed; publication is not legal clearance from upstream, Google or FSF. No code or model authorship is claimed for third-party components.
