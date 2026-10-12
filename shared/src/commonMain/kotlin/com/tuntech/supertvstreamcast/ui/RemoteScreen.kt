package com.tuntech.supertvstreamcast.ui

import androidx.compose.foundation.*
import com.tuntech.supertvstreamcast.analytics.ClickEvent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ImageCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.theme.*
import org.jetbrains.compose.resources.*
import shared.resources.*

@Composable internal fun RemoteContent(state: TvUiState, actions: TvActions, locked: Set<PremiumFeature> = emptySet()) {
    var touchpadPicked by rememberSaveable { mutableStateOf(false) }
    // A reward that runs out sends the remote back to the D-pad.
    val touchpad = touchpadPicked && PremiumFeature.REMOTE_TOUCHPAD !in locked
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
    var keyboardOpen by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val press: (RemoteKey) -> Unit = { key -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); actions.key(key) }
    Heading(stringResource(Res.string.remote), stringResource(Res.string.remote_caption))
    ConnectionCard(state, { actions.connection(true) }, actions.disconnect)
    RemoteBody(state.connected) {
        Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            RemoteButton(stringResource(Res.string.power), Glyph.POWER, enabled = state.can(RemoteKey.POWER), power = true) { press(RemoteKey.POWER) }
            RemoteButton(stringResource(Res.string.input), Glyph.INPUT, enabled = state.can(RemoteKey.INPUT)) { press(RemoteKey.INPUT) }
            RemoteButton(stringResource(Res.string.keypad), Glyph.KEYPAD, enabled = RemoteKey.digits.any { state.can(it) }) { ClickEvent.log("remote_keypad_open"); keypadOpen = true }
            Locked(PremiumFeature.REMOTE_KEYBOARD in locked) {
                RemoteButton(stringResource(Res.string.keyboard), Glyph.KEYBOARD, enabled = state.connected && state.capabilities.text) {
                    ClickEvent.log("remote_keyboard_open")
                    if (PremiumFeature.REMOTE_KEYBOARD in locked) actions.locked(PremiumFeature.REMOTE_KEYBOARD) else keyboardOpen = true
                }
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(TvDimens.RadiusMd)).background(TvColors.RemoteGlossBottom)
            .border(1.dp, Brush.verticalGradient(listOf(TvColors.RemoteShadow, TvColors.RemoteEdge)), RoundedCornerShape(TvDimens.RadiusMd)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ModeTab(stringResource(Res.string.dpad), !touchpad, Modifier.weight(1f)) { ClickEvent.log("remote_mode_dpad"); touchpadPicked = false }
            Locked(PremiumFeature.REMOTE_TOUCHPAD in locked, Modifier.weight(1f)) {
                ModeTab(stringResource(Res.string.touchpad), touchpad, Modifier.fillMaxWidth()) {
                    ClickEvent.log("remote_mode_touchpad")
                    if (PremiumFeature.REMOTE_TOUCHPAD in locked) actions.locked(PremiumFeature.REMOTE_TOUCHPAD) else touchpadPicked = true
                }
            }
        }
        if (touchpad) Touchpad(state, actions, press) else DirectionPad(state, press)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            RemoteButton(stringResource(Res.string.back), Glyph.BACK, enabled = state.can(RemoteKey.BACK)) { press(RemoteKey.BACK) }
            RemoteButton(stringResource(Res.string.menu), Glyph.MENU, enabled = state.can(RemoteKey.MENU)) { press(RemoteKey.MENU) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Rocker(stringResource(Res.string.volume), stringResource(Res.string.volume_up), stringResource(Res.string.volume_down),
                Glyph.PLUS, Glyph.MINUS, RemoteKey.VOLUME_UP, RemoteKey.VOLUME_DOWN, state, press, Modifier.width(64.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RemoteButton(stringResource(Res.string.mute), Glyph.MUTE, enabled = state.can(RemoteKey.MUTE)) { press(RemoteKey.MUTE) }
                val homeLabel = stringResource(Res.string.tv_home)
                PhysicalKey(homeLabel, state.can(RemoteKey.HOME), Modifier.size(TvDimens.IconButton), CircleShape, onClick = { press(RemoteKey.HOME) }) { color ->
                    FeatureIcon(Feature.HOME, Modifier.size(24.dp), color)
                }
            }
            Rocker(stringResource(Res.string.channel), stringResource(Res.string.channel_up), stringResource(Res.string.channel_down),
                Glyph.UP, Glyph.DOWN, RemoteKey.CHANNEL_UP, RemoteKey.CHANNEL_DOWN, state, press, Modifier.width(64.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple(RemoteKey.REWIND, Glyph.REWIND, Res.string.rewind), Triple(RemoteKey.PLAY, Glyph.PLAY, Res.string.play),
                Triple(RemoteKey.PAUSE, Glyph.PAUSE, Res.string.pause), Triple(RemoteKey.STOP, Glyph.STOP, Res.string.stop),
                Triple(RemoteKey.FAST_FORWARD, Glyph.FAST_FORWARD, Res.string.fast_forward),
            ).forEach { (key, glyph, label) ->
                KeyChip(stringResource(label), state.can(key), Modifier.weight(1f), glyph) { press(key) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyChip(stringResource(Res.string.info), state.can(RemoteKey.INFO), Modifier.weight(1f)) { press(RemoteKey.INFO) }
            KeyChip(stringResource(Res.string.guide), state.can(RemoteKey.GUIDE), Modifier.weight(1f)) { press(RemoteKey.GUIDE) }
        }
    }
    if (state.connected && state.capabilities.apps) AppsSection(state, actions, PremiumFeature.REMOTE_APPS in locked)
    if (!state.connected) Text(stringResource(Res.string.connect_first), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, textAlign = TextAlign.Center)
    if (keypadOpen) KeypadDialog(state, press) { ClickEvent.log("remote_keypad_close"); keypadOpen = false }
    if (keyboardOpen) KeyboardDialog(actions.text) { ClickEvent.log("remote_keyboard_close"); keyboardOpen = false }
}

private val RemoteShape = GenericShape { size, _ ->
    val w = size.width; val h = size.height
    moveTo(w * 0.18f, 0f); lineTo(w * 0.82f, 0f)
    cubicTo(w * 0.94f, 0f, w, h * 0.025f, w, h * 0.075f)
    cubicTo(w, h * 0.28f, w * 0.978f, h * 0.4f, w * 0.978f, h * 0.56f)
    cubicTo(w * 0.978f, h * 0.72f, w, h * 0.8f, w * 0.986f, h * 0.92f)
    cubicTo(w * 0.982f, h * 0.982f, w * 0.86f, h, w * 0.72f, h)
    lineTo(w * 0.28f, h)
    cubicTo(w * 0.14f, h, w * 0.018f, h * 0.982f, w * 0.014f, h * 0.92f)
    cubicTo(0f, h * 0.8f, w * 0.022f, h * 0.72f, w * 0.022f, h * 0.56f)
    cubicTo(w * 0.022f, h * 0.4f, 0f, h * 0.28f, 0f, h * 0.075f)
    cubicTo(0f, h * 0.025f, w * 0.06f, 0f, w * 0.18f, 0f); close()
}

/** Cells per side of the repeating grain tile; one small bitmap instead of thousands of point draws per frame. */
private const val GrainCells = 32

private fun grainBrush(step: Float, dot: Float, color: Color): Brush {
    val side = (step * GrainCells).roundToInt().coerceAtLeast(1)
    val points = ArrayList<Float>()
    for (row in 0 until GrainCells) for (col in 0 until GrainCells) {
        val seed = (((row * 73856093) xor (col * 19349663)) ushr 16) and 15
        if (seed < 5) { points += (col + 0.5f) * step + seed * dot; points += (row + 0.5f) * step }
    }
    val tile = ImageBitmap(side, side)
    ImageCanvas(tile).drawRawPoints(PointMode.Points, points.toFloatArray(), Paint().apply { this.color = color; strokeWidth = dot })
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

@Composable private fun RemoteBody(connected: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val highlight = TvColors.RemoteHighlight
    val shadow = TvColors.RemoteShadow
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 348.dp).fillMaxWidth()
            .drawWithCache {
                // The pearl remote sits on a pale page, so it casts a soft layered shadow; the black remote needs none.
                val layers = if (TvColors.isDark) 0 else 7
                val radius = CornerRadius(size.width * 0.2f)
                val tint = shadow.copy(alpha = 0.035f)
                onDrawBehind {
                    for (i in 1..layers) {
                        val spread = i * 1.6.dp.toPx()
                        drawRoundRect(tint, Offset(-spread * 0.6f, spread * 0.9f), Size(size.width + spread * 1.2f, size.height + spread * 0.5f), radius)
                    }
                }
            }
            .clip(RemoteShape).drawWithCache {
                val cap = 132.dp.toPx()
                val body = Brush.horizontalGradient(0f to TvColors.RemoteBodyBottom, 0.06f to TvColors.RemoteBodyTop, 0.4f to TvColors.RemoteBodyBottom, 0.9f to TvColors.RemoteBodyBottom, 1f to TvColors.RemoteShadow)
                val crown = Brush.verticalGradient(listOf(TvColors.RemoteGlossTop, TvColors.RemoteGlossBottom), endY = cap)
                val reflection = Brush.linearGradient(0f to Color.Transparent, 0.32f to Color.Transparent, 0.44f to highlight.copy(alpha = 0.025f), 0.49f to highlight.copy(alpha = 0.09f), 0.52f to highlight.copy(alpha = 0.025f), 0.66f to Color.Transparent, 1f to Color.Transparent,
                    start = Offset.Zero, end = Offset(size.width, cap * 0.85f))
                val grain = grainBrush(3.dp.toPx(), 0.35.dp.toPx(), highlight.copy(alpha = 0.045f))
                val pearl = TvColors.RemotePearl.takeIf { it.isNotEmpty() }?.let { stops ->
                    Brush.linearGradient(stops.map { it.copy(alpha = 0.16f) }, start = Offset.Zero, end = Offset(size.width, cap))
                }
                val seamDark = shadow.copy(alpha = if (TvColors.isDark) 0.95f else 0.45f); val seamLight = highlight.copy(alpha = 0.075f)
                val irShape = CornerRadius(3.dp.toPx()); val irTop = Offset(size.width * 0.31f, 7.dp.toPx()); val irSize = Size(size.width * 0.38f, 5.dp.toPx())
                val ir = Brush.verticalGradient(listOf(TvColors.RemoteEdge.copy(alpha = 0.55f), TvColors.RemoteGlossBottom), startY = irTop.y, endY = irTop.y + irSize.height)
                onDrawBehind {
                    drawRect(body); clipRect(top = cap) { drawRect(grain) }
                    clipRect(bottom = cap) { drawRect(crown); pearl?.let { drawRect(it) }; drawRect(reflection) }
                    drawLine(seamDark, Offset(0f, cap), Offset(size.width, cap), 1.dp.toPx())
                    drawLine(seamLight, Offset(0f, cap + 1.dp.toPx()), Offset(size.width, cap + 1.dp.toPx()), 0.5.dp.toPx())
                    drawRoundRect(ir, irTop, irSize, irShape)
                }
            }
            .border(1.2.dp, Brush.linearGradient(0f to TvColors.RemoteMetal, 0.22f to TvColors.RemoteEdge, 0.5f to TvColors.RemoteGlossBottom, 0.8f to TvColors.RemoteEdge, 1f to TvColors.RemoteShadow), RemoteShape)
            .padding(3.dp).border(0.5.dp, Brush.verticalGradient(listOf(highlight.copy(alpha = 0.16f), Color.Transparent, highlight.copy(alpha = 0.035f))), RemoteShape)
            .padding(horizontal = 18.dp, vertical = 17.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(14.dp).drawWithCache {
                val led = Offset(size.width / 2, size.height / 2)
                val glow = Brush.radialGradient(listOf(TvColors.RemoteLed.copy(alpha = 0.35f), Color.Transparent), led, 9.dp.toPx())
                val ledColor = if (connected) TvColors.RemoteLed else TvColors.RemoteKeyBottom
                val hole = shadow.copy(alpha = 0.9f); val rim = TvColors.RemoteEdge.copy(alpha = 0.5f)
                val holes = Array(3) { Offset(size.width - 15.dp.toPx() + it * 4.dp.toPx(), led.y) }
                onDrawBehind {
                    if (connected) drawCircle(glow, 9.dp.toPx(), led)
                    drawCircle(shadow, 3.dp.toPx(), led); drawCircle(ledColor, 2.dp.toPx(), led)
                    drawCircle(highlight.copy(alpha = if (connected) 0.7f else 0.1f), 0.55.dp.toPx(), led - Offset(0.5.dp.toPx(), 0.6.dp.toPx()))
                    for (point in holes) { drawCircle(rim, 1.1.dp.toPx(), point); drawCircle(hole, 0.75.dp.toPx(), point) }
                }
            })
            content()
            Box(Modifier.padding(top = 2.dp).size(68.dp, 16.dp).drawWithCache {
                val holes = Path(); val glints = Path(); val r = 0.65.dp.toPx()
                for (row in 0..2) for (col in 0..12) {
                    val x = 4.dp.toPx() + col * 5.dp.toPx(); val y = 3.dp.toPx() + row * 4.dp.toPx()
                    holes.addOval(Rect(x - r, y - r, x + r, y + r))
                    glints.addRect(Rect(x - r, y + r, x + r, y + r + 0.35.dp.toPx()))
                }
                val glint = highlight.copy(alpha = 0.06f)
                onDrawBehind { drawPath(holes, shadow); drawPath(glints, glint) }
            })
        }
    }
}

@Composable private fun PhysicalKey(label: String?, enabled: Boolean, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(TvDimens.RadiusSm),
                                    power: Boolean = false, concave: Boolean = false, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sunk = pressed && enabled
    val reduced = LocalReducedMotion.current
    val top = if (power) lerp(TvColors.RemotePower, TvColors.RemoteHighlight, 0.16f) else TvColors.RemoteKeyTop
    val bottom = if (power) lerp(TvColors.RemotePower, TvColors.RemoteShadow, 0.25f) else TvColors.RemoteKeyBottom
    val faceTop = if (concave) TvColors.RemoteGlossTop else top
    val faceBottom = if (concave) TvColors.RemoteGlossBottom else bottom
    val color = (if (power) TvColors.RemoteOnPower else TvColors.RemoteInk).copy(alpha = if (enabled) 1f else 0.3f)
    Box(modifier.heightIn(min = TvDimens.Touch - 8.dp).pressScale(source, 0.96f).graphicsLayer { translationY = if (sunk && !reduced) 1.dp.toPx() else 0f }
        .shadow(if (sunk || concave) 1.dp else 4.dp, shape, ambientColor = TvColors.RemoteShadow.copy(alpha = 0.45f), spotColor = TvColors.RemoteShadow.copy(alpha = 0.8f))
        .clip(shape).background(Brush.verticalGradient(if (sunk != concave) listOf(faceBottom, faceTop) else listOf(faceTop, faceBottom)))
        .drawWithCache {
            val shadow = TvColors.RemoteShadow
            val highlight = TvColors.RemoteHighlight
            val bottomShade = Brush.verticalGradient(listOf(Color.Transparent, shadow.copy(alpha = if (sunk) 0.12f else 0.4f)), startY = size.height * 0.65f)
            val glint = Brush.verticalGradient(listOf(highlight.copy(alpha = if (power) 0.16f else 0.025f), Color.Transparent), endY = size.height * 0.55f)
            val arc = Path().apply {
                moveTo(size.width * 0.17f, size.height * 0.29f)
                cubicTo(size.width * 0.28f, size.height * 0.1f, size.width * 0.68f, size.height * 0.08f, size.width * 0.83f, size.height * 0.26f)
            }
            val arcBrush = Brush.horizontalGradient(listOf(Color.Transparent, highlight.copy(alpha = 0.21f), highlight.copy(alpha = 0.07f), Color.Transparent))
            val arcStroke = Stroke(1.dp.toPx(), cap = StrokeCap.Round)
            val pearl = if (concave) TvColors.RemotePearl.takeIf { it.isNotEmpty() }?.let { stops -> Brush.sweepGradient(stops.map { it.copy(alpha = 0.18f) }) } else null
            onDrawBehind {
                pearl?.let { drawRect(it) }
                drawRect(bottomShade)
                if (concave) drawPath(arc, arcBrush, style = arcStroke) else if (!sunk) drawRect(glint)
            }
        }
        .border(0.5.dp, Brush.verticalGradient(listOf(if (sunk || concave) TvColors.RemoteShadow else TvColors.RemoteHighlight.copy(alpha = if (power) 0.35f else 0.19f), TvColors.RemoteEdge.copy(alpha = 0.4f), if (concave) TvColors.RemoteHighlight.copy(alpha = 0.14f) else TvColors.RemoteShadow)), shape)
        .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick).then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        content(color)
    }
}
@Composable private fun RemoteButton(label: String, glyph: Glyph, modifier: Modifier = Modifier, enabled: Boolean = true, power: Boolean = false, onClick: () -> Unit) {
    PhysicalKey(label, enabled, modifier.size(TvDimens.IconButton), CircleShape, power = power, onClick = onClick) { color -> GlyphIcon(glyph, Modifier.size(22.dp), color) }
}

