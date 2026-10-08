# Midnight Cinema UI, version 2

The shared Compose UI has been rebuilt around a single navy/cyan/coral/lavender design system. Typography is centralized in TvTheme; spacing, touch sizes and surfaces reuse tokens. No domain/network behavior was changed.

## Screens

- Home: original cinema art with a dark contrast scrim, a compact real connection card, paired Remote/Mirror tiles and a full-width IPTV tile.
- Navigation: an inset rounded dock with accessible tab roles, localized short labels and subtle selected-color/scale transitions. Content scrolls above it and respects system insets.
- Remote: sculpted circular D-pad with separately accessible directional keys, center OK, power/back/home/mute controls and a volume rocker. All command buttons retain disabled behavior until a real connection exists.
- Mirror: a dedicated original illustration and three numbered setup cards; native sharing action/AirPlay instructions retain their existing behavior.
- IPTV: dedicated original empty-library art, channel/favorite counts from actual state, search, segmented filters and readable channel cards. The import form moved into a separate dialog; successful imports close it, errors keep the form available.
- Onboarding: three-segment progress, fixed bottom CTA, custom brand cards and goal selection. Settings reuses the same brand cards and privacy panel.
- Player/connection dialog: rounded surfaces, consistent text and error panels; no fake channel content or fabricated active state in production.

## Structure

TvScaffold is the routing/screen shell. CinemaComponents owns shared layout and control components; FeatureScreens owns Home/Remote/Mirror/Settings/Onboarding presentation; PlaylistScreen owns library and its import form. Each receives callbacks/plain state; TvViewModel and repository stay unchanged.

Resources: art_cinema.webp (existing), art_mirror.webp and art_iptv.webp (new original generated illustrations). Product text remains in EN/VI Compose resources, 105 matching string keys plus two localized plural resources. The GlyphIcon vector renderer gives remote and list actions consistent strokes and accessible parent controls.

Phone previews include 320 × 640 and 360 × 780, populated/empty/loading/error/permission states, and a connected remote fixture used only for Compose previews.
