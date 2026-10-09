package com.tuntech.supertvstreamcast.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuntech.supertvstreamcast.domain.Feature
import com.tuntech.supertvstreamcast.platform.rememberReducedMotion

enum class TvThemeMode { SYSTEM, LIGHT, DARK }

/**
 * One complete colour set; the night and the day palette share the same roles. The accent names are
 * historical: [cyan] is the primary accent, [violet] the mirror accent, [coral] the media accent.
 */
@Immutable class TvPalette(
    val isDark: Boolean,
    val background: Color, val surface: Color, val raised: Color, val outline: Color,
    val cyan: Color, val blue: Color, val coral: Color, val violet: Color,
    val text: Color, val muted: Color, val error: Color,
    /** Text and icons drawn on top of [TvColors.Gradient]. */
    val onAccent: Color,
    val primaryContainer: Color, val onPrimaryContainer: Color,
    val backgroundTop: Color, val backgroundBottom: Color,
    val surfaceTop: Color, val remoteTop: Color, val remoteBottom: Color,
    val pink: Color, val amber: Color, val green: Color,
    /** Vivid stops that always carry [onAccent] content: the brand gradient and one pair per feature. */
    val brand: List<Color>,
    val home: List<Color>, val remote: List<Color>, val mirror: List<Color>, val iptv: List<Color>,
    val settings: List<Color>, val premium: List<Color>,
)

internal val DarkPalette = TvPalette(
    isDark = true,
    background = Color(0xFF160F2E), surface = Color(0xFF24163F), raised = Color(0xFF342052), outline = Color(0xFF5D4779),
    cyan = Color(0xFFFF4D9D), blue = Color(0xFF5B9BFF), coral = Color(0xFFFF6B4A), violet = Color(0xFFA78BFF),
    text = Color(0xFFFFF8FC), muted = Color(0xFFCBB9D9), error = Color(0xFFFF6B7A),
    onAccent = Color(0xFF09030F),
    primaryContainer = Color(0xFF4A1232), onPrimaryContainer = Color(0xFFFFD8EA),
    backgroundTop = Color(0xFF2A1250), backgroundBottom = Color(0xFF160F2E),
    surfaceTop = Color(0xFF2E1A4E), remoteTop = Color(0xFF3A255B), remoteBottom = Color(0xFF1C1234),
    pink = Color(0xFFFF4D9D), amber = Color(0xFFFFB000), green = Color(0xFF27D980),
    brand = listOf(Color(0xFFA78BFF), Color(0xFFFF4D9D), Color(0xFFFFB000)),
    home = listOf(Color(0xFFFF4D9D), Color(0xFF5B9BFF)), remote = listOf(Color(0xFFFFB000), Color(0xFFFF4D9D)),
    mirror = listOf(Color(0xFFA78BFF), Color(0xFF5B9BFF)), iptv = listOf(Color(0xFFFF6B4A), Color(0xFFFF4D9D)),
    settings = listOf(Color(0xFF27D980), Color(0xFF5B9BFF)), premium = listOf(Color(0xFFFFB000), Color(0xFFA78BFF)),
)
internal val LightPalette = TvPalette(
    isDark = false,
    background = Color(0xFFFFF7FB), surface = Color(0xFFFFFFFF), raised = Color(0xFFFFF0F7), outline = Color(0xFFE7CADD),
    cyan = Color(0xFFC9005A), blue = Color(0xFF0057D8), coral = Color(0xFFC8371F), violet = Color(0xFF5A35C8),
    text = Color(0xFF26152F), muted = Color(0xFF6B5B76), error = Color(0xFFD9233F),
    onAccent = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD8EA), onPrimaryContainer = Color(0xFF530025),
    backgroundTop = Color(0xFFFFF7FB), backgroundBottom = Color(0xFFF3EEFF),
    surfaceTop = Color(0xFFFFFFFF), remoteTop = Color(0xFFFFFFFF), remoteBottom = Color(0xFFFFE6F0),
    pink = Color(0xFFD0186A), amber = Color(0xFF9A5A00), green = Color(0xFF007A4D),
    brand = listOf(Color(0xFF5A35C8), Color(0xFFC9005A), Color(0xFFC8371F)),
    home = listOf(Color(0xFFD0186A), Color(0xFF0057D8)), remote = listOf(Color(0xFF9A5A00), Color(0xFFC9005A)),
    mirror = listOf(Color(0xFF5A35C8), Color(0xFF0057D8)), iptv = listOf(Color(0xFFC8371F), Color(0xFFC9005A)),
    settings = listOf(Color(0xFF007A4D), Color(0xFF0057D8)), premium = listOf(Color(0xFF9A5A00), Color(0xFF5A35C8)),
)

/**
 * Colour tokens of the active theme. The palette is snapshot state, so composables and draw
 * lambdas that read a token are redrawn when [TvTheme] switches between dark and light.
 */
