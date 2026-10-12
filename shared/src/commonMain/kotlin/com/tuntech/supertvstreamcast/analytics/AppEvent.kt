package com.tuntech.supertvstreamcast.analytics

import com.tuntech.mmp.MmpManager

/**
 * Outcome events for the funnel onboarding → connect → feature used → paywall → purchase; taps are [ClickEvent].
 * A success is only logged after the TV answered. Same parameter rules as [ClickEvent]: no IP, PSK,
 * pairing code, URL, login or typed text.
 */
object AppEvent {
    const val TV_CONNECT_ATTEMPT = "tv_connect_attempt"
    const val TV_CONNECT_CODE_REQUIRED = "tv_connect_code_required"
    const val TV_CONNECT_SUCCESS = "tv_connect_success"
    const val TV_CONNECT_FAIL = "tv_connect_fail"
    const val FEATURE_USED = "feature_used"

    fun log(name: String, params: Map<String, String>) {
        MmpManager.shared?.trackEvent(name, params)
    }
}

/** Where outcome events go; view models take one so tests can record them. */
typealias EventSink = (name: String, params: Map<String, String>) -> Unit

internal fun EventSink.log(name: String, vararg params: Pair<String, Any?>) = invoke(name, clickParams(params.asList()))