@Composable private fun DirectionPad(state: TvUiState, press: (RemoteKey) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val size = minOf(maxWidth, 208.dp)
        Box(Modifier.size(size).shadow(8.dp, CircleShape, ambientColor = TvColors.RemoteShadow.copy(alpha = 0.6f), spotColor = TvColors.RemoteShadow.copy(alpha = 0.8f))
            .clip(CircleShape).drawWithCache {
                val shadow = TvColors.RemoteShadow
                val highlight = TvColors.RemoteHighlight
                val radius = this.size.minDimension / 2
                val center = Offset(this.size.width / 2, this.size.height / 2)
                val metal = Brush.sweepGradient(0f to TvColors.RemoteKeyBottom, 0.12f to TvColors.RemoteEdge, 0.25f to TvColors.RemoteKeyBottom,
                    0.43f to TvColors.RemoteKeyTop, 0.58f to TvColors.RemoteMetal, 0.7f to TvColors.RemoteEdge, 0.83f to TvColors.RemoteKeyBottom, 1f to TvColors.RemoteKeyBottom, center = center)
                val sheen = Brush.linearGradient(listOf(highlight.copy(alpha = 0.12f), Color.Transparent, shadow.copy(alpha = 0.4f)))
                val fineRings = Path(); val darkRings = Path(); val step = 1.5.dp.toPx()
                var r = 49.dp.toPx()
                while (r < radius - 3.dp.toPx()) {
                    fineRings.addOval(Rect(center.x - r, center.y - r, center.x + r, center.y + r))
                    val dr = r + 0.6.dp.toPx(); darkRings.addOval(Rect(center.x - dr, center.y - dr, center.x + dr, center.y + dr))
                    r += step
                }
                val fine = highlight.copy(alpha = 0.045f); val dark = shadow.copy(alpha = 0.13f); val fineStroke = Stroke(0.35.dp.toPx())
                val outerStroke = Stroke(0.7.dp.toPx()); val outerGlint = highlight.copy(alpha = 0.16f)
                val pearl = TvColors.RemotePearl.takeIf { it.isNotEmpty() }?.let { stops -> Brush.sweepGradient(stops.map { it.copy(alpha = 0.3f) }, center) }
                val gap = Brush.radialGradient(0f to shadow, 0.83f to shadow, 0.92f to shadow, 1f to Color.Transparent, center = center, radius = 49.dp.toPx())
                onDrawBehind {
                    drawCircle(metal); pearl?.let { drawCircle(it) }; drawCircle(sheen)
                    drawPath(fineRings, fine, style = fineStroke); drawPath(darkRings, dark, style = fineStroke)
                    drawCircle(shadow, radius - 1.dp.toPx(), style = outerStroke)
                    drawCircle(outerGlint, radius - 2.dp.toPx(), style = outerStroke)
                    drawCircle(gap, 49.dp.toPx())
                    drawCircle(shadow, 45.dp.toPx())
                }
            }) {
            DirectionKey(RemoteKey.UP, Glyph.UP, stringResource(Res.string.up), state, Modifier.align(Alignment.TopCenter).padding(top = 5.dp), press)
            DirectionKey(RemoteKey.DOWN, Glyph.DOWN, stringResource(Res.string.down), state, Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp), press)
            DirectionKey(RemoteKey.LEFT, Glyph.LEFT, stringResource(Res.string.left), state, Modifier.align(Alignment.CenterStart).padding(start = 5.dp), press)
            DirectionKey(RemoteKey.RIGHT, Glyph.RIGHT, stringResource(Res.string.right), state, Modifier.align(Alignment.CenterEnd).padding(end = 5.dp), press)
            val enabled = state.can(RemoteKey.OK)
            PhysicalKey(null, enabled, Modifier.align(Alignment.Center).size(86.dp), CircleShape, concave = true, onClick = { press(RemoteKey.OK) }) { color ->
                Text(stringResource(Res.string.ok), style = MaterialTheme.typography.titleLarge, color = color)
            }
        }
    }
}
@Composable private fun DirectionKey(key: RemoteKey, glyph: Glyph, label: String, state: TvUiState, modifier: Modifier, press: (RemoteKey) -> Unit) {
    val enabled = state.can(key)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val color = TvColors.RemoteInk.copy(alpha = if (enabled) 0.95f else 0.3f)
    Box(modifier.size(TvDimens.Touch).pressScale(source).clip(CircleShape)
        .drawWithCache {
            val shade = Brush.radialGradient(listOf(TvColors.RemoteShadow.copy(alpha = if (pressed && enabled) 0.65f else 0f), Color.Transparent))
            onDrawBehind { drawRect(shade) }
        }
        .clickable(source, null, enabled = enabled, role = Role.Button, onClick = { press(key) }).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(16.dp)) {
            val horizontal = glyph == Glyph.UP || glyph == Glyph.DOWN
            val delta = if (horizontal) Offset(4.dp.toPx(), 0f) else Offset(0f, 4.dp.toPx())
            val relief = if (TvColors.isDark) TvColors.RemoteShadow.copy(alpha = 0.7f) else TvColors.RemoteHighlight
            drawLine(relief, center - delta + Offset(0f, 1.dp.toPx()), center + delta + Offset(0f, 1.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            drawLine(color, center - delta, center + delta, 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** Swipe for arrows and tap for OK on every brand; LG can switch to a pointer (cursor) mode. */
@Composable private fun Touchpad(state: TvUiState, actions: TvActions, press: (RemoteKey) -> Unit) {
    var pointer by rememberSaveable { mutableStateOf(false) }
    val pointerMode = pointer && state.capabilities.pointer && state.connected
    val enabled = state.connected
    val latestPress by rememberUpdatedState(press)
    val latestActions by rememberUpdatedState(actions)
    val hint = stringResource(if (pointerMode) Res.string.pointer_hint else Res.string.touchpad_hint)
    Box(Modifier.fillMaxWidth().height(208.dp).clip(RoundedCornerShape(TvDimens.RadiusLg)).drawWithCache {
            val glass = Brush.verticalGradient(listOf(TvColors.RemoteGlossBottom, TvColors.RemoteGlossTop, TvColors.RemoteKeyBottom))
            val reflection = Brush.linearGradient(0f to Color.Transparent, 0.24f to TvColors.RemoteHighlight.copy(alpha = 0.035f), 0.36f to TvColors.RemoteHighlight.copy(alpha = 0.065f), 0.38f to Color.Transparent, 1f to Color.Transparent,
                start = Offset.Zero, end = Offset(size.width, size.height * 0.65f))
            val inset = 5.dp.toPx(); val rim = TvColors.RemoteHighlight.copy(alpha = 0.025f); val stroke = Stroke(0.5.dp.toPx())
            onDrawBehind {
                drawRect(glass); drawRect(reflection)
                drawRoundRect(rim, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(20.dp.toPx()), style = stroke)
            }
        }
        .border(1.dp, Brush.verticalGradient(listOf(TvColors.RemoteShadow, TvColors.RemoteEdge, TvColors.RemoteHighlight.copy(alpha = 0.12f))), RoundedCornerShape(TvDimens.RadiusLg))
        .semantics { contentDescription = hint }
        .pointerInput(enabled, pointerMode) {
            if (!enabled) return@pointerInput
            detectTapGestures { if (pointerMode) latestActions.click() else latestPress(RemoteKey.OK) }
        }
        .pointerInput(enabled, pointerMode) {
            if (!enabled) return@pointerInput
            var total = Offset.Zero
            var pending = Offset.Zero
            detectDragGestures(
                onDragStart = { total = Offset.Zero; pending = Offset.Zero },
                onDragEnd = { if (!pointerMode) swipeDirection(total.x, total.y, 48.dp.toPx())?.let { latestPress(it) } },
                onDrag = { change, amount ->
                    change.consume()
                    total += amount
                    if (pointerMode) {
                        pending += amount
                        val dx = pending.x.toInt(); val dy = pending.y.toInt()
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) >= 3) {
                            latestActions.move(dx, dy); pending -= Offset(dx.toFloat(), dy.toFloat())
                        }
                    }
                },
            )
        }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlyphIcon(Glyph.TOUCH, Modifier.size(34.dp), if (enabled) TvColors.RemoteInk else TvColors.RemoteInk.copy(alpha = 0.3f))
            Text(hint, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium, color = TvColors.RemoteMuted, textAlign = TextAlign.Center)
        }
    }
    if (state.capabilities.pointer && state.connected) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.pointer_mode), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = TvColors.RemoteInk)
        Switch(checked = pointer, onCheckedChange = { ClickEvent.log("remote_pointer_mode", "on" to it); pointer = it }, colors = SwitchDefaults.colors(checkedThumbColor = TvColors.RemoteKeyTop, checkedTrackColor = TvColors.RemoteMuted, checkedBorderColor = TvColors.RemoteEdge, uncheckedThumbColor = TvColors.RemoteKeyTop, uncheckedTrackColor = TvColors.RemoteBodyBottom, uncheckedBorderColor = TvColors.RemoteEdge))
    }
}

