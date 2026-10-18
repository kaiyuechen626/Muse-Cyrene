> Public development export: see [SOURCE_PUBLICATION_STATUS.txt](SOURCE_PUBLICATION_STATUS.txt). Historical statements about four retained fixtures refer to the local candidate; this export omits them and explicitly skips dependent tests. APK gates remain open.

# License review status — 2026-10-18

LICENSE and NOTICE are preserved verbatim from the original source ZIP. NOTICE authorizes GPL v3 or any later version. New Cyrene application code is supplied under GPL-3.0-or-later. Third-party copyright/license files remain in place, including material3/material-color-utilities and app/src/main/assets/licenses.

No new external dependency or new image/font/audio asset was introduced by the 30-file Cyrene migration. The app dependency change only conditions the existing LeakCanary debug dependency. This does not certify all upstream dependencies or assets for redistribution.

Open questions before public distribution:

- Upstream support/payment QR assets, README bitmaps, screenshots and design-reference images were omitted from this clean candidate; no image or contact ownership is transferred to Cyrene.
- Seventeen referenced Android bitmap resources were replaced with new procedural geometric artwork at the same paths and dimensions. ASSET_PROVENANCE.txt and tools/cyrene-art/generate.cjs document/reproduce them; no original bitmap pixels are reused.
- NOTICE lists dependency licenses, including Junrar's extraction restriction and ML Kit descriptions. Resolve the exact dependency graph and verify upstream license/terms for the actual shipped artifacts; do not treat this inherited list as an exhaustive independently verified bill of materials.
- Generated Node/Python bundles can include Node, npm, CPython, OpenSSL and other libraries. Their build scripts remain available; review the resulting included notices and source obligations before bundling. No such binary packs were imported here.
- gradle/wrapper/gradle-wrapper.jar SHA-256 matches the official Gradle 9.4.1 checksum: 55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c.

Provide the complete corresponding source for the exact distributed APK, including Cyrene changes, necessary build scripts and retained notices. Publish a matching immutable source tag/archive next to each binary, not only a link to unmodified upstream. Signing credentials are private and must not be included. Unresolved ownership or licensing questions require owner confirmation; this document is not a blanket legal clearance.


Clean candidate update (2026-10-18): this tree intentionally does not carry upstream Git history. Preserve LICENSE/NOTICE and follow UPSTREAM_PROVENANCE.txt, CLEAN_SOURCE_POLICY.txt and ASSET_PROVENANCE.txt. Earlier statements about retained history describe the previous review copy only. The upstream Android publishing workflow is omitted. CI signing validation still targets upstream and is NOT approved for Cyrene publication. Source and APK release gates remain open.
