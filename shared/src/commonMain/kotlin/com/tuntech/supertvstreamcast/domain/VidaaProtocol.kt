package com.tuntech.supertvstreamcast.domain

import kotlinx.serialization.json.*
import kotlin.random.Random

/** MQTT 3.1.1 packets this app needs as a client: QoS 0 publish, subscribe, keep-alive. */
object Mqtt {
    const val CONNACK = 2; const val PUBLISH = 3; const val PUBACK = 4; const val SUBACK = 9; const val PINGRESP = 13
    const val MAX_PACKET_BYTES = 1 shl 20

    class Packet(val type: Int, val flags: Int, val body: ByteArray)
    data class Publish(val topic: String, val payload: String)

    fun connect(clientId: String, username: String, password: String, keepAliveSeconds: Int): ByteArray = packet(0x10, buildBytes {
        string("MQTT"); byte(4)
        // Username, password and a clean session.
        byte(0xC2); short(keepAliveSeconds)
        string(clientId); string(username); string(password)
    })
    fun subscribe(packetId: Int, topics: List<String>): ByteArray = packet(0x82, buildBytes {
        short(packetId)
        topics.forEach { string(it); byte(0) }
    })
    fun publish(topic: String, payload: String): ByteArray = packet(0x30, buildBytes { string(topic); raw(payload.encodeToByteArray()) })
    fun puback(packetId: Int): ByteArray = packet(0x40, buildBytes { short(packetId) })
    val PINGREQ = byteArrayOf(0xC0.toByte(), 0)
    val DISCONNECT = byteArrayOf(0xE0.toByte(), 0)

    /** Return code of a CONNACK; 0 means accepted. */
    fun connackCode(packet: Packet): Int { check(packet.type == CONNACK && packet.body.size >= 2); return packet.body[1].toInt() and 0xFF }
    /** True when every topic of a SUBACK was granted. */
    fun subackGranted(packet: Packet): Boolean = packet.type == SUBACK && packet.body.size > 2 && packet.body.drop(2).none { (it.toInt() and 0xFF) == 0x80 }
    /** Topic, payload and, for QoS 1/2, the packet id to acknowledge. */
    fun publish(packet: Packet): Pair<Publish, Int?> {
        check(packet.type == PUBLISH)
        val body = packet.body
        val length = ((body[0].toInt() and 0xFF) shl 8) or (body[1].toInt() and 0xFF)
        val topic = body.copyOfRange(2, 2 + length).decodeToString()
        var offset = 2 + length
        val qos = (packet.flags shr 1) and 3
        val id = if (qos > 0) (((body[offset].toInt() and 0xFF) shl 8) or (body[offset + 1].toInt() and 0xFF)).also { offset += 2 } else null
        return Publish(topic, body.copyOfRange(offset, body.size).decodeToString()) to id
    }
    /** Reads one packet; [read] returns exactly the requested number of bytes. */
    suspend fun read(read: suspend (Int) -> ByteArray): Packet {
        val header = read(1)[0].toInt() and 0xFF
        var length = 0
        var shift = 0
        while (true) {
            val digit = read(1)[0].toInt() and 0xFF
            length = length or ((digit and 0x7F) shl shift)
            if (digit and 0x80 == 0) break
            shift += 7
            check(shift < 28)
        }
        check(length <= MAX_PACKET_BYTES)
        return Packet(header shr 4, header and 0x0F, if (length == 0) ByteArray(0) else read(length))
    }
    fun remainingLength(length: Int): ByteArray {
        val out = ArrayList<Byte>()
        var value = length
        do {
            var digit = value % 128
            value /= 128
            if (value > 0) digit = digit or 0x80
            out += digit.toByte()
        } while (value > 0)
        return out.toByteArray()
    }
    private fun packet(header: Int, body: ByteArray) = byteArrayOf(header.toByte()) + remainingLength(body.size) + body

    private class Bytes { val out = ArrayList<Byte>()
        fun byte(value: Int) { out += value.toByte() }
        fun short(value: Int) { byte(value shr 8 and 0xFF); byte(value and 0xFF) }
        fun raw(bytes: ByteArray) { bytes.forEach { out += it } }
        fun string(value: String) { val bytes = value.encodeToByteArray(); short(bytes.size); raw(bytes) }
    }
    private fun buildBytes(block: Bytes.() -> Unit) = Bytes().apply(block).out.toByteArray()
}

/**
 * Hisense VIDAA remote: an MQTT broker on the TV (TLS, port 36669). Not documented by Hisense; topics follow
 * the community-documented RemoteNOW protocol. The TV may show a 4-digit code that authorizes this phone.
 * Firmware that requires Hisense's own client certificate or per-session credentials refuses the connection.
 */
