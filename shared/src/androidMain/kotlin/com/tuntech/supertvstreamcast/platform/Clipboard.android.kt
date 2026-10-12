package com.tuntech.supertvstreamcast.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable actual fun rememberCopyText(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text -> (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(null, text)) }
    }
}
