package com.tuntech.supertvstreamcast.ui.iptv

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.theme.*
import com.tuntech.supertvstreamcast.ui.*
import org.jetbrains.compose.resources.*
import shared.resources.*

/** The IPTV tab: the screen on top of [IptvUiState.stack], or the player while something plays. */
@Composable internal fun IptvScaffold(state: IptvUiState, actions: IptvActions) {
    var sheet by rememberSaveable { mutableStateOf<IptvSheet?>(null) }
    var seen by rememberSaveable { mutableStateOf(state.imported) }
    LaunchedEffect(state.imported) { if (state.imported != seen) { seen = state.imported; sheet = null } }
    val open: (IptvSheet) -> Unit = { actions.clearError(); sheet = it }
    val player = state.player
    var attempt by remember(player?.channel?.url) { mutableStateOf(0) }
    var fill by rememberSaveable { mutableStateOf(false) }
    val video = rememberVideo(state, actions, attempt, fill)
    Box(Modifier.fillMaxSize()) {
        if (player != null && !player.minimized) IptvPlayer(state, player, actions, video, fill, { fill = !fill }) { attempt++ }
        else {
            when (val screen = state.screen) {
                IptvScreen.Hub -> IptvHub(state, actions, sheet == null, open)
                IptvScreen.Source -> SourceScreen(state, actions, sheet == null, open)
                is IptvScreen.Library -> LibraryScreen(state, screen.list, actions)
                is IptvScreen.Series -> SeriesScreen(state, screen.series, actions)
                is IptvScreen.Programmes -> ProgrammesScreen(state, screen.channel, actions)
                is IptvScreen.Passcode -> PasscodeScreen(screen.flow, actions)
                IptvScreen.Guide -> GuideScreen(state, actions) { open(IptvSheet.GUIDE) }
                IptvScreen.Scanner -> ScannerScreen(actions)
            }
            if (player != null) MiniPlayer(player, actions, video, Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
    }
    IptvSheets(sheet, state, actions) { sheet = it }
    state.pendingImport?.let { request ->
        AlertDialog(onDismissRequest = actions.dismissImport, shape = RoundedCornerShape(30.dp), containerColor = TvColors.Surface,
            title = { Text(stringResource(Res.string.iptv_import_title), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Only the host is shown: the rest of the link can carry a token or a login.
                    Text(stringResource(Res.string.iptv_import_body, request.name.ifBlank { defaultSourceName(request.url) }, request.url.substringAfter("://").substringBefore('/').substringBefore('?')),
                        style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
                    if (request.url.startsWith("http://", true)) Text(stringResource(Res.string.http_warning), style = MaterialTheme.typography.bodySmall, color = TvColors.Coral)
                }
            },
            confirmButton = { TextButton(onClick = actions.acceptImport, enabled = !state.busy) { Text(stringResource(Res.string.import_playlist)) } },
            dismissButton = { TextButton(onClick = actions.dismissImport) { Text(stringResource(Res.string.cancel)) } })
    }
}

private val ScreenPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)

@Composable private fun IptvHub(state: IptvUiState, actions: IptvActions, showStatus: Boolean, open: (IptvSheet) -> Unit) {
    var details by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val recent = remember(state.entries) { state.entries.list(LibraryList.RECENT) }
    val favorites = remember(state.entries) { state.entries.list(LibraryList.FAVORITES) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = ScreenPadding) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(Res.string.iptv), style = MaterialTheme.typography.labelSmall, color = TvColors.Coral)
                    Text(stringResource(Res.string.library), style = MaterialTheme.typography.headlineLarge)
                }
                if (state.hidden.isNotEmpty() || state.passcodeSet) RoundAction(stringResource(Res.string.iptv_hidden), Glyph.LOCK, accent = TvColors.Violet) { actions.openLibrary(LibraryList.HIDDEN) }
                RoundAction(stringResource(Res.string.iptv_help), Glyph.HELP, accent = TvColors.Cyan) { open(IptvSheet.HELP) }
                RoundAction(stringResource(Res.string.add_playlist), Glyph.ADD, enabled = !state.busy, accent = TvColors.Coral) { open(IptvSheet.SOURCES) }
            }
        }
        state.notice?.let { item(key = "notice") { NoticeBanner(it, actions.clearNotice) } }
        if (showStatus && details == null) state.error?.let { item { ErrorNotice(it) } }
        if (state.busy && showStatus) item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { ImportProgress(state, actions.cancelImport) } }
        if (!state.loaded) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = TvColors.Coral) }
        else if (state.sources.isEmpty()) {
            item {
                CinemaPanel(Modifier.fillMaxWidth()) {
                    Artwork(Modifier.height(170.dp), TvArt.iptv)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.playlist_empty_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(Res.string.playlist_empty_body), style = MaterialTheme.typography.bodyLarge, color = TvColors.Muted)
                }
            }
            item { Text(stringResource(Res.string.iptv_sources), style = MaterialTheme.typography.titleLarge) }
            item { SourceChoices(state, actions) { next -> actions.clearError(); next?.let(open) } }
            item { HelpCard { open(IptvSheet.HELP) } }
            item { Text(stringResource(Res.string.playlist_body), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted) }
        } else {
            if (recent.isNotEmpty()) {
                item { SectionHeader(stringResource(Res.string.iptv_continue), recent.size, stringResource(Res.string.iptv_view_all)) { actions.openLibrary(LibraryList.RECENT) } }
                item { EntryRow(recent, actions) }
            }
            if (favorites.isNotEmpty()) {
                item { SectionHeader(stringResource(Res.string.favorites), favorites.size, stringResource(Res.string.iptv_view_all)) { actions.openLibrary(LibraryList.FAVORITES) } }
                item { EntryRow(favorites, actions) }
            }
            item { SectionHeader(stringResource(Res.string.iptv_sources_title), state.sources.size, stringResource(Res.string.iptv_add_source)) { open(IptvSheet.SOURCES) } }
            items(state.sources, key = { it.id }) { source ->
                SourceCard(source, !state.busy, { actions.open(source.id) }, { actions.refresh(source.id) }, { details = source.id }, { deleting = source.id })
            }
            item { Text(stringResource(Res.string.iptv_saved_note), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted) }
        }
    }
    state.sources.firstOrNull { it.id == details }?.let { SourceDetailsDialog(it, state, actions) { details = null; actions.clearError() } }
    state.sources.firstOrNull { it.id == deleting }?.let { source ->
        ConfirmDialog(stringResource(Res.string.iptv_delete_title), stringResource(Res.string.iptv_delete_body, source.title()), stringResource(Res.string.iptv_delete),
            { actions.delete(source.id) }) { deleting = null }
    }
}
/** The first few entries of a library list as tiles; "View all" opens the rest. */
@Composable private fun EntryRow(entries: List<LibraryEntry>, actions: IptvActions) {
    val shown = remember(entries) { entries.take(12) }
    val queue = remember(shown) { shown.map { it.channel } }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(shown, key = { it.channel.url }) { entry ->
            PosterTile(entry.channel, Modifier.width(150.dp), wide = true, progress = entry.progress) { actions.play(entry.channel, queue) }
        }
    }
}
@Composable private fun SourceCard(source: IptvSource, enabled: Boolean, onOpen: () -> Unit, onRefresh: () -> Unit, onDetails: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Card(onClick = onOpen, enabled = enabled, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, TvColors.Outline.copy(alpha = 0.65f)),
        colors = CardDefaults.cardColors(containerColor = TvColors.Surface, disabledContainerColor = TvColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(TvColors.Coral.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                GlyphIcon(source.type.glyph(), Modifier.size(20.dp), TvColors.Coral)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(source.title(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(stringResource(source.type.label()), pluralStringResource(Res.plurals.iptv_item_count, source.channelCount, source.channelCount)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val label = stringResource(Res.string.iptv_more)
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).semantics { contentDescription = label }) { GlyphIcon(Glyph.MENU, Modifier.size(18.dp), TvColors.Muted) }
                DropdownMenu(menu, { menu = false }, containerColor = TvColors.Raised) {
                    if (source.refreshable) DropdownMenuItem({ Text(stringResource(Res.string.iptv_refresh)) }, { menu = false; onRefresh() }, enabled = enabled)
                    DropdownMenuItem({ Text(stringResource(Res.string.iptv_details)) }, { menu = false; onDetails() })
                    DropdownMenuItem({ Text(stringResource(Res.string.iptv_delete), color = TvColors.Error) }, { menu = false; onDelete() }, enabled = enabled)
                }
            }
        }
    }
}
@Composable private fun HelpCard(onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = TvColors.Surface),
        border = BorderStroke(1.dp, TvColors.Cyan.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlyphIcon(Glyph.HELP, color = TvColors.Cyan)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(Res.string.iptv_help_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.iptv_help_body), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted)
            }
            GlyphIcon(Glyph.RIGHT, Modifier.size(18.dp), TvColors.Cyan)
        }
    }
}

