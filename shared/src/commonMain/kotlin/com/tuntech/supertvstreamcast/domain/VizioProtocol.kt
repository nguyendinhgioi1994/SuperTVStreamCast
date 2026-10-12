package com.tuntech.supertvstreamcast.domain

import kotlinx.serialization.json.*

/**
 * Vizio SmartCast REST API: HTTPS with a self-signed certificate on port 7345 (firmware 4.0+) or 9000.
 * Pairing shows a 4-digit PIN on the TV; the auth token it yields goes in the `AUTH` header.
 * Key codes and endpoints follow the community-maintained pyvizio client; Vizio publishes no documentation.
 */
object VizioProtocol {
    val PORTS = listOf(7345, 9000)
    const val CODE_LENGTH = 4

    fun base(host: String, port: Int) = "https://$host:$port"
    const val START_PAIRING = "/pairing/start"
    const val PAIR = "/pairing/pair"
    const val POWER_MODE = "/state/device/power_mode"
    const val DEVICE_INFO = "/state/device/deviceinfo"
    const val KEY_COMMAND = "/key_command/"

    /** (codeset, code) of a key press. Power is the toggle: the API is only reachable while the TV is on. */
    fun code(key: RemoteKey): Pair<Int, Int>? = when (key) {
        RemoteKey.FAST_FORWARD -> 2 to 0; RemoteKey.REWIND -> 2 to 1; RemoteKey.PAUSE -> 2 to 2; RemoteKey.PLAY -> 2 to 3
        RemoteKey.DOWN -> 3 to 0; RemoteKey.LEFT -> 3 to 1; RemoteKey.OK -> 3 to 2; RemoteKey.RIGHT -> 3 to 7; RemoteKey.UP -> 3 to 8
        RemoteKey.BACK -> 4 to 0; RemoteKey.INFO -> 4 to 6; RemoteKey.MENU -> 4 to 8; RemoteKey.HOME -> 4 to 15
        RemoteKey.VOLUME_DOWN -> 5 to 0; RemoteKey.VOLUME_UP -> 5 to 1; RemoteKey.MUTE -> 5 to 4
        RemoteKey.INPUT -> 7 to 1
        RemoteKey.CHANNEL_DOWN -> 8 to 0; RemoteKey.CHANNEL_UP -> 8 to 1
        RemoteKey.POWER -> 11 to 2
        else -> null
    }
    val capabilities = RemoteCapabilities(keys = RemoteKey.entries.filter { code(it) != null }.toSet())

    fun startPairing(deviceId: String, name: String) = buildJsonObject { put("DEVICE_ID", deviceId); put("DEVICE_NAME", name) }.toString()
    fun pair(deviceId: String, challenge: Challenge, code: String) = buildJsonObject {
        put("DEVICE_ID", deviceId); put("CHALLENGE_TYPE", challenge.type)
        put("RESPONSE_VALUE", code); put("PAIRING_REQ_TOKEN", challenge.token)
    }.toString()
    fun keyPress(key: RemoteKey): String {
        val (codeset, code) = code(key) ?: error("Unsupported key")
        return buildJsonObject {
            putJsonArray("KEYLIST") { addJsonObject { put("CODESET", codeset); put("CODE", code); put("ACTION", "KEYPRESS") } }
        }.toString()
    }
    /** Only digits: the TV compares the PIN it shows. */
    fun validCode(code: String) = code.length == CODE_LENGTH && code.all { it.isDigit() }

    data class Challenge(val token: Long, val type: Int)
    /** `STATUS.RESULT` in upper case, or null when the body is not a SmartCast reply. */
    fun result(body: String): String? = root(body)?.get("STATUS")?.objectOrNull()?.get("RESULT")?.primitiveOrNull()?.contentOrNull?.uppercase()
    fun succeeded(body: String) = result(body) == "SUCCESS"
    fun challenge(body: String): Challenge? {
        if (!succeeded(body)) return null
        val item = root(body)?.get("ITEM")?.objectOrNull() ?: return null
        val token = item["PAIRING_REQ_TOKEN"]?.primitiveOrNull()?.longOrNull ?: return null
        return Challenge(token, item["CHALLENGE_TYPE"]?.primitiveOrNull()?.intOrNull ?: 1)
    }
    fun authToken(body: String): String? = if (!succeeded(body)) null
        else root(body)?.get("ITEM")?.objectOrNull()?.get("AUTH_TOKEN")?.primitiveOrNull()?.contentOrNull?.ifBlank { null }
    /** A wrong PIN may be retried; any other refusal ends the pairing attempt. */
    fun wrongCode(body: String) = result(body).orEmpty().let { it.contains("CHALLENGE_INCORRECT") || it.contains("INVALID_PARAMETER") }
    /** Model name from `state/device/deviceinfo`, when the TV reports one. */
    fun model(body: String): String = runCatching {
        root(body)?.get("ITEMS")?.jsonArray?.firstOrNull()?.objectOrNull()?.get("VALUE")?.objectOrNull()
            ?.get("MODEL_NAME")?.primitiveOrNull()?.contentOrNull
    }.getOrNull().orEmpty()

    private fun root(body: String) = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
}

internal fun JsonElement.objectOrNull() = this as? JsonObject
internal fun JsonElement.primitiveOrNull() = this as? JsonPrimitive
