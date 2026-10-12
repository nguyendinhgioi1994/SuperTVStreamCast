# Cast tab: media casting to DLNA and Chromecast

Implemented 11/10/2026 (code; compiled for Android and iOS, unit/host tests pass, UI checked on an Android 16 emulator without a TV). **Not yet tested with a real receiver.** Replaces the old Mirror tab, which only had instructions. The enum is still `Feature.MIRROR`; the tab is labelled "Cast" in every locale.

Market reference: top cast apps (TV Cast, Replica, Web Video Cast, AirScreen-type apps) offer photos / videos / music / web video to DLNA/UPnP TVs and Chromecast plus a screen-mirroring entry. This tab covers the media part for real and keeps screen mirroring as the system feature.

## What the user can do

- **Cast to** capsule: opens the receiver sheet and starts a search (8 s): DLNA/UPnP renderers on both platforms, Chromecast / Google TV on Android. Empty result shows tips (same Wi-Fi, DLNA/Screen Share on, TV awake, iOS Local Network permission).
- **Photos** (multi-select, up to 50): slideshow, 5 s per photo, auto-advance can be paused; previous/next.
- **Videos** (up to 20) and **Music** (mp3/m4a/aac/flac/wav/ogg, up to 50): queue; the next item starts when the TV reports the current one stopped.
- **Web video**: a direct http(s) media link (MP4/M3U8/MP3…). No web page extraction, no DRM.
- **IPTV** tile jumps to the IPTV tab; **YouTube** opens a guide and the YouTube app (its own Cast button does the casting; this app never reports it as casting).
- Order: title, **Screen Mirroring** hero, Cast-to capsule, media grid.
- **Screen Mirroring** hero: the three guide steps are shown inline (no sheet). Android adds "Open Cast settings" (the existing system action); iOS explains Control Center AirPlay. The app never shows mirroring as active.
- **Now casting** bar in the tab and the full controller sheet: artwork (the picked photo itself), title, TV, status, timeline + seek, ±10 s, previous/next, TV volume ±5 (only when the TV reports a volume), Stop. Closing the controller does not stop playback.

Media can be picked before a TV is chosen: the receiver sheet opens and the queue is sent as soon as a TV is selected.

## Truth rules

- "Casting" (and the green state) only after the receiver accepted `SetAVTransportURI` + `Play` (DLNA) or the Cast `load` result was success (Chromecast). A refused load shows "The TV couldn't play this".
- `feature_used` with `feature=mirror`, `media=<photo|video|audio>`, `receiver=<dlna|chromecast>` is logged only then. No URL, file name or IP is logged.
- Status (state, position, duration, volume) comes from polling the receiver every second; controls are disabled when the receiver did not report what they need.

## Structure

- `domain/cast/CastModels.kt`: receiver/media/status models, MIME from extension, web link validation, clock format.
- `domain/cast/Upnp.kt`: SSDP `LOCATION` (private addresses only), device description parsing (AVTransport + RenderingControl control URLs), SOAP envelopes, DIDL-Lite metadata (double-escaped inside SOAP), reply parsing, time format.
- `domain/cast/HttpRange.kt`: request parsing, `Range` handling, response headers incl. `contentFeatures.dlna.org` / `transferMode.dlna.org`.
- `data/cast/MediaServer.kt`: raw TCP server (ktor-network) bound to the Wi-Fi address on a random port, started only when a local file is cast. Each file gets a random path token; only shared files are served; GET/HEAD with byte ranges. `stop()` (Stop casting) forgets every file and closes the socket.
- `data/cast/CastSources.kt`: `ReceiverSource` / `CastController`; `DlnaSource` (SSDP via `LanBrowser.ssdp`, description fetch, SOAP control).
- `data/cast/ReceiverSources.kt` (expect) → Android `ChromecastSource.android.kt` (MediaRouter discovery, session through `CastContext`, `RemoteMediaClient`, Default Media Receiver — same as the IPTV Cast button); iOS returns none.
- `ui/cast/CastViewModel.kt` (StateFlow), `CastTab.kt` (Koin VM + FileKit pickers), `CastScreen.kt` (tab + sheets), `CastPlayer.kt` (now-casting bar, controller), `CastIcons.kt` (Canvas glyphs).
- Strings: `composeResources/values*/strings_cast.xml` (separate file, prefix `cast_`).
- Design: `design/cast-v5/CAST_DESIGN.md` + mockup and hero art, produced with Codex (GPT) on 11/10/2026; app art `art_cast_{dark,light}.webp`.

Tests: `CastDomainTest`, `CastViewModelTest` (commonTest), `MediaServerHostTest` (real socket: whole file, range, HEAD, 404, 416, stop).

## Known limits

- Not run against a real TV yet: DLNA renderers differ (some need specific `protocolInfo`, many refuse HEIC/MKV/HLS); Chromecast not tried with a device.
- iOS: SSDP to the multicast group on a real iPhone needs Apple's multicast entitlement (same as remote discovery); without it no DLNA TV is found. No AirPlay media route yet — iOS users can use AirPlay from the IPTV player; photos picked on iPhone may be HEIC, which many TVs cannot show (no transcoding).
- The phone must stay awake with the app open while a local file plays; there is no foreground service on Android or background mode on iOS for the server.
- Mini "now casting" bar lives in the Cast tab only (not above the dock in other tabs as the design suggests).
- Strings for languages other than EN/VI were machine-translated with Codex and need native review.
