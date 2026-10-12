package com.tuntech.supertvstreamcast.ui.cast

import com.tuntech.supertvstreamcast.widgets.AppOpenAdGate

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuntech.supertvstreamcast.data.cast.LocalMedia
import com.tuntech.supertvstreamcast.domain.cast.MediaKind
import com.tuntech.supertvstreamcast.domain.cast.mimeFor
import com.tuntech.supertvstreamcast.platform.rememberLanAccessRequest
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

/** Cast tab bound to [CastViewModel] and the system pickers. Previews get the static content. */
@Composable internal fun CastTab(onShare: (() -> Unit)?, onIptv: () -> Unit) {
    if (LocalInspectionMode.current) { CastContent(CastUiState(), CastActions(), onShare, onIptv); return }
    val model: CastViewModel = koinViewModel()
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Searching needs local-network access; when refused the scan still runs and reports nothing found.
    val requestLan = rememberLanAccessRequest()
    fun cast(files: List<PlatformFile>?, kind: MediaKind) {
        if (files.isNullOrEmpty()) return
        scope.launch { model.cast(withContext(Dispatchers.IO) { files.mapNotNull { it.toCastItem(kind) } }) }
    }
    val photos = rememberFilePickerLauncher(type = FileKitType.Image, mode = FileKitMode.Multiple(maxItems = 50)) { cast(it, MediaKind.PHOTO) }
    val videos = rememberFilePickerLauncher(type = FileKitType.Video, mode = FileKitMode.Multiple(maxItems = 20)) { cast(it, MediaKind.VIDEO) }
    val music = rememberFilePickerLauncher(type = FileKitType.File(AudioExtensions), mode = FileKitMode.Multiple(maxItems = 50)) { cast(it, MediaKind.AUDIO) }
    val actions = remember(model, requestLan) {
        CastActions(
            scan = { requestLan { model.scan() } }, select = model::select,
            pickPhotos = { AppOpenAdGate.skipNextReturn(); photos.launch() }, pickVideos = { AppOpenAdGate.skipNextReturn(); videos.launch() },
            pickMusic = { AppOpenAdGate.skipNextReturn(); music.launch() },
            castLink = model::castLink, togglePlay = model::togglePlay, seekTo = model::seekTo, skip = model::skip,
            previous = model::previous, next = model::next, volume = model::changeVolume, toggleSlideshow = model::toggleSlideshow,
            stop = model::stop, dismissError = model::dismissError,
        )
    }
    // The phone keeps serving the media while the user is away; coming back must not show an app-open ad.
    LaunchedEffect(state.queue.isNotEmpty()) { AppOpenAdGate.casting = state.queue.isNotEmpty() }
    CastContent(state, actions, onShare?.let { share -> { AppOpenAdGate.skipNextReturn(); share() } }, onIptv)
}

private val AudioExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg")

/** Size and type are read once, off the main thread; the file itself is only read when the TV asks for it. */
private fun PlatformFile.toCastItem(kind: MediaKind): CastItem? {
    val file = this
    val title = runCatching { file.name }.getOrDefault("")
    val size = runCatching { file.size() }.getOrDefault(-1L).takeIf { it > 0 } ?: return null
    val mime = runCatching { file.mimeType()?.toString() }.getOrNull()?.substringBefore(';')?.takeIf { '/' in it && !it.endsWith("/*") }
        ?: mimeFor(title) ?: when (kind) { MediaKind.PHOTO -> "image/jpeg"; MediaKind.VIDEO -> "video/mp4"; MediaKind.AUDIO -> "audio/mpeg" }
    return CastItem(title.ifBlank { kind.name.lowercase() }, kind, mime, local = LocalMedia(title, mime, size) { file.source() })
}
