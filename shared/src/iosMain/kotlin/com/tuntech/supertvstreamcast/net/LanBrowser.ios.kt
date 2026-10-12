@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.tuntech.supertvstreamcast.net

import com.tuntech.supertvstreamcast.domain.LanAnnouncement
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import platform.Foundation.*
import platform.darwin.NSObject
import platform.posix.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Bonjour (`NSNetServiceBrowser`, types listed under `NSBonjourServices` in Info.plist) and a POSIX
 * UDP socket for SSDP. On a real iPhone, sending to the SSDP group needs Apple's multicast
 * entitlement; without it the send fails and only Bonjour and the unicast sweep remain.
 */
fun createLanBrowser(): LanBrowser = LanBrowser(dnsSd = ::bonjourServices, multicast = ::multicastSearch)

private fun bonjourServices(types: List<String>): Flow<LanAnnouncement> = callbackFlow {
    val delegate = BonjourDelegate { trySend(it) }
    val browsers = types.map { type ->
        NSNetServiceBrowser().apply {
            setDelegate(delegate)
            searchForServicesOfType("$type.", inDomain = "local.")
        }
    }
    awaitClose {
        browsers.forEach { it.stop() }
        delegate.services.forEach { it.stop() }
    }
}.flowOn(Dispatchers.Main) // browsers and resolves are scheduled on the main run loop

private class BonjourDelegate(private val found: (LanAnnouncement) -> Unit) : NSObject(), NSNetServiceBrowserDelegateProtocol, NSNetServiceDelegateProtocol {
    /** Services are kept referenced while they resolve; Foundation does not retain them. */
    val services = mutableListOf<NSNetService>()
    override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
        services += didFindService
        didFindService.setDelegate(this)
        didFindService.resolveWithTimeout(3.0)
    }
    override fun netServiceDidResolveAddress(sender: NSNetService) {
        val host = sender.addresses?.firstNotNullOfOrNull { (it as? NSData)?.ipv4Host() } ?: return
        found(LanAnnouncement(host, sender.type.trimEnd('.'), sender.name))
    }
}

private fun NSData.ipv4Host(): String? {
    if (length < sizeOf<sockaddr_in>().convert()) return null
    val address = bytes?.reinterpret<sockaddr_in>()?.pointed ?: return null
    if (address.sin_family.toInt() != AF_INET) return null
    return address.sin_addr.dotted()
}

/** `s_addr` is in network order, so its bytes in memory are the dotted quad from left to right. */
private fun in_addr.dotted(): String = s_addr.toInt().let { (0..3).joinToString(".") { i -> ((it shr (8 * i)) and 0xFF).toString() } }

/** Network-order `s_addr` of an IPv4 dotted quad, or null when [host] is not one. */
private fun ipv4Address(host: String): UInt? {
    val parts = host.split('.').map { it.toIntOrNull()?.takeIf { part -> part in 0..255 } ?: return null }
    if (parts.size != 4) return null
    return parts.foldIndexed(0u) { i, acc, part -> acc or (part.toUInt() shl (8 * i)) }
}

private fun multicastSearch(group: String, port: Int, payloads: List<ByteArray>): Flow<Datagram> = flow {
    val descriptor = socket(AF_INET, SOCK_DGRAM, 0)
    check(descriptor >= 0)
    try {
        var sent = 0
        var nextSend = TimeSource.Monotonic.markNow()
        while (currentCoroutineContext().isActive) {
            // UDP may drop the search, so it goes out twice, a second apart.
            if (sent < 2 && nextSend.hasPassedNow()) {
                payloads.forEach { sendTo(descriptor, group, port, it) }
                sent++; nextSend = TimeSource.Monotonic.markNow() + 1.seconds
            }
            receive(descriptor, timeoutMillis = 250)?.let { emit(it) }
        }
    } finally { close(descriptor) }
}.flowOn(Dispatchers.IO)

private fun sendTo(descriptor: Int, address: String, port: Int, payload: ByteArray) = memScoped {
    val target = alloc<sockaddr_in>()
    memset(target.ptr, 0, sizeOf<sockaddr_in>().convert())
    target.sin_len = sizeOf<sockaddr_in>().convert()
    target.sin_family = AF_INET.convert()
    target.sin_port = (((port shr 8) and 0xFF) or ((port and 0xFF) shl 8)).toUShort()
    target.sin_addr.s_addr = checkNotNull(ipv4Address(address))
    val sent = payload.usePinned { sendto(descriptor, it.addressOf(0), payload.size.convert(), 0, target.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) }
    check(sent.toInt() == payload.size)
}

/** One datagram, or null when none arrived within [timeoutMillis]. */
private fun receive(descriptor: Int, timeoutMillis: Int): Datagram? = memScoped {
    val wait = alloc<pollfd>()
    wait.fd = descriptor
    wait.events = POLLIN.convert()
    if (poll(wait.ptr, 1u, timeoutMillis) <= 0) return@memScoped null
    val buffer = ByteArray(8 * 1024)
    val sender = alloc<sockaddr_in>()
    val senderLength = alloc<socklen_tVar>()
    senderLength.value = sizeOf<sockaddr_in>().convert()
    val read = buffer.usePinned { recvfrom(descriptor, it.addressOf(0), buffer.size.convert(), 0, sender.ptr.reinterpret(), senderLength.ptr) }
    if (read <= 0) return@memScoped null
    Datagram(sender.sin_addr.dotted(), buffer.copyOf(read.toInt()))
}
