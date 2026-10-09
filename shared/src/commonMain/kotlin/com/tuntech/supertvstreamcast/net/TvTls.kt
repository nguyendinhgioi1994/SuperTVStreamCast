package com.tuntech.supertvstreamcast.net

/** A TLS byte stream to one TV, authenticated with this phone's own certificate. */
interface TlsChannel {
    /** DER certificate this phone presented. */
    val localCertificate: ByteArray
    /** DER leaf certificate the TV presented; callers decide whether to trust it. */
    val peerCertificate: ByteArray
    suspend fun write(bytes: ByteArray)
    /** Exactly [count] bytes; throws when the stream ends first. */
    suspend fun read(count: Int): ByteArray
    fun close()
}
typealias TlsOpener = suspend (host: String, port: Int) -> TlsChannel

/**
 * Opens a channel to a private-network [host] once the TLS handshake has completed. The phone's RSA
 * key is generated on first use and stays in the Android Keystore / iOS Keychain. The TV's
 * self-signed certificate is not validated here: the caller pins it.
 */
expect suspend fun openTvTls(host: String, port: Int): TlsChannel
/** Sends one UDP datagram to a broadcast [address]. Nothing is received back. */
expect suspend fun sendBroadcast(address: String, port: Int, payload: ByteArray)
