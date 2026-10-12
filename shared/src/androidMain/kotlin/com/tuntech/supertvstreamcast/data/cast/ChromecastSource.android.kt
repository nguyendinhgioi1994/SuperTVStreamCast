package com.tuntech.supertvstreamcast.data.cast

import android.content.Context
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.api.Result
import com.tuntech.supertvstreamcast.domain.cast.CastKind
import com.tuntech.supertvstreamcast.domain.cast.CastMedia
import com.tuntech.supertvstreamcast.domain.cast.CastReceiver
import com.tuntech.supertvstreamcast.domain.cast.CastStatus
import com.tuntech.supertvstreamcast.domain.cast.MediaKind
import com.tuntech.supertvstreamcast.domain.cast.PlaybackState
import com.tuntech.supertvstreamcast.platform.castAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt

actual fun platformReceiverSources(context: coil3.PlatformContext): List<ReceiverSource> = listOf(ChromecastSource(context.applicationContext))

/**
 * Chromecast and Google TV receivers through the Cast framework's media router, played by the
 * Default Media Receiver (the same app id as the IPTV player's Cast button). Without Google Play
 * services nothing is discovered.
 */
private class ChromecastSource(private val context: Context) : ReceiverSource {
    override val kind = CastKind.CHROMECAST
    private val selector by lazy {
        MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)).build()
    }

    override fun discover(): Flow<List<CastReceiver>> {
        if (!castAvailable(context)) return emptyFlow()
        return callbackFlow {
            val router = MediaRouter.getInstance(context)
            fun publish() {
                trySend(router.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
                    .map { CastReceiver(it.id, it.name, CastKind.CHROMECAST, it.description.orEmpty()) })
            }
            val callback = object : MediaRouter.Callback() {
                override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = publish()
                override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = publish()
                override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = publish()
            }
            router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
            publish()
            awaitClose { router.removeCallback(callback) }
        }.flowOn(Dispatchers.Main)
    }

    override suspend fun connect(receiver: CastReceiver): CastController? = withContext(Dispatchers.Main) {
        if (!castAvailable(context)) return@withContext null
        val cast = runCatching { CastContext.getSharedInstance(context) }.getOrNull() ?: return@withContext null
        val manager = cast.sessionManager
        val router = MediaRouter.getInstance(context)
        val route = router.routes.firstOrNull { it.id == receiver.id } ?: return@withContext null
        val current = manager.currentCastSession
        if (current?.isConnected == true && router.selectedRoute.id == route.id) return@withContext ChromecastController(receiver, current)
        // Selecting the route makes the framework start a session; wait until it reports back.
        val session = withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : SessionManagerListener<CastSession> {
                    fun done(session: CastSession?) { manager.removeSessionManagerListener(this, CastSession::class.java); if (continuation.isActive) continuation.resume(session) }
                    override fun onSessionStarted(session: CastSession, sessionId: String) = done(session)
                    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = done(session)
                    override fun onSessionStartFailed(session: CastSession, error: Int) = done(null)
                    override fun onSessionResumeFailed(session: CastSession, error: Int) = done(null)
                    override fun onSessionEnded(session: CastSession, error: Int) = done(null)
                    override fun onSessionStarting(session: CastSession) = Unit
                    override fun onSessionEnding(session: CastSession) = Unit
                    override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
                    override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
                }
                manager.addSessionManagerListener(listener, CastSession::class.java)
                continuation.invokeOnCancellation { manager.removeSessionManagerListener(listener, CastSession::class.java) }
                router.selectRoute(route)
            }
        }
        session?.takeIf { it.isConnected }?.let { ChromecastController(receiver, it) }
    }
}

private class ChromecastController(override val receiver: CastReceiver, private val session: CastSession) : CastController {
    private val client: RemoteMediaClient? get() = session.remoteMediaClient

    override suspend fun load(media: CastMedia): Boolean = onMain {
        val client = client ?: return@onMain false
        val metadata = MediaMetadata(when (media.kind) {
            MediaKind.PHOTO -> MediaMetadata.MEDIA_TYPE_PHOTO
            MediaKind.AUDIO -> MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
            MediaKind.VIDEO -> MediaMetadata.MEDIA_TYPE_MOVIE
        }).apply { putString(MediaMetadata.KEY_TITLE, media.title) }
        val streamType = if (media.mime.contains("mpegurl", ignoreCase = true)) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED
        val info = MediaInfo.Builder(media.url).setContentType(media.mime).setStreamType(streamType).setMetadata(metadata).build()
        client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build()).ok()
    }
    override suspend fun play() = onMain { client?.play()?.ok() ?: false }
    override suspend fun pause() = onMain { client?.pause()?.ok() ?: false }
    override suspend fun stop() = onMain { client?.stop()?.ok() ?: false }
    override suspend fun seek(positionMs: Long) = onMain { client?.seek(MediaSeekOptions.Builder().setPosition(positionMs).build())?.ok() ?: false }
    override suspend fun setVolume(volume: Int) = onMain { runCatching { session.volume = volume.coerceIn(0, 100) / 100.0 }.isSuccess }
    override suspend fun status(): CastStatus? = onMain {
        if (!session.isConnected) return@onMain null
        val client = client ?: return@onMain null
        val state = when (client.playerState) {
            MediaStatus.PLAYER_STATE_PLAYING -> PlaybackState.PLAYING
            MediaStatus.PLAYER_STATE_PAUSED -> PlaybackState.PAUSED
            MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> PlaybackState.LOADING
            MediaStatus.PLAYER_STATE_IDLE -> if (client.idleReason == MediaStatus.IDLE_REASON_ERROR) PlaybackState.ERROR else PlaybackState.STOPPED
            else -> PlaybackState.IDLE
        }
        CastStatus(state, client.approximateStreamPosition, client.streamDuration.coerceAtLeast(0), runCatching { (session.volume * 100).roundToInt() }.getOrNull())
    }
    override fun release() = Unit

    private suspend fun <T> onMain(block: suspend () -> T): T = withContext(Dispatchers.Main) { block() }

    /** Waits for the receiver's answer to a media command. */
    private suspend fun PendingResult<RemoteMediaClient.MediaChannelResult>.ok(): Boolean = withTimeoutOrNull(15_000) {
        suspendCancellableCoroutine { continuation -> setResultCallback { result: Result -> continuation.resume(result.status.isSuccess) } }
    } ?: false
}
