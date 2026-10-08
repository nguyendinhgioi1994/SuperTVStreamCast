# Validation — 08/10/2026

## Automated

- `:androidApp:assembleDebug`: passed. APK in `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
- `:shared:testAndroidHostTest`: passed, 12 tests, 0 failures (10 new domain/repository checks + 2 template checks).
- `:shared:compileKotlinIosSimulatorArm64`: passed for current sources.
- `:shared:linkDebugFrameworkIosSimulatorArm64`: passed before the final pure-domain playlist-cap adjustment. Link reported a template bundle-ID inference warning; native platform/UI code compiled and linked successfully.
- `Info.plist`: plutil lint passed. EN/VI strings: 69 matching keys.

New tests cover private IPv4 validation, BOM/CRLF/header attributes, quoted commas/groups, unsupported stream schemes, dedupe/metadata isolation, empty and oversized playlists, 5,000-channel limit, Sony PSK/IRCC requests, disconnect, authentication/API/capability failure, redirect and response-size rejection.

## Device smoke check

Debug APK installed/launched on connected Android model `23021RAAEG`. Read-only screenshots inspected for onboarding/brand selection and settings; no AndroidRuntime error in the sampled log. Visual QA prompted fixed onboarding CTA placement, compact title/body grouping, consistent selected colors and full-width remote rows. Updated APK installed after fixes.

This is not a full hardware acceptance test. No Sony/Samsung/LG/Google TV receiver was available to validate a real pairing/session. No live stream was played in this check, no end-to-end Cast/AirPlay session was verified, and no iOS runtime UI test was performed. Android 17 LAN prompt is implemented but not exercised on an Android 17 receiver/phone test setup. Do not infer broad brand/codec/firmware compatibility from successful compilation or mocked HTTP tests.

## Release gates still open

- Vendor remote adapters and multi-model testing.
- Full receiver/casting/mirroring engine, native permission revocation and lifecycle tests.
- Persistent secure IPTV library, EPG, reliable adaptive player/track features.
- iOS runtime, font scaling/RTL/accessibility testing, remaining international locales.
- SDK/account configuration for ads, purchase/restore, analytics, release signing and store publication.

See `docs/plans/ROADMAP.md` for ownership and sequencing.

## UI v2 verification

- Final Android APK build and common/iOS simulator compilation passed; iOS framework link checked for the redesigned UI.
- Real Android Home inspected in Vietnamese. Emulator onboarding welcome → brand → goal → app completed. Remote, Mirror, empty IPTV and import dialog screenshots inspected.
- Manual import fixture on the read-only emulator: `#EXTM3U` with `https://example.com/demo.m3u8`; successful import closed the dialog and showed one channel. Favorite toggle updated the favorite count and kept the library visible. No stream was played. Fixture data is not bundled in the product.
- Emulator 320 × 640 dp at 130% font scale: library and remote reviewed; content scrolls, dock remains reachable, long dock labels ellipsize with full accessibility descriptions.
- Shared domain/repository code was unchanged in this UI pass; existing 12-test result remains the earlier logic verification. New work was verified by compilation and device interactions, not redundant implementation tests.
- Android system bars now explicitly use light icons on the dark theme. Two new original artwork assets are stored as WebP. EN/VI resources match (105 strings + 2 plurals).
- iOS runtime/UI and TV hardware acceptance remain outside this UI verification.

## Multi-brand remote, discovery and IPTV v2

Environment limits on 08/10/2026: the sandbox network policy blocks Google Maven and `dl.google.com`, so no Android SDK, AGP or Compose runtime could be downloaded. `./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest` and iOS compile/link (Linux host) were **not run** for this change.

What was verified, using a scratch Kotlin/JVM project that compiles the repository sources directly from Maven Central dependencies:

- `domain/`, `data/`, `net/` (common expect + the Android/OkHttp actual) and `ui/TvViewModel.kt` (against a minimal ViewModel stub) compile.
- All commonTest suites pass on JVM: 28 tests, 0 failures (new: 10 protocol/domain tests, 7 adapter/discovery tests with scripted sockets and Ktor MockEngine; Sony repository tests adapted to `SonyBraviaAdapter`).
- EN/VI string keys match (145 strings + 2 plurals); every `Res.string` reference resolves.

Not verified: Compose UI files (RemoteScreen, TvScaffold, PlaylistScreen, CinemaComponents, App) and iOS actuals (`LocalNetwork.ios.kt`) were reviewed manually only. Run the standard build commands from CLAUDE.md before merging. No Samsung, LG or Sony TV was available: pairing prompts, token reuse, LG unsigned-manifest registration, pointer socket, TLS pinning against real TV certificates, scan timing and app launch remain hardware gates.

## IPTV sources, EPG and usage guide

Same environment limit: Google Maven is blocked (403), so `:androidApp:assembleDebug`, `:shared:testAndroidHostTest` and iOS compile/link were **not run**.

- Scratch Kotlin 2.4.20 / Ktor 3.6.0 JVM project compiling `domain/`, `data/`, `net/` (+ Android actual), `ui/TvViewModel.kt` (ViewModel stub) and running commonTest: 38 tests, 0 failures (new `IptvSourcesTest`: 11 tests — Xtream server normalization, credential encoding/redaction, auth/status/format checks, category mapping/dedupe/cap, repository call order and rejected account, `tvg-id`/header guide URL, single-stream validation, XMLTV time offsets, guide matching by id and name with entities/CDATA/window, gzip/oversize rejection).
- A Compose Desktop scratch build of the UI also failed on androidx artifacts from Google Maven, so `PlaylistScreen.kt`, `TvScaffold.kt`, `App.kt`, the Android/iOS file pickers and new icons were reviewed manually only.
- EN/VI string resources match (204 strings + 4 plurals); every `Res.string`/`Res.plurals` reference resolves.

Not verified: real Xtream panels, real XMLTV files, the system document pickers, iOS compilation and any runtime UI.
