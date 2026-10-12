package com.tuntech.supertvstreamcast.ui.language

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.AppLanguage
import com.tuntech.supertvstreamcast.theme.TvColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Flag colours are the flags' own, independent of the app theme. */
private val Red = Color(0xFFDA251D)
private val DarkRed = Color(0xFFC8102E)
private val Yellow = Color(0xFFFFCD00)
private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF15151A)
private val Navy = Color(0xFF3C3B6E)
private val Blue = Color(0xFF0038A8)
private val Green = Color(0xFF138808)
private val DarkGreen = Color(0xFF006C35)
private val Saffron = Color(0xFFFF9933)
private val BrazilGreen = Color(0xFF009B3A)
private val BrazilBlue = Color(0xFF002776)

/**
 * A simplified, drawn flag for each shipped language: vector, so it stays sharp at any density,
 * and free of text. Portuguese uses Brazil's flag, matching its Brazilian translation.
 */
@Composable
internal fun LanguageFlag(language: AppLanguage) {
    val shape = RoundedCornerShape(6.dp)
    Canvas(Modifier.size(42.dp, 28.dp).clip(shape).border(1.dp, TvColors.Outline, shape)) {
        when (language) {
            AppLanguage.ENGLISH -> drawStripesFlag()
            AppLanguage.ARABIC -> {
                drawRect(DarkGreen)
                drawRect(White, Offset(size.width * 0.22f, size.height * 0.36f), Size(size.width * 0.56f, size.height * 0.1f))
                drawRect(White, Offset(size.width * 0.26f, size.height * 0.64f), Size(size.width * 0.5f, size.height * 0.06f))
            }
            AppLanguage.GERMAN -> horizontalBands(Black to 1f, DarkRed to 1f, Yellow to 1f)
            AppLanguage.SPANISH -> horizontalBands(DarkRed to 1f, Yellow to 2f, DarkRed to 1f)
            AppLanguage.FRENCH -> verticalBands(listOf(Blue, White, Red))
            AppLanguage.PORTUGUESE -> {
                drawRect(BrazilGreen)
                val diamond = Path().apply {
                    moveTo(size.width * 0.5f, size.height * 0.12f)
                    lineTo(size.width * 0.9f, size.height * 0.5f)
                    lineTo(size.width * 0.5f, size.height * 0.88f)
                    lineTo(size.width * 0.1f, size.height * 0.5f)
                    close()
                }
                drawPath(diamond, Yellow)
                drawCircle(BrazilBlue, size.height * 0.22f)
            }
            AppLanguage.INDONESIAN -> horizontalBands(Red to 1f, White to 1f)
            AppLanguage.FILIPINO -> {
                horizontalBands(Blue to 1f, DarkRed to 1f)
                val triangle = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width * 0.45f, size.height / 2f)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(triangle, White)
                drawCircle(Yellow, size.height * 0.1f, Offset(size.width * 0.15f, size.height / 2f))
            }
            AppLanguage.HINDI -> {
                horizontalBands(Saffron to 1f, White to 1f, Green to 1f)
                drawCircle(Navy, size.height * 0.13f, style = Stroke(size.height * 0.035f))
            }
            AppLanguage.THAI -> horizontalBands(DarkRed to 1f, White to 1f, Navy to 2f, White to 1f, DarkRed to 1f)
            AppLanguage.VIETNAMESE -> {
                drawRect(Red)
                drawStar(center, size.height * 0.32f, Yellow)
            }
            AppLanguage.CHINESE -> {
                drawRect(Red)
                drawStar(Offset(size.width * 0.17f, size.height * 0.27f), size.height * 0.16f, Yellow)
                listOf(0.33f to 0.1f, 0.4f to 0.2f, 0.4f to 0.35f, 0.33f to 0.45f).forEach { (x, y) ->
                    drawStar(Offset(size.width * x, size.height * y), size.height * 0.05f, Yellow)
                }
            }
            AppLanguage.KOREAN -> {
                drawRect(White)
                val radius = size.height * 0.22f
                drawArc(DarkRed, 180f, 180f, true, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2))
                drawArc(Blue, 0f, 180f, true, Offset(center.x - radius, center.y - radius), Size(radius * 2, radius * 2))
                val bar = Size(size.width * 0.1f, size.height * 0.05f)
                listOf(0.12f to 0.16f, 0.78f to 0.16f, 0.12f to 0.78f, 0.78f to 0.78f).forEach { (x, y) ->
                    repeat(3) { drawRect(Black, Offset(size.width * x, size.height * y + it * bar.height * 1.6f), bar) }
                }
            }
            AppLanguage.JAPANESE -> {
                drawRect(White)
                drawCircle(DarkRed, size.height * 0.3f)
            }
            AppLanguage.TURKISH -> {
                drawRect(Red)
                val moon = Offset(size.width * 0.38f, center.y)
                drawCircle(White, size.height * 0.25f, moon)
                drawCircle(Red, size.height * 0.2f, Offset(moon.x + size.height * 0.06f, center.y))
                drawStar(Offset(size.width * 0.6f, center.y), size.height * 0.1f, White)
            }
        }
    }
}

/** Equal-height bands from top to bottom, each weighted by its share of the height. */
private fun DrawScope.horizontalBands(vararg bands: Pair<Color, Float>) {
    val total = bands.sumOf { it.second.toDouble() }.toFloat()
    var top = 0f
    bands.forEach { (color, weight) ->
        val height = size.height * weight / total
        drawRect(color, Offset(0f, top), Size(size.width, height))
        top += height
    }
}

private fun DrawScope.verticalBands(colors: List<Color>) {
    val width = size.width / colors.size
    colors.forEachIndexed { index, color -> drawRect(color, Offset(index * width, 0f), Size(width, size.height)) }
}

/** English: 13 stripes with a starred canton. */
private fun DrawScope.drawStripesFlag() {
    drawRect(White)
    val stripe = size.height / 13f
    repeat(7) { drawRect(DarkRed, Offset(0f, it * 2 * stripe), Size(size.width, stripe)) }
    val canton = Size(size.width * 0.4f, stripe * 7f)
    drawRect(Navy, size = canton)
    repeat(3) { row ->
        repeat(4) { column ->
            drawCircle(
                White,
                radius = stripe * 0.32f,
                center = Offset(canton.width * (column + 0.5f) / 4f, canton.height * (row + 0.5f) / 3f),
            )
        }
    }
}

/** A five-pointed star pointing up. */
private fun DrawScope.drawStar(center: Offset, outer: Float, color: Color) {
    val inner = outer * 0.382f
    val star = Path()
    repeat(10) { index ->
        val radius = if (index % 2 == 0) outer else inner
        val angle = -PI / 2 + index * PI / 5
        val x = center.x + radius * cos(angle).toFloat()
        val y = center.y + radius * sin(angle).toFloat()
        if (index == 0) star.moveTo(x, y) else star.lineTo(x, y)
    }
    star.close()
    drawPath(star, color)
}
