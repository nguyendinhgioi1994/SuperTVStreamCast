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
