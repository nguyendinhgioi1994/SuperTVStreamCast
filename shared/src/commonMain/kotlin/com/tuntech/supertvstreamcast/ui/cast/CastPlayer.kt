package com.tuntech.supertvstreamcast.ui.cast

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tuntech.supertvstreamcast.analytics.ClickEvent
import com.tuntech.supertvstreamcast.domain.Feature
import com.tuntech.supertvstreamcast.domain.cast.MediaKind
import com.tuntech.supertvstreamcast.domain.cast.PlaybackState
import com.tuntech.supertvstreamcast.domain.cast.formatClock
import com.tuntech.supertvstreamcast.theme.*
import com.tuntech.supertvstreamcast.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import org.jetbrains.compose.resources.stringResource
import shared.resources.*

/** The picked photo itself (small enough to decode), for the controller and the mini bar; null for anything else. */
@Composable private fun rememberPhoto(item: CastItem?): ByteArray? {
    val local = item?.local?.takeIf { item.kind == MediaKind.PHOTO && it.size in 1..MaxPreviewBytes }
    return produceState<ByteArray?>(null, local) {
        value = local?.let { media -> withContext(Dispatchers.IO) { runCatching { media.open().buffered().use { it.readByteArray() } }.getOrNull() } }
    }.value
}
private const val MaxPreviewBytes = 20L * 1024 * 1024

@Composable private fun Thumb(item: CastItem, size: Dp, radius: Dp, modifier: Modifier = Modifier) {
    val photo = rememberPhoto(item)
    val shape = RoundedCornerShape(radius)
    if (photo != null) AsyncImage(photo, null, modifier.size(size).clip(shape).background(TvColors.Raised), contentScale = ContentScale.Crop)
    else Box(modifier.size(size).clip(shape).background(Brush.linearGradient(item.kind.stops())).gloss(), contentAlignment = Alignment.Center) {
        CastIcon(item.kind.glyph, Modifier.size(size * 0.5f), TvColors.OnAccent)
    }
}

@Composable internal fun PlaybackState.label() = stringResource(when (this) {
    PlaybackState.PLAYING -> Res.string.cast_state_playing; PlaybackState.PAUSED -> Res.string.cast_state_paused
    PlaybackState.LOADING -> Res.string.cast_state_buffering; PlaybackState.STOPPED -> Res.string.cast_state_stopped
    PlaybackState.ERROR -> Res.string.cast_state_interrupted; PlaybackState.IDLE -> Res.string.cast_state_casting
})

/** Shown in the tab while something is queued: thumbnail, title, state, play/pause and stop. Tapping it opens the controller. */
@Composable internal fun NowCastingBar(state: CastUiState, actions: CastActions, onOpen: () -> Unit) {
    val item = state.current ?: return
    val shape = RoundedCornerShape(22.dp)
    val violet = TvColors.Violet
    val track = TvColors.Outline
    val progress = state.status.let { if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) else null }
        ?: if (item.kind == MediaKind.PHOTO && state.queue.size > 1) (state.index + 1f) / state.queue.size else null
    val subtitle = "${when { state.sending -> stringResource(Res.string.cast_state_starting); state.casting -> state.status.state.label(); else -> stringResource(Res.string.cast_choose_tv) }}" +
        (state.selected?.let { " · ${it.name}" } ?: "")
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(shape)
        .background(TvColors.Raised).border(1.dp, TvColors.Outline, shape)
        .drawBehind {
            drawRect(violet, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
            if (progress != null) {
                val y = size.height - 8.dp.toPx(); val start = 12.dp.toPx(); val end = size.width - 12.dp.toPx()
                drawLine(track, Offset(start, y), Offset(end, y), 2.dp.toPx(), StrokeCap.Round)
                drawLine(violet, Offset(start, y), Offset(start + (end - start) * progress, y), 2.dp.toPx(), StrokeCap.Round)
            }
        }.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Thumb(item, 44.dp, 12.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (state.casting) TvColors.Text else TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val playing = state.status.state == PlaybackState.PLAYING
        if (state.sending) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), color = TvColors.Violet, strokeWidth = 1.8.dp) }
        else if (item.kind != MediaKind.PHOTO) MiniButton(if (playing) Glyph.PAUSE else Glyph.PLAY, stringResource(if (playing) Res.string.cast_pause else Res.string.cast_play), state.casting) {
            ClickEvent.log("cast_mini_play"); actions.togglePlay()
        }
        MiniButton(Glyph.STOP, stringResource(Res.string.cast_stop), true) { ClickEvent.log("cast_mini_stop"); actions.stop() }
    }
}
@Composable private fun MiniButton(glyph: Glyph, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        GlyphIcon(glyph, Modifier.size(22.dp), if (enabled) TvColors.Text else TvColors.Muted.copy(alpha = 0.45f))
    }
}

