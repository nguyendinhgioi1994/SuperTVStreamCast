package com.tuntech.supertvstreamcast.ui.cast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.analytics.ClickEvent
import com.tuntech.supertvstreamcast.domain.Feature
import com.tuntech.supertvstreamcast.domain.cast.CastKind
import com.tuntech.supertvstreamcast.domain.cast.CastReceiver
import com.tuntech.supertvstreamcast.domain.cast.MediaKind
import com.tuntech.supertvstreamcast.theme.*
import com.tuntech.supertvstreamcast.ui.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import shared.resources.*

/** Callbacks of the Cast tab; the pickers live in [CastTab], so here they are plain actions. */
class CastActions(
    val scan: () -> Unit = {},
    val select: (CastReceiver) -> Unit = {},
    val pickPhotos: () -> Unit = {},
    val pickVideos: () -> Unit = {},
    val pickMusic: () -> Unit = {},
    val castLink: (String) -> Boolean = { true },
    val togglePlay: () -> Unit = {},
    val seekTo: (Long) -> Unit = {},
    val skip: (Long) -> Unit = {},
    val previous: () -> Unit = {},
    val next: () -> Unit = {},
    val volume: (Int) -> Unit = {},
    val toggleSlideshow: () -> Unit = {},
    val stop: () -> Unit = {},
    val dismissError: () -> Unit = {},
)

internal enum class CastSheet { RECEIVERS, LINK, YOUTUBE, PLAYER }

private val CastArt: DrawableResource get() = if (TvColors.isDark) Res.drawable.art_cast_dark else Res.drawable.art_cast_light

/**
 * The Cast tab: title, the Screen Mirroring card with its guide steps shown inline, the receiver
 * capsule, the media grid and, while something plays, the now-casting bar. [onShare] opens the
 * system Cast settings (Android only).
 */
@Composable internal fun CastContent(state: CastUiState, actions: CastActions, onShare: (() -> Unit)?, onIptv: () -> Unit) {
    var sheet by remember { mutableStateOf<CastSheet?>(null) }
    fun open(next: CastSheet) { ClickEvent.log("cast_open_${next.name}"); sheet = next }
    // Media picked before a TV was chosen: ask for one, and search right away.
    LaunchedEffect(state.waitingForReceiver) {
        if (state.waitingForReceiver) { sheet = CastSheet.RECEIVERS; if (!state.scanning && state.receivers.isEmpty()) actions.scan() }
    }
    // The controller opens once the TV accepted the first item, not before.
    var shownFor by remember { mutableStateOf<List<CastItem>?>(null) }
    LaunchedEffect(state.casting, state.queue) {
        if (state.casting && shownFor !== state.queue && (sheet == null || sheet == CastSheet.RECEIVERS || sheet == CastSheet.LINK)) { shownFor = state.queue; sheet = CastSheet.PLAYER }
    }

    Reveal(0) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.cast_title), style = MaterialTheme.typography.headlineLarge.copy(brush = TvColors.TitleGradient))
            Text(stringResource(Res.string.cast_subtitle), style = MaterialTheme.typography.bodyLarge, color = TvColors.Muted)
        }
    }
    Reveal(1) { MirroringHero(onShare) }
    Reveal(2) {
        ReceiverBar(state) {
            open(CastSheet.RECEIVERS)
            if (!state.scanning && state.receivers.isEmpty()) actions.scan()
        }
    }
    AnimatedVisibility(state.error != null, enter = fadeIn(tween(TvMotion.Card)) + expandVertically(), exit = fadeOut(tween(TvMotion.Micro)) + shrinkVertically()) {
        state.error?.let { CastErrorBanner(it, actions.dismissError) }
    }
    AnimatedVisibility(state.current != null, enter = fadeIn(tween(TvMotion.Card)) + expandVertically(), exit = fadeOut(tween(TvMotion.Micro)) + shrinkVertically()) {
        NowCastingBar(state, actions) { open(CastSheet.PLAYER) }
    }
    Reveal(3) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.cast_media_title), style = MaterialTheme.typography.titleLarge)
            val tiles = listOf(
                MediaTile(CastGlyph.PHOTO, Res.string.cast_photos, Res.string.cast_photos_body, TvColors.featureStops(Feature.HOME)) { ClickEvent.log("cast_photos"); actions.pickPhotos() },
                MediaTile(CastGlyph.VIDEO, Res.string.cast_videos, Res.string.cast_videos_body, TvColors.featureStops(Feature.IPTV)) { ClickEvent.log("cast_videos"); actions.pickVideos() },
                MediaTile(CastGlyph.MUSIC, Res.string.cast_music, Res.string.cast_music_body, listOf(TvColors.Amber, TvColors.Violet)) { ClickEvent.log("cast_music"); actions.pickMusic() },
                MediaTile(CastGlyph.WEB, Res.string.cast_web, Res.string.cast_web_body, TvColors.featureStops(Feature.MIRROR)) { open(CastSheet.LINK) },
                MediaTile(null, Res.string.iptv, Res.string.cast_iptv_body, TvColors.featureStops(Feature.IPTV)) { ClickEvent.log("cast_iptv"); onIptv() },
                MediaTile(CastGlyph.YOUTUBE, Res.string.cast_youtube, Res.string.cast_youtube_body, TvColors.featureStops(Feature.REMOTE)) { open(CastSheet.YOUTUBE) },
            )
            tiles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { MediaCard(it, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
    TextButton(onClick = { open(CastSheet.RECEIVERS); if (!state.scanning && state.receivers.isEmpty()) actions.scan() },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        GlyphIcon(Glyph.HELP, Modifier.size(20.dp), TvColors.Violet); Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.cast_help_button), style = MaterialTheme.typography.labelLarge, color = TvColors.Violet)
    }

    when (sheet) {
        CastSheet.RECEIVERS -> ReceiverSheet(state, actions) { sheet = null }
        CastSheet.LINK -> LinkSheet(state, actions, onChooseTv = { sheet = CastSheet.RECEIVERS; if (state.receivers.isEmpty()) actions.scan() }) { sheet = null }
        CastSheet.YOUTUBE -> YouTubeGuideSheet { sheet = null }
        CastSheet.PLAYER -> ControllerSheet(state, actions, onReceiver = { sheet = CastSheet.RECEIVERS }) { sheet = null }
        null -> Unit
    }
}

