package com.tuntech.supertvstreamcast.ui.iptv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.theme.*
import com.tuntech.supertvstreamcast.ui.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.*
import shared.resources.*

internal fun IptvNotice.label() = when (this) {
    IptvNotice.ADDED -> Res.string.iptv_notice_added; IptvNotice.REFRESHED -> Res.string.iptv_notice_refreshed
    IptvNotice.REFRESH_FAILED -> Res.string.iptv_notice_refresh_failed; IptvNotice.DELETED -> Res.string.iptv_notice_deleted
    IptvNotice.SAVED -> Res.string.iptv_notice_saved; IptvNotice.HIDDEN -> Res.string.iptv_notice_hidden
    IptvNotice.UNHIDDEN -> Res.string.iptv_notice_unhidden; IptvNotice.PASSCODE_SET -> Res.string.iptv_notice_passcode
    IptvNotice.REMINDER_SET -> Res.string.iptv_notice_reminder_set; IptvNotice.REMINDER_REMOVED -> Res.string.iptv_notice_reminder_removed
    IptvNotice.REMINDER_DENIED -> Res.string.iptv_notice_reminder_denied; IptvNotice.QR_INVALID -> Res.string.iptv_notice_qr_invalid
}
internal fun SourceType.label() = when (this) {
    SourceType.PLAYLIST -> Res.string.iptv_type_playlist; SourceType.FILE -> Res.string.iptv_type_file
    SourceType.XTREAM -> Res.string.iptv_type_xtream; SourceType.STREAM -> Res.string.iptv_type_stream
}
internal fun SourceType.glyph() = when (this) {
    SourceType.PLAYLIST -> Glyph.LINK; SourceType.FILE -> Glyph.FILE; SourceType.XTREAM -> Glyph.LOCK; SourceType.STREAM -> Glyph.PLAY
}
internal fun ContentKind.label() = when (this) {
    ContentKind.LIVE -> Res.string.iptv_tab_live; ContentKind.MOVIE -> Res.string.iptv_tab_movies; ContentKind.SERIES -> Res.string.iptv_tab_series
}
internal fun LibraryList.label() = when (this) {
    LibraryList.FAVORITES -> Res.string.favorites; LibraryList.RECENT -> Res.string.iptv_continue; LibraryList.HIDDEN -> Res.string.iptv_hidden
}
/** Sources added without a name (device files, pasted playlists) are shown by their type. */
@Composable internal fun IptvSource.title() = name.ifBlank { stringResource(type.label()) }

/** Short confirmation that disappears on its own. */
@Composable internal fun NoticeBanner(notice: IptvNotice, onDone: () -> Unit) {
    LaunchedEffect(notice) { delay(3_500); onDone() }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TvColors.Cyan.copy(alpha = 0.1f))
        .border(1.dp, TvColors.Cyan.copy(alpha = 0.25f), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlyphIcon(Glyph.CHECK, Modifier.size(18.dp), TvColors.Cyan)
        Text(stringResource(notice.label()), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = TvColors.Text)
    }
}
@Composable internal fun TopRow(title: String, subtitle: String? = null, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RoundAction(stringResource(Res.string.back), Glyph.BACK, onClick = onBack)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}
@Composable internal fun SectionHeader(title: String, count: Int? = null, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (count != null) Text("$count", Modifier.clip(RoundedCornerShape(100.dp)).background(TvColors.Raised).padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium, color = TvColors.Muted)
        Spacer(Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}
@Composable internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(100.dp)).background(if (selected) TvColors.Coral.copy(alpha = 0.14f) else TvColors.Surface)
        .border(1.dp, if (selected) TvColors.Coral.copy(alpha = 0.5f) else TvColors.Outline, RoundedCornerShape(100.dp))
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) TvColors.Coral else TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable internal fun SegmentTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) TvColors.Raised else Color.Transparent)
        .clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }.padding(horizontal = 8.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) TvColors.Cyan else TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
