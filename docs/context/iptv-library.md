# IPTV library: saved sources, movies and series, favorites, hidden channels

Implemented 09/10/2026 after comparing the IPTV tab with `../iptv_kmp` feature by feature. Its behavior was studied; no code was copied, and the storage, state and UI here are this project's own. Import sources, M3U parsing and the guide parser are described in [iptv-sources.md](iptv-sources.md); what was left out is listed at the end and in `docs/plans/ROADMAP.md`.

## What the user gets

- **Sources are saved.** Every playlist URL, pasted/device playlist, Xtream account and single stream becomes a source on the IPTV hub and is still there after the app restarts. A source with the same link and login is updated in place instead of being added twice.
- **Hub** (`IptvScreen.Hub`): Continue watching and Favorites rows across all sources (12 tiles, **View all** opens the full list), then the sources. A source's menu has **Update now**, **Details** and **Delete** (confirmed; removes its favorites, hidden channels and history too).
- **Details**: type, host, item count, last update, Xtream subscription status / expiry / connections, rename, automatic update (never, 1, 3 or 7 days; default 7) and **Share**. Sharing sends the app's import link plus the plain link; for an Xtream account the link contains the login, so it is confirmed first.
- **Source screen**: Live / Movies / Series tabs when the source has more than one kind, group chips, search across all kinds, the guide banner. Movies and series show as a poster grid. A channel's menu hides it or opens its programme list.
- **Xtream movies and series**: `get_vod_streams` and `get_series` are imported with the live channels; a panel without one of them still imports the rest. A series opens a page with cover, plot, season chips and episodes (`get_series_info`, fetched when opened). Episodes play in order: when one ends the next starts.
- **Video on demand** (Xtream movies/episodes, and playlist entries recognized by `tvg-type`, an Xtream `/movie/` or `/series/` path or a video file extension) carries a **VOD** tag, resumes where it stopped and shows its progress on cards.
- **Favorites, Continue watching, Hidden**: one list each across all sources, with search and **Clear all**. They survive a re-sync of the source.
- **Hidden channels** disappear from every list. They are behind a 4-digit passcode page (create and confirm on first use, change from the Hidden list). There is no recovery and no way to turn the lock off.
- **Programme list**: per channel, upcoming programmes by local day and time with the one on air highlighted. The provider's guide loads by itself the first time a source is opened in a session.
- **Player**: the list the channel was opened from sits under the video for switching; previous/next, favorite, programme list, and on Android a full-screen button (landscape, system bars hidden; Back leaves full screen first). iPhone rotates on its own.
- **Shared links**: `tvspace://import-playlist?name=…&url=…` opens the app on the IPTV tab and asks before anything is downloaded; only the name and host are shown.

## Storage and privacy

`data/IptvStore.kt` keeps the library in an app-private directory (`iptvDirectory`): `index.json` (sources, per-channel user state, passcode digest) and one `catalog_<id>.jsonl` per source, a channel per line. Files are written to a temporary name and moved into place.

- Playlist links, Xtream logins and stream URLs (which embed the login) are in these files. This is a change from the earlier session-only library: the in-app privacy text, the Xtream dialog and the usage guide were reworded to say so.
- Android: internal storage; the manifest has `allowBackup="false"`. iOS: Application Support, flagged `NSURLIsExcludedFromBackupKey`. The files rely on the platform's storage encryption; they are not encrypted with a Keystore/Keychain key of their own.
- Nothing is logged: `IptvSource.toString()` and `XtreamLogin.toString()` hide the link and login, and only the host is ever shown in the UI.
- The passcode is stored as a salted SHA-256 digest (`passcodeHash`). It keeps channels out of sight; it does not encrypt anything.
- The guide is not stored. It is downloaded again per session (48 MB cap as before).

## Code

- `domain/IptvLibrary.kt`: `IptvSource`, `XtreamAccount`, `LibraryEntry` and the list operations (`toggleFavorite`, `watched`, `setHidden`, `progress`, `cleared`, `list`), `isRefreshDue`, `resumePosition`, `guessContentKind`, `PasscodeFlow`, import links.
- `domain/IptvSources.kt`: `XtreamApi` for movies/series (`channel(kind)`, `parseSeries`, `parseAccountInfo`, `movieUrl`, `episodeUrl`, `seriesKey`), `Episode`, `SeriesInfo`.
- `data/TvRepository.kt`: `loadXtream` (account → live, movies, series), `loadSeries`.
- `ui/iptv/`: `IptvViewModel` (state `IptvUiState`, screen stack `IptvScreen`), `IptvActions`, `IptvScreens.kt` (hub, source, library, series, programmes), `IptvPlayer.kt` (player, passcode page), `IptvDialogs.kt`, `IptvWidgets.kt`, `ImportLinks`. `TvViewModel` no longer holds IPTV state.
- `platform/StreamPlayer` takes a start position and reports position/duration every 5 s and when closed, plus the end of the video; `rememberFullscreenRequest` (Android only).
- A series is a `Channel` with `kind = SERIES` whose `url` is only an identity (`server/series/<id>`, no login); it is never handed to the player.

Tests: `commonTest/IptvLibraryTest.kt` (library lists, history bound, resume rule, refresh rule, share links, content kind, passcode flow, import links, store round trip and damaged files), `IptvSourcesTest.kt` (Xtream live + movies + series, series info layouts).

## Verification (09/10/2026)

Android debug APK, `testAndroidHostTest`, iOS simulator compile, framework link and Xcode simulator build pass. On the iOS simulator the app starts and the `tvspace://` scheme is offered to it, but the run stopped at the consent form and onboarding. **The IPTV screens were not exercised at runtime**: none of the screens above were opened, and no real playlist, Xtream panel, resume, full-screen switch or shared link was exercised. `IptvViewModel` has no unit test of its own.

## Not taken over from iptv_kmp

- Room database with paging: lists are held in memory per source (50,000-item cap), which is what this app's library already did.
- Player engine features: custom overlay controls, audio/subtitle/aspect/speed menus, mini player, picture-in-picture, Chromecast/AirPlay button, VLC fallback for raw `.ts` on iOS. The stock Media3 / AVKit controls stay.
- EPG tab with separate EPG sources, day picker, time grid, programme reminders (notifications) and the iptv-org lookup backend.
- QR share and QR scan (camera), source sync to a TV by code (needs a backend), Android TV / Apple TV apps.
- Free-tier quotas, daily watch-time limit and premium gates for hiding channels: limits wait for the real Qonversion project and pricing tests.
- One row per `group-title` group for channels listed in several groups, and the Remote Config URL blacklist / keyword map.
