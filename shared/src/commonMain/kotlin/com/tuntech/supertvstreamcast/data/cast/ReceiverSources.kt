package com.tuntech.supertvstreamcast.data.cast

/** Receivers only a platform SDK can reach: Chromecast on Android, none on iOS (AirPlay is the system's). */
expect fun platformReceiverSources(context: coil3.PlatformContext): List<ReceiverSource>