private fun LazyGridScope.wide(key: Any? = null, content: @Composable () -> Unit) = item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

/** Channels of the active source: Live / Movies / Series tabs when it has more than one kind, group chips, search. */
@Composable private fun SourceScreen(state: IptvUiState, actions: IptvActions, showStatus: Boolean, open: (IptvSheet) -> Unit) {
    val source = state.active ?: return
    var search by rememberSaveable(source.id) { mutableStateOf("") }
    var group by rememberSaveable(source.id) { mutableStateOf("") }
    var tab by rememberSaveable(source.id) { mutableStateOf(ContentKind.LIVE) }
    var details by rememberSaveable { mutableStateOf(false) }
    val now = rememberEpochSeconds()
    val kinds = remember(state.channels) { ContentKind.entries.filter { kind -> state.channels.any { it.kind == kind } } }
    val kind = if (tab in kinds) tab else kinds.firstOrNull() ?: ContentKind.LIVE
    val ofKind = remember(state.channels, state.hidden, kind) { state.channels.filter { it.kind == kind && it.url !in state.hidden } }
    val groups = remember(ofKind) { channelGroups(ofKind) }
    val selected = group.takeIf { it in groups }.orEmpty()
    // A search looks through every kind of the source, not just the open tab.
    val visible = remember(state.channels, state.hidden, ofKind, search, selected) {
        if (search.isBlank()) ofKind.filter { selected.isEmpty() || it.group == selected }
        else state.channels.filter { it.url !in state.hidden && (it.title.contains(search, true) || it.group.contains(search, true)) }
    }
    val posters = search.isBlank() && kind != ContentKind.LIVE
    LazyVerticalGrid(GridCells.Fixed(if (posters) 3 else 1), Modifier.fillMaxSize(), contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        wide {
            TopRow(source.title(), source.host, actions.back) {
                RoundAction(stringResource(Res.string.iptv_details), Glyph.MENU) { details = true }
            }
        }
        state.notice?.let { wide("notice") { NoticeBanner(it, actions.clearNotice) } }
        if (showStatus && !details) state.error?.let { wide { ErrorNotice(it) } }
        if (state.busy && showStatus && !details) wide { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { ImportProgress(state, actions.cancelImport) } }
        if (state.catalogLoading) wide {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = TvColors.Coral)
                Text(stringResource(Res.string.iptv_loading_channels), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted)
            }
        }
        if (source.truncated) wide { Text(stringResource(Res.string.library_truncated), style = MaterialTheme.typography.bodySmall, color = TvColors.Coral) }
        if (state.channels.isNotEmpty()) {
            wide {
                OutlinedTextField(search, { search = it }, placeholder = { Text(stringResource(Res.string.search)) }, leadingIcon = { GlyphIcon(Glyph.SEARCH, color = TvColors.Muted) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp))
            }
            if (search.isBlank()) {
                if (kinds.size > 1) wide {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TvColors.Surface).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        kinds.forEach { SegmentTab(stringResource(it.label()), kind == it, Modifier.weight(1f)) { tab = it; group = "" } }
                    }
                }
                if (kind == ContentKind.LIVE) wide { GuideBanner(state) { if (state.guide.isEmpty()) open(IptvSheet.GUIDE) else actions.openGuide() } }
                if (groups.isNotEmpty()) wide {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Chip(stringResource(Res.string.all_groups), selected.isEmpty()) { group = "" } }
                        items(groups, key = { it }) { name -> Chip(name, selected == name) { group = name } }
                    }
                }
            }
            if (visible.isEmpty()) wide { EmptyNote(stringResource(Res.string.no_results)) }
        } else if (!state.catalogLoading && !state.busy) wide { EmptyNote(stringResource(Res.string.iptv_empty_source)) }
        items(visible, key = { it.url }) { channel ->
            if (posters) PosterTile(channel, progress = state.entry(channel.url)?.progress) { actions.play(channel, visible) }
            else ChannelCard(channel, channel.url in state.favorites, upcoming(state.guide[channel.url], now), now, { actions.favorite(channel) }, { actions.play(channel, visible) },
                progress = if (channel.kind == ContentKind.LIVE) null else state.entry(channel.url)?.progress,
                menu = ChannelMenu(onHide = { actions.hide(channel, true) }, onProgrammes = if (state.guide[channel.url].isNullOrEmpty()) null else ({ actions.openProgrammes(channel) })))
        }
    }
    if (details) SourceDetailsDialog(source, state, actions) { details = false; actions.clearError() }
}
@Composable private fun GuideBanner(state: IptvUiState, onClick: () -> Unit) {
    val loaded = state.guide.isNotEmpty()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TvColors.Surface).clickable(enabled = !state.busy, role = Role.Button, onClick = onClick)
        .padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlyphIcon(Glyph.GUIDE, Modifier.size(20.dp), if (loaded) TvColors.Cyan else TvColors.Coral)
        Text(when {
            loaded -> pluralStringResource(Res.plurals.guide_loaded, state.guide.size, state.guide.size)
            state.guideLoading -> stringResource(Res.string.iptv_guide_loading)
            else -> stringResource(Res.string.guide_add)
        }, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = TvColors.Text)
        GlyphIcon(Glyph.RIGHT, Modifier.size(18.dp), TvColors.Muted)
    }
}

