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
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
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

@Composable actual fun rememberAppPreferences(): AppPreferences = remember {
    val prefs=NSUserDefaults.standardUserDefaults
    object: AppPreferences {
        override var onboardingDone: Boolean
            get()=prefs.boolForKey("tv_space_onboarding")
            set(value){prefs.setBool(value,"tv_space_onboarding")}
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
@Composable actual fun StreamPlayer(url: String,modifier: Modifier,onError: () -> Unit) {
    val report by rememberUpdatedState(onError)
    val player=remember(url) {NSURL.URLWithString(url)?.let { AVPlayer(uRL=it) }}
    val controller=remember(player) {AVPlayerViewController().apply{this.player=player}}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(player,lifecycle) {
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_PAUSE)player?.pause()}
        lifecycle.addObserver(observer)
        player?.play()
        onDispose {lifecycle.removeObserver(observer);player?.pause();controller.player=null}
    }
    LaunchedEffect(player) {
        if(player==null){report();return@LaunchedEffect}
        while(true) {
            if(player.currentItem?.status==AVPlayerItemStatusFailed){report();break}
            delay(500)
        }
    }
    UIKitViewController(factory={controller},modifier=modifier)
}

@Composable actual fun rememberLanAccessRequest(): ((Boolean)->Unit)->Unit = remember { {callback -> callback(true)} }
@Composable actual fun AppBackHandler(enabled: Boolean,onBack: ()->Unit) = Unit

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
