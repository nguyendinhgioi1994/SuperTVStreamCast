package com.tuntech.supertvstreamcast.net

import com.tuntech.supertvstreamcast.domain.LanAnnouncement
import com.tuntech.supertvstreamcast.domain.LanServices
import com.tuntech.supertvstreamcast.domain.Ssdp
import kotlinx.coroutines.flow.*

/** One UDP datagram received from [sender]. */
class Datagram(val sender: String, val payload: ByteArray)

/**
 * Asks devices on the Wi-Fi to announce themselves: DNS-SD (Android NSD / iOS Bonjour) for
 * [LanServices.DNS_SD_TYPES] and an SSDP M-SEARCH for [LanServices.SSDP_TARGETS]. Only used after the
 * user starts a scan; listening stops when the collector stops. A failing source (no multicast
 * entitlement, Wi-Fi off) ends quietly and leaves the other one running.
 */
class LanBrowser(
    private val dnsSd: (types: List<String>) -> Flow<LanAnnouncement>,
    /** Sends [payloads] to the multicast group (and again shortly after) and emits the unicast replies. */
    private val multicast: (group: String, port: Int, payloads: List<ByteArray>) -> Flow<Datagram>,
) {
    fun browse(): Flow<LanAnnouncement> = merge(
        dnsSd(LanServices.DNS_SD_TYPES).catch { },
        multicast(LanServices.SSDP_GROUP, LanServices.SSDP_PORT, LanServices.SSDP_TARGETS.map { Ssdp.search(it).encodeToByteArray() })
            .mapNotNull { Ssdp.announcement(it.sender, it.payload.decodeToString()) }
            .catch { },
    )
    /** Raw replies to an SSDP M-SEARCH for [targets]; used by DLNA casting, which needs the `LOCATION` header. Ends quietly when the socket fails. */
    fun ssdp(targets: List<String>): Flow<Datagram> =
        multicast(LanServices.SSDP_GROUP, LanServices.SSDP_PORT, targets.map { Ssdp.search(it).encodeToByteArray() }).catch { }
    companion object {
        val None = LanBrowser({ emptyFlow() }, { _, _, _ -> emptyFlow() })
    }
}
