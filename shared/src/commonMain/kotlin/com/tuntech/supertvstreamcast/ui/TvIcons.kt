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

/** Original scalable line icons, shared by navigation and feature tiles. */
@Composable fun FeatureIcon(feature: Feature, modifier: Modifier = Modifier, color: Color = TvColors.Cyan) {
    Canvas(modifier.size(28.dp)) {
        val u = size.width / 24f
        fun point(x: Float, y: Float) = Offset(x*u, y*u)
        fun line(a: Offset,b: Offset) = drawLine(color,a,b,2*u)
        when(feature) {
            Feature.REMOTE -> {
                drawRoundRect(color,point(7f,2f),Size(10*u,20*u),CornerRadius(3*u),style=Stroke(1.8f*u))
                drawCircle(color,2*u,point(12f,7f),style=Stroke(1.6f*u))
                line(point(10f,13f),point(14f,13f));line(point(12f,11f),point(12f,15f));drawCircle(color,u,point(12f,18f))
            }
            Feature.MIRROR -> {
                drawRoundRect(color,point(2f,3f),Size(20*u,14*u),CornerRadius(2*u),style=Stroke(1.8f*u))
                line(point(8f,21f),point(16f,21f));line(point(12f,17f),point(12f,21f))
                line(point(8f,10f),point(16f,10f));line(point(13f,7f),point(16f,10f));line(point(16f,10f),point(13f,13f))
            }
            Feature.IPTV -> {
                drawRoundRect(color,point(2f,4f),Size(20*u,16*u),CornerRadius(3*u),style=Stroke(1.8f*u))
                line(point(10f,8f),point(16f,12f));line(point(16f,12f),point(10f,16f));line(point(10f,16f),point(10f,8f))
            }
            Feature.HOME -> {
                line(point(3f,11f),point(12f,3f));line(point(12f,3f),point(21f,11f));line(point(6f,9f),point(6f,21f));line(point(18f,9f),point(18f,21f));line(point(6f,21f),point(18f,21f))
            }
            Feature.SETTINGS -> {
                drawCircle(color,7*u,point(12f,12f),style=Stroke(2*u));drawCircle(color,2.5f*u,point(12f,12f),style=Stroke(2*u))
                for(i in 0..3) { val x=if(i%2==0) 12f else if(i==1) 3f else 21f; val y=if(i%2==1) 12f else if(i==0) 3f else 21f;drawCircle(color,1.5f*u,point(x,y)) }
            }
        }
    }
}

enum class Glyph { UP, DOWN, LEFT, RIGHT, POWER, BACK, PLUS, MINUS, MUTE, CHECK, ADD, SEARCH, PLAY, HEART, LOCK, WIFI, CLOSE, MENU,
    PAUSE, STOP, REWIND, FAST_FORWARD, INPUT, KEYPAD, KEYBOARD, TOUCH, LINK, FILE, GUIDE, HELP }
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
            Glyph.HEART -> {val path=androidx.compose.ui.graphics.Path().apply{moveTo(12*u,20*u);cubicTo(0f,12*u,3*u,1*u,12*u,7*u);cubicTo(21*u,1*u,24*u,12*u,12*u,20*u);close()};drawPath(path,color,style=Stroke(1.8f*u))}
        }
    }
}
