package com.tuntech.supertvstreamcast.ui.iptv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kashif.cameraK.compose.CameraPreviewView
import com.kashif.cameraK.compose.rememberCameraKState
import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.state.CameraConfiguration
import com.kashif.cameraK.state.CameraKState
import com.kashif.qrscannerplugin.rememberQRScannerPlugin
import com.tuntech.common.util.rememberLifecyclePermissionsController
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.theme.*
import com.tuntech.supertvstreamcast.ui.*
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.camera.CAMERA
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.*
import shared.resources.*

private val GuidePadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp)
private val ChannelColumn = 92.dp
private val RowHeight = 66.dp
private val MinuteWidth = 3.dp
private const val GRID_SECONDS = 36 * 3600L
private const val HALF_HOUR = 1800L

/** Guide of the active source for every channel that has programmes: a time grid or a now-and-next list. */
@Composable internal fun GuideScreen(state: IptvUiState, actions: IptvActions, onAddGuide: () -> Unit) {
    val now = rememberEpochSeconds()
    var grid by rememberSaveable { mutableStateOf(true) }
    var search by rememberSaveable { mutableStateOf("") }
    var reminding by remember { mutableStateOf<Pair<Channel, Programme>?>(null) }
    val channels = remember(state.channels, state.guide, state.hidden, search) {
        state.channels.filter { it.kind == ContentKind.LIVE && it.url !in state.hidden && !state.guide[it.url].isNullOrEmpty() && (search.isBlank() || it.title.contains(search, true)) }
    }
    Column(Modifier.fillMaxSize().padding(GuidePadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopRow(stringResource(Res.string.guide_title), state.active?.title(), actions.back) {
            RoundAction(stringResource(if (grid) Res.string.iptv_guide_list else Res.string.iptv_guide_grid), if (grid) Glyph.MENU else Glyph.KEYPAD) { grid = !grid }
            RoundAction(stringResource(Res.string.guide_add), Glyph.ADD, accent = TvColors.Coral, onClick = onAddGuide)
        }
        state.notice?.let { NoticeBanner(it, actions.clearNotice) }
        OutlinedTextField(search, { search = it }, placeholder = { Text(stringResource(Res.string.search)) }, leadingIcon = { GlyphIcon(Glyph.SEARCH, color = TvColors.Muted) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp))
        val play = { channel: Channel -> actions.play(channel, channels) }
        when {
            channels.isEmpty() -> EmptyNote(stringResource(if (state.guide.isEmpty()) Res.string.iptv_guide_empty else Res.string.no_results))
            grid -> GuideGrid(state, channels, now, play) { channel, programme -> reminding = channel to programme }
            else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(channels, key = { it.url }) { channel ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { play(channel) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Logo(channel.logo, Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)))
                            Text(channel.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            GlyphIcon(Glyph.PLAY, Modifier.size(16.dp), TvColors.Coral)
                        }
                        upcoming(state.guide[channel.url], now, 4).forEach { programme ->
                            ProgrammeRow(programme, now, state.reminder(channel.url, programme.start)) { reminding = channel to programme }
                        }
                    }
                }
            }
        }
    }
    reminding?.let { (channel, programme) -> ReminderDialog(channel, programme, state.reminder(channel.url, programme.start), now, actions) { reminding = null } }
}

/**
 * Channels down, time across, from the last half hour to 36 hours ahead (the span the guide
 * keeps). The ruler and every channel strip share one scroll position.
 */
