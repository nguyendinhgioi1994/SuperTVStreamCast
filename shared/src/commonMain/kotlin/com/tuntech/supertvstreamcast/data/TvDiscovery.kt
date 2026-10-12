package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.net.LanBrowser
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * User-started search. Listens for DNS-SD / SSDP announcements while sweeping the phone's /24 subnet
 * (unicast), and probes each host once. A host is listed only when it answers a vendor protocol probe
 * (Samsung device API, Sony interface info, LG SSAP, Roku ECP device info, Panasonic Viera remote description,
 * Fire TV DIAL remote app), or when it announces the Android TV Remote v2
 * service itself. Announcements reach TVs outside the /24 and answer first; the sweep finds TVs that
 * announce nothing. Listing does not mean paired.
 */
class TvDiscovery(
    private val client: HttpClient, private val open: SocketOpener,
    private val browser: LanBrowser = LanBrowser.None,
    private val parallelism: Int = 48, private val listenMillis: Long = 5_000,
) {
    suspend fun scan(localIp: String, onFound: (TvDevice) -> Unit = {}): List<TvDevice> {
        val lock = Mutex()
        val probed = mutableSetOf<String>()
        val found = mutableListOf<TvDevice>()
        val permits = Semaphore(parallelism)
        suspend fun report(device: TvDevice) = lock.withLock {
            if (found.none { it.brand == device.brand && it.host == device.host }) { found += device; onFound(device) }
        }
        /** Announced hosts are few and skip the sweep's queue, so they are listed first. */
        suspend fun check(host: String, announced: Boolean) {
            if (!lock.withLock { probed.add(host) }) return
            (if (announced) probe(host) else permits.withPermit { probe(host) })?.let { report(it) }
        }
        coroutineScope {
            val probes = this
            launch {
                withTimeoutOrNull(listenMillis) {
                    browser.browse().collect { announcement ->
                        val host = announcement.host
                        if (!isLocalIpv4(host)) return@collect
                        if (announcement.service == LanServices.GOOGLE_TV_REMOTE)
                            report(TvDevice(TvBrand.GOOGLE, host, announcement.name.ifBlank { TvBrand.GOOGLE.title }))
                        // A Google TV may also be a Sony BRAVIA with IP control; probed like any other host.
                        probes.launch { check(host, announced = true) }
                    }
                }
            }
            subnetHosts(localIp).forEach { host -> launch { check(host, announced = false) } }
        }
        return lock.withLock { found.sortedWith(deviceOrder) }
    }
    suspend fun probe(host: String): TvDevice? = coroutineScope {
        listOf(async { samsung(host) }, async { sony(host) }, async { lg(host) }, async { roku(host) }, async { panasonic(host) }, async { fireTv(host) })
            .awaitAll().firstNotNullOfOrNull { it }
    }
    private suspend fun samsung(host: String) = quiet {
        val response = client.get("http://$host:8001/api/v2/")
        if (response.status.isSuccess()) SamsungProtocol.info(host, response.bodyAsText())?.device else null
    }
    private suspend fun sony(host: String) = quiet {
        val response = client.post("http://$host/sony/system") {
            contentType(ContentType.Application.Json)
            setBody(SonyProtocol.request("getInterfaceInformation"))
        }
        if (response.status.isSuccess()) SonyProtocol.identity(host, response.bodyAsText()) else null
    }
    /** Roku players and Roku TVs (TCL, Hisense, …) answer ECP device info without pairing. */
    private suspend fun roku(host: String) = quiet {
        val response = client.get("${RokuProtocol.base(host)}/query/device-info")
        if (response.status.isSuccess()) RokuProtocol.info(host, response.bodyAsText())?.device else null
    }
    private suspend fun panasonic(host: String) = quiet {
        val response = client.get(PanasonicProtocol.description(host))
        if (response.status.isSuccess()) PanasonicProtocol.identity(host, response.bodyAsText()) else null
    }
    /** Reading the DIAL app description starts nothing on the TV; only Fire TVs list the remote app. */
    private suspend fun fireTv(host: String) = quiet {
        val response = client.get(FireTvProtocol.dial(host))
        if (response.status.isSuccess() && FireTvProtocol.isFireTv(response.bodyAsText()))
            TvDevice(TvBrand.AMAZON, host, "Fire TV", platform = TvPlatform.FIRE_TV) else null
    }
    /** An unregistered SSAP request is answered with an error carrying the same id. */
    private suspend fun lg(host: String) = quiet {
        val socket = open(host, "ws://$host:3000")
        try {
            socket.send(LgProtocol.request("probe", "ssap://api/getServiceList"))
            when (val message = LgProtocol.message(socket.receive())) {
                is LgProtocol.Message.Failure -> message.id == "probe"
                is LgProtocol.Message.Response -> message.id == "probe"
                else -> false
            }.let { if (it) TvDevice(TvBrand.LG, host, "LG webOS TV") else null }
        } finally { socket.close() }
    }
    private suspend fun quiet(block: suspend () -> TvDevice?): TvDevice? = try {
        withTimeout(2_500) { block() }
    } catch (e: CancellationException) {
        if (e is TimeoutCancellationException) null else throw e
    } catch (_: Exception) { null }
}
