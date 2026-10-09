package com.tuntech.supertvstreamcast.ui.iptv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Hand-over point for `tvspace://import-playlist?name=…&url=…` links opened from outside the app.
 * The platform entry points call [handle]; the dashboard picks the link up once the library is
 * ready, so a link that launched the app is not lost during the splash or onboarding. The link
 * may carry a login and is never logged.
 */
object ImportLinks {
    private val mutable = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = mutable

    fun handle(link: String?) { if (!link.isNullOrBlank()) mutable.value = link }
    fun consume() { mutable.value = null }
}