object VidaaProtocol {
    const val PORT = 36669
    const val USERNAME = "hisenseservice"
    const val PASSWORD = "multimqttservice"
    const val CODE_LENGTH = 4

    /** A locally administered, MAC-shaped identifier; the TV lists authorized phones by it. */
    fun newClientId(random: Random = Random): String {
        val bytes = random.nextBytes(6)
        bytes[0] = ((bytes[0].toInt() or 0x02) and 0xFE).toByte()
        return bytes.joinToString(":") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
    }
    private fun device(clientId: String) = "$clientId\$normal"
    fun subscriptions(clientId: String) = listOf("/remoteapp/mobile/${device(clientId)}/#", "/remoteapp/mobile/broadcast/#")
    fun action(clientId: String, service: String, action: String) = "/remoteapp/tv/$service/${device(clientId)}/actions/$action"
    fun key(clientId: String) = action(clientId, "remote_service", "sendkey")
    fun authCode(code: String): String? {
        val digits = code.trim().takeIf { it.length == CODE_LENGTH && it.all(Char::isDigit) } ?: return null
        return buildJsonObject { put("authNum", digits.toInt()) }.toString()
    }

    fun keyName(key: RemoteKey): String? = when (key) {
        RemoteKey.POWER -> "KEY_POWER"
        RemoteKey.UP -> "KEY_UP"; RemoteKey.DOWN -> "KEY_DOWN"; RemoteKey.LEFT -> "KEY_LEFT"; RemoteKey.RIGHT -> "KEY_RIGHT"
        RemoteKey.OK -> "KEY_OK"; RemoteKey.BACK -> "KEY_RETURNS"; RemoteKey.HOME -> "KEY_HOME"; RemoteKey.MENU -> "KEY_MENU"
        RemoteKey.VOLUME_UP -> "KEY_VOLUMEUP"; RemoteKey.VOLUME_DOWN -> "KEY_VOLUMEDOWN"; RemoteKey.MUTE -> "KEY_MUTE"
        RemoteKey.CHANNEL_UP -> "KEY_CHANNELUP"; RemoteKey.CHANNEL_DOWN -> "KEY_CHANNELDOWN"
        RemoteKey.PLAY -> "KEY_PLAY"; RemoteKey.PAUSE -> "KEY_PAUSE"; RemoteKey.STOP -> "KEY_STOP"
        RemoteKey.REWIND -> "KEY_BACK"; RemoteKey.FAST_FORWARD -> "KEY_FORWARDS"
        in RemoteKey.digits -> "KEY_" + key.name.removePrefix("NUM_")
        else -> null
    }
    val capabilities = RemoteCapabilities(RemoteKey.entries.filter { keyName(it) != null }.toSet(), apps = true)

    sealed interface Event {
        /** The TV shows a code that has to be typed into the phone. */
        data object CodeRequested : Event
        data class CodeResult(val accepted: Boolean) : Event
        /** The TV closed its code prompt. */
        data object CodeClosed : Event
        data object Sources : Event
        data class Apps(val apps: List<Pair<TvApp, JsonObject>>) : Event
        data class DeviceInfo(val mac: String) : Event
        data object Other : Event
    }
    fun event(message: Mqtt.Publish): Event {
        val topic = message.topic
        return when {
            topic.endsWith("/ui_service/data/authentication") -> Event.CodeRequested
            topic.endsWith("/ui_service/data/authenticationcode") ->
                Event.CodeResult(json(message.payload)?.jsonObjectOrNull()?.get("result")?.jsonPrimitive?.intOrNull == 1)
            topic.endsWith("/ui_service/data/authenticationcodeclose") -> Event.CodeClosed
            topic.endsWith("/ui_service/data/sourcelist") -> Event.Sources
            topic.endsWith("/ui_service/data/applist") -> Event.Apps((json(message.payload) as? JsonArray).orEmpty().mapNotNull { element ->
                val app = element.jsonObjectOrNull() ?: return@mapNotNull null
                val id = app["appId"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: return@mapNotNull null
                TvApp(id, app["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: id) to app
            })
            topic.endsWith("/platform_service/data/getdeviceinfo") -> Event.DeviceInfo(
                json(message.payload)?.jsonObjectOrNull()?.entries
                    ?.firstNotNullOfOrNull { (name, value) -> if (name.contains("mac", ignoreCase = true)) (value as? JsonPrimitive)?.contentOrNull?.takeIf { macBytes(it) != null } else null }
                    .orEmpty(),
            )
            else -> Event.Other
        }
    }
    private fun json(text: String) = runCatching { Json.parseToJsonElement(text) }.getOrNull()
    private fun JsonElement.jsonObjectOrNull() = this as? JsonObject
}
