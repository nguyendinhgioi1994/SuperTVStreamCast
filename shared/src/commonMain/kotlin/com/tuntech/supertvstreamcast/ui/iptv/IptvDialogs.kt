package com.tuntech.supertvstreamcast.ui.iptv

import androidx.compose.foundation.*
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.theme.*
import com.tuntech.supertvstreamcast.ui.*
import org.jetbrains.compose.resources.*
import shared.resources.*

internal enum class IptvSheet { SOURCES, URL, XTREAM, STREAM, GUIDE, HELP }

/** The add-source, guide and help dialogs. While an import runs the dialog stays open and its dismiss action cancels the import. */
@Composable internal fun IptvSheets(sheet: IptvSheet?, state: IptvUiState, actions: IptvActions, onSheet: (IptvSheet?) -> Unit) {
    val close = { if (state.importing) actions.cancelImport() else if (!state.busy) onSheet(null) }
    when (sheet) {
        IptvSheet.SOURCES -> IptvDialog(stringResource(Res.string.iptv_sources), Glyph.ADD, onDismiss = close) {
            SourceChoices(state, actions) { next -> actions.clearError(); onSheet(next) }
        }
        IptvSheet.URL -> UrlDialog(state, actions.addPlaylist, close)
        IptvSheet.XTREAM -> XtreamDialog(state, actions.addXtream, close)
        IptvSheet.STREAM -> StreamDialog(state, actions.addStream, close)
        IptvSheet.GUIDE -> GuideDialog(state, actions, close)
        IptvSheet.HELP -> IptvDialog(stringResource(Res.string.iptv_help_title), Glyph.HELP, onDismiss = close) { HelpSteps() }
        null -> Unit
    }
}
/** Ways to add channels; `null` means a system picker opened and the caller's dialog should close. */
@Composable internal fun SourceChoices(state: IptvUiState, actions: IptvActions, onOpen: (IptvSheet?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SourceRow(Glyph.LINK, stringResource(Res.string.source_url), stringResource(Res.string.source_url_body), !state.busy) { onOpen(IptvSheet.URL) }
        SourceRow(Glyph.LOCK, stringResource(Res.string.source_xtream), stringResource(Res.string.source_xtream_body), !state.busy) { onOpen(IptvSheet.XTREAM) }
        SourceRow(Glyph.FILE, stringResource(Res.string.source_file), stringResource(Res.string.source_file_body), !state.busy) { onOpen(null); actions.pickPlaylistFile() }
        SourceRow(Glyph.PLAY, stringResource(Res.string.source_stream), stringResource(Res.string.source_stream_body), !state.busy) { onOpen(IptvSheet.STREAM) }
        SourceRow(Glyph.SEARCH, stringResource(Res.string.iptv_scan_qr), stringResource(Res.string.iptv_scan_qr_body), !state.busy) { onOpen(null); actions.openScanner() }
    }
}
@Composable private fun SourceRow(glyph: Glyph, title: String, body: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TvColors.Raised).border(1.dp, TvColors.Outline, RoundedCornerShape(20.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).heightIn(min = TvDimens.Touch).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val accent = if (enabled) TvColors.Coral else TvColors.Muted
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            GlyphIcon(glyph, Modifier.size(20.dp), accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (enabled) TvColors.Text else TvColors.Muted)
            Text(body, style = MaterialTheme.typography.bodySmall, color = TvColors.Muted)
        }
        GlyphIcon(Glyph.RIGHT, Modifier.size(18.dp), accent)
    }
}
@Composable internal fun IptvDialog(title: String, glyph: Glyph, confirm: String? = null, confirmEnabled: Boolean = true, onConfirm: () -> Unit = {},
    onDismiss: () -> Unit, cancellable: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(30.dp), containerColor = TvColors.Surface,
        icon = { Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(TvColors.Coral.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { GlyphIcon(glyph, color = TvColors.Coral) } },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp), content = content) },
        confirmButton = { if (confirm != null) TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(if (cancellable) Res.string.cancel else if (confirm == null) Res.string.close else Res.string.back)) } })
}
@Composable private fun DialogStatus(state: IptvUiState) {
    state.error?.let { ErrorNotice(it) }
    if (state.busy) ImportProgress(state, null)
}
@Composable private fun Field(value: String, onValue: (String) -> Unit, label: String, enabled: Boolean, secret: Boolean = false, minLines: Int = 1,
    keyboard: KeyboardType = KeyboardType.Uri) {
    OutlinedTextField(value, onValue, label = { Text(label) }, enabled = enabled, singleLine = minLines == 1, minLines = minLines, maxLines = if (minLines == 1) 1 else 5,
        shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None)
}
@Composable private fun SavedNote() = Text(stringResource(Res.string.iptv_saved_note), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted)
@Composable private fun UrlDialog(state: IptvUiState, onImport: (String, String) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    IptvDialog(stringResource(Res.string.playlist_dialog_title), Glyph.LINK, stringResource(if (state.busy) Res.string.working else Res.string.import_playlist),
        input.isNotBlank() && !state.busy, { onImport(input, name) }, onDismiss, state.importing) {
        Text(stringResource(Res.string.playlist_dialog_hint), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
        Field(input, { if (it.length <= PLAYLIST_MAX_BYTES) input = it }, stringResource(Res.string.playlist_input), !state.busy, minLines = 3)
        Field(name, { name = it.take(60) }, stringResource(Res.string.stream_name), !state.busy, keyboard = KeyboardType.Text)
        if (input.trim().startsWith("http://", true)) Text(stringResource(Res.string.http_warning), style = MaterialTheme.typography.bodySmall, color = TvColors.Coral)
        SavedNote()
        DialogStatus(state)
    }
}
@Composable private fun XtreamDialog(state: IptvUiState, onImport: (String, String, String, String) -> Unit, onDismiss: () -> Unit) {
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    IptvDialog(stringResource(Res.string.xtream_title), Glyph.LOCK, stringResource(if (state.busy) Res.string.working else Res.string.xtream_login),
        server.isNotBlank() && user.isNotBlank() && pass.isNotEmpty() && !state.busy, { onImport(server, user, pass, name) }, onDismiss, state.importing) {
        Text(stringResource(Res.string.xtream_hint), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
        // A pasted provider link (…/get.php?username=…&password=…) fills in all three fields.
        Field(server, { value -> val link = XtreamApi.credentials(value)
            if (link == null) server = value.trim() else { server = link.server; user = link.username; pass = link.password } }, stringResource(Res.string.xtream_server), !state.busy)
        Field(user, { user = it }, stringResource(Res.string.xtream_username), !state.busy, keyboard = KeyboardType.Text)
        Field(pass, { pass = it }, stringResource(Res.string.xtream_password), !state.busy, secret = true, keyboard = KeyboardType.Password)
        Field(name, { name = it.take(60) }, stringResource(Res.string.stream_name), !state.busy, keyboard = KeyboardType.Text)
        if (!server.startsWith("https://", true) && server.isNotBlank()) Text(stringResource(Res.string.http_warning), style = MaterialTheme.typography.bodySmall, color = TvColors.Coral)
        Text(stringResource(Res.string.xtream_privacy), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted)
        DialogStatus(state)
    }
}
@Composable private fun StreamDialog(state: IptvUiState, onPlay: (String, String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    IptvDialog(stringResource(Res.string.stream_title), Glyph.PLAY, stringResource(Res.string.stream_play), url.isNotBlank() && !state.busy, { onPlay(url, name) }, onDismiss) {
        Text(stringResource(Res.string.stream_hint), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
        Field(url, { url = it.trim() }, stringResource(Res.string.stream_url), !state.busy)
        Field(name, { name = it.take(60) }, stringResource(Res.string.stream_name), !state.busy, keyboard = KeyboardType.Text)
        DialogStatus(state)
    }
}
@Composable private fun GuideDialog(state: IptvUiState, actions: IptvActions, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    val provider = state.active?.guideUrl.orEmpty()
    IptvDialog(stringResource(Res.string.guide_title), Glyph.GUIDE, stringResource(if (state.busy) Res.string.working else Res.string.guide_import),
        url.isNotBlank() && !state.busy, { actions.importGuide(url) }, onDismiss, state.importing) {
        Text(stringResource(Res.string.guide_hint), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
        if (provider.isNotEmpty()) OutlinedButton(onClick = { actions.importGuide(provider) }, enabled = !state.busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            GlyphIcon(Glyph.GUIDE, Modifier.size(18.dp), TvColors.Cyan); Spacer(Modifier.width(8.dp)); Text(stringResource(Res.string.guide_provider))
        }
        Field(url, { url = it.trim() }, stringResource(Res.string.guide_url), !state.busy)
        OutlinedButton(onClick = { actions.clearError(); actions.pickGuideFile() }, enabled = !state.busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            GlyphIcon(Glyph.FILE, Modifier.size(18.dp), TvColors.Cyan); Spacer(Modifier.width(8.dp)); Text(stringResource(Res.string.guide_file))
        }
        if (url.startsWith("http://", true)) Text(stringResource(Res.string.http_warning), style = MaterialTheme.typography.bodySmall, color = TvColors.Coral)
        DialogStatus(state)
    }
}
@Composable private fun HelpSteps() {
    val steps = listOf(Res.string.help_url to Res.string.help_url_body, Res.string.help_xtream to Res.string.help_xtream_body,
        Res.string.help_file to Res.string.help_file_body, Res.string.help_stream to Res.string.help_stream_body,
        Res.string.help_guide to Res.string.help_guide_body, Res.string.help_watch to Res.string.help_watch_body,
        Res.string.iptv_help_manage to Res.string.iptv_help_manage_body)
    steps.forEachIndexed { index, (title, body) ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(30.dp).background(TvColors.Coral.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = TvColors.Coral)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
            }
        }
    }
    Text(stringResource(Res.string.help_legal), style = MaterialTheme.typography.bodySmall, color = TvColors.Cyan)
}

/** Yes/no question before something is removed. */
@Composable internal fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(30.dp), containerColor = TvColors.Surface,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) }, text = { Text(body, style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = TvColors.Error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } })
}
/** Name, automatic refresh, account status and sharing of one saved source. */
@Composable internal fun SourceDetailsDialog(source: IptvSource, state: IptvUiState, actions: IptvActions, onDismiss: () -> Unit) {
    var name by remember(source.id) { mutableStateOf(source.name) }
    var confirmShare by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf(false) }
    IptvDialog(stringResource(Res.string.iptv_details), source.type.glyph(), stringResource(Res.string.iptv_save), name.trim() != source.name && !state.busy,
        { actions.rename(source.id, name) }, onDismiss) {
        Text(listOf(stringResource(source.type.label()), source.host, pluralStringResource(Res.plurals.iptv_item_count, source.channelCount, source.channelCount))
            .filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted)
        if (source.syncedAt > 0) Text(stringResource(Res.string.iptv_updated, formatDay(source.syncedAt) + " " + formatClock(source.syncedAt)), style = MaterialTheme.typography.bodySmall, color = TvColors.Muted)
        if (source.truncated) Text(stringResource(Res.string.library_truncated), style = MaterialTheme.typography.bodySmall, color = TvColors.Coral)
        source.account?.let { account ->
            CinemaPanel(Modifier.fillMaxWidth()) {
                if (account.status.isNotBlank()) Text(stringResource(Res.string.iptv_account_status, account.status), style = MaterialTheme.typography.bodyMedium)
                if (account.expiresAt > 0) Text(stringResource(Res.string.iptv_account_expires, formatDay(account.expiresAt)), style = MaterialTheme.typography.bodyMedium)
                if (account.maxConnections > 0) Text(stringResource(Res.string.iptv_account_connections, account.activeConnections, account.maxConnections), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Field(name, { name = it.take(60) }, stringResource(Res.string.iptv_name), !state.busy, keyboard = KeyboardType.Text)
        if (source.refreshable) {
            Text(stringResource(Res.string.iptv_auto_refresh), style = MaterialTheme.typography.titleSmall)
            REFRESH_CHOICES.forEach { hours ->
                val selected = source.refreshHours == hours
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).selectable(selected = selected, role = Role.RadioButton, onClick = { actions.refreshEvery(source.id, hours) })
                    .heightIn(min = 44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RadioButton(selected, null)
                    Text(if (hours == 0) stringResource(Res.string.iptv_refresh_never) else pluralStringResource(Res.plurals.iptv_refresh_days, hours / 24, hours / 24), style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedButton(onClick = { actions.refresh(source.id) }, enabled = !state.busy, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(Res.string.iptv_refresh))
            }
        }
        if (source.shareUrl != null) OutlinedButton(onClick = { if (source.type == SourceType.XTREAM) confirmShare = true else actions.share(source) },
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(Res.string.iptv_share)) }
        if (source.shareUrl != null) OutlinedButton(onClick = { qr = true }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(Res.string.iptv_share_qr))
        }
        DialogStatus(state)
    }
    // An Xtream share link carries the username and password, so sharing it is confirmed first.
    if (confirmShare) ConfirmDialog(stringResource(Res.string.iptv_share), stringResource(Res.string.iptv_share_login_warning), stringResource(Res.string.iptv_share),
        { actions.share(source) }) { confirmShare = false }
    if (qr) ShareQrDialog(source) { qr = false }
}
