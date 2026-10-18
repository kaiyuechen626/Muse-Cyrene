> Public development export: see [SOURCE_PUBLICATION_STATUS.txt](SOURCE_PUBLICATION_STATUS.txt). Historical statements about four retained fixtures refer to the local candidate; this export omits them and explicitly skips dependent tests. APK gates remain open.

# Cyrene build plan (2026-10-18)

This is a source-based build recipe, not a claim of a bit-identical or freshly verified release build. No SDK is installed and no APK is built by this preparation. Run commands from the repository root only after selecting the signing/upgrade policy.

## Prerequisites

- JDK 21, Android SDK platform 36, Build Tools 36.0.0, NDK 27.0.12077973, CMake 3.22.1 and platform-tools. Accept the SDK licenses yourself.
- Configure JAVA_HOME, ANDROID_HOME locally for your shell; optionally an ignored local.properties with sdk.dir. Do not commit host paths.
- Use the included Gradle wrapper and gradle/wrapper/gradle-wrapper.properties (no separately installed Gradle required).
- Node.js 22/npm for the embedded Web UI. From web/, run npm ci then npm run build; return to the root. The Gradle sync task otherwise silently omits a missing web/apps/client/dist.
- Built-in Node/Python runtime bundles are not tracked: follow tools/runtime-pack/README.md and tools/python-pack/README.md for supported build hosts and dependencies. Without these generated libraries/archives, an APK may compile but runtime features are not complete. Audit their redistribution notices before a full-feature release. Do not copy unknown binaries from the old work directory.

## Personal debug / test build

The old build_muse.cmd invokes an external PowerShell bootstrap with hardcoded sibling work/ and outputs/ layout, SDK downloads, local debug-key generation, tests and assembleDebug. It is intentionally not copied. Its default version was upstream 2.6.0 / 260 and it did not create a formal release.

For an upgrade test, first verify access to the existing private test signing key outside this repository. Do not create a replacement if you intend to update an installed build. The cyreneKeystore option configures the main app's debug signature using the historical debug alias/password; it is not a general release signing interface. The helper now uses the same cyreneKeystore debug configuration as the main app. This is source configuration only; actual certificates and bridging remain unverified.

PowerShell example (replace the path locally; never commit it):

~~~powershell
$testKey = 'X:/PRIVATE/existing-test-key.keystore'
if (!(Test-Path -LiteralPath $testKey)) { throw 'Existing key required for upgrade testing' }
.\gradlew.bat :app:testDebugUnitTest --tests '*AutoReadReplyGateTest' -PcyreneBuild=true "-PcyreneKeystore=$testKey" -PversionName=0.1.0 -PversionCode=261 --no-daemon --max-workers=2
.\gradlew.bat :app:assembleDebug -PcyreneBuild=true "-PcyreneKeystore=$testKey" -PversionName=0.1.0 -PversionCode=261 -PnoUniversal --no-daemon --max-workers=2
~~~

Output: app/build/outputs/apk/debug/. Debug builds are debuggable even if LeakCanary is absent. Do not advertise them as production Release APKs.

## Proposed release version and signing gate

Proposed versionName **0.1.0**, versionCode **261** (above the inspected installed-test artifact's 260). This is the Cyrene version, based on Muse v2.6.1. Neither applicationId nor build defaults have been silently changed; use explicit properties. cyreneBuild is checked for presence, so even -PcyreneBuild=false selects Cyrene. It selects io.zer0.muse.cyrene and disables the debug LeakCanary dependency; it does not select Release signing or set versions.

Before Release, decide whether to retain the existing signing certificate for upgrades or establish a distinct public signing policy. Never silently rotate the certificate: same-package Android upgrades require a compatible signer and increasing versionCode. Existing private key material was not copied or modified. Its presence alone does not prove signing access, security or upgrade compatibility. Preserve the original app/backup until on-device upgrade tests pass.

After that decision, configure the existing approved key in ignored keystore.properties (storeFile, storePassword, keyAlias, keyPassword). Use an absolute local storeFile so app and accessibility-provider resolve the same key. Do not publish this file or secrets. With cyreneBuild present, the helper uses io.zer0.muse.cyrene.a11y and io.zer0.muse.cyrene.permission.A11Y_BRIDGE. Without that property the upstream identities remain unchanged. Java/AIDL class namespaces remain upstream. The new helper needs separate accessibility authorization; coexistence and signatures are not yet verified.

Proposed command, **not executed**:

~~~powershell
.\gradlew.bat :app:assembleRelease -PcyreneBuild=true -PversionName=0.1.0 -PversionCode=261 -PnoUniversal --no-daemon --max-workers=2
~~~

Output: app/build/outputs/apk/release/. Verify debuggable=false with aapt/apkanalyzer, applicationId, versions, ABI, certificate and signature with apksigner; then test the exact APK on-device. The historical upstream .github/workflows/ci.yml is absent from this candidate. The only current workflow is web-ci.yml; it does not build or publish Android. The unchanged upstream certificate validator is not suitable for Cyrene release approval.

## Source/artifact traceability

After review approval (not in this preparation), commit the reviewed tree and create a distinct Cyrene tag such as cyrene-v0.1.0. Build from that exact clean commit, record JDK/SDK/wrapper versions, command, optional runtime-pack inputs, certificate digest and source commit in a release manifest. Generate APK SHA-256 using Get-FileHash -Algorithm SHA256. Attach the matching complete source archive and instructions alongside the APK; do not attach the old unreviewed source ZIP or private signing keys. No byte-for-byte reproducibility claim is made until independently verified.


Clean candidate update (2026-10-18): this tree intentionally does not carry upstream Git history. Preserve LICENSE/NOTICE and follow UPSTREAM_PROVENANCE.txt, CLEAN_SOURCE_POLICY.txt and ASSET_PROVENANCE.txt. Earlier statements about retained history describe the previous review copy only. The upstream Android publishing workflow is omitted. CI signing validation still targets upstream and is NOT approved for Cyrene publication. Source and APK release gates remain open.

## Approved source repairs (2026-10-18; no execution approval)

Cyrene first release uses manual updates only. Both settings entrances explain this;
upstream queries, notifications and cached banners are suppressed for Cyrene.
No independent repository/channel is configured. Original Muse builds retain their
update behavior. A Cyrene update must retain package identity/signing certificate
and increase versionCode; this work neither signs nor installs anything.

Full Node AND Python (both supported ABIs, npm, stdlib, C extensions and dependency
libraries) remain first-release requirements. Read tools/runtime-inputs/README.md:
the empty input lock deliberately blocks packaging until actual official hashes
are verified. Do not bypass it or borrow old binaries. Python's existing desktop
and self-test pruning remains unchanged; this is not a new component reduction.
Its existing Tk/test pruning contract still needs review before full-feature signoff.

Legal resources are merged rather than discarded. This can expose packaging
conflicts and does not complete the notice inventory. Four fixtures, Junrar,
ML Kit and runtime redistribution evidence remain blocked; no release clearance.
All build, unit/instrumented/runtime tests, signing and upgrade checks remain pending.