object TvColors {
    internal var palette by mutableStateOf(DarkPalette)
    val isDark get() = palette.isDark
    val Background get() = palette.background
    val Surface get() = palette.surface
    val Raised get() = palette.raised
    val Outline get() = palette.outline
    val Cyan get() = palette.cyan
    val Blue get() = palette.blue
    val Coral get() = palette.coral
    val Violet get() = palette.violet
    val Text get() = palette.text
    val Muted get() = palette.muted
    val Error get() = palette.error
    val OnAccent get() = palette.onAccent
    val Pink get() = palette.pink
    val Amber get() = palette.amber
    /** Success and a genuinely connected TV. */
    val Green get() = palette.green
    /** The brand gradient; content on it uses [OnAccent]. */
    val Gradient get() = Brush.linearGradient(palette.brand)
    /** For gradient text on the page background: the readable accents, not the vivid stops. */
    val TitleGradient get() = Brush.linearGradient(listOf(Violet, Pink, if (isDark) Amber else Coral))
    val PremiumGradient get() = Brush.linearGradient(palette.premium)
    fun featureStops(feature: Feature) = when (feature) {
        Feature.HOME -> palette.home; Feature.REMOTE -> palette.remote; Feature.MIRROR -> palette.mirror
        Feature.IPTV -> palette.iptv; Feature.SETTINGS -> palette.settings
    }
    /** Each feature owns a vivid gradient; content on it uses [OnAccent]. */
    fun featureGradient(feature: Feature) = Brush.linearGradient(featureStops(feature))
    /** The readable accent of a feature for text and outlines on the page background. */
    fun featureAccent(feature: Feature) = when (feature) {
        Feature.HOME -> Pink; Feature.REMOTE -> Amber; Feature.MIRROR -> Violet; Feature.IPTV -> Coral; Feature.SETTINGS -> Green
    }
    val SurfaceGradient get() = Brush.linearGradient(listOf(palette.surfaceTop, if (isDark) Surface else Raised))
    val BackgroundGradient get() = Brush.verticalGradient(
        if (isDark) listOf(palette.backgroundTop, Background, Background) else listOf(palette.backgroundTop, Background, palette.backgroundBottom),
    )
    val HeroScrim get() = Brush.verticalGradient(
        0f to Background.copy(alpha = if (isDark) 0.04f else 0f), 0.45f to Background.copy(alpha = 0.18f), 1f to Background.copy(alpha = 0.94f),
    )
    val RemoteGradient get() = Brush.verticalGradient(listOf(palette.remoteTop, palette.remoteBottom))
    /** Soft halo behind selected or genuinely connected surfaces. */
    fun glow(accent: Color, center: Offset, radius: Float, strength: Float = 1f) = Brush.radialGradient(
        listOf(accent.copy(alpha = (if (isDark) 0.24f else 0.16f) * strength), Color.Transparent), center, radius,
    )
}
object TvDimens {
    val Space = 20.dp
    val Radius = 28.dp
    val Touch = 56.dp
    val SpaceXs = 6.dp
    val SpaceSm = 10.dp
    val SpaceMd = 16.dp
    val SpaceLg = 24.dp
    val SpaceXl = 32.dp
    val RadiusSm = 12.dp
    val RadiusMd = 18.dp
    val RadiusLg = 24.dp
    val RadiusXl = 30.dp
    val IconButton = 52.dp
}
private val TvTypography = Typography(
    displaySmall = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.Bold, fontSize=34.sp, lineHeight=39.sp, letterSpacing=0.sp),
    headlineLarge = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.Bold, fontSize=30.sp, lineHeight=36.sp, letterSpacing=0.sp),
    headlineMedium = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=26.sp, lineHeight=32.sp, letterSpacing=0.sp),
    headlineSmall = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=23.sp, lineHeight=29.sp, letterSpacing=0.sp),
    titleLarge = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=20.sp, lineHeight=26.sp),
    titleMedium = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=16.sp, lineHeight=22.sp),
    titleSmall = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=14.sp, lineHeight=20.sp),
    bodyLarge = TextStyle(fontSize=15.sp, lineHeight=23.sp),
    bodyMedium = TextStyle(fontSize=13.sp, lineHeight=20.sp),
    bodySmall = TextStyle(fontSize=12.sp, lineHeight=18.sp),
    labelLarge = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=13.sp, lineHeight=18.sp),
    labelMedium = TextStyle(fontWeight=FontWeight.Medium, fontSize=11.sp, lineHeight=16.sp, letterSpacing=0.sp),
    labelSmall = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=10.sp, lineHeight=14.sp, letterSpacing=1.sp),
)

/** The mode chosen in Settings; [TvTheme] resolves SYSTEM against the device setting. */
val LocalTvThemeMode = staticCompositionLocalOf { TvThemeMode.SYSTEM }
/** True when the system asks for reduced motion: looping and travelling animations stay still. */
val LocalReducedMotion = staticCompositionLocalOf { false }

private fun TvPalette.scheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = cyan, onPrimary = onAccent, primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = coral, onSecondary = onAccent, secondaryContainer = raised, onSecondaryContainer = cyan,
        tertiary = violet, onTertiary = onAccent,
        background = background, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = raised, onSurfaceVariant = muted, surfaceTint = cyan,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = raised, surfaceContainerHighest = raised,
        error = error, outline = outline, outlineVariant = outline,
    )
}

@Composable fun TvTheme(mode: TvThemeMode = TvThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) { TvThemeMode.SYSTEM -> isSystemInDarkTheme(); TvThemeMode.DARK -> true; TvThemeMode.LIGHT -> false }
    val palette = if (dark) DarkPalette else LightPalette
    if (TvColors.palette !== palette) TvColors.palette = palette
    CompositionLocalProvider(LocalTvThemeMode provides mode, LocalReducedMotion provides rememberReducedMotion()) {
        MaterialTheme(typography = TvTypography, colorScheme = remember(palette) { palette.scheme() }, content = content)
    }
}
