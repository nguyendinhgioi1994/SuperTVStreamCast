# IPTV import sources, EPG and usage guide

Implemented 08/10/2026. The IPTV tab's **+** button (and the empty library) lists five sources; a **?** button opens the in-app usage guide. All content is supplied by the user; nothing is bundled.

| Source | Behavior | Limits / safety |
|---|---|---|
| Playlist URL | Single-line HTTP(S) URL is downloaded; any other input is parsed as pasted M3U (previous behavior). `url-tvg` / `x-tvg-url` in the `#EXTM3U` header is remembered as the provider guide. | 2 MB, 5,000 channels, redirects rejected, warning shown for `http://` |
| Xtream Codes API | `player_api.php` account request first; the library is built only if `user_info.auth == 1` and status is Active. Then `get_live_categories` + `get_live_streams`; stream URL `server/live/user/pass/id.m3u8` (`.ts` if HLS not allowed). Provider XMLTV (`xmltv.php`) is offered as the guide. | Login in memory for the session only, never persisted/logged; `XtreamLogin.toString()` hides credentials; 12 MB listing; first 5,000 live channels kept with a visible notice. Live TV only (no VOD/series yet). |
| From device | System document picker (Android `OpenDocument`, iOS `UIDocumentPickerViewController` as copy); bytes parsed as M3U. | Reads at most limit + 1 bytes; oversize/unreadable → error |
| Single stream | Plays one HTTP(S) URL with an optional name, without replacing the library; zapping disabled for it. | Same URL rule as playlist streams |
| EPG (XMLTV) | Provider guide, URL or file. Programmes matched to channels by `tvg-id`/`epg_channel_id`, else by normalized display name; only programmes ending after now and starting within 36 h, max 24 per channel. Channel cards show current programme + progress + minutes left; the player also shows the next programme. | 12 MB, uncompressed only (gzip detected and reported); requires a library; dropped when the library is replaced |

Code: `domain/IptvSources.kt` (Xtream URL/JSON logic, XMLTV parser, time parsing, URL encoding), `data/TvRepository.kt` (downloads), `ui/TvViewModel.kt` (`importXtream`, `importPlaylistFile`, `playStream`, `importGuide*`), `ui/PlaylistScreen.kt` (sources, dialogs, guide banner, help), `platform/rememberFilePicker`. Parsing runs on `Dispatchers.Default`. LAN-hosted servers/streams go through the local-network permission request first.

Times are shown as progress and minutes left, so no timezone formatting is needed. Tests: `commonTest/IptvSourcesTest.kt`.
