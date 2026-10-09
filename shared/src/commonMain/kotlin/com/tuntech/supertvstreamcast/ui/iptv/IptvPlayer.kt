package com.tuntech.supertvstreamcast.ui.iptv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.platform.CastButton
import com.tuntech.supertvstreamcast.platform.StreamPlayer
import com.tuntech.supertvstreamcast.platform.rememberPictureInPicture
import com.tuntech.supertvstreamcast.platform.rememberFullscreenRequest
import com.tuntech.supertvstreamcast.widgets.BackHandler
import com.tuntech.supertvstreamcast.theme.*
import com.tuntech.supertvstreamcast.ui.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.*
import shared.resources.*

/** The one native player of the IPTV tab; it keeps playing while it moves between the full player and the mini player. */
@Composable internal fun rememberVideo(state: IptvUiState, actions: IptvActions, attempt: Int, fill: Boolean): @Composable (Modifier, Boolean) -> Unit {
    val current by rememberUpdatedState(state)
    val callbacks by rememberUpdatedState(actions)
    val retry by rememberUpdatedState(attempt)
    val crop by rememberUpdatedState(fill)
    return remember {
        movableContentOf { modifier: Modifier, controls: Boolean ->
            val channel = current.player?.channel ?: return@movableContentOf
            // Each retry gets a fresh native player for the same channel, continuing from the last known position.
            key(channel.url, retry) {
                val start = remember { if (retry == 0) current.player?.startMs ?: 0 else resumePosition(current.entry(channel.url)) }
                StreamPlayer(channel.url, channel.userAgent, channel.referer, modifier, start, controls, crop,
                    onProgress = { position, duration -> callbacks.progress(channel.url, position, duration) }, onEnded = { callbacks.ended() }, onError = { callbacks.playerError() })
            }
        }
    }
}

/**
 * The video with the native transport controls, then the player actions and the list the channel
 * was opened from. Videos on demand resume where they stopped and report their position.
 */
@Composable internal fun IptvPlayer(state: IptvUiState, player: PlayerState, actions: IptvActions, video: @Composable (Modifier, Boolean) -> Unit,
    fill: Boolean, onFill: () -> Unit, onRetry: () -> Unit) {
    val channel = player.channel
    val requestFullscreen = rememberFullscreenRequest()
    val pip = rememberPictureInPicture()
    var fullscreen by remember { mutableStateOf(false) }
    val leaveFullscreen: () -> Unit = { fullscreen = false; requestFullscreen?.invoke(false) }
    // Back leaves full screen before it minimizes the player.
    BackHandler(enabled = fullscreen, onBack = leaveFullscreen)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // In the picture-in-picture window and sideways, the picture fills the screen.
        // Black is the picture's letterbox, not a themed surface.
        if (pip?.active == true) { video(Modifier.fillMaxSize().background(Color.Black), false); return@BoxWithConstraints }
        if (maxWidth > maxHeight) {
            video(Modifier.fillMaxSize().background(Color.Black), true)
            if (fullscreen) RoundAction(stringResource(Res.string.iptv_fullscreen_exit), Glyph.CLOSE, Modifier.align(Alignment.TopStart).padding(12.dp), onClick = leaveFullscreen)
            return@BoxWithConstraints
        }
        val now = rememberEpochSeconds()
        val programmes = state.guide[channel.url].orEmpty()
        val zapping = player.queue.size > 1 && player.queue.any { it.url == channel.url }
        Column(Modifier.fillMaxSize().padding(horizontal = TvDimens.Space).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TopRow(channel.title, channel.group, actions.back) {
                KindBadge(channel.kind)
                CastButton(Modifier.size(44.dp))
                RoundAction(stringResource(Res.string.close), Glyph.CLOSE, onClick = actions.closePlayer)
            }
            video(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp)).background(Color.Black), true)
            NowNext(upcoming(programmes, now), now)
            state.error?.let {
                ErrorNotice(it)
                if (it == UiError.PLAYER) OutlinedButton(onClick = { actions.clearError(); onRetry() }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.retry))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundAction(stringResource(Res.string.previous_channel), Glyph.LEFT, enabled = zapping) { actions.zap(-1) }
                val favorite = channel.url in state.favorites
                RoundAction(stringResource(Res.string.favorite_action), Glyph.HEART, accent = if (favorite) TvColors.Coral else TvColors.Muted) { actions.favorite(channel) }
                RoundAction(stringResource(Res.string.next_channel), Glyph.RIGHT, enabled = zapping) { actions.zap(1) }
            }
            // Secondary actions as labelled chips: guide, picture size, picture-in-picture, full screen.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (programmes.isNotEmpty()) Chip(stringResource(Res.string.iptv_programmes), false) { actions.openProgrammes(channel) }
                Chip(stringResource(Res.string.iptv_fill_screen), fill, onFill)
                if (pip != null) Chip(stringResource(Res.string.iptv_pip), false, pip.enter)
                if (requestFullscreen != null) Chip(stringResource(Res.string.iptv_fullscreen), false) { fullscreen = true; requestFullscreen(true) }
            }
            if (zapping) {
                val index = remember(player.queue, channel.url) { player.queue.indexOfFirst { it.url == channel.url }.coerceAtLeast(0) }
                val list = rememberLazyListState(index)
                LaunchedEffect(index) { list.animateScrollToItem(index) }
                LazyColumn(Modifier.fillMaxWidth().weight(1f), list, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(player.queue, key = { it.url }) { item ->
                        ChannelCard(item, item.url in state.favorites, upcoming(state.guide[item.url], now), now, { actions.favorite(item) }, { actions.play(item, player.queue) },
                            progress = if (item.kind == ContentKind.LIVE) null else state.entry(item.url)?.progress, playing = item.url == channel.url)
                    }
                }
            }
        }
    }
}
/** Small floating player over the IPTV screens: tap to return to the full player, or close it. */
@Composable internal fun MiniPlayer(player: PlayerState, actions: IptvActions, video: @Composable (Modifier, Boolean) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.width(220.dp).clip(shape).background(TvColors.Raised).border(1.dp, TvColors.Outline, shape)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            video(Modifier.fillMaxSize(), false)
            // The native view swallows touches, so a transparent layer on top takes the tap.
            val expand = stringResource(Res.string.iptv_mini_expand)
            Box(Modifier.fillMaxSize().clickable(onClickLabel = expand, role = Role.Button, onClick = actions.expand))
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(player.channel.title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            val close = stringResource(Res.string.close)
            IconButton(onClick = actions.closePlayer, modifier = Modifier.size(44.dp).semantics { contentDescription = close }) { GlyphIcon(Glyph.CLOSE, Modifier.size(16.dp), TvColors.Muted) }
        }
    }
}

