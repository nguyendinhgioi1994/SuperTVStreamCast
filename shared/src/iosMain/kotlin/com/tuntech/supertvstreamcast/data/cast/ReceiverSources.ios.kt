package com.tuntech.supertvstreamcast.data.cast

/** iOS reaches DLNA renderers from common code; AirPlay goes through the system player's route picker. */
actual fun platformReceiverSources(context: coil3.PlatformContext): List<ReceiverSource> = emptyList()
