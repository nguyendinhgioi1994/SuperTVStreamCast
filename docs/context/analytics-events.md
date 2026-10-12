# Click events (`ec_*`)

Every user tap is sent to the MMP clients (Firebase Analytics) as `ec_<area>_<element>` through
`analytics/ClickEvent.kt` (`ClickEvent.log` / `tracked`). Names are snake case, at most 40 characters;
parameter values are lower-case enum names or short text (≤ 100 characters).

**Never sent:** IP addresses, PSK, pairing codes, IPTV passcodes or the digits typed for them, playlist
URLs/tokens, Xtream server/username/password, channel or programme titles, keyboard text, IPTV group names.

Screen views are still `screen_view`; purchase results keep their `iap_*` events (`PaywallActions.kt`,
`:monetization`). Onboarding completion stays `complete_onboarding`.

Common parameters:

| Where | Parameters added to every event |
|---|---|
| Home / Remote / Mirror / Settings (TV actions) | `tab` (home, remote, mirror, settings), `brand` (samsung, lg, sony, google, …) |
| IPTV (`ec_iptv_*` from actions) | `screen` (hub, source, library, series, programmes, passcode, guide, scanner) |
| Paywall | `source` (onboarding, home, home_banner, setting, header, iptv_limit), `variant` (one, two) |

## Navigation

| Event | Tap | Params |
|---|---|---|
| `ec_dock_home` … `ec_dock_settings` | Bottom dock tab (home, remote, mirror, iptv, settings) | |
| `ec_home_card_remote` / `_mirror` / `_iptv` | Quick-access cards on Home | |
| `ec_home_premium_banner` | Premium carousel on Home (opens the paywall) | `slide` (sports, no_ads, remote, movies) |
| `ec_header_brand` | Brand pill in the header on Home and Settings (opens Settings); Remote and Cast have no header | `brand` |

## TV connection

| Event | Tap |
|---|---|
| `ec_tv_connect_open` | Connect button on the connection card (Home or Remote) |
| `ec_tv_connect_close` | Back / outside the connection dialog |
| `ec_tv_scan` | Scan network |
| `ec_tv_pick` | A discovered TV (`device_brand`) |
| `ec_tv_connect` | Connect in the dialog (`pairing_code` = true when sending a TV code) |
| `ec_tv_disconnect` | Disconnect |
| `ec_tv_wake` | Wake TV (Wake-on-LAN) |

Results are not click events; a connection only counts after the protocol answers.

## Outcome events (`analytics/AppEvent.kt`, logged by `TvViewModel`)

Funnel steps, not taps. A success is only logged after the TV's protocol answered; the same "never sent" rules apply.

| Event | When | Params |
|---|---|---|
| `tv_connect_attempt` | A handshake starts (a valid local address was given) | `brand`, `discovered` (picked from a scan), `pairing_code` (typed TV code) |
| `tv_connect_code_required` | The TV shows a pairing code to type | same as attempt |
| `tv_connect_success` | The TV accepted the session | same + `device_brand` |
| `tv_connect_fail` | The handshake failed; `reason` is the error shown (`connection`, `pairing_code`, `control_disabled`) | same + `reason` |
| `feature_used` | First remote command the TV accepted in a connection | `feature` = remote, `action` (key, text, app, pointer), `brand` |
| `feature_used` (IPTV, logged by `IptvViewModel.started`) | The native player is really playing (Media3 `isPlaying`, AVPlayer `timeControlStatus` = playing), once per channel/episode opened; a retry of the same channel is not counted again | `feature` = iptv, `kind` (live, movie, series), `type` (playlist, file, xtream, stream) |

Not yet: `feature_used` for Mirror — it only opens the system Cast settings, so no result is known.

## Remote

| Event | Tap |
|---|---|
| `ec_remote_<key>` | A remote key: `power`, `input`, `up`, `down`, `left`, `right`, `ok`, `back`, `menu`, `home`, `volume_up`, `volume_down`, `mute`, `channel_up`, `channel_down`, `rewind`, `play`, `pause`, `stop`, `fast_forward`, `info`, `guide` (param `key`) |
| `ec_remote_number` | Keypad digit (param `key` = `num_0` … `num_9`) |
| `ec_remote_keypad_open` / `_close` | Number keypad dialog |
| `ec_remote_keyboard_open` / `_close` / `_send` | Keyboard dialog (text is not sent) |
| `ec_remote_mode_dpad` / `ec_remote_mode_touchpad` | D-pad / touchpad switch |
| `ec_remote_pointer_mode` | Pointer switch (`on`) |
| `ec_remote_touchpad_click` | Pointer click on the touchpad |
| `ec_remote_apps_load` | Load apps |
| `ec_remote_app_launch` | Launch a TV app (`app` = the app name reported by the TV) |

Touchpad drags (pointer moves) are not logged.

## Mirror and Settings

