# Cyrene modification record

Unofficial modification of Muse v2.6.1 (commit d7ce76b9640e42afcb571fa3813c9bcf0779969c).

Notice/preparation date: 2026-10-18. This date records the release preparation, not a claimed original authorship timestamp. New Cyrene code: GPL-3.0-or-later. Original copyright and third-party notices remain applicable.

Migrated 17 modified and 13 added files, SHA-256 verified before the release preparation edits below. No original ZIP file was missing. Changes cover TTS settings, reply completion/deduplication, callback/state fixes, foreground playback, pause/resume, app labeling, conditional build identity/LeakCanary and gate tests.

| Kind | File | Migrated source SHA-256 |
| --- | --- | --- |
| modified | `app/build.gradle.kts` | `4c615227573d6af3f6e89768888ce30ea702d1b091d66a6bae50dbf529d0ae3f` |
| modified | `app/src/main/AndroidManifest.xml` | `4f83a7f46d05ee03d50087d151daf21f7ac5604679aee104a4b10dd024fee52a` |
| modified | `app/src/main/java/io/zer0/muse/data/SettingsModels.kt` | `3ca423154bf4db17f2bb7e1f861bed1ea62bb6f1576f9c1370214b460d991bc4` |
| modified | `app/src/main/java/io/zer0/muse/ui/chat/ChatAudioCoordinator.kt` | `aadedc0e38350d78074515153f4b55200f436ebab68af0392b6b36c07579de87` |
| modified | `app/src/main/java/io/zer0/muse/ui/chat/ChatGenerationController.kt` | `eb3be8bc4a2501deac2f5ceee9081e26dbbdf3c1588faab6a570f426c1ea08c7` |
| modified | `app/src/main/java/io/zer0/muse/ui/ChatScreen.kt` | `bb2ec9c054e5d3bb43c9fb86ee2e3eee73491139c25e19cf0d9b812ecc878aa3` |
| modified | `app/src/main/java/io/zer0/muse/ui/ChatViewModel.kt` | `1ad8ed30aeaa20f5fb5c111ae2d8b2d01dfa340719aa28d27dd55fc40ba652fc` |
| modified | `app/src/main/java/io/zer0/muse/ui/settings/MediaSettingsPage.kt` | `09f9f0704264c4f5309b1bbf545ad179aa3b998b3049ac16112de08328eb8556` |
| modified | `app/src/main/java/io/zer0/muse/ui/speech/TtsManager.kt` | `7d96f6784f27f02f7978074841f1212ebe54d2810509eb3c41982b8d0cdaa216` |
| modified | `app/src/main/res/values-en/strings.xml` | `181d2b5fdb2b86de7e81c2707213d589adf77c7eada281cc7b3008ef3550001c` |
| modified | `app/src/main/res/values-es/strings.xml` | `9ec3fc4afd7c6fb49b4c838d01fe7da4cc188ace2dbcb75897a00cd9cadb70f6` |
| modified | `app/src/main/res/values-ja/strings.xml` | `835bf32e50dfed17d206a74792fa0105b6969b1cc1114d7f71e0549029f00ee6` |
| modified | `app/src/main/res/values-ko/strings.xml` | `b79b3c3ce9629325a6248e03c77ffbbcf0839ef77a30b9c28fcb9281e143b7d4` |
| modified | `app/src/main/res/values-pt-rBR/strings.xml` | `2c3df744e6cd2000602bf362ffdeb8544f49757fd1947470ff5f20727804d1d7` |
| modified | `app/src/main/res/values-ru/strings.xml` | `a5b3df8ce7cbdfe00729c68386933e4396625dc088a4de2e76d980cbfe5fc08a` |
| modified | `app/src/main/res/values/strings.xml` | `65beb7938d98de88b1a8a8fbd59404968f16ee8a7c6a6e3794ce448c49391795` |
| modified | `release_notes.md` | `9b3e3afab65e88e68c5f198482228512b9ccaa13899886547ff4f6c9765510a8` |
| added | `CYRENE_CHANGES.txt` | `42407d5468712f64d018b5ff53df580267d9a3de38e9973cdde81ff4425d4115` |
| added | `app/src/main/java/io/zer0/muse/ui/chat/AutoReadReplyGate.kt` | `fc15a91017d1d19e699bc147778c231979f28bf4bba467355d12696e0f82ea12` |
| added | `app/src/main/java/io/zer0/muse/ui/speech/ReadingPlaybackService.kt` | `db0dd50abd4937cb7e9459e347a7f38a6101f7a0745c2db5dbeef0ca7d9e0ce8` |
| added | `app/src/main/res/values/strings_auto_read.xml` | `3f50e0ec234915059ad5300e3de5d35a7e9f4ec53caeab6862dbd51ab9ff218e` |
| added | `app/src/main/res/values/strings_reading_background.xml` | `4dcd9478ca6a5023e2d48c8443da8805ce9ec00cc7a050e6190bbe2a39ffd113` |
| added | `app/src/main/res/values-en/strings_auto_read.xml` | `33ca649ef61df14568dd41d825dff20b7d9c4180247d3beccc3e79ad322a767f` |
| added | `app/src/main/res/values-en/strings_reading_background.xml` | `fb7423e099ee8e335403d2186e6bf294a9e54f069a788581f46aa9719a69ed00` |
| added | `app/src/main/res/values-es/strings_auto_read.xml` | `ba6764d990732300278aee42cce9491c7428854a054a0f6bc74e5bbfd1bbb463` |
| added | `app/src/main/res/values-ja/strings_auto_read.xml` | `ee807bf3f553608be7ee01f7171d224693853154b5bab4f8012340a903326c00` |
| added | `app/src/main/res/values-ko/strings_auto_read.xml` | `457fe1dcc6f4311a01c76223d2c9d25bbaa9b257f0b196b9f142f199fbdbc7e7` |
| added | `app/src/main/res/values-pt-rBR/strings_auto_read.xml` | `7afa8be5eb50a9544d715bfa22c612a0324008898f0d3a6dc15241e3aae5c8e0` |
| added | `app/src/main/res/values-ru/strings_auto_read.xml` | `f7d3be2535e51ecb92de293bd5aad602a44d388f4de2a8620afa065d82add3e8` |
| added | `app/src/test/java/io/zer0/muse/ui/chat/AutoReadReplyGateTest.kt` | `f10900a7fabb20aa0f1156b7b08d76a28f8d046e3df087a7cc391a1dba57a7d2` |

