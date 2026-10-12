package com.tuntech.supertvstreamcast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tuntech.common.util.PlatformContext
import com.tuntech.common.widget.dialog.ModalStack
import com.tuntech.monetization.ad.AdManager
import com.tuntech.monetization.ad.AdManagerCallback
import com.tuntech.supertvstreamcast.constant.AdName
import com.tuntech.supertvstreamcast.domain.PremiumFeature
import com.tuntech.supertvstreamcast.domain.PremiumSettings
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.ui.app_loading.showAdLoading
import com.tuntech.supertvstreamcast.widgets.dialog.showAlertDialog
import org.jetbrains.compose.resources.stringResource
import shared.resources.*

/** Rewarded placement offered at this gate. */
internal fun PremiumFeature.rewardName() = when (this) {
    PremiumFeature.IPTV_WATCH_TIME -> AdName.UNLOCK_IPTV_TIME
    PremiumFeature.IPTV_SOURCES -> AdName.UNLOCK_IPTV_SOURCE
    else -> AdName.UNLOCK_REMOTE
}

/**
 * Shown when a free user taps a premium-only remote feature or reaches an IPTV limit: Premium
 * first, and, when [rewardAvailable], one rewarded ad as the opt-in alternative.
 */
@Composable internal fun PremiumGateDialog(
    feature: PremiumFeature,
    settings: PremiumSettings,
    rewardAvailable: Boolean,
    onUpgrade: () -> Unit,
    onWatchAd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val body = when (feature) {
        PremiumFeature.REMOTE_KEYBOARD -> Res.string.premium_gate_keyboard
        PremiumFeature.REMOTE_TOUCHPAD -> Res.string.premium_gate_touchpad
        PremiumFeature.REMOTE_APPS -> Res.string.premium_gate_apps
        PremiumFeature.IPTV_SOURCES -> Res.string.premium_gate_sources
        PremiumFeature.IPTV_WATCH_TIME -> Res.string.premium_gate_watch
    }
    val reward = when (feature) {
        PremiumFeature.IPTV_WATCH_TIME -> stringResource(Res.string.premium_gate_ad_time, settings.rewardWatchSeconds / 60)
        PremiumFeature.IPTV_SOURCES -> stringResource(Res.string.premium_gate_ad_source)
        else -> stringResource(Res.string.premium_gate_ad_unlock, settings.rewardUnlockMinutes)
    }
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(30.dp), containerColor = TvColors.Surface,
        icon = {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(TvColors.PremiumGradient), contentAlignment = Alignment.Center) {
                GlyphIcon(Glyph.CROWN, color = TvColors.OnAccent)
            }
        },
        title = { Text(stringResource(Res.string.premium_gate_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(stringResource(body), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = TvColors.Muted, textAlign = TextAlign.Center)
                PrimaryCta(stringResource(Res.string.premium_gate_upgrade), glyph = Glyph.CROWN, onClick = onUpgrade)
                if (rewardAvailable) OutlinedButton(onClick = onWatchAd, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    GlyphIcon(Glyph.PLAY, Modifier.size(18.dp), TvColors.Cyan); Spacer(Modifier.width(8.dp)); Text(reward, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) } })
}

/** Small crown pinned to a premium-only control while it is locked. */
@Composable internal fun LockBadge(modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.premium_gate_title)
    Box(modifier.size(18.dp).clip(CircleShape).background(TvColors.PremiumGradient).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        GlyphIcon(Glyph.CROWN, Modifier.size(11.dp), TvColors.OnAccent)
    }
}

/**
 * Shows the rewarded ad for [feature] and calls [onReward] only once the SDK reported the reward.
 * A missing or failed ad ends with a short notice; closing the ad early grants nothing.
 */
internal fun AdManager.showReward(context: PlatformContext, modal: ModalStack, feature: PremiumFeature, onReward: () -> Unit) {
    var earned = false
    var shown = false
    var reported = false
    fun unavailable() {
        if (reported) return
        reported = true
        modal.showAlertDialog(title = Res.string.premium_gate_ad_unavailable_title, description = Res.string.premium_gate_ad_unavailable_body, positiveAction = Res.string.ok)
    }
    showRewardedAd(context = context, name = feature.rewardName(), callback = AdManagerCallback(
        onAdLoading = modal::showAdLoading,
        onAdShowedFullScreenContent = { shown = true; modal.dismissLoading() },
        onUserEarnedReward = { earned = true },
        onAdFailedToLoad = { modal.dismissLoading(); unavailable() },
        onAdClosed = {
            modal.dismissLoading()
            when {
                earned -> {
                    onReward()
                    modal.showAlertDialog(title = Res.string.premium_gate_reward_title, description = Res.string.premium_gate_reward_body, positiveAction = Res.string.ok)
                }
                !shown -> unavailable()
            }
        },
    ))
}

@Preview(widthDp = 360, heightDp = 780)
@Composable private fun PremiumGatePreview() {
    TvTheme { PremiumGateDialog(PremiumFeature.REMOTE_KEYBOARD, PremiumSettings(), rewardAvailable = true, onUpgrade = {}, onWatchAd = {}, onDismiss = {}) }
}
