package com.tuntech.supertvstreamcast.constant

object IapConstant {
    /**
     * Qonversion project key (public SDK key) of Super TV Cast. Leaving it blank makes the splash skip
     * IAP init, so no paywall is auto-shown and no product is listed.
     */
    const val PROJECT_KEY = "r0TkVZ-7O8Slj14JGj2Ibu4fIxZhSwS9"
    const val ENTITLEMENT_KEY = "premium"

    const val IAP_SOURCE_SETTING = "setting"
    const val IAP_SOURCE_HOME = "home"
    const val IAP_SOURCE_HEADER = "header"
    const val IAP_SOURCE_HOME_BANNER = "home_banner"
    const val IAP_SOURCE_ONBOARDING = "onboarding"
    const val IAP_SOURCE_IPTV_LIMIT = "iptv_limit"
    const val IAP_SOURCE_REMOTE_LOCK = "remote_lock"
}