// ---------------------------------------------------------------- receiver capsule

@Composable private fun ReceiverBar(state: CastUiState, onClick: () -> Unit) {
    val shape = RoundedCornerShape(TvDimens.RadiusLg)
    val live = state.casting
    val border by animateColorAsState(if (live) TvColors.Green.copy(alpha = 0.7f) else TvColors.Outline.copy(alpha = 0.8f), tween(TvMotion.Card), label = "receiverBorder")
    val source = remember { MutableInteractionSource() }
    val receiver = state.selected
    val status = when {
        state.connecting -> stringResource(Res.string.cast_state_connecting)
        live -> stringResource(Res.string.cast_state_casting)
        else -> null
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).pressScale(source, 0.98f).clip(shape).background(TvColors.SurfaceGradient).border(1.dp, border, shape)
        .clickable(interactionSource = source, indication = LocalIndication.current, role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GradientTile(TvColors.featureStops(Feature.MIRROR), 40.dp) { CastIcon(receiver?.kind?.glyph() ?: CastGlyph.CAST, Modifier.size(24.dp), TvColors.OnAccent) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.cast_to), style = MaterialTheme.typography.labelMedium, color = TvColors.Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(receiver?.name ?: stringResource(Res.string.cast_choose_tv), Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (live) Box(Modifier.size(18.dp).background(TvColors.Green, CircleShape), contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.CHECK, Modifier.size(12.dp), TvColors.OnAccent)
                }
            }
            if (status != null) Text(status, style = MaterialTheme.typography.bodySmall, color = if (live) TvColors.Green else TvColors.Muted)
        }
        if (receiver != null) KindBadge(receiver.kind)
        if (state.connecting) CircularProgressIndicator(Modifier.size(18.dp), color = TvColors.Violet, strokeWidth = 1.8.dp)
        else GlyphIcon(Glyph.DOWN, Modifier.size(18.dp), TvColors.Muted)
    }
}

