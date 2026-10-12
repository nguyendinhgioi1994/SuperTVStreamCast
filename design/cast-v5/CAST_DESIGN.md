# Cast v5 — Popwave, on the big screen

Design handoff for Super TV Cast · Android + iOS · Compose Multiplatform.
This is a design-only package. Existing source files are read-only; implementation belongs to the engineering agent. All handoff assets live in this directory.

## 1. Direction and source of truth

Make Cast feel like a personal cinema console: a receiver capsule first, a cinematic Screen Mirroring invitation, six tactile media tiles, and a small persistent playback console. The premium effect comes from rounded silhouettes, restrained reflections, a single luminous hero and a clear type hierarchy. The media labels remain on calm, opaque surfaces; the icons carry the saturated color. Avoid six competing full-card gradients and repetitive instructions on the landing screen.

Read for context: `docs/context/ui-design.md`, `theme/TvTheme.kt`, `ui/CinemaComponents.kt`, `ui/FeatureScreens.kt`, `ui/TvIcons.kt`; also `theme/TvMotion.kt` and the scaffold inset contract. The existing graph was inspected read-only; its FeatureIcon / MIRROR nodes are limited, so current Kotlin files govern the handoff. No graph updates or source edits are part of this work.

Reuse `Heading`, `CinemaPanel`, `IconBubble` construction, `Modifier.gloss`, `pressScale`, `Reveal`, `tvBackdrop` and `FloatingDock` styling. The user-facing name becomes **Cast** everywhere this feature appears, including dock, Home shortcut and accessibility names. Until engineering renames the enum, `Feature.MIRROR` is the Cast feature color/route alias; it does not imply an active mirroring session.

The four wireframes and numeric rules below are authoritative. The generated mockup is a visual reference; engineering must implement real text, vectors, dynamic layout and semantics, not rasterize the screenshot.

### Product truth rules

1. A discovered receiver is **available**, not connected. A remembered receiver is **selected**, not connected. Green check, green outline and connection pulse appear only after the receiver confirms a usable session. A remote-control TV connection from another tab does not establish a media-casting session.
2. The hero always opens a **system guide**. Never show “Mirroring”, an active switch, a live timer or “Stop mirroring” in this app based on opening system settings. System mirroring is independent of the app media session.
3. “Casting” appears only after receiver playback/photo-display acknowledgement. Before that use Connecting, Preparing or Starting. A local thumbnail is not proof that the TV is displaying it.
4. Expose media controls according to capabilities supplied by engineering. Do not invent duration, position, volume, seeking, playlist support or a success state.
5. Web video accepts a direct media URL, not arbitrary webpage extraction. IPTV navigates to the IPTV tab. YouTube opens a guide and the YouTube app; it is not a YouTube downloader or a claim that any chosen DLNA TV can receive YouTube.

## 2. Layout foundation and token map

All measurements are **dp**; typography sizes/line heights are **sp**. Sizes are minimums unless explicitly decorative. Logical reference width is 390 dp, with 350 dp content (`390 − 2 × TvDimens.Space`). Respect status-bar, navigation-bar, cutout and IME insets once, at the shell.

| Rule | Value |
|---|---|
| Content width | `min(availableWidth − 40, TvDimens.ContentMaxWidth = 720)`; centered |
| Screen horizontal inset | `TvDimens.Space = 20` |
| Spacing system | 6, 10, 16, 20, 24, 32 (`SpaceXs` through `SpaceXl`) plus 4 / 8 / 12 where specified |
| Minimum tap target | 48 × 48; standard icon control `TvDimens.IconButton = 52`; primary CTA `TvDimens.Touch = 56` |
| Card radii | Hero 30 (`RadiusXl`); media/receiver 24 (`RadiusLg`); sheet 30 top; mini 22; buttons 18 (`RadiusMd`) |
| Borders | Decorative 1; hero gradient frame 1.5; focused interactive perimeter 2 |
| Content bottom clearance | Measured dock + mini stack height + 16, including safe area once |
| Narrow device | Preserve 20 inset; 2 grid columns at normal font scale; never shrink icons or touch targets |
| Large text | Grow cards; at font scale ≥ 1.3 or tile width < 150, use a 1-column list of min-height 100 cards, icon left and copy right |
| Wide device | At available content width ≥ 600 and font scale < 1.3, use 3 columns; otherwise 2; header/hero remain full width |
| Short/landscape device | Entire page scrolls; sheets use full usable height and scroll; controller artwork can reduce to 160 high |

### Existing colors to use exactly

`TvColors.Cyan` is historically named and is **pink**, not cyan. Do not introduce teal as a new brand primary.

| Token | Night | Day | Cast use |
|---|---|---|---|
| `Background` | #160F2E | #FFF7FB | Page, safe areas |
| `Surface` | #24163F | #FFFFFF | Opaque receiver, cards, sheets, hero copy bed |
| `Raised` | #342052 | #FFF0F7 | Controller mini, secondary buttons, badges |
| `Outline` | #5D4779 | #E7CADD | Decorative panel edges only |
| `Text` | #FFF8FC | #26152F | Primary copy/control ink |
| `Muted` | #CBB9D9 | #6B5B76 | Secondary copy, inactive dock icons |
| `OnAccent` | #09030F | #FFFFFF | All text and primary glyphs on saturated gradient fills, at full alpha |
| `Violet` | #A78BFF | #5A35C8 | Cast accent, selected states |
| `Blue` | #5B9BFF | #0057D8 | Cast second stop |
| `Pink` | #FF4D9D | #D0186A | Supporting photos / artwork glow |
| `Amber` | #FFB000 | #9A5A00 | Music / warm highlights |
| `Coral` | #FF6B4A | #C8371F | Videos / IPTV |
| `Green` | #27D980 | #007A4D | Confirmed connection only |
| `Error` | #FF6B7A | #D9233F | Error icon, border and stop on `Surface` |

**One proposed token:** `CastControlEdge` night **#9177B1**, day **#8A6C98**. Use for an input outline, slider inactive track and keyboard/switch-control focus ring that must remain visible. Existing `Outline` stays a decorative edge; it does not pass 3:1 everywhere. This is a proposal in the handoff, not a source modification. Token can later become a shared strong-outline role.

### Gradient stops and effects

Unless specified otherwise, linear gradients run top-left `(0,0)` → bottom-right `(width,height)`; stop positions are normalized. Do not use readable day accent values in place of feature stops where the token values differ.

| Surface | Stops / treatment |
|---|---|
| Page | `TvColors.BackgroundGradient`: night 0 #2A1250, .5 `Background`, 1 `Background`; day 0 #FFF7FB, .5 `Background`, 1 #F3EEFF. Use existing `tvBackdrop` wash, not an extra animated layer |
| Title | `TvColors.TitleGradient`: 0 `Violet`, .5 `Pink`, 1 (`Amber` night / `Coral` day) |
| Cast hero frame, receiver icon, Web icon, selected dock, playback primary | `featureStops(Feature.MIRROR)`: 0 #A78BFF, 1 #5B9BFF night; 0 #5A35C8, 1 #0057D8 day |
| Photos icon | `featureStops(HOME)`: 0 #FF4D9D, 1 #5B9BFF night; 0 #D0186A, 1 #0057D8 day |
| Videos / IPTV icon | `featureStops(IPTV)`: 0 #FF6B4A, 1 #FF4D9D night; 0 #C8371F, 1 #C9005A day |
| Music icon | `PremiumGradient`: 0 #FFB000, 1 #A78BFF night; 0 #9A5A00, 1 #5A35C8 day |
| YouTube guide icon | `featureStops(REMOTE)`: 0 #FFB000, 1 #FF4D9D night; 0 #9A5A00, 1 #C9005A day; generic play glyph, no YouTube logo |
| Card/body surface | `SurfaceGradient`: night 0 #2E1A4E, 1 `Surface`; day 0 `Surface`, 1 `Raised`. Under text reserve a solid `Surface` copy region, as specified below |
| Hero image transition | Vertical overlay: 0 `Surface` alpha 0; .48 alpha 0; .72 alpha .35; 1 alpha 1. Final copy bed is **opaque `Surface`**, independent of the photo |
| Sheet scrim | #000000 alpha .56 night / .32 day; sheet content always opaque |
| Connected receiver | `Surface` fill; 1dp `Green` border; static outer green halo `glow(Green, center, radius=width*.65, strength=.35)`, only with confirmed session |
| Progress | Solid `Violet` active track; `CastControlEdge` inactive track; do not put meaningful mini progress directly on imagery |

Gloss is decorative. For icon tiles reuse `Modifier.gloss()` (white .16 circle at .95w/−.1h, radius .45w; white .08 circle at .05w/1.1h, radius .3w). Keep all glyph details `OnAccent` at alpha 1. Put essential icon paths in the middle 60% of the tile, away from bright highlights. Panel sheen is a 1dp top inset white .10 night / violet .06 day stroke; leave the text region opaque. Shadows: hero 18dp violet/pink, media icons 8dp their accent, media panels 4dp neutral, mini/dock 16/22dp pink-violet at low opacity. Render soft fallback shadows on both platforms; no backdrop blur is required. “Glass” means layered highlights over opaque colors so AA contrast is deterministic.

## 3. Cast tab, top to bottom

Use a `LazyColumn` (or the existing page scroll owner) and a shell overlay for mini + dock. There must be **one** vertical scroll owner. Do not nest a scrolling `LazyVerticalGrid`; emit rows of tiles or non-scrolling adaptive grid content. Receiver is the first content, before the title. No separate app header/premium button is added to this tab.

### 3.1 Receiver bar

Top margin 8 after status inset. Full width, min-height **64**, radius **24**, `Surface`, 1dp decorative `Outline`. Row padding horizontal **12**, vertical **10**, gap **10**. Left **40 × 40** glossy Cast tile, radius 14, cast glyph 24. Center is weighted: “Cast to” (`labelMedium`, `Muted`), then receiver name / “Choose a TV” (`titleMedium`, `Text`). Name max 1 line ellipsis visually; accessible label contains full name. Right protocol badge min 38 × 24, horizontal padding 8, radius 8, `Raised`, `labelMedium`/`Muted`; then down chevron 18. Entire bar is a single button; badge and chevron are decorative, not nested buttons.