## Additional release preparation edits

README.md, README_EN.md, CYRENE_CHANGES.txt: replaced stale personal-test instructions with unofficial identity, evidence, limits and self-contained build guidance. .gitignore: added scoped privacy/build exclusions. Added BUILDING_CYRENE.md, CYRENE_RELEASE_CHECKLIST.md, CYRENE_LICENSE_REVIEW.md and this record. Added dated comments to migrated code/resources and a Cyrene preface to release_notes.md. No functional UI/playback behavior, applicationId, certificate or version default was changed during preparation.

LICENSE and NOTICE remain byte-for-byte identical to the supplied upstream ZIP. Git checkout reports inherited EOL normalization differences; these are not functional Cyrene changes. Cloudflare Worker and private bootstrap files are not included.


Clean candidate update (2026-10-18): this tree intentionally does not carry upstream Git history. Preserve LICENSE/NOTICE and follow UPSTREAM_PROVENANCE.txt, CLEAN_SOURCE_POLICY.txt and ASSET_PROVENANCE.txt. Earlier statements about retained history describe the previous review copy only. The upstream Android publishing workflow is omitted. CI signing validation still targets upstream and is NOT approved for Cyrene publication. Source and APK release gates remain open.

## Source publication documentation, 2026-10-18

Added SOURCE_PUBLICATION_STATUS.txt and TEST_FIXTURE_PROVENANCE.txt; linked them
from both READMEs and CLEAN_SOURCE_POLICY.txt. Verified all four held fixtures
against locally retained upstream v2.6.1 Git blobs and recomputed their SHA256.
Separated development-source readiness from APK acceptance. No fixture, license,
runtime implementation or test contract changed; no build or test was run.

## Authorized technical repair, 2026-10-18

