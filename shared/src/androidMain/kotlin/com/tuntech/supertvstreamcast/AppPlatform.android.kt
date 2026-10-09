package com.tuntech.supertvstreamcast.platform

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun createAppPreferences(context: android.content.Context): AppPreferences {
    val prefs=context.applicationContext.getSharedPreferences("tv_space",0)
    return object: AppPreferences {
        override var brand: String
            get()=prefs.getString("brand","").orEmpty()
            set(value){prefs.edit().putString("brand",value).apply()}
        override var goal: String
            get()=prefs.getString("goal","").orEmpty()
            set(value){prefs.edit().putString("goal",value).apply()}
        override var lastDevice: String
            get()=prefs.getString("last_device","").orEmpty()
            set(value){prefs.edit().putString("last_device",value).apply()}
    }
}
@Composable actual fun rememberScreenSharingAction(): (() -> Boolean)? {
    val context=LocalContext.current
    return remember(context) {{
        try {context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS));true}
        catch (_: android.content.ActivityNotFoundException) {false}
        catch (_: SecurityException) {false}
    }}
}
/**
 * Media3 ExoPlayer: HLS, MPEG-TS and progressive streams, following redirects between HTTP and
 * HTTPS as IPTV servers do. A link without a recognizable container is retried once as HLS, and a
 * transient failure is retried once before [onError] is reported.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable actual fun StreamPlayer(url: String,userAgent: String,referer: String,modifier: Modifier,startMs: Long,controls: Boolean,fill: Boolean,
    onProgress: (Long,Long)->Unit,onEnded: ()->Unit,onError: ()->Unit) {
    val context=LocalContext.current
    val report by rememberUpdatedState(onError)
    val progress by rememberUpdatedState(onProgress)
    val ended by rememberUpdatedState(onEnded)
    val players=remember(url,userAgent,referer) {
        val http=DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
        if(userAgent.isNotEmpty()) http.setUserAgent(userAgent)
        if(referer.isNotEmpty()) http.setDefaultRequestProperties(mapOf("Referer" to referer))
        val local=ExoPlayer.Builder(context,DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context,http)))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true)
            .setHandleAudioBecomingNoisy(true).build()
        // With Cast available the wrapper hands playback to a Chromecast session and back by itself.
        // The receiver fetches the stream on its own, so per-channel request headers do not reach it.
        val shown: Player=if(castAvailable(context)) runCatching {androidx.media3.cast.CastPlayer.Builder(context).setLocalPlayer(local).build()}.getOrDefault(local) else local
        shown.apply {
                val path=url.substringBefore('?').lowercase()
                val hls=path.endsWith(".m3u8")
                // A receiver cannot sniff the container, so name it where the link shows it.
                val known=when {path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T;path.endsWith(".mp4")||path.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
                    path.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA;path.endsWith(".webm") -> MimeTypes.VIDEO_WEBM;else -> null}
                fun item(mime: String?)=MediaItem.Builder().setUri(url).setMimeType(mime).build()
                addListener(object: Player.Listener {
                    var triedHls=hls
                    var retried=false
                    override fun onPlaybackStateChanged(state: Int) {
                        if(state==Player.STATE_READY) retried=false else if(state==Player.STATE_ENDED) ended()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        when {
                            error.errorCode==PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {seekToDefaultPosition();prepare()}
                            !triedHls&&(error.errorCode==PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED||
                                error.errorCode==PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED) -> {
                                triedHls=true;setMediaItem(item(MimeTypes.APPLICATION_M3U8));prepare()
                            }
                            !retried -> {retried=true;prepare()}
                            else -> report()
                        }
                    }
                })
                setMediaItem(item(if(hls) MimeTypes.APPLICATION_M3U8 else known),if(startMs>0) startMs else C.TIME_UNSET)
                playWhenReady=true
                prepare()
            }
        local to shown
    }
    val player=players.second
    // Only a video with a known length (not a live stream) has a position worth remembering.
    val position={
        val duration=player.duration
        if(duration!=C.TIME_UNSET&&duration>0&&!player.isCurrentMediaItemLive) progress(player.currentPosition,duration)
    }
    LaunchedEffect(player) {while(true) {kotlinx.coroutines.delay(5_000);position()}}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player,lifecycle) {
        val observer=LifecycleEventObserver {_,event ->
            if(event==Lifecycle.Event.ON_STOP) {position();player.pause()} else if(event==Lifecycle.Event.ON_START) player.play()
        }
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);position();player.release();if(players.first!==player) players.first.release()}
    }
    AndroidView(factory={PlayerView(it).apply {
        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        setShutterBackgroundColor(android.graphics.Color.BLACK)
        setShowSubtitleButton(true)
        keepScreenOn=true
    }},update={
        it.player=player
        it.useController=controls
        it.resizeMode=if(fill) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
    },onRelease={it.player=null},modifier=modifier)
}

@Composable actual fun rememberFullscreenRequest(): ((Boolean)->Unit)? {
    val activity=androidx.activity.compose.LocalActivity.current ?: return null
    val original=remember(activity) {activity.requestedOrientation}
    val apply=remember(activity) {{on: Boolean ->
        activity.requestedOrientation=if(on) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else original
        val bars=androidx.core.view.WindowCompat.getInsetsController(activity.window,activity.window.decorView)
        bars.systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if(on) bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars()) else bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }}
    DisposableEffect(apply) {onDispose {apply(false)}}
    return apply
}
@Composable actual fun rememberLanAccessRequest(): ((Boolean)->Unit)->Unit {
    val context=LocalContext.current
    var pending by remember {mutableStateOf<((Boolean)->Unit)?>(null)}
    val launcher=androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {granted -> val callback=pending;pending=null;callback?.invoke(granted)}
    return {callback ->
        val permission="android.permission.ACCESS_LOCAL_NETWORK"
        if(android.os.Build.VERSION.SDK_INT<37 || context.checkSelfPermission(permission)==android.content.pm.PackageManager.PERMISSION_GRANTED) callback(true)
        else {pending=callback;launcher.launch(permission)}
    }
}
@Composable actual fun rememberFilePicker(maxBytes: Int,onPicked: (ByteArray?)->Unit): ()->Unit {
    val context=LocalContext.current.applicationContext
    val result by rememberUpdatedState(onPicked)
    val scope=rememberCoroutineScope()
    val launcher=androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) {uri -> if(uri!=null) scope.launch {
        val bytes=withContext(Dispatchers.IO) {
            try {context.contentResolver.openInputStream(uri)?.use {stream -> readAtMost(stream,maxBytes+1)}}
            catch (_: java.io.IOException) {null} catch (_: SecurityException) {null}
        }
        result(bytes)
    }}
    return remember(launcher) {{launcher.launch(arrayOf("*/*"))}}
}
private fun readAtMost(stream: java.io.InputStream,limit: Int): ByteArray {
    val out=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(64*1024)
    while(out.size()<limit) {
        val read=stream.read(buffer,0,minOf(buffer.size,limit-out.size()))
        if(read<0) break
        out.write(buffer,0,read)
    }
    return out.toByteArray()
}
