# Implemented foundation

`App.kt` owns preferences/ViewModel and lifecycle-aware state collection. `ui/TvScaffold.kt` is stateless apart from editable form/filter state; model events are callbacks. `ui/TvViewModel.kt` owns onboarding, selected brand/tab, connection state, commands, playlist import, favorites and player routes.

`domain/TvModels.kt` defines product models, strict private IPv4 validation and bounded M3U parser. `data/TvRepository.kt` downloads playlists. Remote control moved to `RemoteAdapter` implementations (Sony, Samsung, LG) — see [remote-discovery-iptv.md](remote-discovery-iptv.md). Sony `getRemoteControllerInfo` obtains actual IRCC codes, then SOAP `X_SendIRCC` sends keys. HTTP failure/JSON API error/unsupported code prevents success. Playlists download only via HTTPS URLs; redirects are rejected rather than followed implicitly; raw M3U can contain HTTP/HTTPS stream URLs. Network requests have timeouts; imports cap bytes/characters and channels. UI shows loading, error, empty/no results states.

Onboarding complete/brand/goal are local preferences (Android SharedPreferences, iOS NSUserDefaults). PSK, IRCC codes, Samsung tokens and LG client keys are held only in memory and removed on disconnect/ViewModel clear; the last connected TV's brand/address/name is saved without secrets. Library and favorites are session-only in this foundation. Replacing a playlist prunes favorites to channels in the new list.

`platform/AppPlatform.kt` holds seams for preferences, native playback, system sharing. Android VideoView provides native playback controls; iOS AVPlayerViewController provides native controls/AirPlay availability. Players pause in the background and dispose with the player screen. No bundled channel or provider account.

System screen sharing: Android opens Cast settings and reports unavailable activities; iOS explains Control Center Screen Mirroring. No active session indicator is fabricated. Direct remote adapters exist for Sony BRAVIA (IP control + PSK), Samsung Tizen and LG webOS (on-screen pairing). Other brands have explicit development messages.

UI uses original hero WebP, scalable navigation icons and original launcher assets. Existing wizard greeting files/tests remain unrelated scaffolding. No sibling code or tuntech module was copied. Current translation scope EN/VI; international release expansion tracked in the roadmap.

Transport currently allows HTTP for local TV and user-supplied HTTP media (Android cleartext enabled; iOS ATS arbitrary loads enabled). Before production, restrict transport policies where model/stream requirements allow, and move custom native mirroring permissions/services into their dedicated phase.

Android 17 (target SDK 37) declares ACCESS_LOCAL_NETWORK and requests it at connect/command/local-IP media use. Denial is localized and shown on the relevant screen. Android back closes dialogs/player first, then returns to Home; first-run onboarding back moves to the previous step. App backups are disabled on Android; no PSK is included in saved state.