/** "VOD" / "Series" tag; live channels carry none. */
@Composable internal fun KindBadge(kind: ContentKind) {
    if (kind == ContentKind.LIVE) return
    val accent = if (kind == ContentKind.SERIES) TvColors.Violet else TvColors.Blue
    Text(stringResource(if (kind == ContentKind.SERIES) Res.string.iptv_tab_series else Res.string.iptv_badge_vod),
        Modifier.clip(RoundedCornerShape(6.dp)).background(accent.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
}
/** Channel logo or poster; the play glyph stays when there is none or it fails to load. */
@Composable internal fun Logo(url: String, modifier: Modifier, scale: ContentScale = ContentScale.Fit, padding: Int = 4) {
    Box(modifier.background(TvColors.Coral.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
        var shown by remember(url) { mutableStateOf(false) }
        if (!shown) GlyphIcon(Glyph.PLAY, Modifier.size(19.dp), TvColors.Coral)
        if (url.isNotEmpty()) AsyncImage(url, null, Modifier.fillMaxSize().padding(padding.dp), contentScale = scale,
            onSuccess = { shown = true }, onError = { shown = false })
    }
}
@Composable internal fun FavoriteButton(favorite: Boolean, onClick: () -> Unit) {
    val label = stringResource(Res.string.favorite_action)
    IconToggleButton(checked = favorite, onCheckedChange = { onClick() }, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) {
        if (favorite) Box(Modifier.size(32.dp).background(TvColors.Coral.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) { GlyphIcon(Glyph.HEART, Modifier.size(20.dp), TvColors.Coral) }
        else GlyphIcon(Glyph.HEART, Modifier.size(20.dp), TvColors.Muted)
    }
}
/** Entries of the "more" menu of a channel; a null action hides its row. */
class ChannelMenu(val hidden: Boolean = false, val onHide: (() -> Unit)? = null, val onProgrammes: (() -> Unit)? = null)

@Composable internal fun ChannelCard(channel: Channel, favorite: Boolean, programmes: List<Programme>, now: Long, onFavorite: () -> Unit, onPlay: () -> Unit,
    progress: Float? = null, playing: Boolean = false, menu: ChannelMenu? = null) {
    Card(onClick = onPlay, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = TvColors.Surface),
        border = BorderStroke(1.dp, if (playing) TvColors.Cyan.copy(alpha = 0.7f) else TvColors.Outline.copy(alpha = 0.65f))) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Logo(channel.logo, Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(channel.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KindBadge(channel.kind)
                    if (channel.group.isNotBlank()) Text(channel.group, style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                NowNext(programmes, now, showNext = false)
                if (progress != null && progress > 0f) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().height(3.dp), color = TvColors.Coral, trackColor = TvColors.Outline)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FavoriteButton(favorite, onFavorite)
                if (menu != null && (menu.onHide != null || menu.onProgrammes != null)) ChannelMenuButton(menu)
            }
        }
    }
}
@Composable private fun ChannelMenuButton(menu: ChannelMenu) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.iptv_more)
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(44.dp).semantics { contentDescription = label }) { GlyphIcon(Glyph.MENU, Modifier.size(18.dp), TvColors.Muted) }
        DropdownMenu(open, { open = false }, containerColor = TvColors.Raised) {
            menu.onProgrammes?.let { action -> DropdownMenuItem({ Text(stringResource(Res.string.iptv_programmes)) }, { open = false; action() }) }
            menu.onHide?.let { action -> DropdownMenuItem({ Text(stringResource(if (menu.hidden) Res.string.iptv_unhide else Res.string.iptv_hide)) }, { open = false; action() }) }
        }
    }
}
/** Poster tile for movies and series grids and the hub rows. */
@Composable internal fun PosterTile(channel: Channel, modifier: Modifier = Modifier, wide: Boolean = false, progress: Float? = null, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(if (wide) 16f / 10f else 2f / 3f).clip(RoundedCornerShape(16.dp)).border(1.dp, TvColors.Outline, RoundedCornerShape(16.dp))) {
            Logo(channel.logo, Modifier.fillMaxSize(), if (wide) ContentScale.Fit else ContentScale.Crop, if (wide) 10 else 0)
            Box(Modifier.padding(6.dp)) { KindBadge(channel.kind) }
            if (progress != null && progress > 0f) LinearProgressIndicator(progress = { progress }, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = TvColors.Coral, trackColor = TvColors.Outline)
        }
        Text(channel.title, Modifier.padding(horizontal = 2.dp), style = MaterialTheme.typography.labelLarge, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
/** Wall clock for EPG progress, refreshed every 30 seconds while visible. */
@Composable internal fun rememberEpochSeconds(): Long {
    var now by remember { mutableStateOf(epochSeconds()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = epochSeconds() } }
    return now
}
/** Current programme with progress and minutes left, or the next one if nothing is airing. */
@Composable internal fun NowNext(programmes: List<Programme>, now: Long, showNext: Boolean = true) {
    val first = programmes.firstOrNull() ?: return
    if (isAiring(first, now)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(stringResource(Res.string.guide_now, first.title), style = MaterialTheme.typography.bodySmall, color = TvColors.Cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { ((now - first.start).toFloat() / (first.stop - first.start)).coerceIn(0f, 1f) }, Modifier.weight(1f).height(3.dp),
                    color = TvColors.Cyan, trackColor = TvColors.Outline)
                val left = ((first.stop - now + 59) / 60).toInt()
                Text(pluralStringResource(Res.plurals.guide_minutes_left, left, left), style = MaterialTheme.typography.labelSmall, color = TvColors.Muted)
            }
            if (showNext) programmes.getOrNull(1)?.let { Text(stringResource(Res.string.guide_next, it.title), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    } else Text(stringResource(Res.string.guide_next, first.title), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
/** Progress bar with the running channel count; [onCancel] adds a cancel action outside dialogs. */
@Composable internal fun ImportProgress(state: IptvUiState, onCancel: (() -> Unit)?) {
    LinearProgressIndicator(Modifier.fillMaxWidth(), color = TvColors.Coral)
    if (state.importing) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (state.importCount > 0) pluralStringResource(Res.plurals.import_progress, state.importCount, state.importCount) else stringResource(Res.string.import_connecting),
            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted)
        if (onCancel != null) TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
    }
}
@Composable internal fun EmptyNote(text: String) {
    CinemaPanel(Modifier.fillMaxWidth()) { Text(text, style = MaterialTheme.typography.bodyLarge, color = TvColors.Muted) }
}
