# IPTV import sources, EPG, player and usage guide

Implemented 08/10/2026, reworked 09/10/2026 after comparing behavior with `../iptv_kmp` (its parser/player ideas were studied; no code was copied). The IPTV tab's **+** button (and the empty library) lists four ways to add a source; the guide is added from a source's screen; a **?** button opens the in-app usage guide. Since 09/10/2026 every source is saved on the device — see [iptv-library.md](iptv-library.md), which also covers Xtream movies/series, favorites, hidden channels and the player list. All content is supplied by the user; nothing is bundled.

| Source | Behavior | Limits / safety |
|---|---|---|
| Playlist URL | Single-line HTTP(S) URL is downloaded and parsed line by line while it streams; any other input is parsed as pasted M3U. Redirects are followed (also HTTPS → HTTP, as provider load balancers do). A link that is one HLS stream (`#EXT-X-…`) is saved and played as a single stream instead. A provider `get.php` link whose login the server accepts is added as an Xtream account. `url-tvg` / `x-tvg-url` / `tvg-url` in the header is remembered as the provider guide. | 64 MB download, 50,000 channels (extra channels are dropped with a visible notice, the import does not fail), warning shown for `http://` |
| Xtream Codes API | `player_api.php` account request first; the library is built only if `user_info.auth == 1` and status is Active. Then categories + streams for live, movies and series (each array element decoded on its own while streaming); stream URL `server/live/user/pass/id.m3u8` (`.ts` if HLS not allowed); `stream_icon` becomes the logo. Pasting a full `get.php?username=…&password=…` link into the server field fills all three fields. Provider XMLTV (`xmltv.php`) is loaded as the guide when the account is opened. | Login saved in app-private storage with the source, never logged; `XtreamLogin.toString()` hides credentials; 96 MB per listing; first 50,000 items per kind. |
| From device | System document picker (Android `OpenDocument`, iOS `UIDocumentPickerViewController` as copy); bytes parsed as M3U. | Reads at most limit + 1 bytes (64 MB); oversize/unreadable → error |
| Single stream | Saves one HTTP(S) URL with an optional name as its own source and plays it; zapping disabled for it. | Same URL rule as playlist streams |
| EPG (XMLTV) | Provider guide, URL or file, plain or gzip (`.xml.gz`, concatenated members included). Programmes matched to channels by `tvg-id`/`epg_channel_id`, else by normalized display name; only programmes ending after now and starting within 36 h, max 24 per channel. Channel cards show current programme + progress + minutes left; the player also shows the next programme. | 48 MB as downloaded/stored, 400 MB inflated (read in chunks, never held whole); needs an open source with live channels; kept for the session only |

## M3U parsing

`domain/IptvPlaylist.kt` `PlaylistParser` is fed one line at a time (`LineSplitter` cuts streamed bytes on `\n`). It accepts tags in any case, a missing `#EXTM3U` first line (an `#EXTINF` is enough), `"`, `'` or unquoted attributes, `#EXTGRP`, `tvg-logo`/`logo`, `tvg-name` as title fallback, and per-channel request headers from `#EXTVLCOPT:http-user-agent` / `http-referrer`, `user-agent="…"` attributes or a `url|User-Agent=…&Referer=…` suffix. Only HTTP(S) streams are kept and duplicates by URL are dropped. HTML/JSON bodies and binary data (a line over 256 KB) are rejected as "not a playlist".

## Import flow

Imports run off the main thread, show the number of channels read so far, and can be cancelled (dialog dismiss button becomes **Cancel**; the library is left unchanged). Errors distinguish "cannot download" (`UiError.DOWNLOAD`: unreachable server or HTTP error status) from an unusable body (`PLAYLIST`, `XTREAM`, `GUIDE`) and a rejected Xtream account (`XTREAM_AUTH`).

## Player

- Android: Media3 ExoPlayer + `PlayerView` (HLS, MPEG-TS, progressive), cross-protocol redirects, decoder fallback, audio focus, screen kept on. A URL without a recognizable container is retried once as HLS; a live stream that falls behind the window is resumed; one silent retry before the error is shown. Pauses on `ON_STOP`.
- iOS: `AVPlayerViewController` (HLS/MP4) with the playback audio session so sound works with the ring switch off. Raw `.ts` streams are not playable by AVPlayer.
- Playlist-requested `User-Agent` / `Referer` are sent on both platforms; otherwise the platform default user agent is used.
- The player screen has **Try again** after a playback error, previous/next channel and favorite. Held in landscape the picture fills the screen (the activity handles rotation itself, so the stream is not restarted).
- Channel logos load with Coil (`coil-network-ktor3`); the play glyph stays when there is no logo or it fails to load.

Code: `domain/IptvPlaylist.kt` (M3U, line/UTF-8 chunk helpers), `domain/IptvSources.kt` (Xtream URL/JSON logic, streaming XMLTV reader, time parsing, URL encoding), `data/TvRepository.kt` (streamed downloads, `parseGuideBytes`), `data/Gzip*.kt` (expect/actual inflate: `GZIPInputStream`, zlib), `ui/TvViewModel.kt` (`importXtream`, `importPlaylistFile`, `playStream`, `importGuide*`, `cancelImport`), `ui/PlaylistScreen.kt` (sources, dialogs, guide banner, help), `platform/StreamPlayer`, `platform/rememberFilePicker`. LAN-hosted servers/streams go through the local-network permission request first.

Times are shown as progress and minutes left, so no timezone formatting is needed. Tests: `commonTest/IptvSourcesTest.kt`, `TvDomainTest.kt`, `TvRepositoryTest.kt`.

## Verification (09/10/2026)

Compile/tests: Android debug APK, `testAndroidHostTest`, iOS simulator compile and framework link. A one-off JVM run against public data (not part of the suite): the iptv-org index playlist (11,200 channels, ~2.5 MB) imported in about 3.5 s; a 6.6 MB gzip guide (76 MB of XML) downloaded and matched in under 2 s; an HLS link, an HTML page and a redirecting link were classified correctly. Not verified on a device: playback of real provider streams on Android/iOS, Xtream against a real panel, the document picker, logos, rotation.

## Saved library

The library, favorites, recents and hidden channels are saved on the device since 09/10/2026; only the guide is per session. See [iptv-library.md](iptv-library.md) for where it is stored and what that means for logins.
