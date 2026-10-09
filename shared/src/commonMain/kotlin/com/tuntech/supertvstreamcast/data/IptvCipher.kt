package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.platform.SecretStore
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString

/** AES-256 in counter mode; the same call encrypts and decrypts. [iv] is the 16-byte initial counter block. */
expect fun aesCtr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray
/** Bytes from the platform's cryptographic random generator. */
expect fun secureRandomBytes(size: Int): ByteArray

/**
 * Seals the IPTV library files: AES-256-CTR with a fresh IV, then HMAC-SHA256 over IV and
 * ciphertext. The key is created on first use and lives only in [secrets] (Android Keystore /
 * iOS Keychain), so the files are unreadable without this installation of the app.
 */
class IptvCipher(private val secrets: SecretStore) {
    private var cached: ByteArray? = null

    /** 32 bytes for encryption followed by 32 for authentication. */
    private fun key(): ByteArray {
        cached?.let { return it }
        val stored = secrets.get(KEY)?.let { runCatching { it.decodeHex().toByteArray() }.getOrNull() }?.takeIf { it.size == 64 }
        val key = stored ?: secureRandomBytes(64).also { secrets.put(KEY, it.toByteString().hex()) }
        cached = key
        return key
    }
    fun seal(plain: ByteArray): ByteArray {
        val key = key()
        val iv = secureRandomBytes(IV)
        val body = iv + aesCtr(key.copyOfRange(0, 32), iv, plain)
        return MAGIC + body + mac(key, body)
    }
    /** The plain bytes, or null when the data was not sealed with this key or was altered. */
    fun open(sealed: ByteArray): ByteArray? {
        if (!isSealed(sealed) || sealed.size < MAGIC.size + IV + MAC) return null
        val key = key()
        val body = sealed.copyOfRange(MAGIC.size, sealed.size - MAC)
        // ByteString equality on two digests; a mismatch only ever means "unreadable".
        if (mac(key, body).toByteString() != sealed.toByteString(sealed.size - MAC, MAC)) return null
        return aesCtr(key.copyOfRange(0, 32), body.copyOfRange(0, IV), body.copyOfRange(IV, body.size))
    }
    fun isSealed(bytes: ByteArray) = bytes.size >= MAGIC.size && MAGIC.indices.all { bytes[it] == MAGIC[it] }
    private fun mac(key: ByteArray, body: ByteArray) = body.toByteString().hmacSha256(key.toByteString(32, 32)).toByteArray()

    private companion object {
        const val KEY = "library.key"
        const val IV = 16
        const val MAC = 32
        /** "TVS1": marks a sealed file, so files written before sealing existed are still read once. */
        val MAGIC = byteArrayOf(0x54, 0x56, 0x53, 0x31)
    }
}
