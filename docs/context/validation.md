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
- IPTV library on a device (saved sources, Xtream movies/series, resume, hidden channels, shared links), library file encryption, EPG tab, reliable adaptive player/track features.
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

## Full local build and iOS simulator launch (09/10/2026)

Run on macOS with Xcode 27 and full network access, covering the entry flow, multi-brand remote and IPTV sources changes that earlier sections could not build.

- `./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64` passed; host tests: 50 tests, 0 failures, 0 errors.
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` passed.
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug` for the iPhone 17 simulator (iOS 26.2) first failed on `checkSyntheticImportProjectIsCorrectlyIntegratedForEmbedAndSign`. Fixed by running `:shared:integrateLinkagePackage` with `XCODEPROJ_PATH`, which generated `iosApp/KotlinMultiplatformLinkedPackage/` and updated `project.pbxproj`; the build then succeeded (unsigned, `CODE_SIGNING_ALLOWED=NO`).
- The app was installed and launched on that simulator: it left Splash, showed Onboarding step 1 of 3 and the UMP consent form for Google's test publisher. The process stayed alive.

Not verified: nothing was tapped on iOS, so consent choice, the rest of onboarding, paywall, dashboard tabs, remote, pickers and playback on iOS remain untested. No signed/device build. Android runtime was not re-run in this pass. Hardware and account gates above are unchanged.

## IPTV library (09/10/2026)

Saved sources, Xtream movies/series, favorites / continue watching / hidden channels, programme list and shared links ([iptv-library.md](iptv-library.md)).

- `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64` and `:shared:linkDebugFrameworkIosSimulatorArm64` pass. New tests: `IptvLibraryTest` (9) and two rewritten Xtream tests in `IptvSourcesTest`.
- EN/VI string keys match.

- Xcode simulator build passes; on the iPhone 17 simulator the app starts and iOS offers to open a `tvspace://import-playlist` link in TV Space (scheme registered). The run stopped at the consent form and onboarding, so the IPTV tab itself was not reached. Installing on the attached Android phone was refused by the device (needs confirmation on the phone).

Not verified: the new screens, the saved library across an app restart, playback resume, Android full screen, the `tvspace://` link on either platform and real Xtream movie/series panels are untested at runtime. `IptvViewModel` has no unit test.

## Google TV Remote v2, Wake-on-LAN and secret store (09/10/2026)

Build and logic, on the working tree that also contains the IPTV library rework done the same day:

- `./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest :shared:compileKotlinIosSimulatorArm64 :shared:linkDebugFrameworkIosSimulatorArm64` passed; host tests: 73 tests, 0 failures. The Xcode simulator build of `iosApp` passed.
- New `GoogleTvTest` (9 tests): SHA-256 against published vectors, protobuf encoding/parsing, pairing message bytes, pairing secret and its check byte, remote events and key codes, magic packet, and the adapter against a scripted TLS channel (code required → mistyped code keeps the session → paired, pinned, configured, ping answered, key sent; reconnect from the stored pin; changed TV certificate is not used and pairing restarts; rejected secret never connects or pins).
- `AdapterTest`: Samsung token and LG client key are reused from the secret store by a new adapter instance; Samsung reports `wifiMac`. `TvRepositoryTest`: Sony reads `hwAddr` after authenticating.
- `DerCertificateHostTest` (JVM): the hand-built self-signed certificate is accepted by the JDK `CertificateFactory`, verifies against its key, and its RSA key is read back by `Der.certificateKey`.

iOS runtime, iPhone 17 simulator (iOS 26.2), with a temporary launch-time probe in a private copy of the app (not in the repository) against a local `openssl s_server -Verify 1` on the Mac's LAN address:

- Keychain secret store: put, overwrite, read back (non-ASCII value) and clear behaved as expected. This needs the normally signed simulator build; a build made with `CODE_SIGNING_ALLOWED=NO` has no Keychain and reads everything as absent.
- `openTvTls`: the RSA identity was created in the Keychain, the handshake completed, the server logged the client certificate `CN = atvremote`, `peerCertificate` hashed to the server certificate's SHA-256, the first write was logged by the server, four reads returned the server's data, a second connection presented the same certificate, and a closed port failed with an exception.
- This run found and fixed a crash: reading Network.framework's default message context global from Kotlin/Native aborts the app, so each write now creates its own context.
- `sendBroadcast` returned without error on the simulator; whether the datagram reached the network was not observed.

Not verified:

- No Google TV / Android TV was available. The protocol constants and message layout follow the public reverse-engineered description of Android TV Remote Service v2 and are exercised only against the scripted channel: real pairing, the on-TV code, certificate acceptance on port 6466, ping cadence and key behaviour are hardware gates.
- Android runtime of the Keystore-backed pieces (AES secret store, RSA identity, TLS 1.2 client-certificate handshake, UDP broadcast): the two local emulators did not finish booting headless, so this code is compiled and reviewed only.
- Wake-on-LAN against a real TV (does the TV wake, is the reported address the one that listens), and broadcast sending on a physical iPhone, which needs Apple's multicast entitlement.
- The connection dialog's code field, wake button and Settings → Forget paired TVs were not exercised in a running UI.
