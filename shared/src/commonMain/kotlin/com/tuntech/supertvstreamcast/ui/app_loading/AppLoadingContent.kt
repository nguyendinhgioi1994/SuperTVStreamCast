package com.tuntech.supertvstreamcast.ui.app_loading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuntech.common.widget.dialog.LoadingContent
import com.tuntech.common.widget.dialog.ModalContent
import com.tuntech.common.widget.dialog.ModalStack
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.ui.Glyph
import com.tuntech.supertvstreamcast.ui.GlyphIcon
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import shared.resources.Res
import shared.resources.ad_loading
import shared.resources.close
import shared.resources.please_wait_a_moment


/** The app's single blocking loading. With a timeout the close button unlocks after the countdown. */
@Composable
fun AppLoadingContent(
    title: String,
    description: String,
    isTimeoutEnabled: Boolean = true,
    onClose: () -> Unit = {},
    viewModel: AppLoadingViewModel = koinViewModel(),
) {
    val remainingTime by viewModel.remainingTime.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        if (isTimeoutEnabled) {
            viewModel.start()
        }
        onDispose {
            viewModel.stop()
        }
    }

    AppLoadingBody(
        title = title,
        description = description,
        remainingSeconds = if (isTimeoutEnabled) remainingTime / 1000 else null,
        onClose = onClose,
    )
}

/** [remainingSeconds] null hides the close button; 0 enables it. */
@Composable
private fun AppLoadingBody(
    title: String,
    description: String,
    remainingSeconds: Long?,
    onClose: () -> Unit = {},
) {
    Box(
        modifier = Modifier.fillMaxSize().background(TvColors.Background.copy(alpha = 0.94f)).safeDrawingPadding(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp), color = TvColors.Cyan, strokeWidth = 3.dp)
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TvColors.Text,
                textAlign = TextAlign.Center,
            )
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TvColors.Muted,
                textAlign = TextAlign.Center,
            )
        }

        if (remainingSeconds != null) {
            val closeLabel = stringResource(Res.string.close)
            IconButton(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp)
                    .semantics { contentDescription = closeLabel },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = TvColors.Raised,
                    disabledContainerColor = TvColors.Raised.copy(alpha = 0.5f),
                ),
                onClick = onClose,
                enabled = remainingSeconds <= 0,
            ) {
                if (remainingSeconds > 0) {
                    Text(
                        text = "${remainingSeconds % 60}".padStart(2, '0'),
                        style = MaterialTheme.typography.labelLarge,
                        color = TvColors.Muted,
                    )
                } else {
                    GlyphIcon(Glyph.CLOSE, Modifier.size(18.dp), TvColors.Text)
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 780)
@Composable
private fun PreviewAppLoadingContent() {
    TvTheme { AppLoadingBody(title = "Purchasing …", description = "Please wait a moment", remainingSeconds = 12) }
}

fun ModalStack.showAppLoading(
    title: StringResource,
    description: StringResource,
    isTimeoutEnabled: Boolean = true,
    onClose: () -> Unit = {},
) {
    show(
        ModalContent(
            id = ModalContent.uuid,
            loading = LoadingContent {
                AppLoadingContent(
                    title = stringResource(title),
                    description = stringResource(description),
                    isTimeoutEnabled = isTimeoutEnabled,
                    onClose = {
                        onClose()
                        dismissLoading()
                    },
                )
            }
        )
    )
}

fun ModalStack.showAdLoading(
    onClose: (() -> Unit)? = null,
) {
    show(
        ModalContent(
            id = ModalContent.uuid,
            loading = LoadingContent {
                AppLoadingContent(
                    title = stringResource(Res.string.ad_loading),
                    description = stringResource(Res.string.please_wait_a_moment),
                    onClose = {
                        onClose?.invoke()
                        dismissLoading()
                    },
                )
            }
        )
    )
}
