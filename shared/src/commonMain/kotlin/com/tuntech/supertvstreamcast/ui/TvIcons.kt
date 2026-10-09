package com.tuntech.supertvstreamcast.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.Feature
import com.tuntech.supertvstreamcast.theme.TvColors

/**
 * Original duotone icons shared by navigation and feature tiles: a soft tinted body with solid
 * details on top, both in [color].
 */
@Composable fun FeatureIcon(feature: Feature, modifier: Modifier = Modifier, color: Color = TvColors.Cyan) {
    Canvas(modifier.size(28.dp)) {
        val u = size.width / 24f
        val soft = color.copy(alpha = color.alpha * 0.34f)
        val round = androidx.compose.ui.graphics.StrokeCap.Round
        fun point(x: Float, y: Float) = Offset(x*u, y*u)
        fun line(x: Float, y: Float, a: Float, b: Float, width: Float = 2f) = drawLine(color,point(x,y),point(a,b),width*u,cap=round)
        fun body(x: Float, y: Float, w: Float, h: Float, r: Float) = drawRoundRect(soft,point(x,y),Size(w*u,h*u),CornerRadius(r*u))
        fun shape(points: List<Offset>) = drawPath(androidx.compose.ui.graphics.Path().apply {
            moveTo(points[0].x,points[0].y);points.drop(1).forEach{lineTo(it.x,it.y)};close()
        },color)
        when(feature) {
            Feature.HOME -> {
                body(4.5f,10f,15f,11.5f,3f)
                drawPath(androidx.compose.ui.graphics.Path().apply{moveTo(2.5f*u,11.5f*u);lineTo(12f*u,3.5f*u);lineTo(21.5f*u,11.5f*u)},color,
                    style=Stroke(2.4f*u,cap=round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
                drawRoundRect(color,point(10f,14.5f),Size(4*u,7*u),CornerRadius(1.6f*u))
            }
            Feature.REMOTE -> {
                body(6.5f,1.5f,11f,21f,4f)
                drawCircle(color,3f*u,point(12f,7.5f))
                drawCircle(color,1.3f*u,point(9.8f,13.5f));drawCircle(color,1.3f*u,point(14.2f,13.5f))
                drawCircle(color,1.3f*u,point(9.8f,17.5f));drawCircle(color,1.3f*u,point(14.2f,17.5f))
            }
            Feature.MIRROR -> {
                body(2f,3.5f,20f,14f,3.5f)
                line(8.5f,21f,15.5f,21f)
                // Cast waves rising from the corner of the screen.
                drawCircle(color,1.5f*u,point(5.6f,14f))
                for(i in 1..2) { val r=(1.5f+i*3.2f)*u
                    drawArc(color,270f,90f,false,Offset(5.6f*u-r,14f*u-r),Size(2*r,2*r),style=Stroke(2f*u,cap=round)) }
            }
            Feature.IPTV -> {
                line(8.5f,2f,12f,5.5f,1.8f);line(15.5f,2f,12f,5.5f,1.8f)
                body(2f,5.5f,20f,15.5f,4f)
                shape(listOf(point(9.8f,9.6f),point(15.8f,13.25f),point(9.8f,16.9f)))
            }
            Feature.SETTINGS -> {
                drawRoundRect(soft,point(3f,5f),Size(18*u,3.4f*u),CornerRadius(1.7f*u))
                drawRoundRect(soft,point(3f,10.3f),Size(18*u,3.4f*u),CornerRadius(1.7f*u))
                drawRoundRect(soft,point(3f,15.6f),Size(18*u,3.4f*u),CornerRadius(1.7f*u))
                drawCircle(color,3.1f*u,point(15.5f,6.7f));drawCircle(color,3.1f*u,point(8f,12f));drawCircle(color,3.1f*u,point(14f,17.3f))
            }
        }
    }
}

enum class Glyph { UP, DOWN, LEFT, RIGHT, POWER, BACK, PLUS, MINUS, MUTE, CHECK, ADD, SEARCH, PLAY, HEART, LOCK, WIFI, CLOSE, MENU,
    PAUSE, STOP, REWIND, FAST_FORWARD, INPUT, KEYPAD, KEYBOARD, TOUCH, LINK, FILE, GUIDE, HELP, SUN, MOON, AUTO, CROWN }
@Composable fun GlyphIcon(glyph: Glyph, modifier: Modifier=Modifier, color: Color=TvColors.Text) {
    Canvas(modifier.size(24.dp)) {
        val u=size.width/24f
        fun p(x:Float,y:Float)=Offset(x*u,y*u)
        fun line(x:Float,y:Float,a:Float,b:Float) = drawLine(color,p(x,y),p(a,b),1.8f*u,cap=androidx.compose.ui.graphics.StrokeCap.Round)
        fun path(points: List<Offset>) {
            val path=androidx.compose.ui.graphics.Path().apply { moveTo(points[0].x,points[0].y);points.drop(1).forEach{lineTo(it.x,it.y)} }
            drawPath(path,color,style=Stroke(1.8f*u,cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        when(glyph) {
            Glyph.UP -> path(listOf(p(6f,15f),p(12f,9f),p(18f,15f)))
            Glyph.DOWN -> path(listOf(p(6f,9f),p(12f,15f),p(18f,9f)))
            Glyph.LEFT -> path(listOf(p(15f,6f),p(9f,12f),p(15f,18f)))
            Glyph.RIGHT -> path(listOf(p(9f,6f),p(15f,12f),p(9f,18f)))
            Glyph.PLUS, Glyph.ADD -> {line(5f,12f,19f,12f);line(12f,5f,12f,19f)}
            Glyph.MINUS -> line(5f,12f,19f,12f)
            Glyph.CLOSE -> {line(6f,6f,18f,18f);line(18f,6f,6f,18f)}
            Glyph.BACK -> {path(listOf(p(11f,5f),p(4f,12f),p(11f,19f)));line(4f,12f,20f,12f)}
            Glyph.CHECK -> path(listOf(p(5f,12f),p(10f,17f),p(20f,7f)))
            Glyph.POWER -> {drawArc(color,-45f,270f,false,p(4f,4f),Size(16*u,16*u),style=Stroke(1.8f*u,cap=androidx.compose.ui.graphics.StrokeCap.Round));line(12f,2f,12f,11f)}
            Glyph.SEARCH -> {drawCircle(color,6.5f*u,p(10.5f,10.5f),style=Stroke(1.8f*u));line(16f,16f,21f,21f)}
            Glyph.PLAY -> {val path=androidx.compose.ui.graphics.Path().apply{moveTo(8*u,5*u);lineTo(19*u,12*u);lineTo(8*u,19*u);close()};drawPath(path,color)}
            Glyph.MUTE -> {path(listOf(p(11f,4f),p(6f,9f),p(2f,9f),p(2f,15f),p(6f,15f),p(11f,20f),p(11f,4f)));line(16f,9f,22f,15f);line(22f,9f,16f,15f)}
            Glyph.LOCK -> {drawRoundRect(color,p(5f,10f),Size(14*u,11*u),CornerRadius(2*u),style=Stroke(1.8f*u));drawArc(color,180f,180f,false,p(8f,3f),Size(8*u,12*u),style=Stroke(1.8f*u));drawCircle(color,u,p(12f,15f))}
            Glyph.WIFI -> {for(i in 0..2){val r=(9f-i*3f)*u;drawArc(color,225f,90f,false,Offset(12*u-r,21*u-r),Size(2*r,2*r),style=Stroke(1.8f*u,cap=androidx.compose.ui.graphics.StrokeCap.Round))};drawCircle(color,u,p(12f,20f))}
            Glyph.MENU -> {line(4f,6f,20f,6f);line(4f,12f,20f,12f);line(4f,18f,20f,18f)}
            Glyph.PAUSE -> {line(8f,5f,8f,19f);line(16f,5f,16f,19f)}
            Glyph.STOP -> drawRoundRect(color,p(6f,6f),Size(12*u,12*u),CornerRadius(2*u))
            Glyph.REWIND -> {path(listOf(p(12f,6f),p(5f,12f),p(12f,18f)));path(listOf(p(19f,6f),p(12f,12f),p(19f,18f)))}
            Glyph.FAST_FORWARD -> {path(listOf(p(5f,6f),p(12f,12f),p(5f,18f)));path(listOf(p(12f,6f),p(19f,12f),p(12f,18f)))}
            Glyph.INPUT -> {drawRoundRect(color,p(3f,5f),Size(18*u,13*u),CornerRadius(2*u),style=Stroke(1.8f*u));line(8f,21f,16f,21f);line(7f,11.5f,14f,11.5f);path(listOf(p(11f,8.5f),p(14f,11.5f),p(11f,14.5f)))}
            Glyph.KEYPAD -> {for(row in 0..2) for(col in 0..2) drawCircle(color,1.4f*u,p(6f+col*6f,6f+row*6f))}
            Glyph.KEYBOARD -> {drawRoundRect(color,p(2f,6f),Size(20*u,12*u),CornerRadius(2*u),style=Stroke(1.8f*u));for(i in 0..3){drawCircle(color,0.9f*u,p(6f+i*4f,10f))};line(8f,14.5f,16f,14.5f)}
            Glyph.TOUCH -> {drawCircle(color,3f*u,p(12f,12f));drawCircle(color,8f*u,p(12f,12f),style=Stroke(1.5f*u))}
            Glyph.LINK -> {drawRoundRect(color,p(2.5f,8f),Size(10*u,8*u),CornerRadius(4*u),style=Stroke(1.8f*u));drawRoundRect(color,p(11.5f,8f),Size(10*u,8*u),CornerRadius(4*u),style=Stroke(1.8f*u))}
            Glyph.FILE -> {path(listOf(p(14f,3f),p(6f,3f),p(6f,21f),p(18f,21f),p(18f,7f),p(14f,3f),p(14f,7f),p(18f,7f)));line(9f,12f,15f,12f);line(9f,16f,15f,16f)}
            Glyph.GUIDE -> {drawRoundRect(color,p(3f,5f),Size(18*u,16*u),CornerRadius(2*u),style=Stroke(1.8f*u));line(3f,10f,21f,10f);line(8f,3f,8f,7f);line(16f,3f,16f,7f);line(7f,14f,11f,14f);line(7f,17.5f,15f,17.5f)}
            Glyph.HELP -> {drawCircle(color,9*u,p(12f,12f),style=Stroke(1.8f*u));drawArc(color,180f,230f,false,p(9f,6.5f),Size(6*u,6*u),style=Stroke(1.8f*u,cap=androidx.compose.ui.graphics.StrokeCap.Round));line(12f,12.5f,12f,14f);drawCircle(color,1.1f*u,p(12f,17.5f))}
            Glyph.SUN -> {drawCircle(color,4f*u,p(12f,12f),style=Stroke(1.8f*u));for(i in 0..7){val a=i*kotlin.math.PI/4;val c=kotlin.math.cos(a).toFloat();val d=kotlin.math.sin(a).toFloat();line(12f+7f*c,12f+7f*d,12f+9.5f*c,12f+9.5f*d)}}
            Glyph.MOON -> {val moon=androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference,
                androidx.compose.ui.graphics.Path().apply{addOval(Rect(p(12f,12.5f),8.5f*u))},androidx.compose.ui.graphics.Path().apply{addOval(Rect(p(16.5f,8f),7.5f*u))})
                drawPath(moon,color,style=Stroke(1.8f*u,join=androidx.compose.ui.graphics.StrokeJoin.Round))}
            Glyph.AUTO -> {drawCircle(color,8.5f*u,p(12f,12f),style=Stroke(1.8f*u));drawArc(color,90f,180f,true,p(6.5f,6.5f),Size(11*u,11*u))}
            Glyph.CROWN -> {path(listOf(p(4f,17f),p(4f,8f),p(8.5f,12f),p(12f,6f),p(15.5f,12f),p(20f,8f),p(20f,17f),p(4f,17f)));line(5f,20.5f,19f,20.5f)}
            Glyph.HEART -> {val path=androidx.compose.ui.graphics.Path().apply{moveTo(12*u,20*u);cubicTo(0f,12*u,3*u,1*u,12*u,7*u);cubicTo(21*u,1*u,24*u,12*u,12*u,20*u);close()};drawPath(path,color,style=Stroke(1.8f*u))}
        }
    }
}

/** The app mark: a gradient tile holding a screen and a play triangle. Drawn, so it follows the theme. */
@Composable fun BrandMark(modifier: Modifier = Modifier) {
    val gradient = TvColors.Gradient
    val ink = TvColors.OnAccent
    Canvas(modifier.size(96.dp)) {
        val u = size.width / 24f
        drawRoundRect(gradient, cornerRadius = CornerRadius(7 * u))
        drawRoundRect(ink, Offset(5 * u, 6.5f * u), Size(14 * u, 9.5f * u), CornerRadius(1.8f * u), style = Stroke(1.5f * u))
        drawLine(ink, Offset(9 * u, 18.5f * u), Offset(15 * u, 18.5f * u), 1.5f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(10.6f * u, 9 * u); lineTo(14.6f * u, 11.25f * u); lineTo(10.6f * u, 13.5f * u); close() }, ink)
    }
}