@Composable private fun GuideGrid(state: IptvUiState, channels: List<Channel>, now: Long, onPlay: (Channel) -> Unit, onProgramme: (Channel, Programme) -> Unit) {
    val start = remember { (epochSeconds() / HALF_HOUR) * HALF_HOUR }
    val end = start + GRID_SECONDS
    val scroll = rememberScrollState()
    fun x(time: Long): Dp = MinuteWidth * ((time.coerceIn(start, end) - start) / 60f)
    val width = x(end)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(26.dp)) {
            Spacer(Modifier.width(ChannelColumn))
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Box(Modifier.width(width).fillMaxHeight()) {
                    for (tick in 0 until (GRID_SECONDS / HALF_HOUR).toInt()) {
                        val time = start + tick * HALF_HOUR
                        Text(formatClock(time), Modifier.offset(x = x(time) + 4.dp), style = MaterialTheme.typography.labelMedium, color = TvColors.Muted, maxLines = 1)
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            items(channels, key = { it.url }) { channel ->
                Row(Modifier.fillMaxWidth().height(RowHeight)) {
                    Column(Modifier.width(ChannelColumn).fillMaxHeight().padding(end = 6.dp).clip(RoundedCornerShape(14.dp)).background(TvColors.Raised)
                        .clickable(role = Role.Button) { onPlay(channel) }.padding(8.dp), verticalArrangement = Arrangement.Center) {
                        Text(channel.title, style = MaterialTheme.typography.labelLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
                        Box(Modifier.width(width).fillMaxHeight()) {
                            state.guide[channel.url].orEmpty().forEach { programme ->
                                if (programme.stop > start && programme.start < end) GuideCell(programme, now, state.reminder(channel.url, programme.start) != null,
                                    Modifier.offset(x = x(programme.start)).width((x(programme.stop) - x(programme.start) - 3.dp).coerceAtLeast(6.dp)).fillMaxHeight()) { onProgramme(channel, programme) }
                            }
                            if (now in start until end) Box(Modifier.offset(x = x(now)).width(2.dp).fillMaxHeight().background(TvColors.Coral))
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun GuideCell(programme: Programme, now: Long, reminded: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val airing = isAiring(programme, now)
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.clip(shape).background(if (airing) TvColors.Cyan.copy(alpha = 0.12f) else TvColors.Surface)
        .border(1.dp, if (reminded) TvColors.Coral else if (airing) TvColors.Cyan.copy(alpha = 0.4f) else TvColors.Outline.copy(alpha = 0.6f), shape)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(programme.title, style = MaterialTheme.typography.labelLarge, color = if (airing) TvColors.Text else TvColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(formatClock(programme.start), style = MaterialTheme.typography.labelMedium, color = TvColors.Muted, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

/** Programme details with the reminder choice: minutes before the start, or removing the one that is set. */
@Composable internal fun ReminderDialog(channel: Channel, programme: Programme, reminder: Reminder?, now: Long, actions: IptvActions, onDismiss: () -> Unit) {
    val offsets = remember(programme.start, now / 60) { reminderOffsets(programme.start, now) }
    var minutes by remember { mutableStateOf(offsets.firstOrNull { it == 5 } ?: offsets.firstOrNull() ?: 0) }
    val canSet = reminder == null && offsets.isNotEmpty()
    IptvDialog(programme.title, Glyph.GUIDE, if (canSet) stringResource(Res.string.iptv_reminder_title) else null, onConfirm = { actions.remind(channel, programme, minutes); onDismiss() }, onDismiss = onDismiss) {
        Text("${channel.title} · ${formatDay(programme.start)} · ${formatClock(programme.start)} – ${formatClock(programme.stop)}", style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
        if (programme.description.isNotBlank()) Text(programme.description, style = MaterialTheme.typography.bodyMedium)
        when {
            reminder != null -> {
                Text(stringResource(Res.string.iptv_reminder_at, formatClock(reminder.at)), style = MaterialTheme.typography.bodyMedium, color = TvColors.Coral)
                OutlinedButton(onClick = { actions.unremind(reminder.id); onDismiss() }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.iptv_reminder_remove))
                }
            }
            canSet -> offsets.forEach { option ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).selectable(selected = minutes == option, role = Role.RadioButton, onClick = { minutes = option })
                    .heightIn(min = 44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RadioButton(minutes == option, null)
                    Text(if (option == 0) stringResource(Res.string.iptv_reminder_at_start) else pluralStringResource(Res.plurals.iptv_reminder_before, option, option), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** The source's share link as a QR code that the scanner of another TV Space reads back. */
@Composable internal fun ShareQrDialog(source: IptvSource, onDismiss: () -> Unit) {
    val link = source.shareUrl?.let { buildImportLink(source.name, it) } ?: return
    IptvDialog(stringResource(Res.string.iptv_share_qr), Glyph.LINK, onDismiss = onDismiss) {
        // A QR code needs dark modules on a light quiet zone in both themes.
        Image(rememberQrCodePainter(link), stringResource(Res.string.iptv_share_qr),
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp))
        Text(stringResource(Res.string.iptv_qr_hint), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted)
        if (source.type == SourceType.XTREAM) Text(stringResource(Res.string.iptv_share_login_warning), style = MaterialTheme.typography.bodySmall, color = TvColors.Coral)
    }
}

/** Camera page that reads a playlist QR code; codes that are not a playlist link are ignored. */
@Composable internal fun ScannerScreen(actions: IptvActions) {
    val permissions = rememberLifecyclePermissionsController()
    var granted by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        granted = try { permissions.providePermission(Permission.CAMERA); true } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    }
    Column(Modifier.fillMaxSize().padding(GuidePadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TopRow(stringResource(Res.string.iptv_scan_qr), onBack = actions.back)
        when (granted) {
            null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = TvColors.Coral)
            false -> {
                EmptyNote(stringResource(Res.string.iptv_scan_permission))
                OutlinedButton(onClick = permissions::openAppSettings, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(Res.string.iptv_open_settings))
                }
            }
            true -> {
                Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(24.dp)).background(Color.Black), contentAlignment = Alignment.Center) { QrCamera(actions.scanned) }
                Text(stringResource(Res.string.iptv_scan_hint), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
            }
        }
    }
}
@Composable private fun QrCamera(onScanned: (String) -> Unit) {
    val scanned by rememberUpdatedState(onScanned)
    val plugin = rememberQRScannerPlugin()
    val camera by rememberCameraKState(config = CameraConfiguration(cameraLens = CameraLens.BACK), setupPlugins = { it.attachPlugin(plugin) })
    LaunchedEffect(plugin) {
        val text = plugin.getQrCodeFlow().first { parseImportLink(it) != null }
        // Stop decoding so a second frame cannot fire while the page closes.
        plugin.pauseScanning()
        scanned(text)
    }
    when (val state = camera) {
        CameraKState.Initializing -> CircularProgressIndicator(color = TvColors.Coral)
        is CameraKState.Ready -> CameraPreviewView(controller = state.controller, modifier = Modifier.fillMaxSize())
        is CameraKState.Error -> Text(stringResource(Res.string.iptv_camera_error), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = TvColors.Error)
    }
}
