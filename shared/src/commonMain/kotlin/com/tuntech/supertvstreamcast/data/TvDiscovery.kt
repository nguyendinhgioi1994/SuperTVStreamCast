package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * User-started unicast scan of the phone's /24 subnet. A host is listed only when it answers a vendor
 * protocol probe (Samsung device API, Sony interface info, LG SSAP). Listing does not mean paired.
 */
class TvDiscovery(private val client: HttpClient, private val open: SocketOpener, private val parallelism: Int = 48) {
    suspend fun scan(localIp: String, onFound: (TvDevice) -> Unit = {}): List<TvDevice> = coroutineScope {
        val permits = Semaphore(parallelism)
        subnetHosts(localIp).map { host ->
            async { permits.withPermit { probe(host) }?.also(onFound) }
        }.awaitAll().filterNotNull()
    }
    suspend fun probe(host: String): TvDevice? = coroutineScope {
        listOf(async { samsung(host) }, async { sony(host) }, async { lg(host) }).awaitAll().firstNotNullOfOrNull { it }
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