At width < 360 or large text, move protocol under the name, allow bar to grow to 80+. Never squeeze the full receiver name into a 48dp icon slot. Accessible receiver state is always spoken. No receiver: omit protocol. Selected but unconfirmed: violet cast glyph, no green. Connecting: 18dp indeterminate arc replaces the right chevron, with visible status under name; bar remains tappable to view/cancel. Connected: green check 16 beside name, green perimeter; casting adds state text, not more saturation. Offline remembered selection: show name with “Not connected”; not a green dot.

### 3.2 Page heading

16 below receiver. `Heading` title “Cast”, `headlineLarge` **30/36 Bold**, `TitleGradient`. Subtitle “Your favorites. A bigger screen.” `bodyLarge` **15/23**, `Muted`, 6 below title. Keep both inside a column that grows with translated strings. Reserve no crown button here.

### 3.3 Screen Mirroring hero

16 below heading. Full width, radius **30**, min-height **268**, gradient perimeter **1.5**, shadow **18**. A single tappable card with no nested CTA. Top artwork viewport is **176 high**, clipped by outer card; use matching dark/light artwork, `ContentScale.Crop`, center alignment. The image is decorative and must have null description. At 390 width the image viewport is 350 × 176; vertical crop retains the phone/TV central band. If crop loses either device, use `ContentScale.Fit` in the same viewport with theme `Surface` behind it; legibility of both devices wins over filling the frame. On tablets grow artwork to 220 high.

Badge at top-left x16/y12: “System guide”, `labelMedium`, text `OnAccent`, Cast feature gradient, radius100, inset10/5 (about 26 high). Badge is decorative. Card lower **92 minimum** is an opaque `Surface` text bed, with 16 horizontal / 14 vertical padding. Title `titleLarge` **20/26** “Screen Mirroring”; helper `bodyMedium` **13/20** “Open system Cast / AirPlay”, 4 gap. Right arrow 18 in a 32 decorative circular `Raised` chip; copy column reserves 44dp for it. On iOS helper may become “Open the AirPlay guide”; on Android “Open the Cast guide”. The cross-platform helper is valid for concept mockups only.

At large text the copy bed grows; title wraps naturally. The hero never substitutes a connection error or becomes a live session controller. Tapping opens the guide described in §7; Android guide offers system Cast as its primary action when available, iOS guide explains Control Center. Do not gate opening the guide on media receiver selection.

### 3.4 Media section

20 below hero. “Cast your media” `titleLarge` **20/26**. Grid begins 12 below heading. At 390 width two columns each **169**, gap **12**; row gap **12**. Order is Photos, Videos / Music, Web video / IPTV, YouTube. Three-column order remains row-major.

Each tile full-surface tap, radius **24**, min-height **128**, `SurfaceGradient` + thin decorative outline. Padding **12**. Top row: **40 × 40** gradient icon tile radius14, duotone glyph24; right arrow16 centered in a decorative 24 circle, `Raised`. After **8**, label `titleMedium` **16/22**, then **2**, helper `bodySmall` **12/18** `Muted`; bottom min12. Copy region is solid `Surface` (a surface patch can begin at y56 and share the outer clip). Photos and Videos can use a small glossy media corner motif, but no embedded thumbnail collage or arbitrary recent-media count.

| Tile | Helper | Action |
|---|---|---|
| Photos | Slideshow | Multi-select native photo picker → slideshow preflight → Cast slideshow |
| Videos | From your phone | Native video picker → compact review → Cast video |
| Music | Your tracks | Native audio/file picker → compact review → Cast music |
| Web video | Paste a video link | Direct-link entry sheet (§6) |
| IPTV | Live channels | Navigate to existing IPTV tab; no implied auto-play |
| YouTube | Open app & cast | Guide with Open YouTube action (§7) |

All six are usable without a receiver. For local media, collect selection first, then ask for a receiver on the Cast action. Preserve the selection through the chooser; dismissal returns to review. Never disable the whole grid to force discovery. IPTV and guides always work independently. During an existing session, preflight requires an explicit Replace casting action before sending new content; switching receiver requires the switch dialog (§5).

### 3.5 Small footer help

16 below grid, a full-width min-height48 button “Can’t find your TV?” with Help glyph20, `Violet` ink, no filled container. Opens receiver sheet expanded to help. This is the only footer string on the landing page. Page end has measured clearance so the last tile and help can scroll completely above mini/dock.

### 3.6 Persistent lower overlay

Reuse the floating dock. At normal font scale its body remains **68** high (54 item height + 7 × 2 padding), horizontal inset **16**, radius **34**, plus device bottom safe area. Cast selected pill uses the existing Cast/MIRROR feature gradient with `OnAccent` icon + label. Other four entries remain Home / Remote / IPTV / Settings icon-only visually but fully named for accessibility. Preserve existing proportions only when every slot is at least48 wide: for narrower widths, allocate four idle slots48 each and give all remaining inner width to the selected slot. At320dp viewport the inner dock width is274, giving idle48 and selected82. At large type, grow item height to80+ and stack selected icon above its label; never keep the current width formula if it produces undersized targets. Measure/wrap the selected label and grow height as needed; inactive items remain icon-only.

Mini bar is described in §8, **12** above the dock body, centered at content width. One shell-owned stack computes its real height. `LocalDockInset` currently accounts for the dock; engineering extends the inset by mini height + 12 only while mini exists. Do not add that inset a second time in Cast content. Modal sheets cover and temporarily hide this overlay; dismissing a sheet restores it. Mini persists across all dashboard tabs while the app has a media session, independent of Cast tab scroll.

### Cast tab wireframe (scroll content; overlay at bottom)

```text
┌────────────────────────────────────────┐
│             system status              │
│  ╭──────────────────────────────────╮  │
│  │ [cast] Cast to      [DLNA]    v  │  │
│  │        Living Room TV  ✓        │  │
│  ╰──────────────────────────────────╯  │
│  Cast                                  │
│  Your favorites. A bigger screen.      │
│  ╭──────────────────────────────────╮  │
│  │ [System guide]                   │  │
│  │  glossy PHONE ~~~> big TV         │  │
│  │──────────────────────────────────│  │
│  │ Screen Mirroring             (>) │  │
│  │ Open system Cast / AirPlay        │  │
│  ╰──────────────────────────────────╯  │
│  Cast your media                       │
│  ╭────────────────╮ ╭───────────────╮  │
│  │ [photo]     >  │ │ [video]    >  │  │
│  │ Photos         │ │ Videos        │  │
│  │ Slideshow      │ │ From your...  │  │
│  ╰────────────────╯ ╰───────────────╯  │
│  │ Music          │ │ Web video     │  │
│  │ IPTV           │ │ YouTube       │  │
│  [ ? Can’t find your TV? ]             │
│       ↑ content scrolls clear ↑        │
│  ╭──────────────────────────────────╮  │
│  │ [art] Summer...       [Ⅱ] [■]    │  │
│  │       Casting · Living Room TV   │  │
│  ╰━━━━━━━━━━━━──────────────────────╯  │
│ ╭────────────────────────────────────╮ │
│ │ home remote [cast Cast] iptv gears │ │
│ ╰────────────────────────────────────╯ │
│             bottom safe area           │
└────────────────────────────────────────┘
```

## 4. Receiver selection sheet

Use a modal sheet with top corners30, opaque `Surface`, max-width720 centered. Initial height is content-driven between **400** and **min(usableHeight × .80, 680)**; short screens use full usable height. Drag to expand to full usable height. Handle32 × 4, radius2, `CastControlEdge`, top10, bottom10; it is decorative. Visible dismiss control52 × 52 is the alternative to drag. Header horizontal20: title “Choose a TV” `headlineSmall` **23/29**, close24 in right52 target; supporting copy `bodyMedium` under title. Header and footer pinned; receiver/help content scrolls.

Search-status panel below header16: full width min-height64 radius18, `Raised`, padding12. Left cast-scanner32; right title `titleSmall` and helper `bodySmall`. Scanning has lavender expanding arcs, never green. If results arrive keep a compact **40** high searching row above list; do not remove stable rows every scan tick. Use provider completion/timeouts, not a fake timed empty state. After 12 seconds without results the UI may expose help while still searching; “No TVs found” only after the discovery state reports a completed attempt.

Receiver rows: min-height**88**, outer horizontal20; radius18, `Surface`; vertical separation8. Left tile44 radius14 with protocol glyph24; center name `titleMedium`, max2 lines, then protocol badge + availability label `labelMedium`; right48 × 48 decorative state area. Full row is one selectable button, no extra Connect button. Row content padding12, gap12. Names are exact discovered names, never inferred from brand. Distinct endpoints/types can remain distinct rows (same TV may expose DLNA and Chromecast); always display the protocol and accessible full name/type to disambiguate. Keep order stable within a scan: current session first, then discovered rows in first-seen order. No IP addresses in the default UI.

Protocol badges are literal **DLNA / UPnP**, **Chromecast** (Android), **AirPlay** (iOS). Badge uses `Raised` and `Muted`, padding8/4, radius8, noninteractive. Wrap to its own line at narrow widths; never force names and badges on one line. Only providers delivered by the platform are shown. For AirPlay routes owned by the OS, the design must use an actual native route picker rather than a fabricated custom list; custom sheet may offer “Choose with AirPlay” as a 56dp button, and update selection only from real OS feedback. Native picker typography/strings are OS-owned.

Selected but idle: 1dp `Violet` outline + check glyph, state “Selected”. Connected: 1dp `Green` outline + green check, state “Connected”. Connecting: static selected outline, spinner in right area, “Connecting…” beneath name; other new selections temporarily disabled until cancel/result. Current-casting row: “Casting” and green check, but selecting it simply closes the chooser.

Footer with20 inset / safe-area padding: rescan56 min-height primary button (Cast gradient), then48 text-button “Connection help”. While scanning, rescan becomes disabled “Searching…” (still48+); Close remains available. Connection attempt exposes secondary “Cancel connection”56. Cancelling returns to last confirmed state, not a disconnected success indicator.

### Empty/help content