/** Favorites, recently watched or hidden channels across all sources. */
@Composable private fun LibraryScreen(state: IptvUiState, list: LibraryList, actions: IptvActions) {
    var clearing by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable(list) { mutableStateOf("") }
    val now = rememberEpochSeconds()
    val all = remember(state.entries, list) { state.entries.list(list) }
    val entries = remember(all, search) { if (search.isBlank()) all else all.filter { it.channel.title.contains(search, true) || it.channel.group.contains(search, true) } }
    val queue = remember(entries) { entries.map { it.channel } }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = ScreenPadding) {
        item {
            TopRow(stringResource(list.label()), pluralStringResource(Res.plurals.iptv_item_count, all.size, all.size), actions.back) {
                if (all.isNotEmpty()) TextButton(onClick = { clearing = true }) { Text(stringResource(Res.string.iptv_clear_all)) }
            }
        }
        state.notice?.let { item(key = "notice") { NoticeBanner(it, actions.clearNotice) } }
        if (list == LibraryList.HIDDEN) item {
            OutlinedButton(onClick = actions.changePasscode, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                GlyphIcon(Glyph.LOCK, Modifier.size(18.dp), TvColors.Violet); Spacer(Modifier.width(8.dp)); Text(stringResource(Res.string.iptv_passcode_change))
            }
        }
        if (all.size > 8) item {
            OutlinedTextField(search, { search = it }, placeholder = { Text(stringResource(Res.string.search)) }, leadingIcon = { GlyphIcon(Glyph.SEARCH, color = TvColors.Muted) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(18.dp))
        }
        if (entries.isEmpty()) item { EmptyNote(stringResource(if (all.isEmpty()) Res.string.iptv_empty_list else Res.string.no_results)) }
        items(entries, key = { it.channel.url }) { entry ->
            val channel = entry.channel
            ChannelCard(channel, entry.favorite, emptyList(), now, { actions.favorite(channel) }, { actions.play(channel, queue) }, progress = entry.progress,
                menu = ChannelMenu(hidden = entry.hidden, onHide = { actions.hide(channel, !entry.hidden) }))
        }
    }
    if (clearing) ConfirmDialog(stringResource(Res.string.iptv_clear_title), stringResource(Res.string.iptv_clear_body), stringResource(Res.string.iptv_clear_all),
        { actions.clear(list) }) { clearing = false }
}