The earlier byte-identical NOTICE statement describes the preparation snapshot;
NOTICE is now corrected for ML Kit and explicitly records unresolved licensing.
LICENSE itself and all four held fixtures remain byte-identical.

This repair separates vendor architecture from Android ABI, adds strict official
metadata/hash preflight and versioned caches, re-extracts verified inputs, and
fixes Python selected-ABI stdlib/sysconfigdata handling. The input lock is empty
and packaging remains blocked. No runtime component list or dependency was reduced.

Cyrene gets a distinct helper package/bridge permission, explicit unchanged Java
service class names, the existing-key debug configuration, and embedded-helper
package/signer checks before installation. Original Muse identities remain default.
Cyrene uses manual updates only; upstream network checking, notification, cached
banner and update-download entrances are gated. Original Muse updates are preserved.

Legal META-INF resources are merged, not dropped; the source inventory flags
unknown resolved versions and Junrar/ML Kit blockers. This is not an APK SBOM or
legal clearance. Documentation describes full Node/Python release requirements
and all outstanding build, test, signer, coexistence and upgrade verification.

Files changed/added in this repair:
- `accessibility-provider/build.gradle.kts`
- `accessibility-provider/src/main/AndroidManifest.xml`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/assets/licenses/components/junrar-review.txt`
- `app/src/main/assets/licenses/components/ml-kit-review.txt`
- `app/src/main/assets/licenses/manifest.json`
- `app/src/main/java/io/zer0/muse/AppInfraModule.kt`
- `app/src/main/java/io/zer0/muse/tools/system/AccessibilityClient.kt`
- `app/src/main/java/io/zer0/muse/tools/system/AccessibilityProviderInstaller.kt`
- `app/src/main/java/io/zer0/muse/ui/HomeScreen.kt`
- `app/src/main/java/io/zer0/muse/ui/settings/SettingsSubPages.kt`
- `app/src/main/java/io/zer0/muse/ui/SettingsScreen.kt`
- `app/src/main/java/io/zer0/muse/update/UpdateChecker.kt`
- `app/src/main/java/io/zer0/muse/update/UpdateNotifier.kt`
- `app/src/main/res/values/strings_cyrene_updates.xml`
- `app/src/main/res/values-en/strings_cyrene_updates.xml`
- `BUILDING_CYRENE.md`
- `CYRENE_RELEASE_CHECKLIST.md`
- `NOTICE`
- `tools/python-pack/build-python-pack.sh`
- `tools/python-pack/README.md`
- `tools/runtime-inputs/inputs.lock.json`
- `tools/runtime-inputs/README.md`
- `tools/runtime-inputs/verified_fetch.py`
- `tools/runtime-pack/build-runtime-pack.sh`
- `tools/runtime-pack/README.md`
- `CYRENE_MODIFICATIONS.md`


## Public development export, 2026-10-18

Omitted the four binary fixtures listed in TEST_FIXTURE_PROVENANCE.txt only in
this export. Explicitly ignored three RAR reader methods, the separated RAR
integration method and the two-test market sample class. ZIP/7z integration
remains active. Original binaries remain in the local candidate. No production
source was changed. Updated source-status documentation and fixture ignore rules.
This reduces executable regression coverage; no Android tests/builds were run.

## Release build preparation, 2026-10-18

Pinned 17 runtime inputs with signed Termux metadata and Python publisher Sigstore verification, added rejection tests and an isolated Linux packaging workflow. Build-workflow provenance is not asserted. Cyrene ABI splits now match the complete runtime bundle (arm64-v8a and x86_64); the upstream build retains its original ABI list. No speech or playback implementation changed. APK/device acceptance and dependency redistribution review remain pending.

Corrected three inherited Lint baseline paths to use the built-in
`$GRADLE_USER_HOME` variable spelling. Lint 32.2.1 interprets the previous braced
name as a different variable and failed while reading the baseline. Existing
issue entries and enabled checks are unchanged; no new suppression was added.

Disabled the Cyrene universal APK because AGP included dependency-only 32-bit
ABIs without their complete runtimes. Cyrene emits arm64-v8a and x86_64 APKs;
the original Muse universal option remains unchanged.
