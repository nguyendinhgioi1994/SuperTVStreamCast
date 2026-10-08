package com.tuntech.supertvstreamcast.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.theme.*
import org.jetbrains.compose.resources.*
import supertvstreamcast.shared.generated.resources.*

@Composable internal fun RemoteContent(state: TvUiState, actions: TvActions) {
    var touchpad by rememberSaveable { mutableStateOf(false) }
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
    var keyboardOpen by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val press: (RemoteKey) -> Unit = { key -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); actions.key(key) }
    Heading(stringResource(Res.string.remote), stringResource(Res.string.remote_caption))
    ConnectionCard(state, { actions.connection(true) }, actions.disconnect)
    CinemaPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundAction(stringResource(Res.string.power), Glyph.POWER, enabled = state.can(RemoteKey.POWER), accent = TvColors.Coral) { press(RemoteKey.POWER) }
            RoundAction(stringResource(Res.string.input), Glyph.INPUT, enabled = state.can(RemoteKey.INPUT)) { press(RemoteKey.INPUT) }
            Spacer(Modifier.weight(1f))
            RoundAction(stringResource(Res.string.keypad), Glyph.KEYPAD, enabled = RemoteKey.digits.any { state.can(it) }) { keypadOpen = true }
            RoundAction(stringResource(Res.string.keyboard), Glyph.KEYBOARD, enabled = state.connected && state.capabilities.text) { keyboardOpen = true }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TvColors.Background.copy(alpha = 0.6f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ModeTab(stringResource(Res.string.dpad), !touchpad, Modifier.weight(1f)) { touchpad = false }
            ModeTab(stringResource(Res.string.touchpad), touchpad, Modifier.weight(1f)) { touchpad = true }
        }
        if (touchpad) Touchpad(state, actions, press) else DirectionPad(state, press)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundAction(stringResource(Res.string.back), Glyph.BACK, enabled = state.can(RemoteKey.BACK)) { press(RemoteKey.BACK) }
            val homeLabel = stringResource(Res.string.tv_home)
            val homeEnabled = state.can(RemoteKey.HOME)
            IconButton(onClick = { press(RemoteKey.HOME) }, enabled = homeEnabled, modifier = Modifier.size(52.dp).clip(CircleShape).background(TvColors.Raised).semantics { contentDescription = homeLabel }) {
                FeatureIcon(Feature.HOME, Modifier.size(24.dp), if (homeEnabled) TvColors.Text else TvColors.Muted.copy(alpha = 0.45f))
            }
            RoundAction(stringResource(Res.string.menu), Glyph.MENU, enabled = state.can(RemoteKey.MENU)) { press(RemoteKey.MENU) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Rocker(stringResource(Res.string.volume), stringResource(Res.string.volume_up), stringResource(Res.string.volume_down),
                Glyph.PLUS, Glyph.MINUS, RemoteKey.VOLUME_UP, RemoteKey.VOLUME_DOWN, state, press, Modifier.weight(1f))
            RoundAction(stringResource(Res.string.mute), Glyph.MUTE, enabled = state.can(RemoteKey.MUTE)) { press(RemoteKey.MUTE) }
            Rocker(stringResource(Res.string.channel), stringResource(Res.string.channel_up), stringResource(Res.string.channel_down),
                Glyph.UP, Glyph.DOWN, RemoteKey.CHANNEL_UP, RemoteKey.CHANNEL_DOWN, state, press, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
    if (state.connected && state.capabilities.apps) AppsSection(state, actions)
    if (!state.connected) Text(stringResource(Res.string.connect_first), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, textAlign = TextAlign.Center)
    if (keypadOpen) KeypadDialog(state, press) { keypadOpen = false }
    if (keyboardOpen) KeyboardDialog(actions.text) { keyboardOpen = false }
}

@Composable private fun DirectionPad(state: TvUiState, press: (RemoteKey) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val size = minOf(maxWidth, 248.dp)
        Box(Modifier.size(size).clip(CircleShape).background(TvColors.RemoteGradient).border(1.dp, TvColors.Outline, CircleShape)) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(TvColors.Cyan.copy(alpha = 0.08f), radius = this.size.minDimension / 2 - 12.dp.toPx(), style = Stroke(1.dp.toPx()))
                drawCircle(TvColors.Background.copy(alpha = 0.35f), radius = this.size.minDimension / 2 - 62.dp.toPx(), style = Stroke(1.dp.toPx()))
            }
            DirectionKey(RemoteKey.UP, Glyph.UP, stringResource(Res.string.up), state, Modifier.align(Alignment.TopCenter).padding(top = 10.dp), press)
            DirectionKey(RemoteKey.DOWN, Glyph.DOWN, stringResource(Res.string.down), state, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), press)
            DirectionKey(RemoteKey.LEFT, Glyph.LEFT, stringResource(Res.string.left), state, Modifier.align(Alignment.CenterStart).padding(start = 10.dp), press)
            DirectionKey(RemoteKey.RIGHT, Glyph.RIGHT, stringResource(Res.string.right), state, Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), press)
            val enabled = state.can(RemoteKey.OK)
            Button(onClick = { press(RemoteKey.OK) }, enabled = enabled, contentPadding = PaddingValues(0.dp), shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = TvColors.Raised), modifier = Modifier.align(Alignment.Center).size(78.dp)) {
                Box(Modifier.fillMaxSize().background(if (enabled) TvColors.Gradient else TvColors.SurfaceGradient), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.ok), style = MaterialTheme.typography.titleLarge, color = if (enabled) TvColors.Background else TvColors.Muted)
                }
            }
        }
    }
}
@Composable private fun DirectionKey(key: RemoteKey, glyph: Glyph, label: String, state: TvUiState, modifier: Modifier, press: (RemoteKey) -> Unit) {
    val enabled = state.can(key)
    IconButton(onClick = { press(key) }, enabled = enabled, modifier = modifier.size(58.dp).semantics { contentDescription = label }) {
        GlyphIcon(glyph, Modifier.size(28.dp), if (enabled) TvColors.Text else TvColors.Muted.copy(alpha = 0.45f))
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
    Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(28.dp)).background(TvColors.RemoteGradient)
        .border(1.dp, if (enabled) TvColors.Cyan.copy(alpha = 0.25f) else TvColors.Outline, RoundedCornerShape(28.dp))
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
            GlyphIcon(Glyph.TOUCH, Modifier.size(34.dp), if (enabled) TvColors.Cyan else TvColors.Muted.copy(alpha = 0.45f))
            Text(hint, Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted, textAlign = TextAlign.Center)
        }
    }
    if (state.capabilities.pointer && state.connected) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.pointer_mode), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(checked = pointer, onCheckedChange = { pointer = it })
    }
}