internal fun CastKind.glyph() = when (this) { CastKind.DLNA -> CastGlyph.TV; CastKind.CHROMECAST -> CastGlyph.CAST; CastKind.AIRPLAY -> CastGlyph.AIRPLAY }
internal fun CastKind.label() = when (this) {
    CastKind.DLNA -> Res.string.cast_badge_dlna; CastKind.CHROMECAST -> Res.string.cast_badge_chromecast; CastKind.AIRPLAY -> Res.string.cast_badge_airplay
}

@Composable internal fun KindBadge(kind: CastKind) {
    Text(stringResource(kind.label()), Modifier.clip(RoundedCornerShape(8.dp)).background(TvColors.Raised).padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = TvColors.Muted, maxLines = 1)
}

/** A glossy rounded tile filled with [stops]; content is drawn in [TvColors.OnAccent]. */
@Composable internal fun GradientTile(stops: List<Color>, size: Dp, radius: Dp = size * 0.35f, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(radius)
    Box(Modifier.size(size).clip(shape).background(Brush.linearGradient(stops)).gloss(), contentAlignment = Alignment.Center, content = content)
}

// ---------------------------------------------------------------- hero + grid

/** Screen Mirroring with the AirPlay / Cast steps already open; there is no custom mirroring engine. */
@Composable private fun MirroringHero(onShare: (() -> Unit)?) {
    val ios = onShare == null
    val shape = RoundedCornerShape(TvDimens.RadiusXl)
    val drift = rememberPulse(14000)
    Column(Modifier.fillMaxWidth().clip(shape).background(TvColors.Surface).border(1.5.dp, Brush.linearGradient(TvColors.featureStops(Feature.MIRROR) + TvColors.Pink), shape)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2.15f)) {
            Image(painterResource(CastArt), null, Modifier.fillMaxSize().graphicsZoom(1.02f + 0.03f * drift.value), contentScale = ContentScale.Crop)
            // Fades the picture into the copy bed below, so the text never sits on the artwork.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.62f to Color.Transparent, 1f to TvColors.Surface)))
            Text(stringResource(Res.string.cast_system_guide), Modifier.padding(start = 16.dp, top = 12.dp).clip(RoundedCornerShape(50))
                .background(Brush.linearGradient(TvColors.featureStops(Feature.MIRROR))).padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium, color = TvColors.OnAccent)
        }
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientTile(TvColors.featureStops(Feature.MIRROR), 40.dp) { CastIcon(if (ios) CastGlyph.AIRPLAY else CastGlyph.CAST, Modifier.size(24.dp), TvColors.OnAccent) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.cast_mirroring), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(Res.string.cast_mirror_help), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
                }
            }
            GuideSteps(listOf(stringResource(Res.string.cast_tip_wifi),
                stringResource(if (ios) Res.string.cast_mirror_ios_step2 else Res.string.cast_mirror_android_step2),
                stringResource(if (ios) Res.string.cast_mirror_ios_step3 else Res.string.cast_mirror_android_step3)))
            if (onShare != null) PrimaryCta(stringResource(Res.string.cast_open_cast_settings), glyph = Glyph.RIGHT) { ClickEvent.log("cast_open_system"); onShare() }
        }
    }
}

@Composable private fun Modifier.graphicsZoom(zoom: Float): Modifier =
    if (LocalReducedMotion.current) this else this.then(Modifier.graphicsLayerScale(zoom))
private fun Modifier.graphicsLayerScale(zoom: Float) = this.then(Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom })

private class MediaTile(val glyph: CastGlyph?, val title: StringResource, val body: StringResource, val stops: List<Color>, val onClick: () -> Unit)

/** Same tile as Home's quick access: the tile's gradient, white copy, flat. */
@Composable private fun MediaCard(tile: MediaTile, modifier: Modifier) {
    GradientCard(Brush.linearGradient(tile.stops), modifier, onClick = tile.onClick) {
        Column(Modifier.fillMaxSize().heightIn(min = 128.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(TvColors.OnAccent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    if (tile.glyph != null) CastIcon(tile.glyph, Modifier.size(24.dp), TvColors.OnAccent) else FeatureIcon(Feature.IPTV, Modifier.size(24.dp), TvColors.OnAccent)
                }
                ArrowChip()
            }
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(tile.title), style = MaterialTheme.typography.titleMedium, color = TvColors.OnAccent)
                Text(stringResource(tile.body), style = MaterialTheme.typography.bodySmall, color = TvColors.OnAccent.copy(alpha = 0.88f))
            }
        }
    }
}

