# Super TV Cast UI, version 4 — Popwave

The shared Compose UI has one design system, "Popwave": a saturated pink / violet / amber identity with a night and a day palette. Direction, colour tokens and artwork were produced with Codex (GPT) on 09/10/2026 and implemented here. No domain/network behaviour was changed.

## Identity

- Primary accent hot pink, with violet, electric blue, coral, amber and green as supporting families. `TvColors.Cyan` is the primary accent (pink) — the token names are historical; `Violet` is the mirror accent, `Coral` the media accent, and `Pink`, `Amber`, `Green` were added.
- `TvColors.Gradient` is the brand gradient (violet → pink → amber). Every feature owns a gradient (`TvColors.featureGradient(feature)`) and a readable accent (`featureAccent`). Content on any gradient uses `TvColors.OnAccent`: near-black ink on the bright night stops, white on the deep day stops; all pairs pass WCAG AA.
- Bottom bar: a floating capsule. The selected tab opens into a pill filled with that feature's gradient and shows its label; other tabs are icon-only.
- Icons: drawn duotone `FeatureIcon`s (soft tinted body + solid details); `IconBubble` is a glossy gradient tile.
- Feature tiles, the IPTV card and the Premium card are filled with their gradients (`Modifier.gloss()` adds highlights); page titles use `TvColors.TitleGradient`.
- Cards are flat (no shadow) and share two styles from `CinemaComponents.kt`, radius `RadiusLg`: `GradientCard` (tappable tiles: Home quick access/IPTV, Cast media tiles, IPTV source choices; white copy, `ArrowChip`) and `SurfaceCard` (info/list cards like the connection card: Cast receiver capsule, IPTV sources, channels, episodes, help). Shadows remain only on the primary CTA, the dock, remote keys and the player play button.
- A genuinely connected TV is shown in green (border, glow, pulsing dot); never before a real handshake.
- Launcher icon: violet TV with a white play button, a landscape phone mirroring it and a remote, on an amber→coral gradient. Source render `design/app_icon_source.png` (AI-generated raster, no SVG); its gradient is extended so the group fits the adaptive safe zone. Android: one WebP background layer per density (`drawable-*/ic_launcher_background.webp`), empty foreground, vector `ic_launcher_monochrome` for themed icons, legacy mipmaps. iOS: opaque `app-icon-1024.png` (= `design/app_icon.png`). Notification small icon `ic_stat_notification`: solid white TV with play cut-out, same glyph as the monochrome layer.

## Theme

- `theme/TvTheme.kt`: `TvPalette` holds one complete colour set; `DarkPalette` and `LightPalette` share the same roles. `TvColors` keeps the token names used across the UI (`Background`, `Surface`, `Raised`, `Outline`, `Cyan`, `Blue`, `Coral`, `Violet`, `Text`, `Muted`, `Error`, gradients) and reads them from the active palette, which is snapshot state: composables and draw lambdas redraw when the theme changes.
- `TvTheme(mode)` resolves System / Light / Dark, builds the Material 3 scheme from the palette, and provides `LocalTvThemeMode` and `LocalReducedMotion`.
- The choice is made in Settings → Appearance (`ThemeSelector`), stored by `ThemeRepository` (DataStore key `themeMode`, default System) and read in `MainApp`. `MainNavHost` gives the system bars light icons at night and dark icons in daylight.
- Text pairs meet WCAG AA in both palettes (day: text 16.2:1, muted 5.9:1, primary 5.5:1 on the background; night: 17.6, 10.1, 6.0).

## Motion

`theme/TvMotion.kt` defines easings and durations (micro 120, control 180, card 240, screen 360, modal 420, splash 700 ms) and the selection/press springs. `ui/Motion.kt` has the building blocks:

- `Reveal(index)`: staggered fade + rise when a tab opens (Home, Mirror).
- `Modifier.pressScale`: buttons, cards and remote keys dip while held.
- `rememberPulse` / `rememberSweep`: looping values for the hero drift, splash halo, connected status dot, D-pad ring and CTA shimmer.
- `Modifier.tvBackdrop`: the page wash — theme gradient, slowly drifting cyan/violet/coral light and, at night, a few stars.
- `tabTransition`: tabs cross-fade with a short rise.

With the system reduced-motion setting on (`platform/ReducedMotion`), loops stay still, reveals appear at once and transitions become a 100 ms fade. Glow, the pulsing dot and the breathing D-pad ring appear only when `state.connected` is true after a real handshake.

Navigation: Splash / Onboarding / Dashboard cross-fade in 360 ms; the Paywall slides up in 420 ms with the emphasized easing.

## Screens