@Composable private fun Rocker(label: String, upLabel: String, downLabel: String, upGlyph: Glyph, downGlyph: Glyph, up: RemoteKey, down: RemoteKey,
                               state: TvUiState, press: (RemoteKey) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(32.dp)
    Column(modifier.shadow(5.dp, shape, ambientColor = TvColors.RemoteShadow.copy(alpha = 0.35f), spotColor = TvColors.RemoteShadow.copy(alpha = 0.6f))
        .clip(shape).background(Brush.verticalGradient(listOf(TvColors.RemoteKeyTop, TvColors.RemoteKeyBottom)))
        .border(1.dp, Brush.verticalGradient(listOf(TvColors.RemoteHighlight.copy(alpha = 0.14f), TvColors.RemoteShadow.copy(alpha = 0.85f))), shape).padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PhysicalKey(upLabel, state.can(up), Modifier.fillMaxWidth().height(50.dp), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 4.dp, bottomEnd = 4.dp), onClick = { press(up) }) { color -> GlyphIcon(upGlyph, Modifier.size(22.dp), color) }
        Text(label, Modifier.fillMaxWidth().padding(vertical = 9.dp), style = MaterialTheme.typography.labelSmall, color = TvColors.RemoteMuted.copy(alpha = if (state.can(up) || state.can(down)) 1f else 0.3f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        PhysicalKey(downLabel, state.can(down), Modifier.fillMaxWidth().height(50.dp), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 30.dp, bottomEnd = 30.dp), onClick = { press(down) }) { color -> GlyphIcon(downGlyph, Modifier.size(22.dp), color) }
    }
}
@Composable private fun ModeTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(TvDimens.RadiusSm)
    Box(modifier.heightIn(min = TvDimens.Touch - 8.dp).pressScale(source, 0.97f).then(if (selected) Modifier.shadow(2.dp, shape, ambientColor = TvColors.RemoteShadow, spotColor = TvColors.RemoteShadow) else Modifier)
        .clip(shape).then(if (selected) Modifier.background(Brush.verticalGradient(listOf(TvColors.RemoteKeyTop, TvColors.RemoteKeyBottom))).border(1.dp, Brush.verticalGradient(listOf(TvColors.RemoteHighlight.copy(alpha = 0.12f), TvColors.RemoteShadow)), shape) else Modifier)
        .clickable(source, null, role = Role.Tab, onClick = onClick).semantics { this.selected = selected }.padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) TvColors.RemoteInk else TvColors.RemoteMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable internal fun KeyChip(label: String, enabled: Boolean, modifier: Modifier, glyph: Glyph? = null, onClick: () -> Unit) {
    PhysicalKey(if (glyph != null) label else null, enabled, modifier, RoundedCornerShape(TvDimens.RadiusSm), onClick = onClick) { color ->
        if (glyph != null) GlyphIcon(glyph, Modifier.size(20.dp), color)
        else Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Wraps a premium-only control; while [locked] a crown sits on its corner. */
@Composable private fun Locked(locked: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        content()
        if (locked) LockBadge(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp))
    }
}