// ---------------------------------------------------------------- error banner

internal fun CastError.text() = when (this) {
    CastError.NO_WIFI -> Res.string.cast_error_no_wifi; CastError.NO_RECEIVER -> Res.string.cast_error_receiver
    CastError.CONNECT -> Res.string.cast_error_connect; CastError.LOAD -> Res.string.cast_error_load
    CastError.BAD_LINK -> Res.string.cast_error_bad_link; CastError.CONTROL -> Res.string.cast_error_control
}

@Composable internal fun CastErrorBanner(error: CastError, onDismiss: () -> Unit) {
    val shape = RoundedCornerShape(TvDimens.RadiusMd)
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape).background(TvColors.Surface).border(1.dp, TvColors.Error.copy(alpha = 0.65f), shape)
        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlyphIcon(if (error == CastError.NO_WIFI) Glyph.WIFI else Glyph.INFO, color = TvColors.Error)
        Text(stringResource(error.text()), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = TvColors.Text)
        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            val label = stringResource(Res.string.cast_dismiss)
            GlyphIcon(Glyph.CLOSE, Modifier.size(20.dp).semantics { contentDescription = label }, TvColors.Muted)
        }
    }
}

// ---------------------------------------------------------------- sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CastBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = TvDimens.RadiusXl, topEnd = TvDimens.RadiusXl), containerColor = TvColors.Surface,
        scrimColor = Color.Black.copy(alpha = if (TvColors.isDark) 0.56f else 0.32f),
        dragHandle = { Box(Modifier.padding(vertical = 10.dp).size(32.dp, 4.dp).background(TvColors.Outline, RoundedCornerShape(2.dp))) }) {
        Column(Modifier.fillMaxWidth().widthIn(max = TvDimens.ContentMaxWidth).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable internal fun SheetHeader(title: String, body: String?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
        }
        val label = stringResource(Res.string.cast_close)
        IconButton(onClick = onClose, modifier = Modifier.size(52.dp).semantics { contentDescription = label }) { GlyphIcon(Glyph.CLOSE, color = TvColors.Text) }
    }
}

@Composable private fun ReceiverSheet(state: CastUiState, actions: CastActions, onClose: () -> Unit) {
    CastBottomSheet(onClose) {
        SheetHeader(stringResource(Res.string.cast_choose_tv), stringResource(Res.string.cast_sheet_body), onClose)
        if (state.scanning) ScannerPanel()
        state.receivers.forEach { receiver ->
            ReceiverRow(receiver, state) { if (receiver.id == state.selected?.id && !state.waitingForReceiver) onClose() else { ClickEvent.log("cast_pick_receiver", "kind" to receiver.kind); actions.select(receiver) } }
        }
        // Close once the chosen TV accepted the session (the controller then takes over if media waits).
        val selectedId = state.selected?.id
        var openedWith by remember { mutableStateOf(selectedId) }
        LaunchedEffect(selectedId) { if (selectedId != null && selectedId != openedWith && !state.waitingForReceiver) onClose(); openedWith = selectedId }
        if (state.scanned && !state.scanning && state.receivers.isEmpty()) EmptyHelp()
        else if (state.receivers.isNotEmpty() && !state.scanning) Tips()
        PrimaryCta(stringResource(when {
            state.scanning -> Res.string.cast_searching
            state.scanned -> Res.string.cast_search_again
            else -> Res.string.cast_search_start
        }), enabled = !state.scanning && !state.connecting, glyph = Glyph.REFRESH) { ClickEvent.log("cast_scan"); actions.scan() }
        state.error?.takeIf { it == CastError.CONNECT }?.let { CastErrorBanner(it, actions.dismissError) }
    }
}

