@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.tuntech.supertvstreamcast.net

import com.tuntech.supertvstreamcast.domain.Der
import com.tuntech.supertvstreamcast.domain.isLocalIpv4
import com.tuntech.supertvstreamcast.platform.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import platform.CoreFoundation.*
import platform.Network.*
import platform.Security.*
import platform.darwin.*
import platform.posix.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual suspend fun openTvTls(host: String, port: Int): TlsChannel {
    require(isLocalIpv4(host))
    val identity = withContext(Dispatchers.Default) { identity() }
    val queue = dispatch_queue_create("tvspace.tls", null)
    var peer: ByteArray? = null
    val parameters = nw_parameters_create_secure_tcp({ options ->
        val security = nw_tls_copy_sec_protocol_options(options)
        sec_protocol_options_set_local_identity(security, sec_identity_create(identity.ref))
        // TVs present self-signed certificates; the adapter compares the leaf with the one pinned at pairing.
        sec_protocol_options_set_verify_block(security, { _, trust, complete ->
            val reference = sec_trust_copy_ref(trust)
            peer = reference?.let(::leafCertificate)
            if (reference != null) CFRelease(reference)
            complete?.invoke(peer != null)
        }, queue)
    }, { _ -> })
    val connection = nw_connection_create(nw_endpoint_create_host(host, port.toString()), parameters)
    nw_connection_set_queue(connection, queue)
    try {
        withTimeout(10_000) {
            suspendCancellableCoroutine { continuation ->
                var settled = false
                nw_connection_set_state_changed_handler(connection) { state, _ ->
                    if (!settled) when (state) {
                        nw_connection_state_ready -> { settled = true; continuation.resume(Unit) }
                        nw_connection_state_failed, nw_connection_state_waiting, nw_connection_state_cancelled -> {
                            settled = true; continuation.resumeWithException(IllegalStateException("TLS connection failed"))
                        }
                        else -> Unit
                    }
                }
                nw_connection_start(connection)
            }
        }
        return ConnectionChannel(connection, identity.certificate, checkNotNull(peer))
    } catch (e: Throwable) { nw_connection_cancel(connection); throw e }
}

private class ConnectionChannel(
    private val connection: nw_connection_t, override val localCertificate: ByteArray, override val peerCertificate: ByteArray,
) : TlsChannel {
    private var pending = ByteArray(0)
    override suspend fun write(bytes: ByteArray) = suspendCancellableCoroutine { continuation ->
        val data = bytes.usePinned { dispatch_data_create(it.addressOf(0), bytes.size.convert(), null, null) }
        // The default-context global cannot be read from Kotlin/Native (it aborts in the object bridge), so each write gets its own context.
        nw_connection_send(connection, data, nw_content_context_create("tvspace"), true) { error ->
            if (error == null) continuation.resume(Unit) else continuation.resumeWithException(IllegalStateException("TLS write failed"))
        }
    }
    override suspend fun read(count: Int): ByteArray {
        while (pending.size < count) pending += receive()
        return pending.copyOfRange(0, count).also { pending = pending.copyOfRange(count, pending.size) }
    }
    private suspend fun receive(): ByteArray = suspendCancellableCoroutine { continuation ->
        nw_connection_receive(connection, 1u, 65_536u) { content, _, _, _ ->
            var bytes = ByteArray(0)
            if (content != null) dispatch_data_apply(content) { _, _, buffer, size ->
                if (buffer != null) bytes += buffer.readBytes(size.toInt())
                true
            }
            if (bytes.isNotEmpty()) continuation.resume(bytes) else continuation.resumeWithException(IllegalStateException("TLS stream closed"))
        }
    }
    override fun close() = nw_connection_cancel(connection)
}

private fun leafCertificate(trust: SecTrustRef): ByteArray? {
    val certificate = SecTrustGetCertificateAtIndex(trust, 0) ?: return null
    return cfScope { own(SecCertificateCopyData(certificate))?.bytes() }
}

private class Identity(val ref: SecIdentityRef, val certificate: ByteArray)
private var cachedIdentity: Identity? = null
private const val IDENTITY_LABEL = "TV Space remote identity"
private val IDENTITY_TAG = "com.tuntech.supertvstreamcast.remote".encodeToByteArray()

