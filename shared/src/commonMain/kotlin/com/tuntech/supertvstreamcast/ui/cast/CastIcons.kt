package com.tuntech.supertvstreamcast.ui.cast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.theme.TvColors

/** Cast-only glyphs, drawn like [com.tuntech.supertvstreamcast.ui.FeatureIcon]: a soft body (alpha .34) and solid details on a 24-unit grid. */
internal enum class CastGlyph { CAST, TV, AIRPLAY, PHOTO, VIDEO, MUSIC, WEB, YOUTUBE, PREVIOUS, NEXT, BACK10, FORWARD10, SPEAKER }

@Composable internal fun CastIcon(glyph: CastGlyph, modifier: Modifier = Modifier, color: Color = TvColors.Text) {
    Canvas(modifier.size(24.dp)) {
        val u = size.width / 24f
        val soft = color.copy(alpha = color.alpha * 0.34f)
        val round = StrokeCap.Round
        fun p(x: Float, y: Float) = Offset(x * u, y * u)
        fun line(x: Float, y: Float, a: Float, b: Float, w: Float = 1.8f) = drawLine(color, p(x, y), p(a, b), w * u, cap = round)
        fun body(x: Float, y: Float, w: Float, h: Float, r: Float) = drawRoundRect(soft, p(x, y), Size(w * u, h * u), CornerRadius(r * u))
        fun outline(x: Float, y: Float, w: Float, h: Float, r: Float) = drawRoundRect(color, p(x, y), Size(w * u, h * u), CornerRadius(r * u), style = Stroke(1.8f * u))
        fun fill(points: List<Offset>) = drawPath(Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }, color)
        fun stroke(points: List<Offset>) = drawPath(Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }, color,
            style = Stroke(1.8f * u, cap = round, join = StrokeJoin.Round))
        fun waves(cx: Float, cy: Float) {
            drawCircle(color, 1.5f * u, p(cx, cy))
            for (i in 1..2) { val r = (1.5f + i * 3.2f) * u
                drawArc(color, 270f, 90f, false, Offset(cx * u - r, cy * u - r), Size(2 * r, 2 * r), style = Stroke(2f * u, cap = round)) }
        }
        when (glyph) {
            CastGlyph.CAST -> { body(2f, 3.5f, 20f, 14f, 3.5f); line(8.5f, 21f, 15.5f, 21f, 2f); waves(5.6f, 14f) }
            CastGlyph.TV -> { body(3f, 4f, 18f, 13f, 3f); outline(3f, 4f, 18f, 13f, 3f); line(12f, 17f, 12f, 20f); line(8f, 21f, 16f, 21f) }
            CastGlyph.AIRPLAY -> { body(3f, 4f, 18f, 13f, 2.5f); stroke(listOf(p(7.5f, 17f), p(4.5f, 17f), p(3f, 15.5f), p(3f, 5.5f), p(4.5f, 4f), p(19.5f, 4f), p(21f, 5.5f), p(21f, 15.5f), p(19.5f, 17f), p(16.5f, 17f)))
                fill(listOf(p(7f, 21f), p(17f, 21f), p(12f, 15f))) }
            CastGlyph.PHOTO -> { body(3f, 4f, 18f, 16f, 3f); outline(3f, 4f, 18f, 16f, 3f); drawCircle(color, 1.7f * u, p(16f, 8.5f))
                fill(listOf(p(5f, 18f), p(10f, 11f), p(13f, 14.5f), p(16f, 11f), p(20f, 18f))) }
            CastGlyph.VIDEO -> { body(3f, 4f, 18f, 16f, 3f); outline(3f, 4f, 18f, 16f, 3f); fill(listOf(p(10f, 8f), p(17f, 12f), p(10f, 16f))) }
            CastGlyph.MUSIC -> { body(3f, 3f, 18f, 18f, 5f); line(15f, 5f, 15f, 16f, 2f); line(8f, 8f, 8f, 18f, 2f); fill(listOf(p(8f, 8f), p(15f, 5f), p(15f, 8f), p(8f, 11f)))
                drawOval(color, p(3.6f, 16.3f), Size(4.4f * u, 3.4f * u)); drawOval(color, p(10.6f, 14.3f), Size(4.4f * u, 3.4f * u)) }
            CastGlyph.WEB -> { drawCircle(soft, 9f * u, p(12f, 12f)); drawCircle(color, 9f * u, p(12f, 12f), style = Stroke(1.8f * u))
                drawOval(color, p(8f, 3f), Size(8 * u, 18 * u), style = Stroke(1.8f * u)); line(3.5f, 9f, 20.5f, 9f); line(3.5f, 15f, 20.5f, 15f) }
            CastGlyph.YOUTUBE -> { body(2f, 5f, 20f, 14f, 5f); outline(2f, 5f, 20f, 14f, 5f); fill(listOf(p(10f, 9f), p(15.5f, 12f), p(10f, 15f))) }
            CastGlyph.PREVIOUS -> { fill(listOf(p(17f, 5f), p(8f, 12f), p(17f, 19f))); line(6f, 5f, 6f, 19f, 2.2f) }
            CastGlyph.NEXT -> { fill(listOf(p(7f, 5f), p(16f, 12f), p(7f, 19f))); line(18f, 5f, 18f, 19f, 2.2f) }
            CastGlyph.BACK10, CastGlyph.FORWARD10 -> {
                val back = glyph == CastGlyph.BACK10
                drawArc(color, if (back) -60f else -120f, if (back) -280f else 280f, false, p(3.5f, 3.5f), Size(17 * u, 17 * u), style = Stroke(1.8f * u, cap = round))
                if (back) fill(listOf(p(6.5f, 2f), p(11.5f, 4.2f), p(7f, 7.5f))) else fill(listOf(p(17.5f, 2f), p(12.5f, 4.2f), p(17f, 7.5f)))
                // "10" as two strokes: a one and a zero, so no text is baked into the icon.
                line(9f, 10f, 9f, 15f, 1.6f); drawOval(color, p(11.5f, 10f), Size(3.6f * u, 5f * u), style = Stroke(1.6f * u))
            }
            CastGlyph.SPEAKER -> { fill(listOf(p(3f, 9f), p(7f, 9f), p(12f, 5f), p(12f, 19f), p(7f, 15f), p(3f, 15f)))
                for (r in listOf(4f, 7f)) drawArc(color, -45f, 90f, false, Offset((13f - r) * u, (12f - r) * u), Size(2 * r * u, 2 * r * u), style = Stroke(1.8f * u, cap = round)) }
        }
    }
}
