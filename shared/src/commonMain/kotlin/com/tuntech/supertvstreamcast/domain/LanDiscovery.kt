package com.tuntech.supertvstreamcast.domain

/**
 * A device on the Wi-Fi that announced [service] over DNS-SD (a service type such as
 * `_googlecast._tcp`) or SSDP (`ssdp:` + search target). An announcement alone is not a TV:
 * the host still has to answer a vendor protocol probe, except for [LanServices.GOOGLE_TV_REMOTE].
 */
data class LanAnnouncement(val host: String, val service: String, val name: String = "")

object LanServices {
    /** Android TV Remote v2, the protocol `GoogleTvAdapter` speaks. Announcing it is the TV's own answer. */
    const val GOOGLE_TV_REMOTE = "_androidtvremote2._tcp"
    /** Browsed with Bonjour / NSD. Cast and AirPlay receivers are only candidates for the vendor probes. */
    val DNS_SD_TYPES = listOf(GOOGLE_TV_REMOTE, "_googlecast._tcp", "_airplay._tcp")
    /** Samsung remote receiver, LG second screen, Sony Scalar API, Roku ECP, Panasonic network remote, and DIAL which most smart TVs answer. */
    val SSDP_TARGETS = listOf(
        "urn:samsung.com:device:RemoteControlReceiver:1",
        "urn:lge-com:service:webos-second-screen:1",
        "urn:schemas-sony-com:service:ScalarWebAPI:1",
        RokuProtocol.SSDP_TARGET,
        PanasonicProtocol.SSDP_TARGET,
        "urn:dial-multiscreen-org:service:dial:1",
    )
    const val SSDP_GROUP = "239.255.255.250"
    const val SSDP_PORT = 1900
}

object Ssdp {
    fun search(target: String, waitSeconds: Int = 2) =
        "M-SEARCH * HTTP/1.1\r\nHOST: ${LanServices.SSDP_GROUP}:${LanServices.SSDP_PORT}\r\nMAN: \"ssdp:discover\"\r\nMX: $waitSeconds\r\nST: $target\r\n\r\n"

    /** A search response from [sender]; the host is the one in `LOCATION` when it is private, else the sender. */
    fun announcement(sender: String, text: String): LanAnnouncement? {
        val lines = text.split("\r\n", "\n")
        if (!lines.first().trim().startsWith("HTTP/1.1 200", ignoreCase = true)) return null
        val headers = lines.drop(1).mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon).trim().uppercase() to line.substring(colon + 1).trim()
        }.toMap()
        val target = headers["ST"]?.takeIf { it.isNotEmpty() } ?: return null
        val host = headers["LOCATION"]?.let { LOCATION_HOST.find(it)?.groupValues?.get(1) }?.takeIf(::isLocalIpv4) ?: sender
        if (!isLocalIpv4(host)) return null
        return LanAnnouncement(host, "ssdp:$target", headers["SERVER"].orEmpty())
    }
    private val LOCATION_HOST = Regex("^https?://([^/:]+)", RegexOption.IGNORE_CASE)
}

/** Discovered TVs by address (numerically), then brand, so the list does not reorder as answers arrive. */
val deviceOrder: Comparator<TvDevice> = compareBy<TvDevice>(
    { device -> device.host.split('.').fold(0L) { total, part -> total * 256 + (part.toLongOrNull() ?: 0) } },
    { it.brand.ordinal },
)