@Composable private fun AppsSection(state: TvUiState, actions: TvActions, locked: Boolean) {
    val gate = { actions.locked(PremiumFeature.REMOTE_APPS) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.tv_apps), style = MaterialTheme.typography.titleLarge)
        if (locked) LockBadge()
        Spacer(Modifier.weight(1f))
        if (state.appsLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else TextButton(onClick = if (locked) gate else actions.loadApps) { Text(stringResource(Res.string.load_apps)) }
    }
    if (state.apps.isEmpty()) {
        if (!state.appsLoading) Text(stringResource(Res.string.apps_empty), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
    } else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.apps, key = { it.id }) { app ->
            Column(Modifier.width(96.dp).clip(RoundedCornerShape(20.dp)).background(TvColors.Surface)
                .border(1.dp, TvColors.Outline, RoundedCornerShape(20.dp)).clickable(role = Role.Button) { if (locked) gate() else actions.launch(app) }.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(TvColors.featureGradient(Feature.entries[app.title.length % Feature.entries.size])), contentAlignment = Alignment.Center) {
                    Text(app.title.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = TvColors.OnAccent)
                }
                Text(app.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable private fun KeypadDialog(state: TvUiState, press: (RemoteKey) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(30.dp), containerColor = TvColors.Surface,
        title = { Text(stringResource(Res.string.keypad), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteKey.digits.take(9).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { key -> KeyChip(key.name.removePrefix("NUM_"), state.can(key), Modifier.weight(1f).height(56.dp)) { press(key) } }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KeyChip(stringResource(Res.string.back), state.can(RemoteKey.BACK), Modifier.weight(1f).height(56.dp), Glyph.BACK) { press(RemoteKey.BACK) }
                    KeyChip("0", state.can(RemoteKey.NUM_0), Modifier.weight(1f).height(56.dp)) { press(RemoteKey.NUM_0) }
                    KeyChip(stringResource(Res.string.ok), state.can(RemoteKey.OK), Modifier.weight(1f).height(56.dp)) { press(RemoteKey.OK) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.done)) } })
}
@Composable private fun KeyboardDialog(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(30.dp), containerColor = TvColors.Surface,
        icon = { GlyphIcon(Glyph.KEYBOARD, color = TvColors.Cyan) },
        title = { Text(stringResource(Res.string.keyboard), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(Res.string.keyboard_hint), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
                OutlinedTextField(text, { if (it.length <= 200) text = it }, label = { Text(stringResource(Res.string.keyboard_input)) },
                    singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSend(text); text = "" }, enabled = text.isNotEmpty()) { Text(stringResource(Res.string.send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } })
}
