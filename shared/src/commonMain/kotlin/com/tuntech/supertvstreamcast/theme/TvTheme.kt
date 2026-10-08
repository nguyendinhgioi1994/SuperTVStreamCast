package com.tuntech.supertvstreamcast.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object TvColors {
    val Background = Color(0xFF070F1C)
    val Surface = Color(0xFF101E30)
    val Raised = Color(0xFF192C42)
    val Outline = Color(0xFF273B51)
    val Cyan = Color(0xFF69E2EC)
    val Blue = Color(0xFF89B8FF)
    val Coral = Color(0xFFFFAB97)
    val Violet = Color(0xFFB3A8FF)
    val Text = Color(0xFFF2F6FF)
    val Muted = Color(0xFF98ACC4)
    val Error = Color(0xFFFFB4AB)
    val Gradient = Brush.horizontalGradient(listOf(Cyan, Blue))
    val SurfaceGradient = Brush.linearGradient(listOf(Raised, Surface))
    val BackgroundGradient = Brush.verticalGradient(listOf(Color(0xFF102238), Background, Background))
    val HeroScrim = Brush.verticalGradient(listOf(Background.copy(alpha=0.05f), Background.copy(alpha=0.1f), Background.copy(alpha=0.95f)))
    val RemoteGradient = Brush.verticalGradient(listOf(Color(0xFF20384E), Surface))
}
object TvDimens {
    val Space = 20.dp
    val Radius = 28.dp
    val Touch = 56.dp
}
private val TvTypography = Typography(
    displaySmall = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.Bold, fontSize=34.sp, lineHeight=39.sp, letterSpacing=(-1).sp),
    headlineLarge = TextStyle(fontFamily=FontFamily.SansSerif, fontWeight=FontWeight.Bold, fontSize=30.sp, lineHeight=36.sp, letterSpacing=(-0.7).sp),
    headlineMedium = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=26.sp, lineHeight=32.sp, letterSpacing=(-0.5).sp),
    headlineSmall = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=23.sp, lineHeight=29.sp, letterSpacing=(-0.3).sp),
    titleLarge = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=20.sp, lineHeight=26.sp),
    titleMedium = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=16.sp, lineHeight=22.sp),
    titleSmall = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=14.sp, lineHeight=20.sp),
    bodyLarge = TextStyle(fontSize=15.sp, lineHeight=23.sp),
    bodyMedium = TextStyle(fontSize=13.sp, lineHeight=20.sp),
    bodySmall = TextStyle(fontSize=12.sp, lineHeight=18.sp),
    labelLarge = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=13.sp, lineHeight=18.sp),
    labelMedium = TextStyle(fontWeight=FontWeight.Medium, fontSize=11.sp, lineHeight=16.sp, letterSpacing=0.4.sp),
    labelSmall = TextStyle(fontWeight=FontWeight.SemiBold, fontSize=10.sp, lineHeight=14.sp, letterSpacing=1.3.sp),
)
@Composable fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(typography=TvTypography, colorScheme=darkColorScheme(
        primary=TvColors.Cyan, onPrimary=TvColors.Background,
        secondary=TvColors.Coral, secondaryContainer=TvColors.Raised, onSecondaryContainer=TvColors.Cyan,
        primaryContainer=TvColors.Raised, onPrimaryContainer=TvColors.Cyan,
        background=TvColors.Background, surface=TvColors.Surface, surfaceVariant=TvColors.Raised,
        onBackground=TvColors.Text, onSurface=TvColors.Text, onSurfaceVariant=TvColors.Muted,
        error=TvColors.Error, outline=TvColors.Outline, outlineVariant=TvColors.Outline,
    ), content=content)
}
