package com.tuntech.supertvstreamcast.net

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.tuntech.supertvstreamcast.domain.isLocalIpv4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

actual suspend fun openTvTls(host: String, port: Int): TlsChannel = withContext(Dispatchers.IO) {
    require(isLocalIpv4(host))
    val (key, certificate) = identity()
    val context = SSLContext.getInstance("TLS").apply { init(arrayOf(IdentityKeyManager(key, certificate)), arrayOf(UnverifiedLocalTrust), null) }
    val socket = context.socketFactory.createSocket() as SSLSocket
    try {
        // Keystore-held RSA keys sign TLS 1.2 handshakes on every supported Android version.
        socket.enabledProtocols = arrayOf("TLSv1.2")
        socket.connect(InetSocketAddress(host, port), 5_000)
        socket.soTimeout = 10_000
        socket.startHandshake()
        socket.soTimeout = 0
        val peer = socket.session.peerCertificates.first().encoded
        SocketChannel(socket, certificate.encoded, peer)
    } catch (e: Throwable) { runCatching { socket.close() }; throw e }
}
actual suspend fun sendBroadcast(address: String, port: Int, payload: ByteArray) = withContext(Dispatchers.IO) {
    DatagramSocket().use { socket ->
        socket.broadcast = true
        socket.send(DatagramPacket(payload, payload.size, InetAddress.getByName(address), port))
    }
}

private class SocketChannel(private val socket: SSLSocket, override val localCertificate: ByteArray, override val peerCertificate: ByteArray) : TlsChannel {
    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) { socket.outputStream.run { write(bytes); flush() } }
    override suspend fun read(count: Int): ByteArray = withContext(Dispatchers.IO) {
        val bytes = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val read = socket.inputStream.read(bytes, filled, count - filled)
            if (read < 0) throw EOFException()
            filled += read
        }
        bytes
    }
    // Closing also releases a reader blocked in read().
    override fun close() { runCatching { socket.close() } }
}

/** This phone's remote-control identity: a self-signed RSA certificate whose key stays in the Android Keystore. */
@Synchronized private fun identity(): Pair<PrivateKey, X509Certificate> {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    if (!store.containsAlias(IDENTITY_ALIAS)) {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(IDENTITY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setKeySize(2048)
                .setCertificateSubject(X500Principal("CN=atvremote"))
                .setCertificateNotBefore(Date(1_704_067_200_000L))
                .setCertificateNotAfter(Date(4_102_358_400_000L))
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .build())
        }.generateKeyPair()
    }
    return store.getKey(IDENTITY_ALIAS, null) as PrivateKey to store.getCertificate(IDENTITY_ALIAS) as X509Certificate
}
private const val IDENTITY_ALIAS = "tv_space_remote_identity"

private class IdentityKeyManager(private val key: PrivateKey, private val certificate: X509Certificate) : X509ExtendedKeyManager() {
    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = IDENTITY_ALIAS
    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = IDENTITY_ALIAS
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(IDENTITY_ALIAS)
    override fun getCertificateChain(alias: String?) = arrayOf(certificate)
    override fun getPrivateKey(alias: String?) = key
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
}
/**
 * TVs present self-signed certificates, so the chain cannot be validated here. Only used for sockets
 * to one private-network address; the adapter compares the certificate with the one pinned at pairing.
 */
private object UnverifiedLocalTrust : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw CertificateException("No certificate")
    }
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException()
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