/** This phone's remote-control identity: an RSA key generated in the Keychain plus a self-signed certificate for it. */
private fun identity(): Identity {
    cachedIdentity?.let { return it }
    val reference = findIdentity() ?: run { createIdentity(); checkNotNull(findIdentity()) }
    val certificate = memScoped {
        val holder = alloc<SecCertificateRefVar>()
        check(SecIdentityCopyCertificate(reference, holder.ptr) == errSecSuccess)
        cfScope { own(SecCertificateCopyData(own(holder.value)))!!.bytes() }
    }
    return Identity(reference, certificate).also { cachedIdentity = it }
}
private fun findIdentity(): SecIdentityRef? = cfScope {
    memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(dictionary(listOf(
            kSecClass to kSecClassIdentity, kSecAttrLabel to string(IDENTITY_LABEL), kSecReturnRef to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne,
        )), result.ptr)
        val identity: SecIdentityRef? = if (status == errSecSuccess) result.value?.reinterpret() else null
        identity
    }
}
private fun createIdentity() = cfScope {
    // Leftovers of an interrupted earlier attempt would make the identity lookup ambiguous.
    SecItemDelete(dictionary(listOf(kSecClass to kSecClassKey, kSecAttrApplicationTag to data(IDENTITY_TAG))))
    SecItemDelete(dictionary(listOf(kSecClass to kSecClassCertificate, kSecAttrLabel to string(IDENTITY_LABEL))))
    val keyAttributes = dictionary(listOf(
        kSecAttrIsPermanent to kCFBooleanTrue, kSecAttrApplicationTag to data(IDENTITY_TAG), kSecAttrLabel to string(IDENTITY_LABEL),
        kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
    ))
    val key = checkNotNull(own(SecKeyCreateRandomKey(dictionary(listOf(
        kSecAttrKeyType to kSecAttrKeyTypeRSA, kSecAttrKeySizeInBits to number(2048), kSecPrivateKeyAttrs to keyAttributes,
    )), null)))
    val publicKey = checkNotNull(own(SecKeyCopyPublicKey(key)))
    val pkcs1 = checkNotNull(own(SecKeyCopyExternalRepresentation(publicKey, null))).bytes()
    val serial = ByteArray(8)
    serial.usePinned { check(SecRandomCopyBytes(kSecRandomDefault, serial.size.convert(), it.addressOf(0)) == errSecSuccess) }
    serial[0] = ((serial[0].toInt() and 0x7F) or 0x01).toByte()
    val der = Der.selfSignedCertificate(pkcs1, "atvremote", serial) { tbs ->
        checkNotNull(own(SecKeyCreateSignature(key, kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256, data(tbs), null))).bytes()
    }
    val certificate = checkNotNull(own(SecCertificateCreateWithData(null, data(der))))
    check(SecItemAdd(dictionary(listOf(kSecClass to kSecClassCertificate, kSecValueRef to certificate, kSecAttrLabel to string(IDENTITY_LABEL))), null) == errSecSuccess)
}

actual suspend fun sendBroadcast(address: String, port: Int, payload: ByteArray) = withContext(Dispatchers.Default) {
    memScoped {
        val descriptor = socket(AF_INET, SOCK_DGRAM, 0)
        check(descriptor >= 0)
        try {
            val enabled = alloc<IntVar>()
            enabled.value = 1
            check(setsockopt(descriptor, SOL_SOCKET, SO_BROADCAST, enabled.ptr, sizeOf<IntVar>().convert()) == 0)
            val target = alloc<sockaddr_in>()
            memset(target.ptr, 0, sizeOf<sockaddr_in>().convert())
            target.sin_len = sizeOf<sockaddr_in>().convert()
            target.sin_family = AF_INET.convert()
            target.sin_port = (((port shr 8) and 0xFF) or ((port and 0xFF) shl 8)).toUShort()
            check(inet_pton(AF_INET, address, target.sin_addr.ptr) == 1)
            val sent = payload.usePinned { sendto(descriptor, it.addressOf(0), payload.size.convert(), 0, target.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) }
            check(sent.toInt() == payload.size)
        } finally { close(descriptor) }
    }
}
