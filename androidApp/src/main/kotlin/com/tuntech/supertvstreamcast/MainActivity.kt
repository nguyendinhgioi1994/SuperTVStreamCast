package com.tuntech.supertvstreamcast

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.tuntech.supertvstreamcast.domain.TvBrand
import com.tuntech.supertvstreamcast.theme.TvThemeMode
import com.tuntech.supertvstreamcast.ui.iptv.ImportLinks
import com.tuntech.supertvstreamcast.ui.rememberAppState
import com.tuntech.supertvstreamcast.ui.storeshot.StoreShot
import com.tuntech.supertvstreamcast.ui.storeshot.StoreShotApp

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

        storeShotScene()?.let { scene ->
            setContent { StoreShotApp(scene.shot, scene.assets, scene.theme, scene.brand) }
            return
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

    /**
     * `adb shell am start -n <pkg>/.MainActivity --es StoreShot <scene> --es StoreShotAssets <url>
     * [--es StoreShotTheme light] [--es StoreShotBrand LG]` opens a store screenshot scene. Debuggable
     * builds only.
     */
    private fun storeShotScene(): StoreShotLaunch? {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
        val extras = intent?.extras ?: return null
        val shot = StoreShot.parse(extras.getString("StoreShot")) ?: return null
        val assets = extras.getString("StoreShotAssets") ?: "http://127.0.0.1:8765"
        val theme = if (extras.getString("StoreShotTheme") == "light") TvThemeMode.LIGHT else TvThemeMode.DARK
        val brand = TvBrand.entries.firstOrNull { it.name.equals(extras.getString("StoreShotBrand"), ignoreCase = true) } ?: TvBrand.SAMSUNG
        return StoreShotLaunch(shot, assets, theme, brand)
    }

    private class StoreShotLaunch(val shot: StoreShot, val assets: String, val theme: TvThemeMode, val brand: TvBrand)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ImportLinks.handle(intent.dataString)
    }
}