/** Seasons and episodes of an Xtream series. */
@Composable private fun SeriesScreen(state: IptvUiState, series: Channel, actions: IptvActions) {
    val loaded = state.series?.takeIf { it.series.url == series.url }
    val info = loaded?.info
    var season by rememberSaveable(series.url) { mutableStateOf(-1) }
    val seasons = info?.seasons.orEmpty()
    val current = if (season in seasons) season else seasons.firstOrNull() ?: 0
    val episodes = remember(info, current) { info?.episodes.orEmpty().filter { it.season == current } }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = ScreenPadding) {
        item {
            TopRow(series.title, series.group, actions.back) { FavoriteButton(series.url in state.favorites) { actions.favorite(series) } }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Logo(info?.cover?.ifEmpty { series.logo } ?: series.logo, Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)), androidx.compose.ui.layout.ContentScale.Crop, 0)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOfNotNull(info?.released, info?.genre, info?.rating?.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" }).filter { it.isNotBlank() }
                        .forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    if (!info?.plot.isNullOrBlank()) Text(info!!.plot, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        when {
            loaded?.failed == true -> item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ErrorNotice(UiError.XTREAM)
                    OutlinedButton(onClick = { actions.retrySeries(series) }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(Res.string.retry)) }
                }
            }
            info == null -> item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = TvColors.Coral)
                    Text(stringResource(Res.string.iptv_episodes_loading), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted)
                }
            }
            else -> {
                if (seasons.size > 1) item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(seasons, key = { it }) { number -> Chip(stringResource(Res.string.iptv_season, number), current == number) { season = number } }
                    }
                }
                items(episodes, key = { it.url }) { episode -> EpisodeRow(episode, state.entry(episode.url)) { actions.playEpisode(episode) } }
            }
        }
    }
}
@Composable private fun EpisodeRow(episode: Episode, entry: LibraryEntry?, onPlay: () -> Unit) {
    Card(onClick = onPlay, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, TvColors.Outline.copy(alpha = 0.65f)), colors = CardDefaults.cardColors(containerColor = TvColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(TvColors.Violet.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Text("${episode.number}", style = MaterialTheme.typography.titleSmall, color = TvColors.Violet)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(episode.title.ifBlank { "S${episode.season}E${episode.number}" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val minutes = (episode.durationSecs / 60).toInt()
                if (minutes > 0) Text(pluralStringResource(Res.plurals.iptv_minutes, minutes, minutes), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted)
                if (episode.plot.isNotBlank()) Text(episode.plot, style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                entry?.progress?.takeIf { it > 0f }?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth().height(3.dp), color = TvColors.Coral, trackColor = TvColors.Outline) }
            }
            GlyphIcon(Glyph.PLAY, Modifier.size(18.dp), TvColors.Coral)
        }
    }
}

