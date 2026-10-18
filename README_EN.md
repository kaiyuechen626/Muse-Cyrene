# Muse Cyrene

**Derivative project, not an original implementation · development source**

Based on [Zer0Qing/Muse](https://github.com/Zer0Qing/Muse) v2.6.1. This project does not claim original authorship of upstream code. Upstream copyright, LICENSE and NOTICE are retained. Local modifications are documented in [CYRENE_MODIFICATIONS.md](CYRENE_MODIFICATIONS.md). This is not an official or endorsed upstream release.

Prepared for GitHub source hosting, not a verified APK release. Four historical binary fixtures are omitted pending provenance clarification. Six dependent tests are explicitly ignored; ZIP/7z tests remain active. Production functionality is unchanged. See [source status](SOURCE_PUBLICATION_STATUS.txt) and [fixture provenance](TEST_FIXTURE_PROVENANCE.txt).

An **unofficial modified Android application**, based on [Muse v2.6.1](https://github.com/Zer0Qing/Muse/tree/v2.6.1), commit d7ce76b9640e42afcb571fa3813c9bcf0779969c. This is not a Muse plugin or an official Muse release. No public v0.1.0 APK has been produced.

## Features and evidence

The user tested automatic reading of completed AI replies, continued reading after leaving chat, and pause/resume on the earlier personal test APK. This does not certify this source snapshot or a Release build. Enable automatic reply reading in Settings → Speech; separately enable continued reading after leaving chat. Supply your own TTS credentials and an authorized voice/reference ID. Chat providers and TTS providers are configured separately.

Use -PcyreneBuild=true to retain the existing io.zer0.muse.cyrene application ID and separate app data. The app contains no Cyrene audio pack, API key, free service entitlement or shared relay.

## Limitations and migration

Automatic reading starts after a successful complete reply in the current foreground ordinary chat, not token by token. Continued reading keeps an already-started reading session alive; it does not promise starting playback for a reply completed in the background. New replies arriving while reading or paused are skipped rather than queued. System power restrictions and audio focus can interrupt playback. Session switching, rapid replies, pauses between sentences, notification controls and network errors require regression testing. Tested pause/resume uses Fish Audio cloud audio; system TTS engines, voice conversations, groups, agents and auxiliary accessibility components are not covered by that evidence.

The upstream NDJSON backup importer can fail with SQLite foreign-key error 787 due to batched parent/child insertion order. It is not fixed here. A private backup conversion worked for one user, but is not a general migration tool. Retain the original app and backups; never upload real conversations or keys. Configure TTS credentials separately in the modified app.

## Build and release

See [BUILDING_CYRENE.md](BUILDING_CYRENE.md), [CYRENE_RELEASE_CHECKLIST.md](CYRENE_RELEASE_CHECKLIST.md), and [CYRENE_MODIFICATIONS.md](CYRENE_MODIFICATIONS.md). The old external build_muse.cmd bootstrap is not required or included. Do not use the unadapted upstream release workflow for Cyrene.

Cloudflare Workers are optional separate relay services. This source copy includes no relay code, deployment scripts, credentials or account metadata. A self-hosted endpoint placeholder is https://YOUR-WORKER.YOUR-SUBDOMAIN.workers.dev; it is not a working public service. Muse appends /tts. Service availability, fees and voice rights are governed separately by the provider.

## Licensing

LICENSE and NOTICE are retained from upstream, including GPL version 3 or later and third-party notices. New Cyrene code is GPL-3.0-or-later. Preparation/modification notice date: 2026-10-18. Cyrene v0.1.0 is an independent fork version based on upstream v2.6.1. Asset redistribution and a complete resolved dependency license inventory remain open review items; see CYRENE_LICENSE_REVIEW.md. Before distributing APKs, provide complete corresponding source and build instructions for the exact Cyrene release tag/commit, with artifact checksums. Upstream support, donation and update destinations are not Cyrene services. A Cyrene public repository/contact has not been selected.
