package com.tuntech.supertvstreamcast.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.AppLanguage
import com.tuntech.supertvstreamcast.theme.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.*
import shared.resources.*

/**
 * The general / support / legal / troubleshooting rows of Settings, built by the Dashboard.
 * A null callback hides its row (no premium to restore, no Remote Config URL, no support address).
 */
class SettingsLinks(
    val language: AppLanguage = AppLanguage.ENGLISH, val openLanguage: () -> Unit = {},
    val version: String = "",
    val rate: (() -> Unit)? = null, val shareApp: (() -> Unit)? = null, val contact: (() -> Unit)? = null,
    val restore: (() -> Unit)? = null, val manageSubscription: (() -> Unit)? = null,
    val terms: (() -> Unit)? = null, val privacy: (() -> Unit)? = null,
    val shareLogs: (() -> Unit)? = null, val cleanLogs: (() -> Unit)? = null,
    /** Run after the version line is tapped [VersionTaps] times in a row. */
    val copyDeviceId: (() -> Unit)? = null,
)

private const val VersionTaps = 5

private class SettingsRow(val glyph: Glyph, val title: StringResource, val onClick: () -> Unit, val value: String? = null, val accent: Color? = null)

@Composable internal fun SettingsGroups(links: SettingsLinks) {
    SettingsGroup(Res.string.settings_group_general, listOf(SettingsRow(Glyph.GLOBE, Res.string.settings_language, links.openLanguage, links.language.nativeName)))
    SettingsGroup(Res.string.settings_group_support, listOfNotNull(
        links.rate?.let { SettingsRow(Glyph.STAR, Res.string.settings_rate_app, it, accent = TvColors.Amber) },
        links.shareApp?.let { SettingsRow(Glyph.SHARE, Res.string.settings_share_app, it) },
        links.contact?.let { SettingsRow(Glyph.MAIL, Res.string.settings_contact_us, it) },
        links.restore?.let { SettingsRow(Glyph.REFRESH, Res.string.settings_restore_purchase, it) },
        links.manageSubscription?.let { SettingsRow(Glyph.CROWN, Res.string.settings_manage_subscription, it, accent = TvColors.Amber) },
    ))
    SettingsGroup(Res.string.settings_group_legal, listOfNotNull(
        links.terms?.let { SettingsRow(Glyph.FILE, Res.string.settings_terms, it) },
        links.privacy?.let { SettingsRow(Glyph.LOCK, Res.string.settings_privacy_policy, it) },
    ))
    SettingsGroup(Res.string.settings_group_troubleshooting, listOfNotNull(
        links.shareLogs?.let { SettingsRow(Glyph.SHARE, Res.string.settings_share_logs, it) },
        links.cleanLogs?.let { SettingsRow(Glyph.TRASH, Res.string.settings_clean_logs, it, accent = TvColors.Coral) },
    ))
    if (links.version.isNotBlank()) VersionLine(links.version, links.copyDeviceId)
}

/** The version text; tapping it several times in a row runs [onSecret] (copy the device id for support). */
@Composable private fun VersionLine(version: String, onSecret: (() -> Unit)?) {
    var taps by remember { mutableIntStateOf(0) }
    // The count only builds while the taps follow each other quickly.
    LaunchedEffect(taps) { if (taps > 0) { delay(1_500); taps = 0 } }
    val tap = onSecret?.let { action -> { taps++; if (taps >= VersionTaps) { taps = 0; action() } } }
    Text(stringResource(Res.string.settings_version, version),
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(if (tap != null) Modifier.clickable(interactionSource = null, indication = null, onClick = tap) else Modifier).padding(8.dp),
        style = MaterialTheme.typography.bodySmall, color = TvColors.Muted, textAlign = TextAlign.Center)
}

/** A titled card of tappable rows; nothing is drawn when every row is hidden. */
@Composable private fun SettingsGroup(title: StringResource, rows: List<SettingsRow>) {
    if (rows.isEmpty()) return
    val shape = RoundedCornerShape(TvDimens.Radius)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.fillMaxWidth().clip(shape).background(TvColors.SurfaceGradient).border(1.dp, TvColors.Outline.copy(alpha = 0.7f), shape)) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = TvColors.Outline.copy(alpha = 0.5f))
                SettingsRowItem(row)
            }
        }
    }
}

@Composable private fun SettingsRowItem(row: SettingsRow) {
    val accent = row.accent ?: TvColors.Cyan
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = row.onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            GlyphIcon(row.glyph, Modifier.size(20.dp), accent)
        }
        Text(stringResource(row.title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        row.value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted, maxLines = 1) }
        GlyphIcon(Glyph.RIGHT, Modifier.size(18.dp), TvColors.Muted)
    }
}