| Event | Tap |
|---|---|
| `ec_mirror_share` | Start screen sharing (Android) |
| `ec_settings_premium` | Premium card |
| `ec_settings_tv` / `ec_settings_tv_back` | TV settings row / its back button |
| `ec_settings_brand` | Brand in TV settings (`selected`) |
| `ec_settings_forget_tvs` | Forget paired TVs |
| `ec_settings_theme` | Theme (`mode` = system, light, dark) |
| `ec_settings_language` | Language row (`current`) |
| `ec_settings_rate`, `_share_app`, `_contact`, `_restore`, `_manage_subscription`, `_terms`, `_privacy`, `_share_logs`, `_clean_logs` | The Settings rows |
| `ec_settings_copy_device_id` | Version line tapped five times |

## IPTV

| Event | Tap | Params |
|---|---|---|
| `ec_iptv_sheet_sources` / `_url` / `_xtream` / `_stream` / `_guide` / `_help` | Opens that dialog | |
| `ec_iptv_add_playlist` | Add a playlist | `input` = link / content |
| `ec_iptv_add_playlist_file`, `ec_iptv_add_xtream`, `ec_iptv_add_stream` | Add from file / Xtream / single stream | |
| `ec_iptv_import_cancel` | Cancel a running import | |
| `ec_iptv_import_accept` / `_dismiss` | Playlist offered by a link or QR code | |
| `ec_iptv_qr_scanner_open`, `ec_iptv_qr_scanned`, `ec_iptv_qr_open_settings` | QR scanner, a code read, camera permission settings | |
| `ec_iptv_source_open`, `_refresh`, `_delete`, `_delete_ask`, `_details`, `_menu`, `_rename`, `_auto_refresh`, `_share`, `_share_ask`, `_qr` | Saved source actions | `type` (playlist, file, xtream, stream), `hours` |
| `ec_iptv_kind_live` / `_movie` / `_series` | Content tabs of a source | |
| `ec_iptv_group` | Group chip | `all` |
| `ec_iptv_play`, `ec_iptv_episode_play` | Play a channel / an episode | `kind` |
| `ec_iptv_favorite` | Favorite toggle | `on`, `kind` |
| `ec_iptv_hide` / `ec_iptv_unhide` | Hide / unhide a channel | `kind` |
| `ec_iptv_channel_menu` | Channel ⋯ menu | |
| `ec_iptv_library_open`, `ec_iptv_library_clear_ask`, `ec_iptv_library_clear` | Continue watching / favorites / hidden lists | `list` |
| `ec_iptv_passcode_submit`, `ec_iptv_passcode_change` | Hidden-list passcode (digits are not sent) | |
| `ec_iptv_season`, `ec_iptv_series_retry` | Series season chip / retry | |
| `ec_iptv_guide_open`, `ec_iptv_guide_import`, `ec_iptv_guide_file`, `ec_iptv_guide_view_grid` / `_list` | EPG | |
| `ec_iptv_programmes_open`, `ec_iptv_reminder_open`, `ec_iptv_reminder_set`, `ec_iptv_reminder_remove` | Programmes and reminders | `view`, `minutes` |
| `ec_iptv_player_close`, `_expand`, `_next`, `_previous`, `_fill`, `_pip`, `_fullscreen`, `_fullscreen_exit`, `_retry` | Player controls | `on` (fill) |
| `ec_iptv_back` | Back arrow on IPTV pages | |

## Cast tab

| Event | Tap | Params |
|---|---|---|
| `ec_cast_open_receivers` / `_link` / `_youtube` / `_player` | Receiver capsule, Web video, YouTube, now-casting bar | |
| `ec_cast_photos` / `_videos` / `_music` / `_iptv` | Media tiles | |
| `ec_cast_scan`, `ec_cast_pick_receiver` | Search again, a TV in the list | `kind` (`dlna`, `chromecast`) |
| `ec_cast_link`, `ec_cast_open_system`, `ec_cast_open_youtube` | Cast video, Open Cast settings, Open YouTube | |
| `ec_cast_play_pause`, `ec_cast_previous`, `ec_cast_next`, `ec_cast_volume_up` / `_down`, `ec_cast_stop` | Controller | |
| `ec_cast_mini_play`, `ec_cast_mini_stop` | Now-casting bar | |

Outcome: `feature_used` with `feature=mirror`, `media`, `receiver`, only after the TV accepted the item (see [cast.md](cast.md)).

## Entry flow

| Event | Tap | Params |
|---|---|---|
| `ec_language_select` / `_confirm` / `_back` | Language screen | `language`, `previous`, `initial` |
| `ec_onboarding_next` / `_finish` / `_back` | Onboarding buttons | `page` (1-based) |
| `ec_onboarding_brand` / `_goal` | Choices in onboarding | `selected` |
| `ec_paywall_close` | ✕ | `source`, `variant` |
| `ec_paywall_plan` | A plan (starts its purchase) | `product` |
| `ec_paywall_continue` | Main CTA | `product` |
| `ec_paywall_limited_version` | "Try limited version" | |
| `ec_paywall_restore`, `ec_paywall_terms`, `ec_paywall_privacy`, `ec_paywall_retry` | Footer links / reload plans | |

System back gestures, automatic dismissals and the shared alert dialog (`widgets/dialog/AppDialog.kt`)
are not click events.
