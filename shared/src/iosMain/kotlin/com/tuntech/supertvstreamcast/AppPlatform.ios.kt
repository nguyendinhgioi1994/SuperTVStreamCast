@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.tuntech.supertvstreamcast.platform

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSURL
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.convert
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread

fun createAppPreferences(): AppPreferences {
    val prefs=NSUserDefaults.standardUserDefaults
    return object: AppPreferences {
        override var brand: String
            get()=prefs.stringForKey("tv_space_brand").orEmpty()
            set(value){prefs.setObject(value,"tv_space_brand")}
        override var goal: String
            get()=prefs.stringForKey("tv_space_goal").orEmpty()
            set(value){prefs.setObject(value,"tv_space_goal")}
        override var lastDevice: String
            get()=prefs.stringForKey("tv_space_last_device").orEmpty()
            set(value){prefs.setObject(value,"tv_space_last_device")}
    }
}
@Composable actual fun rememberScreenSharingAction(): (() -> Boolean)? = null
/** AVPlayer (HLS and MP4). The playback audio session keeps sound on when the ring switch is silent. */
@Composable actual fun StreamPlayer(url: String,userAgent: String,referer: String,modifier: Modifier,startMs: Long,controls: Boolean,fill: Boolean,
    onProgress: (Long,Long)->Unit,onEnded: ()->Unit,onError: () -> Unit) {
    val report by rememberUpdatedState(onError)
    val progress by rememberUpdatedState(onProgress)
    val ended by rememberUpdatedState(onEnded)
    val player=remember(url,userAgent,referer) {NSURL.URLWithString(url)?.let {target ->
        val headers=buildMap<Any?,Any?> {
            if(userAgent.isNotEmpty()) put("User-Agent",userAgent)
            if(referer.isNotEmpty()) put("Referer",referer)
        }
        val asset=AVURLAsset.URLAssetWithURL(target,if(headers.isEmpty()) null else mapOf<Any?,Any?>("AVURLAssetHTTPHeaderFieldsKey" to headers))
        AVPlayer(playerItem=AVPlayerItem(asset=asset)).also {created ->
            if(startMs>0) created.seekToTime(CMTimeMakeWithSeconds(startMs/1000.0,1000))
        }
    }}
    // Seconds played and total length; the length is not finite for live streams, which report nothing.
    val position={
        val item=player?.currentItem
        val duration=item?.let {CMTimeGetSeconds(it.duration)} ?: Double.NaN
        val seconds=player?.let {CMTimeGetSeconds(it.currentTime())} ?: Double.NaN
        if(duration.isFinite()&&duration>0&&seconds.isFinite()) {progress((seconds*1000).toLong(),(duration*1000).toLong());seconds>=duration-0.5} else false
    }
    val controller=remember(player) {AVPlayerViewController().apply{this.player=player}}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player,lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_PAUSE)player?.pause()}
        lifecycle.addObserver(observer)
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback,null)
        AVAudioSession.sharedInstance().setActive(true,null)
        player?.play()
        onDispose {lifecycle.removeObserver(observer);position();player?.pause();controller.player=null}
    }
    LaunchedEffect(player) {
        if(player==null){report();return@LaunchedEffect}
        var ticks=0
        while(true) {
            if(player.currentItem?.status==AVPlayerItemStatusFailed||player.status==AVPlayerStatusFailed){report();break}
            delay(500)
            // Every five seconds, and at once when the video stopped at its end.
            if(++ticks%10==0||player.rate==0f) {if(position()&&player.rate==0f){ended();break}}
        }
    }
    UIKitViewController(factory={controller},modifier=modifier,update={
        it.showsPlaybackControls=controls
        it.videoGravity=if(fill) AVLayerVideoGravityResizeAspectFill else AVLayerVideoGravityResizeAspect
    })
}

@Composable actual fun rememberFullscreenRequest(): ((Boolean)->Unit)? = null
@Composable actual fun rememberLanAccessRequest(): ((Boolean)->Unit)->Unit = remember { {callback -> callback(true)} }

@Composable actual fun rememberFilePicker(maxBytes: Int,onPicked: (ByteArray?)->Unit): ()->Unit {
    val result by rememberUpdatedState(onPicked)
    val scope=rememberCoroutineScope()
    val delegate=remember {PickerDelegate {url -> scope.launch {result(withContext(Dispatchers.Default) {readAtMost(url,maxBytes+1)})}}}
    return remember(delegate) {{
        val picker=UIDocumentPickerViewController(forOpeningContentTypes=listOf(UTTypeItem),asCopy=true)
        picker.delegate=delegate
        picker.allowsMultipleSelection=false
        var top=UIApplication.sharedApplication.keyWindow?.rootViewController
        while(top?.presentedViewController!=null) top=top.presentedViewController
        top?.presentViewController(picker,animated=true,completion=null)
    }}
}
private class PickerDelegate(private val onUrl: (NSURL)->Unit): NSObject(),UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController,didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onUrl)
    }
}
/** The picker copies the file into the app sandbox (asCopy), so plain POSIX reads suffice. */
private fun readAtMost(url: NSURL,limit: Int): ByteArray? {
    val path=url.path ?: return null
    val scoped=url.startAccessingSecurityScopedResource()
    val file=fopen(path,"rb")
    try {
        if(file==null) return null
        val buffer=ByteArray(limit)
        var total=0
        buffer.usePinned {pinned ->
            while(total<limit) {
                val read=fread(pinned.addressOf(total),1.convert(),(limit-total).convert(),file).toInt()
                if(read<=0) break
                total+=read
            }
        }
        return buffer.copyOf(total)
    } finally {
        if(file!=null) fclose(file)
        if(scoped) url.stopAccessingSecurityScopedResource()
    }
}