/** Full controller: artwork, title and TV, timeline, transport, TV volume and Stop. Closing it never stops playback. */
@Composable internal fun ControllerSheet(state: CastUiState, actions: CastActions, onReceiver: () -> Unit, onClose: () -> Unit) {
    val item = state.current
    LaunchedEffect(item) { if (item == null) onClose() }
    if (item == null) return
    CastBottomSheet(onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val minimize = stringResource(Res.string.cast_minimize)
            IconButton(onClick = onClose, modifier = Modifier.size(52.dp).semantics { contentDescription = minimize }) { GlyphIcon(Glyph.DOWN, color = TvColors.Text) }
            Text(stringResource(when { state.sending -> Res.string.cast_state_starting; state.status.state == PlaybackState.ERROR -> Res.string.cast_state_interrupted
                else -> Res.string.cast_now_casting }), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            val chooser = stringResource(Res.string.cast_choose_tv)
            IconButton(onClick = onReceiver, modifier = Modifier.size(52.dp).semantics { contentDescription = chooser }) { CastIcon(CastGlyph.CAST, color = TvColors.Violet) }
        }
        Artwork(item)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            state.selected?.let { tv ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CastIcon(tv.kind.glyph(), Modifier.size(18.dp), TvColors.Muted)
                    Text(tv.name, style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted, maxLines = 2)
                }
            }
            StatusChip(state)
        }
        if (item.kind == MediaKind.PHOTO) PhotoProgress(state, actions) else Timeline(state, actions)
        Transport(state, actions)
        Volume(state, actions)
        state.error?.let { CastErrorBanner(it, actions.dismissError) }
        OutlinedButton(onClick = { ClickEvent.log("cast_stop"); actions.stop() }, shape = RoundedCornerShape(TvDimens.RadiusMd),
            border = BorderStroke(1.5.dp, TvColors.Error), colors = ButtonDefaults.outlinedButtonColors(containerColor = TvColors.Surface),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            GlyphIcon(Glyph.STOP, Modifier.size(20.dp), TvColors.Error); Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.cast_stop), style = MaterialTheme.typography.labelLarge, color = TvColors.Error)
        }
    }
}

@Composable private fun Artwork(item: CastItem) {
    val shape = RoundedCornerShape(if (item.kind == MediaKind.AUDIO) 28.dp else 24.dp)
    val photo = rememberPhoto(item)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val modifier = if (item.kind == MediaKind.AUDIO) Modifier.size(220.dp) else Modifier.fillMaxWidth().aspectRatio(16f / 9f).heightIn(max = 300.dp)
        Box(modifier.clip(shape).background(TvColors.Raised), contentAlignment = Alignment.Center) {
            Crossfade(photo, Modifier.fillMaxSize(), tween(TvMotion.Card), label = "castArt") { bytes ->
                if (bytes != null) AsyncImage(bytes, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Box(Modifier.fillMaxSize().background(Brush.linearGradient(item.kind.stops().map { it.copy(alpha = 0.85f) })).gloss(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(88.dp).background(TvColors.OnAccent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                        CastIcon(item.kind.glyph, Modifier.size(52.dp), TvColors.OnAccent)
                    }
                }
            }
        }
    }
}

@Composable private fun StatusChip(state: CastUiState) {
    val live = state.casting && state.status.state == PlaybackState.PLAYING
    val pulse = rememberPulse(1800, live && !LocalReducedMotion.current)
    val text = when {
        state.sending -> stringResource(Res.string.cast_state_starting)
        state.casting -> state.status.state.label()
        else -> stringResource(Res.string.cast_choose_tv)
    }
    Row(Modifier.clip(RoundedCornerShape(50)).background(TvColors.Raised).padding(horizontal = 10.dp, vertical = 5.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (live) Box(Modifier.size(8.dp).background(TvColors.Green.copy(alpha = 0.7f + 0.3f * pulse.value), CircleShape))
        else if (state.sending) CircularProgressIndicator(Modifier.size(12.dp), color = TvColors.Violet, strokeWidth = 1.5.dp)
        Text(text, style = MaterialTheme.typography.labelMedium, color = TvColors.Text)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Timeline(state: CastUiState, actions: CastActions) {
    val status = state.status
    val live = state.current?.mime?.contains("mpegurl", ignoreCase = true) == true && status.durationMs <= 0
    if (live) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("LIVE", Modifier.clip(RoundedCornerShape(6.dp)).background(TvColors.Error).padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium, color = TvColors.OnAccent)
            Text(stringResource(Res.string.cast_live), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
        }
        return
    }
    var scrub by remember { mutableStateOf<Float?>(null) }
    val known = status.durationMs > 0
    val fraction = scrub ?: if (known) (status.positionMs.toFloat() / status.durationMs).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Slider(fraction, { scrub = it }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = known && state.casting,
            onValueChangeFinished = { scrub?.let { actions.seekTo((it * status.durationMs).toLong()) }; scrub = null },
            colors = SliderDefaults.colors(thumbColor = TvColors.Violet, activeTrackColor = TvColors.Violet, inactiveTrackColor = TvColors.Outline,
                disabledThumbColor = TvColors.Outline, disabledActiveTrackColor = TvColors.Outline, disabledInactiveTrackColor = TvColors.Raised))
        Row(Modifier.fillMaxWidth()) {
            Text(formatClock(if (scrub != null) (fraction * status.durationMs).toLong() else status.positionMs), style = MaterialTheme.typography.labelLarge, color = TvColors.Muted)
            Spacer(Modifier.weight(1f))
            Text(if (known) formatClock(status.durationMs) else stringResource(Res.string.cast_duration_unknown), style = MaterialTheme.typography.labelLarge, color = TvColors.Muted)
        }
    }
}

@Composable private fun PhotoProgress(state: CastUiState, actions: CastActions) {
    val count = state.queue.size
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val shown = count.coerceAtMost(24)
            repeat(shown) { i ->
                val active = if (count <= 24) i <= state.index else i <= state.index * shown / count
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(if (active) TvColors.Violet else TvColors.Outline))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.cast_photo_index, state.index + 1, count), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = TvColors.Muted)
            if (count > 1) Row(Modifier.clip(RoundedCornerShape(50)).toggleable(state.slideshow, role = Role.Switch) { actions.toggleSlideshow() }.padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(Res.string.cast_slideshow_every), style = MaterialTheme.typography.labelMedium, color = TvColors.Muted)
                Switch(state.slideshow, null, colors = SwitchDefaults.colors(checkedTrackColor = TvColors.Violet, checkedThumbColor = TvColors.OnAccent))
            }
        }
    }
}

