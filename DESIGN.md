# Midnight Cinema

TV Space combines a navy cinema palette, luminous cyan controls and coral highlights. Source tokens are in `shared/src/commonMain/kotlin/com/tuntech/supertvstreamcast/theme/TvTheme.kt`.

Use token colors, Material 3 typography, rounded surfaces and one primary CTA per task. Keep meaningful images and controls accessible; decorative art has a null description. Text belongs in Compose string resources, never in artwork. Icon source is `design/app_icon.svg`; the hero is original AI-generated artwork converted to WebP under `composeResources/drawable/art_cinema.webp`.

The first-use flow takes three screens: product value, TV brand, primary goal. Home exposes connection status ahead of remote controls. Unsupported brand control remains disabled with an honest development message. Native screen sharing never claims an active session unless the system/receiver can report one.

Previews cover onboarding, Home, empty IPTV and 320 dp phones. Before release, add populated/loading/error/permission previews, dynamic type and RTL verification, TalkBack/VoiceOver and hardware interaction checks.

## Version 2

Cinematic image scrims and layered outlined surfaces provide hierarchy without decorative noise. Coral marks IPTV, lavender marks mirroring, cyan marks remote/connection. A custom typography scale replaces the template defaults. The bottom dock remains compact with short localized labels and color/scale transitions.

Home pairs Remote/Mirror tiles beneath a compact connection row. Remote uses a circular D-pad and a separate volume rocker. Mirror has three readable setup steps with its own artwork. IPTV uses a separate import dialog and a library list; counts always come from real playlist state. See docs/context/ui-design.md for component structure and verification.
