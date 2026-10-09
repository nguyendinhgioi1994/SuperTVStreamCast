package com.tuntech.supertvstreamcast.platform

/** TV choices made in onboarding/settings. The "onboarding finished" flag lives in AppSettingRepository. */
interface AppPreferences {
    var brand: String
    var goal: String
    /** "BRAND|host|name" of the last TV that completed a handshake. No keys or tokens. */
    var lastDevice: String
}