- Splash: the app icon (`composeResources/drawable/app_icon.webp`, 384 px render of `design/app_icon.png`) inside `AppIconLoader` — a spinning violet→pink→amber ring with a breathing halo — then name and tagline, settling in once. No artificial delay.
- Blocking loading (`ui/app_loading/AppLoadingContent`, ad loading / purchase): fades in as a 60 % black dialog-style scrim over the current screen, white text; `AppIconLoader` scales up, title and description rise in, three brand-colour dots bounce in sequence. The close button shows the seconds left inside a draining ring and morphs into the close glyph at 0. Reduced motion keeps a static full ring and drops the dots.
- Home: for free users the hero is `PremiumCarousel` (`ui/PremiumCarousel.kt`): four premium slides (live football from your IPTV playlists, no ads, phone remote, movie night) on a 300dp card that advances every 4.2s, wraps endlessly, pauses while swiped and stays put under reduced motion; PREMIUM badge, "Unlock Premium" CTA pill and page dots are fixed on top, and a tap anywhere opens the paywall (`IAP_SOURCE_HOME_BANNER`). Premium users keep the hero artwork with a slow drift, gradient frame and badge. Below it: real connection card, gradient Remote/Mirror tiles, gradient IPTV card with an artwork thumbnail.
- Navigation: floating capsule (`FloatingDock`). One gradient pill glides between tabs while the selected slot widens and its label fades in. All of it follows one critically damped spring per tab, read only in the layout and draw phases, so a tab change does not recompose the bar.
- Remote: same controls and disabled rules; keys dip on press, OK uses the accent gradient.
  - Two remote bodies drawn from the palette's `remote*` tokens: night is the black remote; day is the **Pearl** remote — white ceramic body with a blush tint, silver-pearl D-pad ring, violet-grey cavities (`remoteShadow`), white ink on a pink-red power key (`remoteOnPower`). `remotePearl` (day only, empty at night) adds a faint iridescent pink→lavender→blue→amber sheen to the crown, the D-pad ring and the OK key, and the pearl body casts a soft layered shadow so it separates from the pale page. D-pad arrows are embossed at night and debossed by day. The remote ink and muted text stay readable on the pearl body (#2B2036 / #6A5F75 on #F5F0F7).
- Cast (was Mirror, 11/10/2026): title, Screen Mirroring card with `art_cast_*` and inline guide steps, receiver capsule, six gradient media tiles, now-casting bar and controller sheet, following `design/cast-v5/CAST_DESIGN.md` (Codex/GPT). See [cast.md](cast.md).
- Settings: premium card, one compact TV row (brand initial, brand · connected device or "No TV connected"), Appearance selector, then grouped rows (`ui/SettingsGroups.kt`, same set as AirPrint / RetroOne) built by `DashboardScreen` as `SettingsLinks`; a null callback hides its row:
  - General → Language (opens `Screen.Language`; EN/VI via `:common` `LocalAppLocale`).
  - Help & support → Rate (store page), Share with friends (store URL; iOS needs `CFAppStoreId` in Info.plist), Contact us (`mailto:` Remote Config `SUPPORT_EMAIL`, default `contact@mmtstudio.site`, hidden while empty), Restore purchase (always shown; Qonversion restore + success/fail dialog), plus Manage subscription while premium (store subscriptions page).
  - Legal → Terms / Privacy from Remote Config `TERM_OF_SERVICE_URL` / `PRIVACY_POLICY_URL`, hidden while empty (defaults `https://mmtstudio.site/terms` / `/privacy`; also used by the paywall footer).
  - Troubleshooting → Send app logs (`AppLogger.sendLogsInEmail`, zip of the rolling log), Clear app logs. Footer: version name (code); five quick taps on it copy `PlatformUtil.getDeviceId` (Android ID / iOS identifierForVendor) through `platform/rememberCopyText` and show a confirmation dialog.
- Settings → TV settings (`TvSettingsContent`, a page inside the Settings tab held by `TvScaffold`; back or leaving the tab closes it): brand grid and the privacy panel with Forget paired TVs.
- Onboarding: gradient progress segments that fill, themed artwork, animated goal rows.
- Paywall: themed hero artwork; monetization widgets unchanged.

## Resources

- Artwork (WebP 1200×800, glossy 3D, no text, no logos): `art_{home,mirror,iptv,remote,onboarding,premium}_{dark,light}`; picked through `ui/TvArt.kt`. Sources are in `design/generated/v4/`. Premium banner art `art_promo_{sports,noads,remote,movies}` is one night rendering for both themes (subject on the right, calm left for the white copy), generated with Codex (GPT image tool) on 11/10/2026 with plain unbranded kits; sources in `design/generated/promo/`.
- Icons are drawn vectors in `ui/TvIcons.kt` (`FeatureIcon`, `GlyphIcon`, `BrandMark`); theme glyphs SUN, MOON, AUTO and CROWN were added.
- Strings added in EN/VI: `appearance_title`, `theme_system`, `theme_light`, `theme_dark`.

## Structure

TvScaffold is the routing/screen shell. CinemaComponents owns shared layout and control components; FeatureScreens owns Home/Mirror/Settings; RemoteScreen owns the remote. Each receives callbacks/plain state.
