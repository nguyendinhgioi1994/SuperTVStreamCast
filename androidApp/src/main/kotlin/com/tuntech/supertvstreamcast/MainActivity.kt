package com.tuntech.supertvstreamcast

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.tuntech.supertvstreamcast.ui.iptv.ImportLinks
import com.tuntech.supertvstreamcast.ui.rememberAppState

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Phones are portrait-only; tablets rotate.
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp >= 600) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // A restored activity already handled the link that started it.
        if (savedInstanceState == null) ImportLinks.handle(intent?.dataString)

        setContent {
            MainApp(
                appState = rememberAppState(),
                isRestored = savedInstanceState != null,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ImportLinks.handle(intent.dataString)
    }
}
