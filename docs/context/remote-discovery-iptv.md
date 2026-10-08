# Multi-brand remote, LAN discovery and IPTV library v2

Implemented 08/10/2026 after reviewing what leading remote/IPTV apps offer (Lean Remote, Web Video Cast, TiviMate, UHF; see roadmap). Store listings were reviewed, not the apps' code or assets.

## Remote adapters

`data/RemoteAdapter.kt` defines `RemoteAdapter` (connect → `RemoteSession(device, capabilities)`, keys, text, apps, launch, optional pointer). `createAdapter` returns an adapter for Sony, Samsung and LG only; other brands keep guidance-only behavior.

| Adapter | Transport | Connected when | Capabilities |
|---|---|---|---|
| Sony BRAVIA | HTTP `/sony/system`, IRCC SOAP, `/sony/appControl`; PSK header | `getRemoteControllerInfo` returns codes incl. `Confirm` | Keys whose IRCC name the TV reported; text (`setTextForm`); apps (`getApplicationList`/`setActiveApp`) |
| Samsung Tizen | `GET :8001/api/v2/` then WebSocket remote channel (`wss://:8002` when `TokenAuthSupport`, else `ws://:8001`) | TV sends `ms.channel.connect` after the viewer allows the request; `ms.channel.unauthorized/timeOut` fails | All mapped keys, text (`SendInputString`), installed apps / launch |
| LG webOS | SSAP WebSocket (`wss://:3001`, fallback `ws://:3000` only when TLS socket cannot open) | `registered` with a client key after on-screen approval; `error` fails | SSAP keys (power off, volume, channel, media) with acknowledged responses; pointer-socket buttons/D-pad/numbers and cursor mode when the pointer socket opens; text (IME), launch points |

Protocol messages/parsers are pure functions in `domain/RemoteProtocols.kt`. Unsupported keys are disabled in the UI (`TvUiState.can`). Remote commands run sequentially on a mutex without blocking the UI; any command failure disconnects and shows a localized error instead of assuming the TV is still connected. Samsung key presses have no protocol acknowledgement; LG SSAP and Sony IRCC/HTTP failures are detected.

Pairing tokens/client keys and PSK are memory-only for the app session. The last successfully connected TV is stored as `BRAND|host|name` (no secret) to prefill the address.

TLS: TVs use self-signed certificates. `net/LocalNetwork.kt` `pinnedLocalClient(host)` accepts TLS only for that RFC1918 host and pins the first leaf certificate for the client lifetime (trust on first use, per session; Android `X509TrustManager` + host interceptor, iOS Darwin challenge handler). Clients are closed on disconnect. There is no global TLS bypass; internet traffic uses default clients.

## Discovery

Scanning starts only from the "Find TVs on this Wi-Fi" button, after the Android 17 local-network permission request. `localIpv4Address()` (Android `NetworkInterface`, iOS `getifaddrs` on `en0`) must be RFC1918; the scan covers the /24 (253 hosts) with 48 parallel hosts and 2.5 s per-probe limits. A host is listed only if it answers a vendor probe: Samsung device API JSON with a TV type, Sony `getInterfaceInformation` with `productCategory: tv`, or an LG SSAP reply to an unregistered request. Listing is not pairing. Sony selections prefill the IP and still require the PSK; Samsung/LG selections start pairing.

## Remote UI

Power, input, number pad dialog, keyboard dialog, D-pad / touchpad modes (swipe → arrows, tap → OK; LG pointer mode moves the cursor), back/home/menu, volume and channel rockers with mute, media transport row, info/guide, and a TV apps row with refresh. Light haptic feedback on key presses.

## IPTV

`#EXTGRP` fills the group when `group-title` is absent. Library has All / Favorites / Recent tabs, group chips, search, and a player with previous/next channel zapping and favorite toggle. Recent history (max 12) and favorites remain session-only and are pruned on playlist replacement.

## Verification

See [validation](validation.md#multi-brand-remote-discovery-and-iptv-v2).