No giant illustration. Center a **72** glossy TV/search tile with two quiet violet rings. 16 below: “No TVs found” `titleLarge`;8 below body `bodyMedium`. Below16, `CinemaPanel` with3 compact tip rows: Wi-Fi glyph24 and same Wi-Fi tip; TV glyph24 and DLNA / Screen Share tip; refresh glyph24 and keep TV awake tip. Padding16, row gaps12, text `bodyMedium`; all words can wrap. Platform-specific tip for iOS Local Network permission appears only when applicable. Permission denied/no Wi-Fi replace generic empty copy with their precise state and an Open settings action when available. Avoid telling every user to install a receiver app.

### Receiver sheet wireframe

```text
┌────────────────────────────────────────┐
│               page scrim               │
│ ╭────────────────────────────────────╮ │
│ │                ━━                  │ │
│ │ Choose a TV                    [×] │ │
│ │ TVs available on your network.     │ │
│ │ ╭────────────────────────────────╮ │ │
│ │ │ ))) Searching for TVs…          │ │ │
│ │ │     Keep your TV switched on.   │ │ │
│ │ ╰────────────────────────────────╯ │ │
│ │ [TV] Living Room TV           [✓] │ │
│ │      [DLNA / UPnP] Connected      │ │
│ │ [TV] Bedroom TV               [>] │ │
│ │      [Chromecast] Available       │ │
│ │ ─── alternate completed-empty ─── │ │
│ │             [TV ?]                │ │
│ │          No TVs found             │ │
│ │ Same Wi-Fi • DLNA on • TV awake   │ │
│ │                                  │ │
│ │ [          Search again         ] │ │
│ │ [        Connection help        ] │ │
│ ╰────────────────────────────────────╯ │
└────────────────────────────────────────┘
```

## 5. Selection, pickers and review

Native photo/video/music pickers retain OS styling, permissions and native string localization. The app does not recreate platform permission dialog copy. Launch permission only from the tapped picker action. If the platform cannot pick audio directly, use a document picker filtered to supported audio, with the same Music entry. Native-picker cancellation returns silently to Cast; no error toast.

After selection, show a content-driven modal **review sheet** with top radius30,20 padding, close52, min-height360. Thumbnail/preview full width, aspect16:9 for photos/video (max200 high), music cover140 square centered; radius24. Preserve photo aspect inside the preview with `ContentScale.Fit`/`Raised` letterbox. If no art, use the media gradient icon centered on `Raised`. Title `titleLarge`, metadata `bodyMedium`, then receiver selection row min64 (same bar) and56 primary Cast action. Review sheet scrolls with large text/keyboard.

Photos: multi-select, preserve picker selection order, show up to3 overlapping 64-square thumbnails (radius12, each12 offset), total count copy, and a slideshow interval selector. Labeled “Time per photo” with3 equal min48 × 48 option buttons “3 seconds”, “5 seconds”, “10 seconds”; default5. Allow wrapping/stacking at large text. Selected option is Cast gradient + `OnAccent`, unselected `Raised` + `Text`; selection announced. No automatic slideshow starts on picker dismissal. Photo Play/Pause governs app slide scheduling, not an unsupported remote video pause command.

Video/music: show exact filename/title, real duration if known, file type optional; no fake duration or size. One item by default; if engineering supplies an actual playlist, show real count and controller next/previous. Cast action with no receiver opens chooser; confirmed selection returns to review, and the user’s existing Cast intent may continue once (no second picker). During preparation show spinner + “Preparing media…”; stop duplicate submission. Media unsupported → preserve review with inline error and Pick another file button.

Switch receiver while casting: centered dialog radius24,20 padding, title `titleLarge`, body `bodyMedium`, full-width56 “Switch TV” and48 “Keep current TV”. Explain that this ends the current cast. Replace media uses the same dialog structure with “Replace casting” / “Keep current cast”. No dialog is needed for first selection, rescan, dismiss, or Stop casting. These dialogs are product flow controls, not implementation approval requests.

## 6. Web video link sheet

Top radius30,20 padding, handle + close52. Title “Cast a video link” `headlineSmall`;8 below helper `bodyMedium`. Outlined text field min**64**, radius18, solid `Surface`, 1dp `CastControlEdge`; focused2dp `Violet`. Label “Video link”, placeholder `https://example.com/video.mp4` (`Muted`); URL is user input, untransformed. Multiline max4 lines, wrap long URLs; keyboard URI with Done. Separate48 text action “Paste link” (never read clipboard automatically).12 below field, type badges “MP4” and “M3U8” in `Raised`, generic info line; show real validation errors under field with Error icon + `Text`, not only a red outline.

Receiver row64 follows16 below. Primary “Cast video” min56 is enabled when a nonblank syntactically usable HTTP(S) URL exists; choosing receiver is deferred if needed. “Checking link…” replaces action during validation. URLs without .mp4/.m3u8 extension may be accepted if resolved as a supported direct media resource; do not use extension-only rejection. Website/login/DRM/unsupported media error uses direct-link explanatory copy, no automatic extraction. Keep user URL and chosen TV after failure. IME padding + sheet scrolling ensure field, error and CTA remain reachable. For an unknown-duration live M3U8 stream, controller uses Live capability variant (§8), not a pretend seek timeline.

## 7. System mirroring and YouTube guide sheets

Both use the same modest guide template: content-driven sheet, top radius30, close52, padding20, leading glossy icon56/radius18, title `headlineSmall`, helper `bodyMedium`, 3 numbered steps in `CinemaPanel`. Step circles28 are decorative, `Raised`/`Violet`; text `titleSmall` + `bodyMedium`, gap4; row gaps16. Buttons56, secondary Done48; whole sheet scrolls. No connected check, preview timer or mini bar is created by these guides.

**Android mirroring:** title Screen Mirroring; explain using phone/TV system feature and that support varies. Steps: same Wi-Fi; open system Cast/Smart View/Wireless display; choose compatible TV and follow system prompt. Button “Open Cast settings” only if available. If unavailable, show fallback instructions and “Done”; do not render a dead button. Optional Chromecast paragraph explains Google Home screen-cast route with button “Open Google Home” only when a real open action is available. System Cast support and Google Home screen casting are separate paths; a DLNA media receiver is not assumed to support either.