@Composable private fun Transport(state: CastUiState, actions: CastActions) {
    val photo = state.current?.kind == MediaKind.PHOTO
    val seekable = state.casting && state.status.durationMs > 0
    val playing = state.status.state == PlaybackState.PLAYING
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (photo) 16.dp else 6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        TransportButton(CastGlyph.PREVIOUS, stringResource(Res.string.cast_previous), state.index > 0 && !state.sending) { ClickEvent.log("cast_previous"); actions.previous() }
        if (!photo) TransportButton(CastGlyph.BACK10, stringResource(Res.string.cast_back10), seekable) { actions.skip(-10_000) }
        if (photo) {
            // A slideshow is paused by the app, not by the TV.
            PlayButton(state.slideshow && state.queue.size > 1, stringResource(if (state.slideshow) Res.string.cast_pause else Res.string.cast_play), state.queue.size > 1, false) {
                actions.toggleSlideshow()
            }
        } else PlayButton(playing, stringResource(if (playing) Res.string.cast_pause else Res.string.cast_play), state.casting, state.sending) {
            ClickEvent.log("cast_play_pause"); actions.togglePlay()
        }
        if (!photo) TransportButton(CastGlyph.FORWARD10, stringResource(Res.string.cast_forward10), seekable) { actions.skip(10_000) }
        TransportButton(CastGlyph.NEXT, stringResource(Res.string.cast_next), state.index < state.queue.lastIndex && !state.sending) { ClickEvent.log("cast_next"); actions.next() }
    }
}

@Composable private fun TransportButton(glyph: CastGlyph, label: String, enabled: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    IconButton(onClick = onClick, enabled = enabled, interactionSource = source, modifier = Modifier.size(52.dp).pressScale(source, 0.96f)
        .clip(CircleShape).background(TvColors.Raised).semantics { contentDescription = label }) {
        CastIcon(glyph, color = if (enabled) TvColors.Text else TvColors.Muted.copy(alpha = 0.45f))
    }
}

@Composable private fun PlayButton(playing: Boolean, label: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val stops = TvColors.featureStops(Feature.MIRROR)
    Box(Modifier.size(72.dp).pressScale(source, 0.96f).then(if (enabled) Modifier.shadow(12.dp, CircleShape, ambientColor = stops.first(), spotColor = stops.last()) else Modifier)
        .clip(CircleShape).background(if (enabled) Brush.linearGradient(stops) else Brush.linearGradient(listOf(TvColors.Raised, TvColors.Raised)))
        .clickable(interactionSource = source, indication = LocalIndication.current, enabled = enabled && !busy, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = TvColors.OnAccent, strokeWidth = 2.dp)
        else Crossfade(playing, animationSpec = tween(TvMotion.Control), label = "playGlyph") {
            GlyphIcon(if (it) Glyph.PAUSE else Glyph.PLAY, Modifier.size(32.dp), if (enabled) TvColors.OnAccent else TvColors.Muted)
        }
    }
}

@Composable private fun Volume(state: CastUiState, actions: CastActions) {
    val volume = state.status.volume
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.cast_tv_volume), style = MaterialTheme.typography.titleSmall)
        if (volume == null) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(TvDimens.RadiusMd)).background(TvColors.Raised).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CastIcon(CastGlyph.SPEAKER, Modifier.size(20.dp), TvColors.Muted)
                Text(stringResource(Res.string.cast_volume_unknown), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
            }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundAction(stringResource(Res.string.cast_volume_down), Glyph.MINUS, enabled = volume > 0) { ClickEvent.log("cast_volume_down"); actions.volume(-5) }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                CastIcon(CastGlyph.SPEAKER, Modifier.size(20.dp), TvColors.Violet)
                Text("$volume%", style = MaterialTheme.typography.titleMedium)
            }
            RoundAction(stringResource(Res.string.cast_volume_up), Glyph.PLUS, enabled = volume < 100) { ClickEvent.log("cast_volume_up"); actions.volume(5) }
        }
    }
}
