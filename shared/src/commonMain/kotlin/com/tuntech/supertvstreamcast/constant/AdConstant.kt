package com.tuntech.supertvstreamcast.constant

expect object AdConstant {
    val APP_OPEN_AD_UNIT_ID: String
    val INTERSTITIAL_AD_UNIT_ID: String
    val BANNER_AD_UNIT_ID: String
    val NATIVE_AD_UNIT_ID: String
    val REWARDED_AD_UNIT_ID: String
}

/** Placement names looked up in the `ADS_*_SETTINGS` Remote Config keys. */
object AdName {
    // App open
    const val APP_FOREGROUND = "APP_FOREGROUND"
    /** Cold start of a returning user, shown by the Splash before the Dashboard. */
    const val SPLASH = "SPLASH"

    // Interstitial
    const val APP_EXIT = "APP_EXIT"
    const val INTRODUCTION = "INTRODUCTION"
    const val PREMIUM = "PREMIUM"
    /** A Home quick-access card opening Remote, Cast or IPTV. */
    const val FEATURE_OPEN = "FEATURE_OPEN"
    /** Opening a saved IPTV source from the hub. */
    const val IPTV_SOURCE_OPEN = "IPTV_SOURCE_OPEN"
    /** After the IPTV player's close button. */
    const val IPTV_PLAYER_CLOSE = "IPTV_PLAYER_CLOSE"

    // Native (INTRODUCTION is also the small native under the onboarding pages)
    const val LANGUAGE = "LANGUAGE"
    /** Full-page natives inserted into the onboarding pager; `position` = content pages before them. */
    const val ONBOARDING_FULL_1 = "ONBOARDING_FULL_1"
    const val ONBOARDING_FULL_2 = "ONBOARDING_FULL_2"

    // Rewarded: the opt-in alternative to the paywall at a premium gate
    const val UNLOCK_REMOTE = "UNLOCK_REMOTE"
    const val UNLOCK_IPTV_TIME = "UNLOCK_IPTV_TIME"
    const val UNLOCK_IPTV_SOURCE = "UNLOCK_IPTV_SOURCE"
}
