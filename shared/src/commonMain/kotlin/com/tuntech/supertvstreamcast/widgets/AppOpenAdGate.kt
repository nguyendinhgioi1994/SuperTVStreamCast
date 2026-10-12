package com.tuntech.supertvstreamcast.widgets

/**
 * Keeps the app-open ad off returns the user expects to land straight back on: a system picker,
 * share sheet or settings page the app itself opened, and IPTV playback or a cast session in
 * progress. Read by [AppLifecycleObserver]; used on the main thread only.
 */
object AppOpenAdGate {
    private var skipNext = false
    var playing = false
    var casting = false

    /** Call right before handing the user to another app or system screen. */
    fun skipNextReturn() { skipNext = true }

    /** Consumes a pending skip; false while playback or casting runs. */
    internal fun allow(): Boolean {
        if (skipNext) { skipNext = false; return false }
        return !playing && !casting
    }
}