/** Full-screen 4-digit passcode entry; the passcode is submitted as soon as the last digit is typed. */
@Composable internal fun PasscodeScreen(flow: PasscodeFlow, actions: IptvActions) {
    var pin by remember(flow) { mutableStateOf("") }
    LaunchedEffect(pin) { if (pin.length == PasscodeFlow.LENGTH) { delay(150); actions.submitPasscode(pin) } }
    Column(Modifier.fillMaxSize().padding(horizontal = TvDimens.Space).padding(top = 12.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        TopRow(stringResource(if (flow.purpose == PasscodePurpose.CHANGE) Res.string.iptv_passcode_change else Res.string.iptv_hidden), onBack = actions.back)
        Spacer(Modifier.weight(1f))
        GlyphIcon(Glyph.LOCK, Modifier.size(36.dp), TvColors.Violet)
        Text(stringResource(when (flow.step) {
            PasscodeStep.VERIFY -> Res.string.iptv_passcode_enter; PasscodeStep.CREATE -> Res.string.iptv_passcode_create; PasscodeStep.CONFIRM -> Res.string.iptv_passcode_confirm
        }), style = MaterialTheme.typography.headlineSmall)
        val filled = stringResource(Res.string.iptv_passcode_progress, pin.length, PasscodeFlow.LENGTH)
        Row(Modifier.semantics { contentDescription = filled }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(PasscodeFlow.LENGTH) { index ->
                Box(Modifier.size(16.dp).background(if (index < pin.length) TvColors.Violet else TvColors.Raised, CircleShape))
            }
        }
        Text(when {
            flow.error && pin.isEmpty() -> stringResource(if (flow.step == PasscodeStep.VERIFY) Res.string.iptv_passcode_wrong else Res.string.iptv_passcode_mismatch)
            flow.step == PasscodeStep.VERIFY -> ""
            else -> stringResource(Res.string.iptv_passcode_hint)
        }, style = MaterialTheme.typography.bodySmall, color = if (flow.error && pin.isEmpty()) TvColors.Error else TvColors.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, minLines = 2)
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("123", "456", "789", " 0<").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    row.forEach { key ->
                        when (key) {
                            ' ' -> Spacer(Modifier.size(72.dp))
                            '<' -> PasscodeKey(stringResource(Res.string.iptv_passcode_delete), pin.isNotEmpty(), { pin = pin.dropLast(1) }) { GlyphIcon(Glyph.BACK, color = TvColors.Muted) }
                            else -> PasscodeKey(key.toString(), pin.length < PasscodeFlow.LENGTH, { pin += key }) { Text(key.toString(), style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun PasscodeKey(label: String, enabled: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(72.dp).semantics { contentDescription = label },
        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = TvColors.Raised, contentColor = TvColors.Text, disabledContainerColor = TvColors.Raised)) { content() }
}
