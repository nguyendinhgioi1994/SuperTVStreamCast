@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package com.tuntech.supertvstreamcast.net

import com.tuntech.supertvstreamcast.domain.isLocalIpv4
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.Security.*
import platform.posix.*

actual fun pinnedLocalClient(host: String): HttpClient {
    var pin: NSData? = null
    return HttpClient(Darwin) {
        followRedirects = false
        install(WebSockets)
        install(HttpTimeout) { connectTimeoutMillis = 5_000; requestTimeoutMillis = 10_000 }
        engine {
            handleChallenge { _, task, challenge, completion ->
                val space = challenge.protectionSpace
                if (space.authenticationMethod != NSURLAuthenticationMethodServerTrust) {
                    completion(NSURLSessionAuthChallengePerformDefaultHandling, null)
                } else {
                    val trust = space.serverTrust
                    val leaf = if (space.host == host && task.originalRequest?.URL?.host == host && trust != null) leafCertificate(trust) else null
                    val accepted = leaf != null && (pin?.isEqualToData(leaf) ?: run { pin = leaf; true })
                    if (accepted && trust != null) completion(NSURLSessionAuthChallengeUseCredential, NSURLCredential.credentialForTrust(trust))
                    else completion(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                }
            }
        }
    }
}
private fun leafCertificate(trust: SecTrustRef): NSData? {
    val certificate = SecTrustGetCertificateAtIndex(trust, 0) ?: return null
    val data = SecCertificateCopyData(certificate) ?: return null
    return CFBridgingRelease(data) as? NSData
}
actual fun discoveryClient(): HttpClient = HttpClient(Darwin) {
    followRedirects = false
    install(WebSockets)
    install(HttpTimeout) { connectTimeoutMillis = 1_500; requestTimeoutMillis = 2_500; socketTimeoutMillis = 2_000 }
}
actual fun localIpv4Address(): String? = memScoped {
    val list = alloc<CPointerVar<ifaddrs>>()
    if (getifaddrs(list.ptr) != 0) return@memScoped null
    try {
        var fallback: String? = null
        var cursor = list.value
        while (cursor != null) {
            val item = cursor.pointed
            val address = item.ifa_addr
            if (address != null && address.pointed.sa_family.toInt() == AF_INET) {
                val buffer = allocArray<ByteVar>(INET_ADDRSTRLEN)
                val ipv4 = address.reinterpret<sockaddr_in>().pointed
                if (inet_ntop(AF_INET, ipv4.sin_addr.ptr, buffer, INET_ADDRSTRLEN.convert()) != null) {
                    val ip = buffer.toKString()
                    if (isLocalIpv4(ip)) {
                        if (item.ifa_name?.toKString() == "en0") return@memScoped ip
                        if (fallback == null) fallback = ip
                    }
                }
            }
            cursor = item.ifa_next
        }
        fallback
    } finally { freeifaddrs(list.value) }
}