@Composable private fun ScannerPanel() {
    val shape = RoundedCornerShape(TvDimens.RadiusMd)
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape).background(TvColors.Raised).padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Scanner(Modifier.size(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.cast_searching), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(Res.string.cast_searching_body), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted)
        }
    }
}

/** Cast glyph with three arcs rippling outwards; still when reduced motion is on. */
@Composable private fun Scanner(modifier: Modifier) {
    val reduced = LocalReducedMotion.current
    val sweep = rememberSweep(1800, !reduced)
    val color = TvColors.Violet
    Box(modifier.drawBehind {
        if (!reduced) for (i in 0..2) {
            val t = (sweep.value + i / 3f) % 1f
            val r = (8f + 14f * t) / 32f * size.width
            drawCircle(color.copy(alpha = 0.55f * (1f - t)), r, center, style = Stroke(1.5.dp.toPx()))
        }
    }, contentAlignment = Alignment.Center) { CastIcon(CastGlyph.CAST, Modifier.size(20.dp), color) }
}

@Composable private fun ReceiverRow(receiver: CastReceiver, state: CastUiState, onClick: () -> Unit) {
    val selected = receiver.id == state.selected?.id
    val live = selected && state.casting
    val connecting = state.connecting && !selected
    val shape = RoundedCornerShape(TvDimens.RadiusMd)
    val border by animateColorAsState(when { live -> TvColors.Green; selected -> TvColors.Violet; else -> TvColors.Outline.copy(alpha = 0.6f) }, tween(TvMotion.Card), label = "rowBorder")
    val stateLabel = stringResource(when { live -> Res.string.cast_state_casting; selected -> Res.string.cast_state_selected; else -> Res.string.cast_state_available })
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(shape).background(TvColors.Raised.copy(alpha = if (selected) 1f else 0.6f)).border(1.dp, border, shape)
        .selectable(selected = selected, enabled = !state.connecting, role = Role.RadioButton, onClick = onClick).padding(12.dp)
        .semantics { stateDescription = stateLabel },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradientTile(if (receiver.kind == CastKind.CHROMECAST) TvColors.featureStops(Feature.HOME) else TvColors.featureStops(Feature.MIRROR), 44.dp) {
            CastIcon(receiver.kind.glyph(), Modifier.size(24.dp), TvColors.OnAccent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(receiver.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KindBadge(receiver.kind)
                Text(if (state.connecting && selected) stringResource(Res.string.cast_state_connecting) else stateLabel,
                    style = MaterialTheme.typography.labelMedium, color = if (live) TvColors.Green else TvColors.Muted, maxLines = 1)
            }
            if (receiver.model.isNotBlank()) Text(receiver.model, style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            when {
                connecting -> Unit
                state.connecting -> CircularProgressIndicator(Modifier.size(18.dp), color = TvColors.Violet, strokeWidth = 1.8.dp)
                selected -> Box(Modifier.size(24.dp).background(if (live) TvColors.Green else TvColors.Violet, CircleShape), contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.CHECK, Modifier.size(14.dp), TvColors.OnAccent)
                }
                else -> GlyphIcon(Glyph.RIGHT, Modifier.size(18.dp), TvColors.Muted)
            }
        }
    }
}

@Composable private fun EmptyHelp() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GradientTile(TvColors.featureStops(Feature.MIRROR), 72.dp, radius = 24.dp) { CastIcon(CastGlyph.TV, Modifier.size(36.dp), TvColors.OnAccent) }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(Res.string.cast_none_found), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(Res.string.cast_none_found_body), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
    }
    Tips()
}

