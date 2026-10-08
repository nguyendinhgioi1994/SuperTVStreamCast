package com.tuntech.supertvstreamcast.platform

interface AppPreferences {
    var onboardingDone: Boolean
    var brand: String
    var goal: String
    /** "BRAND|host|name" of the last TV that completed a handshake. No keys or tokens. */
    var lastDevice: String
}
