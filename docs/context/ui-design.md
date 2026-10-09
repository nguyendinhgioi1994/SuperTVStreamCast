# TV Space UI, version 4 — Popwave

The shared Compose UI has one design system, "Popwave": a saturated pink / violet / amber identity with a night and a day palette. Direction, colour tokens and artwork were produced with Codex (GPT) on 09/10/2026 and implemented here. No domain/network behaviour was changed.

## Identity

- Primary accent hot pink, with violet, electric blue, coral, amber and green as supporting families. `TvColors.Cyan` is the primary accent (pink) — the token names are historical; `Violet` is the mirror accent, `Coral` the media accent, and `Pink`, `Amber`, `Green` were added.
- `TvColors.Gradient` is the brand gradient (violet → pink → amber). Every feature owns a gradient (`TvColors.featureGradient(feature)`) and a readable accent (`featureAccent`). Content on any gradient uses `TvColors.OnAccent`: near-black ink on the bright night stops, white on the deep day stops; all pairs pass WCAG AA.
- Bottom bar: a floating capsule. The selected tab opens into a pill filled with that feature's gradient and shows its label; other tabs are icon-only.
- Icons: drawn duotone `FeatureIcon`s (soft tinted body + solid details); `IconBubble` is a glossy gradient tile.
- Feature tiles, the IPTV card and the Premium card are filled with their gradients (`Modifier.gloss()` adds highlights); page titles use `TvColors.TitleGradient`.
- A genuinely connected TV is shown in green (border, glow, pulsing dot); never before a real handshake.
- Launcher icon: gradient tile with a white screen and play mark (`design/app_icon.svg`, Android adaptive + legacy mipmaps, iOS 1024 px).

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

- Splash: drawn brand mark that settles in with a breathing halo, name and tagline. No artificial delay.
- Home: hero artwork with a slow drift, gradient frame and badge, real connection card, gradient Remote/Mirror tiles, gradient IPTV card with an artwork thumbnail.
- Navigation: floating capsule (`FloatingDock`). One gradient pill glides between tabs while the selected slot widens and its label fades in. All of it follows one critically damped spring per tab, read only in the layout and draw phases, so a tab change does not recompose the bar.
- Remote: same controls and disabled rules; keys dip on press, OK uses the accent gradient.
- Mirror: themed artwork and the three steps drawn as a timeline; native sharing / AirPlay instructions are unchanged.
- Settings: premium card, Appearance selector, brand grid, privacy panel.
- Onboarding: gradient progress segments that fill, themed artwork, animated goal rows.
- Paywall: themed hero artwork; monetization widgets unchanged.

## Resources

- Artwork (WebP 1200×800, glossy 3D, no text, no logos): `art_{home,mirror,iptv,remote,onboarding,premium}_{dark,light}`; picked through `ui/TvArt.kt`. Sources are in `design/generated/v4/`.
- Icons are drawn vectors in `ui/TvIcons.kt` (`FeatureIcon`, `GlyphIcon`, `BrandMark`); theme glyphs SUN, MOON, AUTO and CROWN were added.
- Strings added in EN/VI: `appearance_title`, `theme_system`, `theme_light`, `theme_dark`.

## Structure

TvScaffold is the routing/screen shell. CinemaComponents owns shared layout and control components; FeatureScreens owns Home/Mirror/Settings; RemoteScreen owns the remote. Each receives callbacks/plain state.