**iOS mirroring:** same Wi-Fi; open Control Center; Screen Mirroring → TV, enter on-TV code if requested. Done closes the guide. No “Start AirPlay” button implying the app can switch system screen mirroring on. Native AirPlay media route selection belongs in the receiver flow, not this guide. Apple’s current steps support this sequence: [Apple AirPlay guide](https://support.apple.com/en-au/102661). Android Google Home path is based on [Google screen casting guide](https://support.google.com/chromecast/answer/6059461?hl=en). Our Android system-label examples are conditional guidance, not a guarantee of a vendor’s menu names.

**YouTube:** title “YouTube on your TV”; helper clarifies playback controls remain in YouTube. Steps: same Wi-Fi; open YouTube and choose a video; tap Cast and choose a compatible TV. Supplement: Share opens sharing options; an ordinary YouTube page link is not a direct MP4/M3U8 cast source. Primary “Open YouTube”; launch failure keeps guide and shows “YouTube couldn’t be opened.” with Try again/Done. Do not mark this app Casting from external-app launch. The same-network/app Cast flow is supported by [Google Cast app guide](https://support.google.com/googlecast/answer/3006709?hl=en).

## 8. Now casting controller and mini bar

### 8.1 Full-screen controller sheet

Use a full-height modal destination with sheet behavior: opaque `Background`, top corners30 when safe-area space allows, max content width720 centered, status/navigation insets respected, no dock. Back, down-chevron and swipe-down dismiss the UI while **playback continues**. Provide a visible52 × 52 “Minimize player” control. Gesture dismissal is a convenience, never the only route out. Sheet can scroll when content exceeds usable height; no sheet-drag gesture may swallow a horizontal slider gesture. The header remains accessible while scrolled.

Top to bottom, horizontal20:

1. **Header**, min-height56: left52 down-chevron, weighted title “Now casting” `titleLarge`, right52 receiver button showing Cast glyph24. The receiver button opens chooser over the controller and uses the switching flow. During unconfirmed launch the title is “Starting cast…”; failed launch “Cast interrupted”. No duplicated system status bar.
2. **Artwork**,16 below header. Video/photos: full width aspect16:9, max-height300, radius24, `ContentScale.Fit` over `Raised`. Music: centered square **min(contentWidth, 240)**, radius28; at short viewport reduce to160. Shadow16 subtle violet. No thumbnail → matching gradient icon64 on `Raised`, same footprint. Decorative artwork has null semantics because title describes it. Photos show current photo, not a fictional streaming-video preview.
3. **Title and receiver**,20 below art: media title `headlineSmall` **23/29**, max2 lines then ellipsis, accessible full value. Receiver row8 below: Cast glyph18 + receiver name `bodyMedium` `Muted`, max2 lines. Status chip8 below if necessary: Playing / Paused / Buffering / Starting / Interrupted / Live. Active text is `Text`, tiny green dot + label only for confirmed remote activity. Chip is informational, no touch target required.
4. **Timeline**,16 below metadata: seek interaction slot full width min-height48; track4, active `Violet`, inactive `CastControlEdge`, thumb16 `Violet` with 2dp `Surface` ring. Thumb hit region48. Times row `labelLarge` with tabular figures, elapsed left / duration right,6 below track. Prefer `m:ss` or `h:mm:ss`; duration unknown is “Duration unavailable”, no fabricated `0:00`. Only show elapsed if it is known. Scrub tooltip min48 × 28, `Raised`, `Text`, radius10; time is a preview until release/acknowledgement. Restore last confirmed position on failed seek, show inline command error.
5. **Transport**,16 below times: center group, **52 previous · 52 −10 · 72 play/pause · 52 +10 · 52 next**, gaps6. Total304 fits 350 width. Main button circle72 Cast gradient + `OnAccent` glyph32, shadow12; others circle52 `Raised`, `Text` glyph24. Primary playback target changes only after confirmed playback state; while command is pending show a small progress arc and disable repeat commands, without moving button positions. First/last playlist boundaries disable previous/next visibly and semantically, no wrapping by default. On very narrow widths (< 340 content) keep play centered with ±10 on first row and previous/next on second; no target below48.
6. **Volume**,24 below transport: `titleSmall` label “TV volume”. Row8 below, min-height52, minus52 and plus52, speaker glyph20 and percentage between them (`titleMedium`, `Text`); gaps12. Use actual 0–100 value, clamp steps to5 percentage points, and render the received value. If unknown, show “Level unavailable”; controls can remain enabled only if backend supports relative adjustment. Label is “TV volume”, not phone volume. If unsupported, preserve a quiet min52 info row “Adjust volume on your TV”; do not show active fake buttons. Photos normally use this informational row; show real controls only if the receiver supports them.
7. **Stop**,24 below volume: full-width min56 outlined button radius18, opaque `Surface`, 1.5dp `Error`, Stop glyph20 + “Stop casting” `labelLarge` `Error`. Full-alpha `Error` on `Surface` meets normal-text contrast in both themes. Never use day `Error` small text on `Raised`. Pending: “Stopping…” and spinner, no instant removal; Stop acknowledgement removes controller + mini. Failure leaves the session visible with an inline error. Stop acts immediately without a confirmation dialog.

Minimum bottom24 + navigation inset. No autoplay thumbnails, app upsells, ads, fake waveform or mirrored system preview in this controller.

### 8.2 Media/capability variants

| Media / capability | Timeline | Main button | ±10 | Previous / next | Volume |
|---|---|---|---|---|---|
| Seekable video/music, single | Real seek + times | Play/pause if supported | Enabled if seeking supported | Disabled; explanation Single item | Real receiver capability |
| Actual playlist | Real seek + times | Play/pause | Capability driven | Actual neighboring items | Real receiver capability |
| Photo slideshow | Replace seek with segmented photo-count progress, min48 informational slot; “Photo {index} of {count}”; interval chip “Every {seconds} seconds” | Pause/resume slideshow scheduling | Omit both, preserving centered 3-control group previous52/play72/next52 with16 gaps | Enabled for supplied slideshow; boundary disabled | Capability driven / TV info row |
| Live stream, no DVR | Live chip; omit seek and duration, same vertical region occupied by “Live stream” helper | Show only if remote pause capability exists | Disabled or omitted with one clear Live explanation | Actual playlist only | Real receiver capability |
| Duration/position unknown | “Duration unavailable” / “Position unavailable”; no invented values | Capability driven | Disable until seekable | Actual playlist only | Capability driven |
| Remote transport unsupported | Keep art/title/receiver and Stop; disabled transport row plus “Playback controls aren’t available for this TV” | Disabled | Disabled | Slideshow navigation may still work through sending next photo | Capability driven |

Disabled controls use `Muted` at alpha .45 for icons and `Raised` fill, remain48+ if visually present, set `disabled()` semantics, and do not receive focus as actionable elements. Adjacent explanatory text is full-alpha `Muted`. If capabilities are unknown during Connecting/Starting, disable controls instead of guessing. Photo timing is app scheduling, not a remote duration; do not animate a false seek thumb over photos. Never replace a failed command with an optimistic green check.

### Controller wireframe (seekable playlist)

```text
┌────────────────────────────────────────┐
│             system status              │
│ [v]          Now casting         [cast]│
│  ╭──────────────────────────────────╮  │
│  │                                  │  │
│  │     photo / video thumbnail      │  │
│  │       or square music art        │  │
│  │                                  │  │
│  ╰──────────────────────────────────╯  │
│  Summer by the sea                     │
│  [cast] Living Room TV                 │
│  ● Playing                             │
│  ━━━━━━━━━━━●────────────────────────  │
│  01:24                          04:32  │
│                                        │
│   [|<] [↶10]    ( Ⅱ )    [10↷] [>|]   │
│                                        │
│  TV volume                             │
│  [−]            🔊 45%            [+]  │
│                                        │
│  ╭──────────────────────────────────╮  │
│  │         ■ Stop casting           │  │
│  ╰──────────────────────────────────╯  │
│             bottom safe area           │
└────────────────────────────────────────┘
```

### 8.3 Persistent mini bar

Visible only for an app-managed pending/active/paused/buffering/interrupted media session; never for a remembered TV, connected-but-idle receiver, opened guide or external YouTube/system mirroring. Also hide while fullscreen controller or another modal is on top. Keep across dashboard tabs and foreground restoration when engineering supplies a real session.

Full content width, min-height**68**, radius**22**, opaque `Raised`, 1dp `Outline`, 2dp `Violet` left accent strip, shadow16. Inner padding8, thumbnail**44 × 44**, radius12, gap10. Left opening region comprises thumbnail + weighted copy and is min48 high; it is a sibling of the control buttons, not a parent clickable containing nested buttons. Title `titleSmall` **14/20** 1 line ellipsis, state/receiver `bodySmall` **12/18** 1 line ellipsis. Controls are **48 × 48** each: play/pause + stop, glyph22, gap0; both independently focused/named. Paused shows Play; casting confirmed playing shows Pause. Starting/connecting uses spinner instead of play; Stop remains available if cancellable. Buffering state preserves last confirmed playback glyph with a spinner only when pending a command.

Bottom decorative track **2** high inset12 from left/right,8 from bottom, `CastControlEdge` inactive, `Violet` active. Use only confirmed position; slideshow uses real photo-count progress. For live/unknown progress omit the track and use a static status label. Mini track has no touch action; open full controller to seek. If all media transport is unsupported replace the play slot with a down/up-controller glyph, named “Open player”; Stop remains. An interrupted session replaces play with Retry, same48 target, when retry is available; opening region shows “Cast interrupted”. Never show “Casting” while lost.

At width ≤ 320 or font scale ≥1.3, grow to **88+**, allow subtitle2 lines; button targets remain48. If text would leave a <48 opening region, put both controls on a second trailing row. Shell measures actual height and increases content clearance accordingly.

### Mini bar wireframe

```text
          <------- content width ------->
╭────────────────────────────────────────╮
│▏[44 art] Summer by the sea   [Ⅱ48][■48]│
│▏         Casting · Living Room TV      │
│  ━━━━━━━━━━━━━───────────────────────  │
╰────────────────────────────────────────╯
                 12dp gap
╭──────────── floating dock ─────────────╮
```

## 9. States and error placement

Separate **discovery**, **receiver session**, **media session**, and **system-guide visibility**. Do not derive one from another. UI accepts callbacks/plain state, following existing shared Compose architecture; network/protocol implementation is outside scope.

| State | Landing receiver / sheet | Controller / mini | Action / truth |
|---|---|---|---|
| Idle, no selection | Choose a TV; no protocol/check | Hidden | Grid usable; receiver tap begins/displays discovery |
| Searching | Choose a TV or remembered name; sheet scanner + Searching for TVs | Hidden unless an independent real session exists | Rescan disabled, dismiss/help available; no percentage |
| Completed empty | Neutral receiver; No TVs found + help tips | Hidden | Search again, help, settings when precise permission/network issue |
| Available/selected | Receiver name + actual protocol; Selected label, violet check only | Hidden | Selected does not mean a working receiver session |
| Connecting | Receiver name + Connecting; spinner, no green | Pending mini only if Cast intent exists; Preparing/Connecting title | Cancel connection available; block duplicate connect |
| Connected idle | Receiver name + green check / Connected | Hidden | Only a usable receiver session enables confirmed connection decoration |
| Preparing/starting media | Connected receiver may stay green if session survives | Starting cast / Preparing media; transport disabled | Do not call Casting yet; allow cancellation when backend can cancel |
| Casting / displaying photo | Receiver confirmed, Casting text | Art/title and actual playback; green live indicator only with acknowledgement | Normal controls per capability |
| Paused | Receiver stays connected | Paused; Play/resume, fixed progress | Photo pause means slideshow paused; current photo may stay on TV |
| Buffering | Receiver may remain connected | Buffering; last confirmed times, no automatic fake advance | No retry loop in UI; state supplied by backend |
| Connection failure | Error banner and no green | No active controller for failed initial connection; interrupted mini only if an existing cast was lost | Retry / Choose another TV; retain selected media |
| Media/codec/link failure | Receiver may remain green if still connected | Inline error; attempted title retained, no Playing status | Pick another file / Edit link / Retry as appropriate |
| Remote command failure | Confirmed receiver unchanged | Last confirmed playback/volume/seek restored + inline error | Try again; never mutate unrelated session state |
| Receiver lost | Name + Not connected; remove green immediately | Interrupted, frozen last confirmed progress; Retry/Stop cleanup | Never silently choose another discovered TV |
| Stopping | State retained until acknowledgement | Stopping…; transport disabled | Remove session UI only after stop/cleanup result; stop failure is visible |
| Finished/stop acknowledged | Connected idle if session persists | Controller closes, mini hides | Brief Cast ended snackbar; show no phantom replay bar |

**Error component:** min-height72, radius18, opaque `Surface`, 1dp `Error` at .65, padding12, glyph24, content gap10. Title `titleSmall` `Text`, body `bodyMedium` `Muted`. Actions in a separate row, each min48 and wrap at narrow widths, `Violet` text. Landing error appears12 below receiver; sheet errors belong inside the initiating sheet; controller command errors appear16 above Stop. Show one local error owner rather than three duplicated banners. Inline errors persist until resolved/dismissed, announce politely once, and never block Stop or Close. A snackbar can announce success/cancel; actionable errors are not fleeting snackbars.

Permission/no Wi-Fi use precise copy, an Info/Wi-Fi glyph and context-aware Open settings button. If no settings-launch capability, keep instructions and Done. Never fabricate SSID, TV name, model, scan progress or support guarantee.

## 10. Motion, feedback and accessibility

Motion uses actual `TvMotion` constants, with names/casing exactly as implemented.

| Interaction | Duration / easing / range |
|---|---|
| Tab entry | `Reveal(0..3)` groups: receiver+heading, hero, section, grid; `TvMotion.Screen = 360`, `Emphasized`, stagger `Stagger = 55`, y12→0 and alpha0→1. Do not stagger every tile or replay on every scan result |
| Press | Existing `pressScale`; tile/receiver .98, hero .985, buttons .96 using `TvMotion.press()` (damping .58, stiffness700); release follows same spring |
| Selection changes | `TvMotion.selection()` (damping .72, stiffness520); subtle 0→1 outline fill; receiver rows do not bounce or change position |
| Button glyph/command state | `Control = 180`, `Standard`; fade/crossfade at fixed size, no rotating play triangle |
| Error/help reveal | `Card = 240`, `Standard`, alpha + y8→0; content remeasures, no hard-coded spacer |
| Sheet enter | `Modal = 420`, `Emphasized`, bottom translation + scrim fade |
| Sheet exit | `Card = 240`, `Exit`; interactive dismiss tracks drag before settle |
| Mini enter/exit | `Card = 240`, `Standard` enter y12→0 / `Micro = 120`, `Exit` fade out; inset updates with same height animation |
| Artwork theme switch | `Screen = 360`, `Standard` crossfade; do not replace artwork with a white flash |
| Scanner | Proposed repeat period1800ms linear: 3 outward arcs stagger200ms, radius8→22, alpha .55→0; stays inside32 scanner region. No percentages or completing ring without backend result |
| Busy spinner |1200ms linear repeat, 240° arc, 18dp,1.8dp `Violet`; “Searching/Connecting/Starting” visible alongside it |
| Confirmed connection dot | Existing `rememberPulse(1800)`; dot opacity .7→1, no expanding layout; only while confirmed, static green perimeter |
| Hero | Static artwork by default; optional once-only 1.00→1.015 drift over6000ms `Standard` after first entry, no parallax or recurrent orbit |
| Timeline | Match real updates with `Micro = 120`, `Standard` only when playing; no animation while paused/scrubbing/lost; app photo scheduling is explicit |
| Dock | Preserve existing critically damped dock spring (damping1, stiffness260), not `selection()`; selected Cast pill uses existing animated slot logic |

`LocalReducedMotion=true`: use100ms fades for state/sheet/theme/mini transitions, no translation/scale/spring, no stagger, hero static, scanner static cast glyph + visible Searching copy, busy state static progress glyph + label, connected dot static. No shimmer loops on casting actions or discovery. Decorative shadows/gloss may remain. User cancels immediately; animation never delays a callback.

Accessibility: every action target ≥48 in layout (not an overlapping invisible expansion); receiver/media cards are one `Role.Button`; receiver rows use selectable semantics with selected and state description; dock `Role.Tab`; interval options `Role.RadioButton`. Art/gloss/arrows within an already named card have no separate semantics. Focus order follows top-to-bottom content, then mini opening region, mini controls, dock. Modal focus is trapped within its own content; restore focus to the opening item after dismissal. Slider exposes position/duration and increment/decrement actions, not just a draggable graphic. Provide separate ±10 controls as accessible seek alternatives. Long receiver/media names can wrap on controller and sheets and are read fully even when visual landing label ellipsizes. Read file/TV names as user data, not localization templates.

Use polite live announcements for discovery completion/connected/starting/cast failure; do not announce every scan frame or timeline second. Media state changes are announced once. Support VoiceOver/TalkBack, keyboard and switch input. Fullscreen controller/minibar dismissal never implicitly stops playback. Content remains reachable at200% type scaling; translations can wrap rather than being compressed or uppercased (Vietnamese diacritics must be retained).

### Contrast evidence and implementation constraints

Calculated with WCAG sRGB luminance from the source hex values and proposed outline. Full-alpha text/controls use the exact opaque pair below. Normal text target4.5:1 and meaningful control graphics3:1 follow [WCAG 2.2 contrast requirements](https://www.w3.org/TR/wcag/#contrast-minimum) and [non-text contrast](https://www.w3.org/TR/wcag/#non-text-contrast). This app additionally chooses48dp targets everywhere; do not label48dp as WCAG’s minimum target-size rule.

| Pair | Night contrast | Day contrast |
|---|---:|---:|
| `Text` / `Surface` |15.92:1 |17.06:1 |
| `Muted` / `Surface` |9.10:1 |6.21:1 |
| `Muted` / `Raised` |7.79:1 |5.63:1 |
| `OnAccent` / Cast violet stop |7.54:1 |7.56:1 |
| `OnAccent` / Cast blue stop |7.35:1 |6.26:1 |
| `OnAccent` / weakest media gradient stop (tested endpoints) |6.59:1 |5.22:1 |
| `CastControlEdge` / `Raised` |3.71:1 |4.07:1 |
| `CastControlEdge` / `Surface` |4.34:1 |4.49:1 |
| `Green` / `Raised` |7.68:1 |4.90:1 |

Endpoint checks alone do not certify a rendered gradient. Validate interpolated gradient colors plus actual gloss/composited icon pixels at implementation time; keep highlights out of essential paths and small text off gradient fills except named badges/actions. Hero and grid copy beds are opaque so art/glow cannot alter their contrast. Gradient title may fall back to solid `Violet` for high-contrast mode. Meaningful focus ring uses `CastControlEdge`, not a faint outline; error label body uses `Text`/`Muted` on `Surface`. Day `Error` on `Raised` is ~4.47:1, so it is prohibited for normal-sized error text. Disabled visuals and duotone background bodies are decorative/exempt; solid identifying glyph paths still meet contrast for enabled controls.

## 11. Icon construction for Compose Canvas

Keep the existing original duotone style; use a **24 × 24** logical viewport, rounded stroke caps/joins. Standard stroke1.8 units (`GlyphIcon`), feature solid details2 units (`FeatureIcon`). Scale via `u=size.width/24`. Soft body = active ink at alpha.34, solid identifying paths at alpha1. Dark glyph on bright night gradients, white on deep day gradients. Essential glyph is24dp in a40 tile, decorative duotone fill can be lower contrast.

| Icon | Canvas recipe / reuse |
|---|---|
| Cast / receiver / dock | Reuse `FeatureIcon(MIRROR)` screen body at(2,3.5),20×14,r3.5; stand from(8.5,21)→(15.5,21); dot(5.6,14),r1.5; two quarter arcs radii4.7/7.9. Keep waves inside screen; enough dark outline to read at24 |
| DLNA / UPnP TV | Soft screen(3,4),18×13,r3; solid top/right/bottom outline, stand stem(12,17)→(12,20), base(8,21)→(16,21). Tiny cast dot/arcs lower-left; no DLNA logo |
| Chromecast | Same generic Cast glyph; type badge disambiguates. Do not invent a brand logo |
| AirPlay | Rounded screen(3,4),18×13,r2.5 with lower middle gap; solid upward triangle vertices(7,21),(17,21),(12,15). Native route picker may use system glyph instead |
| Photos — new media glyph | Soft rounded frame(3,4),18×16,r3; solid sun circle(16,8),r1.7; mountain path(5,17)→(10,11)→(13,14)→(16,10)→(20,17), stroke2 or filled lower silhouette; frame edge stroke1.8 |
| Videos — new media glyph | Soft rounded frame(3,4),18×16,r3; filled play vertices(10,8),(17,12),(10,16); two tiny rounded perforations each side if used at28+, omit perforations at24 |
| Music — new media glyph | Soft rounded square(3,3),18×18,r5; solid stem(15,5)→(15,16), second stem(8,8)→(8,18), beam(8,8)→(15,5)→(15,8)→(8,11); two oval noteheads centered(5.8,18)/(12.8,16),r2.2×1.7 |
| Web video | Reuse `Glyph.GLOBE` for tile; `Glyph.LINK` for link-input action. Optional small play cutout must not make the globe unreadable |
| IPTV | Reuse `FeatureIcon(IPTV)` original antenna + TV + play |
| YouTube guide | Generic `Glyph.PLAY` on a duotone rounded media body; never a logo. Visible label provides identity |
| Scanner | Cast glyph plus3 outward quarter arcs, stroke1.5, animated only when searching. Static fallback is Cast + Search magnifier |
| Previous / next — new | Filled triangle previous vertices(16,5),(7,12),(16,19), vertical rounded bar x5,y5→19; next is mirrored x=24−x. Existing REWIND is double-chevron and must not substitute for playlist previous |
| −10 / +10 — new | Circular open arc center(12,12),r8,300° sweep,1.8stroke; tangential arrowhead at start/end, left for back/right for forward; small **10** centered using labelMedium in a composited Box (real Text, not font paths). Distinct accessible labels specify seconds |
| Speaker — new | Closed speaker outline(3,9)→(7,9)→(12,5)→(12,19)→(7,15)→(3,15); two open arcs around(13,12),r4/7,−45→45°. Do not reuse MUTE for ordinary volume |
| Existing actions | `DOWN`, `RIGHT`, `CLOSE`, `CHECK`, `PLAY`, `PAUSE`, `STOP`, `PLUS`, `MINUS`, `REFRESH`, `HELP`, `INFO`, `WIFI`, `LINK` reuse existing `GlyphIcon` geometry |
| Buffering/loading |18dp ring240°/stroke1.8 and visible state text; no new semantic icon name needed |

Arrows inside hero/cards are decorative and32/24dp visually; the parent is the48+ action. Play/pause/stop glyphs in controller are independent48+ targets. Canvas glyphs get descriptions from owning semantic controls, not hard-coded English in the drawing helper.

## 12. Complete app-owned microcopy — EN + VI

Keys below are suggested resource IDs, not edits to resource files. All app-owned visible copy and accessibility labels must come from these resources. Do not uppercase Vietnamese display text. Ellipsis is the single character `…`. `{name}`, `{title}`, `{protocol}`, `{state}`, `{count}`, `{index}`, `{seconds}`, `{elapsed}`, `{duration}`, `{percent}` are typed interpolation values; names/titles/URLs are user data and remain unchanged. Times use locale-aware digits and tabular figures; TV names never get translated. Protocol/product labels are literals in both languages. Native picker, AirPlay route-picker, settings and permission dialogs are OS-owned and use their system localization.

### Landing and navigation

| Key | English | Tiếng Việt |
|---|---|---|
| cast_title / nav_cast | Cast | Truyền |
| cast_subtitle | Your favorites. A bigger screen. | Nội dung yêu thích. Màn hình lớn hơn. |
| cast_to | Cast to | Truyền đến |
| cast_choose_tv | Choose a TV | Chọn TV |
| cast_mirror_title | Screen Mirroring | Phản chiếu màn hình |
| cast_system_guide | System guide | Hướng dẫn hệ thống |
| cast_mirror_helper_concept | Open system Cast / AirPlay | Mở hướng dẫn Cast / AirPlay |
| cast_mirror_helper_android | Open the Cast guide | Mở hướng dẫn truyền màn hình |
| cast_mirror_helper_ios | Open the AirPlay guide | Mở hướng dẫn AirPlay |
| cast_media_heading | Cast your media | Truyền nội dung của bạn |
| cast_photos | Photos | Ảnh |
| cast_photos_helper | Slideshow | Trình chiếu ảnh |
| cast_videos | Videos | Video |
| cast_videos_helper | From your phone | Từ điện thoại |
| cast_music | Music | Nhạc |
| cast_music_helper | Your tracks | Nhạc của bạn |
| cast_web | Web video | Video từ liên kết |
| cast_web_helper | Paste a video link | Dán liên kết video |
| cast_iptv | IPTV | IPTV |
| cast_iptv_helper | Live channels | Kênh trực tiếp |
| cast_youtube | YouTube | YouTube |
| cast_youtube_helper | Open app & cast | Mở ứng dụng để truyền |
| cast_find_help | Can’t find your TV? | Không tìm thấy TV? |
| nav_home | Home | Trang chủ |
| nav_remote | Remote | Điều khiển |
| nav_iptv | IPTV | IPTV |
| nav_settings | Settings | Cài đặt |

### Receiver, scan and connection help

| Key | English | Tiếng Việt |
|---|---|---|
| cast_receivers_body | TVs available on your network. | TV có sẵn trên mạng của bạn. |
| cast_scanning | Searching for TVs… | Đang tìm TV… |
| cast_scan_keep_on | Keep your TV switched on. | Hãy bật TV. |
| cast_searching_short | Searching… | Đang tìm… |
| cast_no_receivers | No TVs found | Không tìm thấy TV |
| cast_no_receivers_body | We couldn’t find a compatible TV on your network. | Chưa tìm thấy TV tương thích trên mạng của bạn. |
| cast_search_again | Search again | Tìm lại |
| cast_connection_help | Connection help | Hỗ trợ kết nối |
| cast_help_wifi | Connect your phone and TV to the same Wi-Fi network. | Kết nối điện thoại và TV với cùng một mạng Wi-Fi. |
| cast_help_dlna | On your TV, enable DLNA or “Screen Share” if available. These names vary by TV. | Bật DLNA hoặc “Screen Share” trên TV nếu có. Tên tính năng có thể khác nhau tùy TV. |
| cast_help_dlna_note | Media casting and screen mirroring may use different TV features. | Truyền nội dung và phản chiếu màn hình có thể dùng các tính năng khác nhau trên TV. |
| cast_help_awake | Keep the TV awake, then search again. | Để TV ở trạng thái bật, rồi tìm lại. |
| cast_help_airplay | Make sure AirPlay is enabled on your TV. | Đảm bảo AirPlay đã được bật trên TV. |
| cast_help_permission_ios | Allow Local Network access for Super TV Cast in Settings. | Cho phép Super TV Cast truy cập Mạng cục bộ trong Cài đặt. |
| cast_help_permission_android | Allow the network permission requested by Super TV Cast in Settings. | Cho phép quyền truy cập mạng mà Super TV Cast yêu cầu trong Cài đặt. |
| cast_help_guest | Guest networks may keep your phone and TV from finding each other. | Mạng khách có thể ngăn điện thoại và TV tìm thấy nhau. |
| cast_help_title | Find your TV | Tìm TV của bạn |
| cast_available | Available | Có sẵn |
| cast_selected | Selected | Đã chọn |
| cast_not_connected | Not connected | Chưa kết nối |
| cast_connecting | Connecting… | Đang kết nối… |
| cast_connecting_name | Connecting to {name}… | Đang kết nối với {name}… |
| cast_connected | Connected | Đã kết nối |
| cast_casting | Casting | Đang truyền |
| cast_cancel_connection | Cancel connection | Hủy kết nối |
| cast_choose_airplay | Choose with AirPlay | Chọn qua AirPlay |
| cast_open_settings | Open settings | Mở cài đặt |
| cast_no_wifi_title | Wi-Fi is unavailable | Wi-Fi chưa khả dụng |
| cast_no_wifi_body | Connect to Wi-Fi to search for TVs. | Kết nối Wi-Fi để tìm TV. |
| cast_permission_title | Network access is off | Quyền truy cập mạng đang tắt |
| cast_permission_body | Allow network access in Settings, then search again. | Cho phép truy cập mạng trong Cài đặt, rồi tìm lại. |
| cast_type_dlna | DLNA / UPnP | DLNA / UPnP |
| cast_type_chromecast | Chromecast | Chromecast |
| cast_type_airplay | AirPlay | AirPlay |

The requested DLNA/“Screen Share” tip is deliberately conditional; do not claim these are the same protocol. Place `cast_help_dlna_note` directly beneath the DLNA tip, `bodySmall`/`Muted`. Expandable Connection help includes guest-network and platform permission/AirPlay tips; the initial empty panel contains only the three main tips.

### Media review, links and replacement dialogs

| Key | English | Tiếng Việt |
|---|---|---|
| cast_photo_review_title | Photo slideshow | Trình chiếu ảnh |
| cast_photo_selection_count | Photos selected: {count} | Số ảnh đã chọn: {count} |
| cast_photo_interval | Time per photo | Thời gian mỗi ảnh |
| cast_interval_3 | 3 seconds | 3 giây |
| cast_interval_5 | 5 seconds | 5 giây |
| cast_interval_10 | 10 seconds | 10 giây |
| cast_slideshow_action | Cast slideshow | Truyền trình chiếu |
| cast_video_review_title | Ready to cast | Sẵn sàng truyền |
| cast_video_action | Cast video | Truyền video |
| cast_music_action | Cast music | Truyền nhạc |
| cast_pick_another | Pick another file | Chọn tệp khác |
| cast_preparing | Preparing media… | Đang chuẩn bị nội dung… |
| cast_choose_before_start | Choose a TV to start casting. | Chọn TV để bắt đầu truyền. |
| cast_items_count | Items selected: {count} | Số mục đã chọn: {count} |
| cast_untitled_video | Untitled video | Video chưa có tên |
| cast_untitled_music | Untitled track | Bản nhạc chưa có tên |
| cast_photo_title | Photo {index} | Ảnh {index} |
| cast_link_title | Cast a video link | Truyền video từ liên kết |
| cast_link_body | Paste a direct MP4 or M3U8 link to play it on your TV. | Dán liên kết MP4 hoặc M3U8 trực tiếp để phát trên TV. |
| cast_link_label | Video link | Liên kết video |
| cast_link_placeholder | https://example.com/video.mp4 | https://example.com/video.mp4 |
| cast_link_paste | Paste link | Dán liên kết |
| cast_link_formats | MP4 · M3U8 | MP4 · M3U8 |
| cast_link_direct_hint | Use a direct media link, not a webpage link. | Dùng liên kết nội dung trực tiếp, không dùng liên kết trang web. |
| cast_link_checking | Checking link… | Đang kiểm tra liên kết… |
| cast_link_edit | Edit link | Sửa liên kết |
| cast_switch_title | Switch TV? | Đổi TV? |
| cast_switch_body | This will end the current cast on {name} and connect to the new TV. | Thao tác này sẽ kết thúc phiên truyền trên {name} và kết nối với TV mới. |
| cast_switch_action | Switch TV | Đổi TV |
| cast_keep_tv | Keep current TV | Giữ TV hiện tại |
| cast_replace_title | Replace the current cast? | Thay nội dung đang truyền? |
| cast_replace_body | This will end the current cast and play your new selection. | Thao tác này sẽ kết thúc phiên truyền hiện tại và phát nội dung mới chọn. |
| cast_replace_action | Replace casting | Thay nội dung truyền |
| cast_keep_current | Keep current cast | Giữ phiên truyền hiện tại |

### Platform and YouTube guides

| Key | English | Tiếng Việt |
|---|---|---|
| cast_guide_android_body | Use your phone’s system screen-sharing feature. Support depends on your phone and TV. | Dùng tính năng chia sẻ màn hình của hệ thống. Khả năng hỗ trợ tùy thuộc điện thoại và TV. |
| cast_guide_wifi_title | Use the same Wi-Fi | Dùng cùng mạng Wi-Fi |
| cast_guide_wifi_body | Connect your phone and TV to the same Wi-Fi network. | Kết nối điện thoại và TV với cùng một mạng Wi-Fi. |
| cast_guide_android_step2_title | Open screen sharing | Mở chia sẻ màn hình |
| cast_guide_android_step2_body | Look for Cast, Smart View or Wireless display in your phone’s system controls. | Tìm Cast, Smart View hoặc Màn hình không dây trong phần điều khiển hệ thống của điện thoại. |
| cast_guide_android_step3_title | Choose a compatible TV | Chọn TV tương thích |
| cast_guide_android_step3_body | Choose your TV and follow the system prompts. | Chọn TV và làm theo hướng dẫn của hệ thống. |
| cast_guide_android_action | Open Cast settings | Mở cài đặt truyền màn hình |
| cast_guide_android_unavailable | Open your phone’s system controls to look for screen sharing. | Mở phần điều khiển hệ thống của điện thoại để tìm tính năng chia sẻ màn hình. |
| cast_guide_google_home | For Chromecast, you can use screen casting in Google Home. | Với Chromecast, bạn có thể dùng tính năng truyền màn hình trong Google Home. |
| cast_guide_google_home_action | Open Google Home | Mở Google Home |
| cast_guide_ios_body | Start screen mirroring from Control Center on your iPhone or iPad. | Bắt đầu phản chiếu màn hình từ Trung tâm điều khiển trên iPhone hoặc iPad. |
| cast_guide_ios_step2_title | Open Control Center | Mở Trung tâm điều khiển |
| cast_guide_ios_step2_body | Swipe down from the top-right corner. On devices with a Home button, swipe up from the bottom. | Vuốt xuống từ góc trên bên phải. Với thiết bị có nút Home, vuốt lên từ cạnh dưới. |
| cast_guide_ios_step3_title | Tap Screen Mirroring | Chạm Phản chiếu màn hình |
| cast_guide_ios_step3_body | Choose your TV. Enter the code shown on the TV if asked. | Chọn TV. Nhập mã hiển thị trên TV nếu được yêu cầu. |
| cast_guide_system_note | Screen mirroring is managed by your phone’s system. This app can’t confirm whether it is active. | Phản chiếu màn hình do hệ thống điện thoại quản lý. Ứng dụng không thể xác nhận tính năng này đang hoạt động. |
| cast_youtube_guide_title | YouTube on your TV | YouTube trên TV |
| cast_youtube_guide_body | Open YouTube to cast. Playback controls stay in YouTube. | Mở YouTube để truyền. Điều khiển phát vẫn nằm trong YouTube. |
| cast_youtube_step2_title | Open YouTube | Mở YouTube |
| cast_youtube_step2_body | Choose a video in the YouTube app. | Chọn video trong ứng dụng YouTube. |
| cast_youtube_step3_title | Tap Cast in YouTube | Chạm nút Truyền trong YouTube |
| cast_youtube_step3_body | Choose a compatible TV from YouTube’s list. | Chọn TV tương thích trong danh sách của YouTube. |
| cast_youtube_share_tip | Share opens sharing options. A YouTube page link won’t work as a direct video link here. | Nút Chia sẻ mở các tùy chọn chia sẻ. Liên kết trang YouTube không dùng được như liên kết video trực tiếp tại đây. |
| cast_youtube_action | Open YouTube | Mở YouTube |
| cast_done | Done | Xong |

Guide note `cast_guide_system_note` is a small full-alpha `Muted` paragraph after the timeline, before Done; do not plaster it over the hero. Android TV compatibility wording and Google Home action remain capability/installed-app dependent.

### Controller, mini, capability messages and feedback

| Key | English | Tiếng Việt |
|---|---|---|
| cast_now_title | Now casting | Đang truyền nội dung |
| cast_starting | Starting cast… | Đang bắt đầu truyền… |
| cast_paused | Paused | Đã tạm dừng |
| cast_playing | Playing | Đang phát |
| cast_buffering | Buffering… | Đang tải để phát… |
| cast_interrupted | Cast interrupted | Phiên truyền bị gián đoạn |
| cast_live | Live | Trực tiếp |
| cast_live_stream | Live stream | Luồng trực tiếp |
| cast_duration_unavailable | Duration unavailable | Chưa có thời lượng |
| cast_position_unavailable | Position unavailable | Chưa có vị trí phát |
| cast_photo_position | Photo {index} of {count} | Ảnh {index} trên {count} |
| cast_photo_every | Every {seconds} seconds | Mỗi {seconds} giây |
| cast_tv_volume | TV volume | Âm lượng TV |
| cast_volume_percent | {percent}% | {percent}% |
| cast_volume_unknown | Level unavailable | Chưa có mức âm lượng |
| cast_volume_on_tv | Adjust volume on your TV | Điều chỉnh âm lượng trên TV |
| cast_transport_unavailable | Playback controls aren’t available for this TV. | TV này không hỗ trợ điều khiển phát. |
| cast_seek_unavailable | Seeking isn’t available for this media. | Nội dung này không hỗ trợ tua. |
| cast_live_seek_unavailable | This live stream can’t be seeked. | Không thể tua luồng trực tiếp này. |
| cast_single_item | Single item — no previous or next media. | Chỉ có một mục — không có nội dung trước hoặc tiếp theo. |
| cast_stop | Stop casting | Dừng truyền |
| cast_stopping | Stopping… | Đang dừng… |
| cast_retry | Retry | Thử lại |
| cast_try_again | Try again | Thử lại |
| cast_choose_another_tv | Choose another TV | Chọn TV khác |
| cast_mini_active | Casting · {name} | Đang truyền · {name} |
| cast_mini_paused | Paused · {name} | Đã tạm dừng · {name} |
| cast_mini_connecting | Connecting · {name} | Đang kết nối · {name} |
| cast_mini_starting | Starting · {name} | Đang bắt đầu · {name} |
| cast_mini_preparing | Preparing · {name} | Đang chuẩn bị · {name} |
| cast_mini_buffering | Buffering · {name} | Đang tải · {name} |
| cast_mini_lost | Interrupted · {name} | Bị gián đoạn · {name} |
| cast_mini_stopping | Stopping · {name} | Đang dừng · {name} |
| cast_ended | Cast ended | Đã kết thúc truyền |
| cast_connected_feedback | Connected to {name}. | Đã kết nối với {name}. |
| cast_cancelled_feedback | Connection cancelled. | Đã hủy kết nối. |

Capability helpers appear once under the affected control region, not one repeated tooltip per button. Photo slide count is real; initial index is1. “Live” applies only when engineering identifies a live stream. Playback-ended snackbar is optional on natural completion but required after explicit successful Stop; do not show it if Stop failed.

### Errors

| Key | English | Tiếng Việt |
|---|---|---|
| cast_error_connect_title | Couldn’t connect to this TV | Không thể kết nối với TV này |
| cast_error_connect_body | Check that the TV is on and on the same Wi-Fi, then try again. | Kiểm tra TV đã bật và dùng cùng mạng Wi-Fi, rồi thử lại. |
| cast_error_lost_title | Connection lost | Mất kết nối |
| cast_error_lost_body | Your TV is no longer available. Reconnect to continue casting. | TV không còn khả dụng. Kết nối lại để tiếp tục truyền. |
| cast_error_start_title | Couldn’t start casting | Không thể bắt đầu truyền |
| cast_error_start_body | The TV couldn’t play this media. Try again or pick another file. | TV không phát được nội dung này. Thử lại hoặc chọn tệp khác. |
| cast_error_file_title | This file couldn’t be opened | Không thể mở tệp này |
| cast_error_file_body | Choose a file your phone can access. | Chọn tệp mà điện thoại có thể truy cập. |
| cast_error_media_title | This media isn’t supported | Nội dung này chưa được hỗ trợ |
| cast_error_media_body | Choose another file or a compatible TV. | Chọn tệp khác hoặc TV tương thích. |
| cast_error_link_title | Check the video link | Kiểm tra liên kết video |
| cast_error_link_invalid | Enter a valid HTTP or HTTPS video link. | Nhập liên kết video HTTP hoặc HTTPS hợp lệ. |
| cast_error_link_direct | This isn’t a supported direct media link. Use an MP4 or M3U8 link. | Đây không phải liên kết nội dung trực tiếp được hỗ trợ. Hãy dùng liên kết MP4 hoặc M3U8. |
| cast_error_link_fetch | The video couldn’t be reached. Check the link and try again. | Không thể truy cập video. Kiểm tra liên kết rồi thử lại. |
| cast_error_clipboard | No link found on the clipboard. | Không có liên kết trong bộ nhớ tạm. |
| cast_error_command_title | The TV didn’t respond | TV không phản hồi |
| cast_error_command_body | The last confirmed playback state is still shown. Try again. | Ứng dụng vẫn hiển thị trạng thái phát được xác nhận gần nhất. Hãy thử lại. |
| cast_error_stop_title | Couldn’t stop casting | Không thể dừng truyền |
| cast_error_stop_body | Try again. If needed, stop playback on your TV. | Thử lại. Nếu cần, dừng phát trên TV. |
| cast_error_youtube | YouTube couldn’t be opened. | Không thể mở YouTube. |
| cast_error_system | System Cast settings couldn’t be opened. Use your phone’s system controls. | Không thể mở cài đặt truyền màn hình. Hãy dùng phần điều khiển hệ thống của điện thoại. |
| cast_error_google_home | Google Home couldn’t be opened. Open it from your phone to continue. | Không thể mở Google Home. Hãy mở ứng dụng từ điện thoại để tiếp tục. |
| cast_error_scan_title | Search couldn’t finish | Chưa thể hoàn tất tìm kiếm |
| cast_error_scan_body | Check your network connection, then search again. | Kiểm tra kết nối mạng, rồi tìm lại. |

Do not expose raw exception messages, protocol codes, URLs with credentials or filenames from unrelated picker items in these errors. Preserve selected-media identity where the user already chose it.

### Accessibility and nonvisual actions

| Key | English | Tiếng Việt |
|---|---|---|
| cast_a11y_close | Close sheet | Đóng bảng |
| cast_a11y_dismiss_error | Dismiss error | Đóng thông báo lỗi |
| cast_a11y_receiver_empty | Choose a TV to cast to | Chọn TV để truyền đến |
| cast_a11y_receiver | Cast to {name}. {protocol}. {state}. Change TV. | Truyền đến {name}. {protocol}. {state}. Đổi TV. |
| cast_a11y_receiver_row | {name}. {protocol}. {state}. | {name}. {protocol}. {state}. |
| cast_a11y_mirror | Screen Mirroring. Open the system guide. | Phản chiếu màn hình. Mở hướng dẫn hệ thống. |
| cast_a11y_photos | Choose photos for a slideshow | Chọn ảnh để trình chiếu |
| cast_a11y_videos | Choose a video to cast | Chọn video để truyền |
| cast_a11y_music | Choose music to cast | Chọn nhạc để truyền |
| cast_a11y_web | Paste a direct video link to cast | Dán liên kết video trực tiếp để truyền |
| cast_a11y_iptv | Open IPTV | Mở IPTV |
| cast_a11y_youtube | Open YouTube casting guide | Mở hướng dẫn truyền YouTube |
| cast_a11y_minimize | Minimize player. Casting continues. | Thu gọn trình phát. Phiên truyền vẫn tiếp tục. |
| cast_a11y_open_player | Open player for {title} | Mở trình phát cho {title} |
| cast_a11y_change_tv | Change casting TV | Đổi TV đang nhận nội dung |
| cast_a11y_play | Play | Phát |
| cast_a11y_pause | Pause | Tạm dừng |
| cast_a11y_resume_slideshow | Resume slideshow | Tiếp tục trình chiếu |
| cast_a11y_pause_slideshow | Pause slideshow | Tạm dừng trình chiếu |
| cast_a11y_back10 | Back 10 seconds | Lùi 10 giây |
| cast_a11y_forward10 | Forward 10 seconds | Tới 10 giây |
| cast_a11y_previous | Previous item | Mục trước |
| cast_a11y_next | Next item | Mục tiếp theo |
| cast_a11y_previous_photo | Previous photo | Ảnh trước |
| cast_a11y_next_photo | Next photo | Ảnh tiếp theo |
| cast_a11y_volume_down | Lower TV volume | Giảm âm lượng TV |
| cast_a11y_volume_up | Raise TV volume | Tăng âm lượng TV |
| cast_a11y_seek | Playback position | Vị trí phát |
| cast_a11y_seek_value | {elapsed} of {duration} | {elapsed} trên {duration} |
| cast_a11y_scan_done | Search complete. TVs found: {count}. | Đã tìm xong. Số TV tìm thấy: {count}. |
| cast_a11y_live | Live stream. Seeking is unavailable. | Luồng trực tiếp. Không hỗ trợ tua. |

Use `cast_stop` as the Stop button’s visible and accessibility name, and matching visible action strings for Retry/Choose/Settings. Do not duplicate them with slightly different descriptions. Accessibility stateDescriptions reuse state resources; interval accessibility label combines Time per photo with the selected duration and native selected semantics.

## 13. Handoff acceptance criteria

- Receiver is the first focusable item; status is based on the Cast receiver session, not the Remote tab connection.
- Hero consistently reads as a guide; system mirroring and external YouTube never create an app media session.
- Six media entries and the help action are reachable by scrolling without overlap by mini/dock, with all action targets≥48.
- All copy uses EN/VI resources, including transient/empty/permission/error states, unknown media metadata and screen-reader labels. System/native copy remains OS-owned.
- Night and day use `OnAccent` correctly. No white small text on bright night gradients; no faint pink input outline used as the only field boundary.
- Selected, connecting, connected, starting, casting, paused, buffering, interrupted and stopping are visually/semantically distinct; no fake timer or scan percentage.
- Fullscreen controller supports all requested controls where real capabilities exist; unsupported controls have clear adjacent explanation. Photo slideshow uses count/interval rather than false video time; live stream has no false seek bar.
- Mini is shell-owned and persists across tabs; dialogs/receiver/controller cover it without duplicating it. Dismissing controller continues casting; Stop removes UI only on acknowledged end/cleanup.
- Native pickers, partial selection, cancellation, no artwork, long names/URLs, large type, reduced motion and landscape have explicit layout behavior.
- Verify rendered EN and VI at320/390/430dp, 200% type, both palettes, and reduced motion. VoiceOver/TalkBack must reach chooser, source actions, slider alternatives, volume, Stop, and dismiss without relying on drag.

## 14. Assets and generation record

| File | Delivery |
|---|---|
| `cast_hero_dark.png` | 1200 × 800 PNG; glossy phone-to-TV media beam, dark Popwave palette, no text/logos |
| `cast_hero_light.png` | 1200 × 800 PNG; matching bright blush/pearl palette, no text/logos |
| `mockup_cast_dark.png` | Portrait PNG; premium Cast tab concept, confirmed receiver, guide hero, six media tiles, active mini and floating dock |

Created with the built-in `image_gen` tool (no CLI/API fallback). Hero generation returned1536 × 1024; final files were proportionally downsampled to exactly1200 × 800 with system `sips`, preserving3:2. No image content was edited with Python. Tool-managed generated originals are external to the repository; the selected deliverables were copied into this directory. All task-authored repository writes are confined here.

Hero art is decorative; copy, glyphs, connection lights and playback controls are Compose elements. These illustrations visually demonstrate possible media transfer; they never substitute for an actual cast state. Artwork palettes are aesthetic and do not define UI contrast. Each theme swaps the matching illustration, keeping identical UI roles and layout.

Mockup captures the **confirmed DLNA + casting media** state. Its third grid row sits behind the persistent mini, illustrating scrolling; padding must still let that row scroll clear. Raster typography/gloss and inferred spacing are illustrative. Follow the explicit128dp tile minimum, 48dp controls, opaque copy beds, green connection perimeter and `OnAccent` rules in this spec even where generation differs. The hero caption in the mockup uses cross-platform concept copy; shipped app uses its platform-specific helper.

### Exact final generation prompts

The following prompts are retained for reproducibility. The mockup correction prompt is applied after the initial screenshot generation; numeric layout in §§2–11 is authoritative where the raster generator did not follow the initial prompt exactly.

#### Dark hero — stylized-concept

```text
Use case: stylized-concept. Asset type: premium mobile Cast tab hero artwork. Generate one landscape PNG exactly 1200x800 pixels (3:2). No text, no typography, no letters, no numbers, no logos, no watermark. Glossy high-end 3D editorial product illustration matching Super TV Cast Popwave art: deep plum background #160F2E, violet glass, hot pink #FF4D9D, lavender #A78BFF, small warm amber #FFB000 lights, polished dark metal TV, translucent beveled objects. A slim upright smartphone in the left foreground beams a small elegant curved pink/violet light ribbon towards one big widescreen TV on the right. Both screens show the same beautiful sunset mountain lake scene. Three small floating media objects along the ribbon: a framed photo landscape, a glass video play triangle tile, and a sculpted musical eighth-note. Beautiful controlled reflections, rounded corners, tactile dimensionality, soft bloom, calm composition, cinematic studio lighting. Entire main object group fits within x=12%-90%, y=12%-72% with clean dark negative space in bottom 28% for eventual UI copy overlay. No text in the image itself. Mostly matte deep plum floor with subtle reflective edge; no busy confetti, no excessive wifi arches, no green connection indicators, no third device. Keep phone and TV clearly readable at small card size.
```

#### Light hero — stylized-concept

```text
Use case: stylized-concept. Asset type: premium mobile Cast tab hero artwork. Generate one landscape PNG exactly 1200x800 pixels (3:2). No text, no typography, no letters, no numbers, no logos, no watermark. Glossy high-end 3D editorial product illustration matching Super TV Cast Popwave art: warm ivory/blush #FFF7FB backdrop softly blending to pale lavender #F3EEFF, violet/pink translucent glass and a warm amber highlight. A slim upright smartphone in the left foreground beams a small elegant curved pink/violet/amber light ribbon towards one big widescreen TV on the right. Both screens show the same beautiful sunset mountain lake scene. Three small floating media objects along the ribbon: a framed photo landscape, a glass video play triangle tile, and a sculpted musical eighth-note. Beautiful controlled reflections, rounded corners, tactile dimensionality, soft bloom, calm composition, bright premium studio lighting, polished violet chrome device rims. Entire main object group fits within x=12%-90%, y=12%-72% with clean bright ivory negative space in bottom 28% for eventual dark UI copy overlay. No text in the image itself. Soft pearl floor with subtle reflection; no busy confetti, no excessive wifi arches, no green connection indicators, no third device. Keep phone and TV clearly readable at small card size.
```

#### Dark Cast screenshot — ui-mockup

```text
Use case: ui-mockup. Asset type: high-fidelity straight-on production-ready mobile app Cast tab, Super TV Cast, Popwave dark palette. Generate a portrait PNG 1170x2532 pixels, flat screenshot view, no physical phone frame, no perspective, no marketing title outside the screen. Use logical 390x844 dp design at 3x. Premium deep plum #160F2E app background with subtle violet wash at top, glossy gradients, clean precise native Android/iOS Compose style, modern sans-serif, no logos. Pixel grid layout: status bar 24dp with 9:41. Horizontal page padding20. At y32 first top receiver selection capsule 350x64 radius24 deep plum glass #24163F, left lavender TV/cast icon, text tiny "Cast to" then "Living Room TV", small "DLNA" pill and green check for genuinely connected receiver, right downward chevron. At y112 page title "Cast" in violet/pink gradient bold30 and below muted "Your favorites. A bigger screen." 15. At y196 a 350x216 radius30 hero card: glossy 3D upright phone on left beaming a pink/violet ribbon and floating photo/video/music glass objects onto a big widescreen TV on right, screens show sunset lake, purple backdrop; lower 90dp is completely opaque plum with large readable title "Screen Mirroring", subtitle "Open system Cast / AirPlay", and right arrow. Tiny violet top-left pill "SYSTEM GUIDE". This is a guide, no live mirroring indicator. Hero thin violet/pink gradient frame. Below at y436 "Cast your media" title20. Two columns of 169dp tiles with12 gap; three rows, each104dp high, gap12. Surfaces #24163F, radius24, gentle edge highlight, at top-left a44dp glossy gradient rounded square with drawn duotone glyph, top-right decorative small chevron, below label16 and helper12. Six tiles exact labels/helpers left-to-right row-by-row: "Photos" / "Slideshow"; "Videos" / "From your phone"; "Music" / "Your tracks"; "Web video" / "Paste a video link"; "IPTV" / "Live channels"; "YouTube" / "Open app & cast". Icon gradients respectively pink-to-blue, coral-to-pink, amber-to-violet, violet-to-blue, coral-to-pink, amber-to-pink. Strong readable white labels. The third media row scrolls partly behind a persistent mini controller anchored y664-732; this is correct scrollable UI, don't squash tiles. Controller capsule350x68 radius22 plum #342052 with lavender left edge, 44px landscape thumbnail, two lines "Summer by the sea" / "Casting · Living Room TV", right 48dp pause and48dp stop icon buttons, 2dp violet progress line along bottom inset12. Bottom floating dock x16 y744 width358 height68 radius34, solid plum #24163F glossy subtle border/shadow; 5 tabs Home icon, Remote icon, selected violet-to-blue wide capsule with cast icon and text "Cast" in near-black ink #09030F, IPTV icon, Settings icon, inactive muted lavender. All dock targets >=48dp. iOS-style bottom safe area34 with home indicator, no extra labels. Clear hierarchy, exquisite controlled gloss, subtle violet bloom only, generously spaced, realistic small-screen proportions, no excessive particles. This is a functional premium interface, precise English spelling.
```

#### Final screenshot correction — precise-object-edit

```text
Use case: precise-object-edit / ui-mockup. Edit the referenced dark-mode Cast app screenshot with ONE targeted correction: change the main pictogram paths INSIDE ALL SIX saturated gradient media icon tiles (photo mountain, video triangle, music note, Web globe, IPTV television, YouTube generic play) to solid near-black ink #09030F. Popwave night mode uses dark OnAccent ink on vivid gradient tiles for accessible contrast. Preserve the shiny colored gradient tile backgrounds and highlights. Also make the receiver pictogram dark ink if it sits on a vivid gradient tile; if it remains on a plum background, leave it lavender. Preserve the entire exact screen layout, every label, font, the glowing artwork, spacing, shapes, existing colors elsewhere, mini player and selected Cast dock ink. Do not change any text or add elements. High quality PNG at the same portrait aspect ratio.
```
