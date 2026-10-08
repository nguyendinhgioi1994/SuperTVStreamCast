package com.tuntech.supertvstreamcast.platform

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.VideoView
import android.widget.MediaController
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

@Composable actual fun rememberAppPreferences(): AppPreferences {
    val context=LocalContext.current.applicationContext
    return remember(context) {
        val prefs=context.getSharedPreferences("tv_space",0)
        object: AppPreferences {
            override var onboardingDone: Boolean
                get()=prefs.getBoolean("onboarding_done",false)
                set(value){prefs.edit().putBoolean("onboarding_done",value).apply()}
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
}
@Composable actual fun rememberScreenSharingAction(): (() -> Boolean)? {
    val context=LocalContext.current
    return remember(context) {{
        try {context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS));true}
        catch (_: android.content.ActivityNotFoundException) {false}
        catch (_: SecurityException) {false}
    }}
}
@Composable actual fun StreamPlayer(url: String,modifier: Modifier,onError: ()->Unit) {
    val context=LocalContext.current
    val report by rememberUpdatedState(onError)
    val view=remember(url) {VideoView(context).apply {
        setMediaController(MediaController(context).also{it.setAnchorView(this)})
        setOnErrorListener {_,_,_->report();true}
        setOnPreparedListener {start()}
        setVideoURI(Uri.parse(url))
    }}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view,lifecycle) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_PAUSE)view.pause()}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);view.stopPlayback()}
    }
    AndroidView(factory={view},modifier=modifier)
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
@Composable actual fun AppBackHandler(enabled: Boolean,onBack: ()->Unit) {
    androidx.activity.compose.BackHandler(enabled,onBack)
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
