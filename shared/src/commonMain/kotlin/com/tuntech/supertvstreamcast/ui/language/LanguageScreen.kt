package com.tuntech.supertvstreamcast.ui.language

import com.tuntech.supertvstreamcast.analytics.ClickEvent
import com.tuntech.supertvstreamcast.analytics.tracked
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tuntech.common.util.LocalAppLocale
import com.tuntech.common.util.LocalPlatformContext
import com.tuntech.common.util.LocaleUtil
import com.tuntech.monetization.ad.widget.NativeAd
import com.tuntech.supertvstreamcast.constant.AdName
import com.tuntech.supertvstreamcast.domain.AppLanguage
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.theme.TvDimens
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.ui.Glyph
import com.tuntech.supertvstreamcast.ui.Heading
import com.tuntech.supertvstreamcast.ui.PrimaryCta
import com.tuntech.supertvstreamcast.ui.RoundAction
import com.tuntech.supertvstreamcast.widgets.CommonAppBackground
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import shared.resources.Res
import shared.resources.app_name
import shared.resources.back
import shared.resources.continue_label
import shared.resources.language_apply
import shared.resources.language_body
import shared.resources.language_name_en
import shared.resources.language_name_ar
import shared.resources.language_name_de
import shared.resources.language_name_es
import shared.resources.language_name_fil
import shared.resources.language_name_fr
import shared.resources.language_name_hi
import shared.resources.language_name_id
import shared.resources.language_name_ja
import shared.resources.language_name_ko
import shared.resources.language_name_pt
import shared.resources.language_name_th
import shared.resources.language_name_tr
import shared.resources.language_name_zh
import shared.resources.language_name_vi
import shared.resources.language_title

/** The language the app shows: the one picked in the app, else the device language. */
@Composable
internal fun rememberAppLanguage(): AppLanguage {
    val context = LocalPlatformContext.current
    val saved by LocalAppLocale.language.collectAsState(LocalAppLocale.getCurrentLocale(context))
    return AppLanguage.resolve(saved, LocaleUtil.currentLanguageCode())
}

/**
 * Language picker (as in RetroOne). On first launch it sits between the Splash and the Onboarding
 * with the device language preselected; from Settings it applies only a different language.
 * Applying goes through `:common`'s [LocalAppLocale] — Android switches the per-app locale
 * (recreating the activity), iOS sets `AppleLanguages`; both keep the choice across launches.
 */
@Composable
fun LanguageScreen(
    isInitial: Boolean,
    navigateNext: () -> Unit,
    navigateBack: () -> Unit,
) {
    val context = LocalPlatformContext.current
    val current = rememberAppLanguage()
    var selectedCode by rememberSaveable { mutableStateOf(current.code) }
    val selected = AppLanguage.entries.firstOrNull { it.code == selectedCode } ?: current

    LanguageContent(
        isInitial = isInitial,
        selected = selected,
        canConfirm = isInitial || selected != current,
        onSelect = { ClickEvent.log("language_select", "language" to it.code, "initial" to isInitial); selectedCode = it.code },
        onConfirm = {
            ClickEvent.log("language_confirm", "language" to selected.code, "previous" to current.code, "initial" to isInitial)
            // Leave first: on Android the locale change recreates the activity with this back stack.
            navigateNext()
            LocalAppLocale.changeLocale(context, selected.code)
        },
        onBack = tracked("language_back", action = navigateBack),
        ad = {
            NativeAd(
                name = AdName.LANGUAGE,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
internal fun LanguageContent(
    isInitial: Boolean,
    selected: AppLanguage,
    canConfirm: Boolean,
    onSelect: (AppLanguage) -> Unit = {},
    onConfirm: () -> Unit = {},
    onBack: () -> Unit = {},
    ad: @Composable () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        CommonAppBackground()
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = TvDimens.ContentMaxWidth).fillMaxSize().safeDrawingPadding().padding(TvDimens.Space),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (isInitial) {
                Text(
                    stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = TvColors.Cyan,
                )
            } else {
                RoundAction(stringResource(Res.string.back), Glyph.BACK, onClick = onBack)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                Heading(stringResource(Res.string.language_title), stringResource(Res.string.language_body))
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppLanguage.entries.forEach { language ->
                        LanguageOption(language, language == selected) { onSelect(language) }
                    }
                }
            }
            PrimaryCta(
                stringResource(if (isInitial) Res.string.continue_label else Res.string.language_apply),
                enabled = canConfirm,
                glyph = if (isInitial) Glyph.RIGHT else Glyph.CHECK,
                onClick = onConfirm,
            )
            ad()
        }
    }
}

@Composable
private fun LanguageOption(language: AppLanguage, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val fill by animateColorAsState(if (active) TvColors.Raised else TvColors.Surface, label = "languageFill")
    val border by animateColorAsState(if (active) TvColors.Cyan.copy(alpha = 0.6f) else TvColors.Outline, label = "languageBorder")
    // The name in the current UI language, shown when it differs from the language's own name.
    val localName = stringResource(language.label())
    Row(
        Modifier.fillMaxWidth().heightIn(min = TvDimens.Touch).clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .selectable(selected = active, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LanguageFlag(language)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(language.nativeName, style = MaterialTheme.typography.titleMedium)
            if (localName != language.nativeName) {
                Text(localName, style = MaterialTheme.typography.bodySmall, color = TvColors.Muted)
            }
        }
        RadioButton(selected = active, onClick = null)
    }
}

private fun AppLanguage.label(): StringResource = when (this) {
    AppLanguage.ENGLISH -> Res.string.language_name_en
    AppLanguage.ARABIC -> Res.string.language_name_ar
    AppLanguage.GERMAN -> Res.string.language_name_de
    AppLanguage.SPANISH -> Res.string.language_name_es
    AppLanguage.FRENCH -> Res.string.language_name_fr
    AppLanguage.PORTUGUESE -> Res.string.language_name_pt
    AppLanguage.INDONESIAN -> Res.string.language_name_id
    AppLanguage.FILIPINO -> Res.string.language_name_fil
    AppLanguage.HINDI -> Res.string.language_name_hi
    AppLanguage.THAI -> Res.string.language_name_th
    AppLanguage.VIETNAMESE -> Res.string.language_name_vi
    AppLanguage.CHINESE -> Res.string.language_name_zh
    AppLanguage.KOREAN -> Res.string.language_name_ko
    AppLanguage.JAPANESE -> Res.string.language_name_ja
    AppLanguage.TURKISH -> Res.string.language_name_tr
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun LanguageInitialPreview() {
    TvTheme { LanguageContent(isInitial = true, selected = AppLanguage.VIETNAMESE, canConfirm = true) }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun LanguageSettingsPreview() {
    TvTheme { LanguageContent(isInitial = false, selected = AppLanguage.ENGLISH, canConfirm = false) }
}
