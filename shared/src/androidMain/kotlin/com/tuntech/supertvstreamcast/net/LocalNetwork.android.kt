package com.tuntech.supertvstreamcast.net

import com.tuntech.supertvstreamcast.domain.isLocalIpv4
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import okhttp3.Dispatcher
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

actual fun pinnedLocalClient(host: String): HttpClient = HttpClient(OkHttp) {
    followRedirects = false
    install(WebSockets)
    install(HttpTimeout) { connectTimeoutMillis = 5_000; requestTimeoutMillis = 10_000 }
    engine {
        config {
            val trust = PinOnFirstUseTrustManager()
            val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
            sslSocketFactory(context.socketFactory, trust)
            hostnameVerifier { name, _ -> name == host }
            addInterceptor { chain ->
                if (chain.request().url.host != host) throw IOException("Host outside TV session")
                chain.proceed(chain.request())
            }
            pingInterval(20, TimeUnit.SECONDS)
        }
    }
}
actual fun discoveryClient(): HttpClient = HttpClient(OkHttp) {
    followRedirects = false
    install(WebSockets)
    install(HttpTimeout) { connectTimeoutMillis = 1_500; requestTimeoutMillis = 2_500; socketTimeoutMillis = 2_000 }
    // A scan probes ~250 hosts × 3 protocols; OkHttp's default 64-call limit would queue probes past their timeout.
    engine { config { dispatcher(Dispatcher().apply { maxRequests = 192; maxRequestsPerHost = 4 }) } }
}
actual fun localIpv4Address(): String? {
    val candidates = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
        .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
        .flatMap { network -> network.inetAddresses.toList().filterIsInstance<Inet4Address>().map { network.name to it.hostAddress.orEmpty() } }
        .filter { isLocalIpv4(it.second) }
    return (candidates.firstOrNull { it.first.startsWith("wlan") } ?: candidates.firstOrNull())?.second
}

/** Accepts the first leaf certificate this client sees and rejects any different one afterwards. */
private class PinOnFirstUseTrustManager : X509TrustManager {
    private var pin: ByteArray? = null
    @Synchronized override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull()?.encoded ?: throw CertificateException("No certificate")
        val pinned = pin
        if (pinned == null) pin = leaf else if (!pinned.contentEquals(leaf)) throw CertificateException("Certificate changed")
    }
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException()
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
