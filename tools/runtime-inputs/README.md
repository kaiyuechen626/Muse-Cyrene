# Runtime input gate — Cyrene, 2026-10-18

## Current release-build preparation

The lock now contains all 17 inputs. `evidence/` preserves signed Termux indexes
and Python Sigstore bundles; see `evidence/PROVENANCE.txt`. Run
`python -B tools/runtime-inputs/check_evidence.py` with gpgv and the hash-pinned
Sigstore requirements installed. The validator supports publisher-authenticated
Python bundles with exact ABI/URL/digest binding. Build provenance remains
unconfirmed and is not inferred from publisher identity. Six rejection/positive
tests passed locally. The Cyrene runtime workflow builds both ABIs without
signing credentials or Release publishing permissions. The sections below
describe the earlier empty-lock preparation state.

Status: **BLOCKED**. `inputs.lock.json` intentionally contains no input records.
Exact official hashes have not been established. Both pack scripts preflight all
selected URLs and fail before packaging if any entry/evidence is absent. This is
an intentional failure, not a completed runtime build or license clearance.

`arm64` is a user target; Termux/CPython use `aarch64`; Android output is
`arm64-v8a`. `x86_64` is separate; npm is `all`. Node and every listed dependency,
npm, Python core, stdlib and C extensions remain required. No version was changed.

Future authorized evidence collection must populate one record per exact URL:

- `url`, `sha256` (64 lowercase hex characters from official metadata).
- `sourceUrl`, `sourceSha256` (hash of the actual official metadata bytes),
  `sourceFormat`, plus a human review date and provenance notes.
- Termux: `sourceFormat` = `termux-packages`, `indexArchitecture` = `aarch64` or
  `x86_64`, and exact `package`, `version`, `architecture` (including `all` for npm).
  The gate fetches official HTTPS `dists/stable/main/binary-*/Packages[.gz]`,
  pins its raw hash and requires matching Filename/Package/Version/Architecture/SHA256.
  Supply the official version spelling, including any epoch. Never invent URLs
  or silently substitute a new dependency version if a package has disappeared.
- Python: `sourceFormat` = `sha256sum`; an official `www.python.org/ftp/python/`
  SHA256 checksum file must explicitly identify the exact Android archive basename.
  The official 3.14.8 directory was readable on 2026-10-18 and lists Android
  archives and Sigstore sidecars, but a suitable SHA256 checksum file was not
  established. This gate does NOT validate Sigstore and must remain blocked until
  supported official evidence is found or a separately reviewed Sigstore verifier
  is implemented. A directory listing or unsigned locally invented checksum is
  not sufficient. Do not copy a hash from an unrelated source tarball.

Official metadata is retrieved over HTTPS, restricted to the supplier host;
redirects to other hosts, HTTP errors, malformed/missing/changed metadata and
hash mismatches are fatal. This is not a claim of APT GPG or Sigstore signature
verification. Preserve authenticated metadata/signature evidence in the later
review process. Moving Termux indexes may require a new reviewed lock.

Caches include the declared runtime/dependency versions and target. Every reuse
is rehashed; packages use temporary download files and atomic replacement only
after hash verification. Failed downloads never become a valid cache entry.
Extraction always starts fresh after verification. Do not run concurrent pack
processes in the same work directory. Do not use old output after any failure.

Still required: exact archives, copyright/LICENSE/NOTICE files, nested npm and
Python components, supplier source/build commits and patches, input/output hash
mapping, all ELF LOAD segment checks, clean ABI packaging and functional tests.
Existing script pruning (Python test/desktop Tk/IDE resources) is unchanged and
needs an explicit supported-function review; this change does not remove further
components. License collection is not completed by an input checksum.

No pack script, tests or downloads of runtime binaries were executed during this
repair. Full Node AND Python remains the initial-release target.

## Second-pass transaction and Gradle entry gate (2026-10-18)

Current empty lock still blocks every Cyrene Gradle entry at configuration time,
including direct package/bundle tasks and excluded-task invocations. Original Muse
does not activate this Gradle gate. Configuration cache and continuous mode are
rejected for Cyrene so they cannot bypass the configuration check. An offline
Python verifier is required (`-PcyreneRuntimePython` can select the interpreter).
This is a deliberate usability restriction until actual official hashes exist.

After separate authorization and evidence review, the sole full publication
entry is `python3 -B tools/runtime-inputs/runtime_bundle.py assemble` on Linux/WSL.
Do not execute it as part of static review. The two shell scripts are internal
stages; direct invocation without transaction paths fails. Each attempt owns a
new directory under `app/build/cyrene-runtime`, both ABIs and both runtimes.
Official metadata bytes, locked archives, source hashes and output hashes are
retained. Offline verification replays the official metadata and hashes. Source
runtime assets/jniLibs are rejected rather than silently used or deleted.

Publication renames the complete attempt to a unique generation on the same
filesystem, then atomically switches a READY state file. Existing generations
are never overwritten/deleted. PENDING, FAILED, missing state or an interrupted
assembly mutex always blocks reuse. Failed pointer publication leaves an
unselected generation; cleanup touches only the current attempt. An interrupted
mutex requires explicit operator investigation, never automatic lock stealing.
Gradle rechecks at graph readiness and runtime-consuming task starts; caching
and up-to-date reuse are disabled for those Cyrene tasks. Do not run assembly,
cleaning or editing concurrently with Gradle: local filesystem mutation is not
an authenticated/hostile-process-resistant build attestation. A hostile build
host or modified build scripts are outside this local integrity gate.

Component membership follows the existing scripts (including existing Python
pruning); no additional runtime component is removed. Exact archive availability,
full ELF/ABI validation, actual Gradle API compilation and failure-injection
acceptance remain unexecuted. No license blocker is cleared by these changes.
