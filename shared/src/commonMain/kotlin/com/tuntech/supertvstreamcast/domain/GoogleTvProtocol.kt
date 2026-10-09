package com.tuntech.supertvstreamcast.domain

/**
 * Android TV Remote Service v2 (Google TV / Android TV). Pairing (port 6467) and remote control
 * (port 6466) both run over TLS with this phone's client certificate; messages are protobuf with a
 * varint length prefix. The TV shows a 6-digit hexadecimal code that proves both certificates.
 */
object GoogleTvProtocol {
    const val PAIRING_PORT = 6467
    const val REMOTE_PORT = 6466
    const val MAX_MESSAGE_BYTES = 64 * 1024
    /** Ping, key, power, volume and app link: the features this app answers for. */
    private const val SUPPORTED_FEATURES = 1 or 2 or 32 or 64 or 512
    private const val STATUS_OK = 200L
    private const val HEXADECIMAL = 3L
    private const val ROLE_INPUT = 1L
    private const val CODE_LENGTH = 6

    private fun pairing(body: ProtoWriter.() -> Unit) = proto { varint(1, 2); varint(2, STATUS_OK); body() }
    private fun ProtoWriter.encoding(field: Int) = message(field) { varint(1, HEXADECIMAL); varint(2, CODE_LENGTH.toLong()) }
    fun pairingRequest(clientName: String) = pairing { message(10) { string(1, "atvremote"); string(2, clientName) } }
    fun pairingOption() = pairing { message(20) { encoding(1); varint(3, ROLE_INPUT) } }
    fun pairingConfiguration() = pairing { message(30) { encoding(1); varint(2, ROLE_INPUT) } }
    fun pairingSecret(secret: ByteArray) = pairing { message(40) { bytes(1, secret) } }

    enum class PairingStep { REQUEST_ACK, OPTION, CONFIGURATION_ACK, SECRET_ACK, OTHER }
    /** @throws IllegalStateException when the TV reports a pairing error (bad configuration, bad secret). */
    fun pairingStep(message: ByteArray): PairingStep {
        val fields = parseProto(message)
        check(fields.varints[2] == STATUS_OK)
        return when {
            11 in fields.bytes -> PairingStep.REQUEST_ACK
            20 in fields.bytes -> PairingStep.OPTION
            31 in fields.bytes -> PairingStep.CONFIGURATION_ACK
            41 in fields.bytes -> PairingStep.SECRET_ACK
            else -> PairingStep.OTHER
        }
    }
    /**
     * SHA-256 over both RSA keys and the code's last two bytes. Null when [code] is not six hex digits
     * or its first byte does not match the hash, i.e. it was mistyped or belongs to another session.
     */
    fun secret(code: String, client: RsaPublicKey, server: RsaPublicKey): ByteArray? {
        val bytes = hexBytes(code.trim())?.takeIf { it.size == CODE_LENGTH / 2 } ?: return null
        val hash = sha256(client.modulus + client.exponent + server.modulus + server.exponent + bytes.copyOfRange(1, bytes.size))
        return hash.takeIf { it[0] == bytes[0] }
    }

    sealed interface Event {
        data class Configure(val features: Int, val vendor: String, val model: String) : Event
        data object Active : Event
        data class Ping(val value: Long) : Event
        data object Started : Event
        data object Failure : Event
        data object Other : Event
    }
    fun event(message: ByteArray): Event {
        val fields = parseProto(message)
        fields.message(1)?.let { configure ->
            val info = configure.message(2)
            return Event.Configure((configure.varints[1] ?: 0L).toInt(), info?.string(2).orEmpty(), info?.string(1).orEmpty())
        }
        return when {
            2 in fields.bytes -> Event.Active
            3 in fields.bytes -> Event.Failure
            8 in fields.bytes -> Event.Ping(fields.message(8)?.varints?.get(1) ?: 0L)
            40 in fields.bytes -> Event.Started
            else -> Event.Other
        }
    }
    /** Features both sides support; echoed in [configure] and [active]. */
    fun features(offered: Int) = offered and SUPPORTED_FEATURES
    fun configure(features: Int) = proto {
        message(1) {
            varint(1, features.toLong())
            message(2) { varint(3, 1); string(4, "1"); string(5, "atvremote"); string(6, "1.0.0") }
        }
    }
    fun active(features: Int) = proto { message(2) { varint(1, features.toLong()) } }
    fun pong(value: Long) = proto { message(9) { varint(1, value) } }
    /** A short press of an Android `KeyEvent` code. */
    fun key(key: RemoteKey) = proto { message(10) { varint(1, keyCode(key).toLong()); varint(2, 3) } }
    fun keyCode(key: RemoteKey): Int = when (key) {
        RemoteKey.POWER -> 26; RemoteKey.UP -> 19; RemoteKey.DOWN -> 20; RemoteKey.LEFT -> 21; RemoteKey.RIGHT -> 22
        RemoteKey.OK -> 23; RemoteKey.BACK -> 4; RemoteKey.HOME -> 3; RemoteKey.MENU -> 82
        RemoteKey.INFO -> 165; RemoteKey.GUIDE -> 172; RemoteKey.INPUT -> 178
        RemoteKey.VOLUME_UP -> 24; RemoteKey.VOLUME_DOWN -> 25; RemoteKey.MUTE -> 164
        RemoteKey.CHANNEL_UP -> 166; RemoteKey.CHANNEL_DOWN -> 167
        RemoteKey.PLAY -> 126; RemoteKey.PAUSE -> 127; RemoteKey.STOP -> 86
        RemoteKey.REWIND -> 89; RemoteKey.FAST_FORWARD -> 90
        else -> 7 + key.name.removePrefix("NUM_").toInt()
    }
    /** Key injection only: text entry and app listing need protocol parts this app does not implement. */
    val capabilities = RemoteCapabilities(RemoteKey.entries.toSet())
}

/** `aa:bb:cc:dd:ee:ff` or `aa-bb-…`; null for anything else, including the all-zero address. */
fun macBytes(value: String): ByteArray? {
    val parts = value.trim().split(':', '-')
    if (parts.size != 6 || parts.any { it.length != 2 }) return null
    return hexBytes(parts.joinToString(""))?.takeIf { bytes -> bytes.any { it != 0.toByte() } }
}
/** Wake-on-LAN magic packet: six 0xFF bytes followed by the MAC sixteen times. */
fun magicPacket(mac: String): ByteArray? {
    val bytes = macBytes(mac) ?: return null
    return ByteArray(6) { 0xFF.toByte() } + ByteArray(96) { bytes[it % 6] }
}
/** Directed broadcast address of the /24 that [host] is on. */
fun subnetBroadcast(host: String): String? = if (isLocalIpv4(host)) host.substringBeforeLast('.') + ".255" else null