@Composable private fun Rocker(label: String, upLabel: String, downLabel: String, upGlyph: Glyph, downGlyph: Glyph, up: RemoteKey, down: RemoteKey,
                               state: TvUiState, press: (RemoteKey) -> Unit, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(30.dp)).background(TvColors.Background.copy(alpha = 0.6f)).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RoundAction(upLabel, upGlyph, enabled = state.can(up)) { press(up) }
        Text(label, style = MaterialTheme.typography.labelLarge, color = TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        RoundAction(downLabel, downGlyph, enabled = state.can(down)) { press(down) }
    }
}
@Composable private fun ModeTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) TvColors.Raised else Color.Transparent)
        .clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }.padding(horizontal = 8.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) TvColors.Cyan else TvColors.Muted, maxLines = 1)
    }
}
@Composable internal fun KeyChip(label: String, enabled: Boolean, modifier: Modifier, glyph: Glyph? = null, onClick: () -> Unit) {
    val color = if (enabled) TvColors.Text else TvColors.Muted.copy(alpha = 0.45f)
    Box(modifier.heightIn(min = TvDimens.Touch - 8.dp).clip(RoundedCornerShape(16.dp)).background(TvColors.Raised)
        .border(1.dp, TvColors.Outline, RoundedCornerShape(16.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .then(if (glyph != null) Modifier.semantics { contentDescription = label } else Modifier).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center) {
        if (glyph != null) GlyphIcon(glyph, Modifier.size(20.dp), color)
        else Text(label, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun AppsSection(state: TvUiState, actions: TvActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.tv_apps), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        if (state.appsLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        else TextButton(onClick = actions.loadApps) { Text(stringResource(Res.string.load_apps)) }
    }
    if (state.apps.isEmpty()) {
        if (!state.appsLoading) Text(stringResource(Res.string.apps_empty), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
    } else LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(state.apps, key = { it.id }) { app ->
            Column(Modifier.width(96.dp).clip(RoundedCornerShape(20.dp)).background(TvColors.Surface)
                .border(1.dp, TvColors.Outline, RoundedCornerShape(20.dp)).clickable(role = Role.Button) { actions.launch(app) }.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(TvColors.Cyan.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Text(app.title.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = TvColors.Cyan)
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