@Composable private fun Tips() {
    CinemaPanel(Modifier.fillMaxWidth()) {
        Tip(Glyph.WIFI, Res.string.cast_tip_wifi)
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CastIcon(CastGlyph.TV, color = TvColors.Violet); Text(stringResource(Res.string.cast_tip_dlna), style = MaterialTheme.typography.bodyMedium)
        }
        Tip(Glyph.POWER, Res.string.cast_tip_awake)
        if (isIos) Tip(Glyph.LOCK, Res.string.cast_tip_ios_network)
    }
}
@Composable private fun Tip(glyph: Glyph, text: StringResource) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlyphIcon(glyph, color = TvColors.Violet); Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable private fun LinkSheet(state: CastUiState, actions: CastActions, onChooseTv: () -> Unit, onClose: () -> Unit) {
    var link by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    CastBottomSheet(onClose) {
        SheetHeader(stringResource(Res.string.cast_link_title), stringResource(Res.string.cast_link_body), onClose)
        OutlinedTextField(link, { link = it; invalid = false }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.cast_link_label)) },
            placeholder = { Text("https://example.com/video.mp4", color = TvColors.Muted) }, maxLines = 4, shape = RoundedCornerShape(TvDimens.RadiusMd),
            isError = invalid, leadingIcon = { GlyphIcon(Glyph.LINK, Modifier.size(20.dp), TvColors.Violet) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            supportingText = if (invalid) ({ Text(stringResource(Res.string.cast_link_invalid), color = TvColors.Error) }) else null)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("MP4", "M3U8", "MP3").forEach { Chip(it) } }
        ReceiverBar(state, onChooseTv)
        PrimaryCta(stringResource(Res.string.cast_link_cast), enabled = link.isNotBlank() && !state.sending, glyph = Glyph.PLAY) {
            ClickEvent.log("cast_link")
            if (actions.castLink(link)) { if (state.selected != null) onClose() } else invalid = true
        }
    }
}
@Composable private fun Chip(text: String) {
    Text(text, Modifier.clip(RoundedCornerShape(8.dp)).background(TvColors.Raised).padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = TvColors.Muted)
}

@Composable private fun GuideSheet(glyph: @Composable () -> Unit, stops: List<Color>, title: String, help: String, steps: List<String>, onClose: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit) {
    CastBottomSheet(onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            GradientTile(stops, 56.dp, radius = 18.dp) { glyph() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(help, style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
            }
        }
        GuideSteps(steps)
        footer()
    }
}

@Composable private fun GuideSteps(steps: List<String>) {
    CinemaPanel(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(28.dp).background(Brush.linearGradient(TvColors.featureStops(Feature.MIRROR)), CircleShape), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = TvColors.OnAccent)
                }
                Text(step, Modifier.weight(1f).padding(top = 3.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable private fun YouTubeGuideSheet(onClose: () -> Unit) {
    val uri = LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }
    GuideSheet({ CastIcon(CastGlyph.YOUTUBE, Modifier.size(30.dp), TvColors.OnAccent) }, TvColors.featureStops(Feature.REMOTE),
        stringResource(Res.string.cast_youtube_title), stringResource(Res.string.cast_youtube_help),
        listOf(stringResource(Res.string.cast_youtube_step1), stringResource(Res.string.cast_youtube_step2), stringResource(Res.string.cast_youtube_step3)), onClose) {
        if (failed) Text(stringResource(Res.string.cast_youtube_failed), style = MaterialTheme.typography.bodyMedium, color = TvColors.Error)
        PrimaryCta(stringResource(Res.string.cast_youtube_open), glyph = Glyph.RIGHT) {
            ClickEvent.log("cast_open_youtube")
            failed = runCatching { uri.openUri("https://www.youtube.com") }.isFailure
            if (!failed) onClose()
        }
        OutlinedButton(onClick = onClose, shape = RoundedCornerShape(TvDimens.RadiusMd), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.cast_done))
        }
    }
}

internal val MediaKind.glyph get() = when (this) { MediaKind.PHOTO -> CastGlyph.PHOTO; MediaKind.VIDEO -> CastGlyph.VIDEO; MediaKind.AUDIO -> CastGlyph.MUSIC }
@Composable internal fun MediaKind.stops() = when (this) {
    MediaKind.PHOTO -> TvColors.featureStops(Feature.HOME); MediaKind.VIDEO -> TvColors.featureStops(Feature.IPTV); MediaKind.AUDIO -> listOf(TvColors.Amber, TvColors.Violet)
}

/** iPhone / iPad: AirPlay wording and the Local Network permission tip. */
internal val isIos: Boolean by lazy { !com.tuntech.supertvstreamcast.getPlatform().name.startsWith("Android") }
