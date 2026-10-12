package com.tuntech.supertvstreamcast.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.tuntech.supertvstreamcast.domain.LanAnnouncement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.coroutines.resume

/** NSD for DNS-SD and a plain UDP socket for SSDP (the replies are unicast, so no multicast lock is held). */
fun createLanBrowser(context: Context): LanBrowser {
    val app = context.applicationContext
    return LanBrowser(dnsSd = { types -> nsdServices(app, types) }, multicast = ::multicastSearch)
}

private fun nsdServices(context: Context, types: List<String>): Flow<LanAnnouncement> = callbackFlow {
    val nsd = context.getSystemService(NsdManager::class.java)
    if (nsd == null) { close(); return@callbackFlow }
    val found = Channel<Pair<String, NsdServiceInfo>>(Channel.UNLIMITED)
    val listeners = types.map { type ->
        object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) { found.trySend(type to info) }
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }.also { listener -> runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) } }
    }
    // Before Android 14 NSD resolves one service at a time, so resolves run in order.
    launch { for ((type, info) in found) resolve(nsd, type, info)?.let { send(it) } }
    awaitClose {
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        found.close()
    }
}

@Suppress("DEPRECATION")
private suspend fun resolve(nsd: NsdManager, type: String, info: NsdServiceInfo): LanAnnouncement? = withTimeoutOrNull(3_000) {
    suspendCancellableCoroutine { continuation ->
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = continuation.resume(null)
            override fun onServiceResolved(info: NsdServiceInfo) {
                val address = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses.firstOrNull { it is Inet4Address } else info.host as? Inet4Address
                continuation.resume(address?.hostAddress?.let { LanAnnouncement(it, type, info.serviceName.orEmpty()) })
            }
        }
        try { nsd.resolveService(info, listener) } catch (_: IllegalArgumentException) { continuation.resume(null) }
    }
}

private fun multicastSearch(group: String, port: Int, payloads: List<ByteArray>): Flow<Datagram> = flow {
    DatagramSocket().use { socket ->
        socket.soTimeout = 250
        val target = InetAddress.getByName(group)
        val buffer = ByteArray(8 * 1024)
        var sent = 0
        var nextSend = 0L
        while (currentCoroutineContext().isActive) {
            // UDP may drop the search, so it goes out twice, a second apart.
            if (sent < 2 && System.currentTimeMillis() >= nextSend) {
                payloads.forEach { socket.send(DatagramPacket(it, it.size, target, port)) }
                sent++; nextSend = System.currentTimeMillis() + 1_000
            }
            val packet = DatagramPacket(buffer, buffer.size)
            try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
            emit(Datagram(packet.address.hostAddress.orEmpty(), packet.data.copyOf(packet.length)))
        }
    }
}.flowOn(Dispatchers.IO)
