package com.tuntech.supertvstreamcast.widgets.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.tuntech.common.widget.dialog.DialogContent
import com.tuntech.common.widget.dialog.ModalContent
import com.tuntech.common.widget.dialog.ModalStack
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.theme.TvDimens
import com.tuntech.supertvstreamcast.theme.TvTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import shared.resources.Res
import shared.resources.ok

@Composable
fun AlertDialogContentBody(
    title: String,
    description: String,
    positiveAction: String,
    negativeAction: String? = null,
    onNegativeAction: () -> Unit = {},
    onPositiveAction: () -> Unit = {},
) {
    Card(
        modifier = Modifier.sizeIn(minWidth = 300.dp, maxWidth = 500.dp).fillMaxWidth(0.9f),
        shape = RoundedCornerShape(TvDimens.Radius),
        colors = CardDefaults.cardColors(containerColor = TvColors.Surface),
        border = BorderStroke(1.dp, TvColors.Outline),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = TvColors.Text, textAlign = TextAlign.Center)
            Text(description, style = MaterialTheme.typography.bodyLarge, color = TvColors.Muted, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (negativeAction != null) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, TvColors.Outline),
                        onClick = onNegativeAction,
                    ) {
                        AlertActionLabel(negativeAction)
                    }
                }
                Button(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TvColors.Cyan, contentColor = TvColors.OnAccent),
                    onClick = onPositiveAction,
                ) {
                    AlertActionLabel(positiveAction)
                }
            }
        }
    }
}

// Two actions share one row: never let a label wrap — a translation that is too long scrolls.
@Composable
private fun AlertActionLabel(text: String) {
    Text(
        modifier = Modifier.basicMarquee(),
        text = text,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
    )
}

/** The negative button is shown only when [negativeAction] is given. */
fun ModalStack.showAlertDialog(
    title: StringResource,
    description: StringResource,
    negativeAction: StringResource? = null,
    positiveAction: StringResource? = null,
    onPositiveAction: () -> Unit = {},
    onNegativeAction: () -> Unit = {},
    onClose: () -> Unit = {},
    properties: DialogProperties = DialogProperties(),
) {
    val contentId = ModalContent.uuid
    val content = ModalContent(
        id = contentId,
        dialog = DialogContent(
            properties = properties,
            onDismissRequest = onClose,
        ) {
            AlertDialogContentBody(
                title = stringResource(title),
                description = stringResource(description),
                positiveAction = stringResource(positiveAction ?: Res.string.ok),
                negativeAction = negativeAction?.let { stringResource(it) },
                onPositiveAction = {
                    dismiss(contentId)
                    onPositiveAction()
                },
                onNegativeAction = {
                    dismiss(contentId)
                    onNegativeAction()
                },
            )
        },
    )
    show(content)
}

@Preview
@Composable
private fun AlertDialogPreview() {
    TvTheme { AlertDialogContentBody("Title", "Description of the alert.", "OK", "Cancel") }
}
