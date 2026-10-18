> Public development export: see [SOURCE_PUBLICATION_STATUS.txt](SOURCE_PUBLICATION_STATUS.txt). Historical statements about four retained fixtures refer to the local candidate; this export omits them and explicitly skips dependent tests. APK gates remain open.

# v0.1.0 acceptance record — 2026-10-18

## Evidence already available

- User-reported PASS on the previous personal APK: automatic reply reading; continued reading after leaving chat; pause/resume. Device context: Galaxy S25; OS/build details and a formal run log were not recorded.
- Previous build screenshot: BUILD SUCCESSFUL. This is a Debug build, not evidence that this prepared source builds as Release.
- This preparation: upstream verification, per-file hash migration, documentation and static inventory checks. No new Android build or phone test performed.

## Required before APK distribution (NOT RUN here)

- [ ] Build/test from the final reviewed commit and record artifact/source hashes.
- [ ] AutoReadReplyGate unit tests and project lint/static checks pass; inspect reports, not just exit text.
- [ ] Toggle off/on; normal completed reply, blank/error/cancelled reply and streaming completion.
- [ ] Switch chats during generation and playback; verify expected original playback and no wrong-session replay.
- [ ] Rapid consecutive replies: verify documented skip behavior, no hidden queue or overlap.
- [ ] Pause during synthesis, within audio, and across sentence boundaries; resume once; stop while paused.
- [ ] Notification pause/resume/stop, lock screen, leaving chat, background duration, process termination.
- [ ] Bluetooth/headphone changes, calls/audio focus, network loss, timeout and server error; no runaway retries.
- [ ] Preserve real data using private backups; use synthetic fixtures for foreign-key import regression. NDJSON import remains a known issue; do not claim fixed.
- [ ] Upgrade from the existing private APK with the intended key/version; preserve settings and chats.
- [ ] Release debuggable=false, correct package/version/ABI and verified signature.
- [ ] Optional runtime packages and accessibility provider reviewed if included; no implied compatibility.
- [ ] Sensitive-data and license review covers final tracked files, final assets and reachable Git history.

Functional changes must follow UI组件库维护规范.md. Existing Cyrene settings reuse SettingsGroup/SettingsSwitchRow and existing playback controls; this preparation does not redesign UI.


Clean candidate update (2026-10-18): this tree intentionally does not carry upstream Git history. Preserve LICENSE/NOTICE and follow UPSTREAM_PROVENANCE.txt, CLEAN_SOURCE_POLICY.txt and ASSET_PROVENANCE.txt. Earlier statements about retained history describe the previous review copy only. The upstream Android publishing workflow is omitted. CI signing validation still targets upstream and is NOT approved for Cyrene publication. Source and APK release gates remain open.

## 2026-10-18 source implementation: still BLOCKED
- [ ] Verify official input hashes and complete tools/runtime-inputs/inputs.lock.json; no invented hashes.
- [ ] Resolve all four unchanged test fixture origins, Junrar 8.1.1, ML Kit and runtime license/source closure.
- [ ] Resolve actual Release dependency graph, per-component notices and final APK/helper inventory.
- [ ] Validate arm64-v8a/x86_64, clean single/dual ABI packs, all ELF LOAD segments and full Node/Python functions.
- [ ] Validate manual-update UI, no upstream network checks/cached banners, and original Muse update behavior.
- [ ] Verify Cyrene helper package/permission, same signer, embedded APK, separate authorization and original coexistence.
- [ ] Verify wrong helper/signature rejection, app fallback and existing-key upgrade/data retention.
- [ ] Complete separately authorized builds/tests/signing. Skipped tests do not count as passed.
No commits, publishing, remote setup or Worker deployment is authorized by this checklist.