/** What is on a channel now and later, by local day and time. */
@Composable private fun ProgrammesScreen(state: IptvUiState, channel: Channel, actions: IptvActions) {
    val now = rememberEpochSeconds()
    val programmes = remember(state.guide, channel.url, now) { state.guide[channel.url].orEmpty().filter { it.stop > now } }
    var reminding by remember { mutableStateOf<Programme?>(null) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = ScreenPadding) {
        item {
            TopRow(channel.title, stringResource(Res.string.iptv_programmes), actions.back) {
                RoundAction(stringResource(Res.string.play), Glyph.PLAY, accent = TvColors.Coral) { actions.play(channel, listOf(channel)) }
            }
        }
        if (programmes.isEmpty()) item { EmptyNote(stringResource(Res.string.iptv_no_programmes)) }
        programmes.groupBy { formatDay(it.start) }.forEach { (day, list) ->
            item(key = day) { Text(day, style = MaterialTheme.typography.titleSmall, color = TvColors.Coral) }
            items(list, key = { it.start }) { programme -> ProgrammeRow(programme, now, state.reminder(channel.url, programme.start)) { reminding = programme } }
        }
    }
    reminding?.let { programme -> ReminderDialog(channel, programme, state.reminder(channel.url, programme.start), now, actions) { reminding = null } }
}
/** [onRemind] is offered for programmes that have not started; [reminder] marks one that is already set. */
@Composable internal fun ProgrammeRow(programme: Programme, now: Long, reminder: Reminder? = null, onRemind: (() -> Unit)? = null) {
    val airing = isAiring(programme, now)
    val upcoming = programme.start > now
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (airing) TvColors.Cyan.copy(alpha = 0.08f) else TvColors.Surface)
        .clickable(enabled = onRemind != null && upcoming, onClickLabel = stringResource(Res.string.iptv_reminder_title), role = Role.Button) { onRemind?.invoke() }
        .border(1.dp, if (airing) TvColors.Cyan.copy(alpha = 0.35f) else TvColors.Outline.copy(alpha = 0.65f), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${formatClock(programme.start)} – ${formatClock(programme.stop)}", style = MaterialTheme.typography.labelLarge, color = if (airing) TvColors.Cyan else TvColors.Muted)
            if (airing) Text(stringResource(Res.string.iptv_on_now), style = MaterialTheme.typography.labelSmall, color = TvColors.Cyan)
            if (reminder != null) Text(stringResource(Res.string.iptv_reminder_set), style = MaterialTheme.typography.labelSmall, color = TvColors.Coral)
        }
        Text(programme.title, style = MaterialTheme.typography.titleSmall)
        if (programme.description.isNotBlank()) Text(programme.description, style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, maxLines = 4, overflow = TextOverflow.Ellipsis)
        if (airing) LinearProgressIndicator(progress = { ((now - programme.start).toFloat() / (programme.stop - programme.start)).coerceIn(0f, 1f) },
            Modifier.fillMaxWidth().height(3.dp), color = TvColors.Cyan, trackColor = TvColors.Outline)
    }
}
