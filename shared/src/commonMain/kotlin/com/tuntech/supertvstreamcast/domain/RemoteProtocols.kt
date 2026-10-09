package com.tuntech.supertvstreamcast.domain

import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64

/** Sony BRAVIA IRCC command names as reported by getRemoteControllerInfo. First available name wins. */
object SonyProtocol {
    fun names(key: RemoteKey): List<String> = when (key) {
        RemoteKey.POWER -> listOf("Power", "TvPower")
        RemoteKey.UP -> listOf("Up"); RemoteKey.DOWN -> listOf("Down")
        RemoteKey.LEFT -> listOf("Left"); RemoteKey.RIGHT -> listOf("Right")
        RemoteKey.OK -> listOf("Confirm"); RemoteKey.BACK -> listOf("Return")
        RemoteKey.HOME -> listOf("Home"); RemoteKey.MENU -> listOf("Options", "ActionMenu")
        RemoteKey.INFO -> listOf("Display"); RemoteKey.GUIDE -> listOf("GGuide", "EPG")
        RemoteKey.INPUT -> listOf("Input")
        RemoteKey.VOLUME_UP -> listOf("VolumeUp"); RemoteKey.VOLUME_DOWN -> listOf("VolumeDown")
        RemoteKey.MUTE -> listOf("Mute")
        RemoteKey.CHANNEL_UP -> listOf("ChannelUp"); RemoteKey.CHANNEL_DOWN -> listOf("ChannelDown")
        RemoteKey.PLAY -> listOf("Play"); RemoteKey.PAUSE -> listOf("Pause"); RemoteKey.STOP -> listOf("Stop")
        RemoteKey.REWIND -> listOf("Rewind"); RemoteKey.FAST_FORWARD -> listOf("Forward")
        else -> listOf("Num" + key.name.removePrefix("NUM_"))
    }
    fun code(codes: Map<String, String>, key: RemoteKey): String? = names(key).firstNotNullOfOrNull { codes[it] }
    fun capabilities(codes: Map<String, String>) = RemoteCapabilities(
        keys = RemoteKey.entries.filter { code(codes, it) != null }.toSet(), text = true, apps = true,
    )
    fun request(method: String, params: JsonElement = JsonArray(emptyList()), id: Int = 1, version: String = "1.0") =
        buildJsonObject { put("method", method); put("params", params); put("id", id); put("version", version) }.toString()
    fun ircc(code: String): String {
        check(Regex("[A-Za-z0-9+/=]+").matches(code))
        return """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1"><IRCCCode>$code</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>"""
    }
    /** Returns the `result` array; throws when the TV reports an API error. */
    fun result(body: String): JsonArray {
        val root = Json.parseToJsonElement(body).jsonObject
        check(root["error"] == null)
        return root.getValue("result").jsonArray
    }
    fun codes(body: String): Map<String, String> = result(body)[1].jsonArray.associate {
        it.jsonObject.getValue("name").jsonPrimitive.content to it.jsonObject.getValue("value").jsonPrimitive.content
    }
    fun apps(body: String): List<TvApp> = result(body)[0].jsonArray.mapNotNull {
        val app = it.jsonObject
        val uri = app["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        TvApp(uri, app["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { uri })
    }
    /** `hwAddr` of the first interface in a `getNetworkSettings` reply that has one. */
    fun mac(body: String): String = runCatching {
        result(body)[0].jsonArray.firstNotNullOfOrNull { it.jsonObject["hwAddr"]?.jsonPrimitive?.contentOrNull?.takeIf { mac -> macBytes(mac) != null } }
    }.getOrNull().orEmpty()
    /** Unauthenticated identity probe; only `productCategory == tv` counts as a TV. */
    fun identity(host: String, body: String): TvDevice? {
        val info = runCatching { result(body)[0].jsonObject }.getOrNull() ?: return null
        if (info["productCategory"]?.jsonPrimitive?.contentOrNull != "tv") return null
        val model = info["modelName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return TvDevice(TvBrand.SONY, host, info["productName"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: "Sony BRAVIA", model)
    }
}

/** Samsung Smart TV (Tizen) WebSocket remote. Pairing is accepted by the viewer on the TV screen. */
object SamsungProtocol {
    const val APP_NAME = "TV Space"
    fun keyCode(key: RemoteKey): String = when (key) {
        RemoteKey.POWER -> "KEY_POWER"; RemoteKey.UP -> "KEY_UP"; RemoteKey.DOWN -> "KEY_DOWN"
        RemoteKey.LEFT -> "KEY_LEFT"; RemoteKey.RIGHT -> "KEY_RIGHT"; RemoteKey.OK -> "KEY_ENTER"
        RemoteKey.BACK -> "KEY_RETURN"; RemoteKey.HOME -> "KEY_HOME"; RemoteKey.MENU -> "KEY_MENU"
        RemoteKey.INFO -> "KEY_INFO"; RemoteKey.GUIDE -> "KEY_GUIDE"; RemoteKey.INPUT -> "KEY_SOURCE"
        RemoteKey.VOLUME_UP -> "KEY_VOLUP"; RemoteKey.VOLUME_DOWN -> "KEY_VOLDOWN"; RemoteKey.MUTE -> "KEY_MUTE"
        RemoteKey.CHANNEL_UP -> "KEY_CHUP"; RemoteKey.CHANNEL_DOWN -> "KEY_CHDOWN"
        RemoteKey.PLAY -> "KEY_PLAY"; RemoteKey.PAUSE -> "KEY_PAUSE"; RemoteKey.STOP -> "KEY_STOP"
        RemoteKey.REWIND -> "KEY_REWIND"; RemoteKey.FAST_FORWARD -> "KEY_FF"
        else -> "KEY_" + key.name.removePrefix("NUM_")
    }
    val capabilities = RemoteCapabilities(RemoteKey.entries.toSet(), text = true, apps = true)
    data class Info(val device: TvDevice, val tokenAuth: Boolean)
    /** Parses http://host:8001/api/v2/; requires a Tizen TV device description. */
    fun info(host: String, body: String): Info? {
        val device = runCatching { Json.parseToJsonElement(body).jsonObject["device"]?.jsonObject }.getOrNull() ?: return null
        val type = device["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (!type.contains("TV", ignoreCase = true)) return null
        val name = device["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: "Samsung TV"
        val model = device["modelName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val token = device["TokenAuthSupport"]?.jsonPrimitive?.contentOrNull == "true"
        val mac = device["wifiMac"]?.jsonPrimitive?.contentOrNull?.takeIf { macBytes(it) != null }.orEmpty()
        return Info(TvDevice(TvBrand.SAMSUNG, host, name, model, mac), token)
    }
    fun url(host: String, secure: Boolean, token: String?): String {
        val name = Base64.encode(APP_NAME.encodeToByteArray())
        val base = if (secure) "wss://$host:8002" else "ws://$host:8001"
        return "$base/api/v2/channels/samsung.remote.control?name=$name" + (token?.let { "&token=$it" } ?: "")
    }
    fun key(key: RemoteKey) = remote("Click", keyCode(key), "SendRemoteKey", option = "false")
    /** Typed into the TV's on-screen keyboard while it is focused. */
    fun text(text: String) = remote(Base64.encode(text.encodeToByteArray()), "base64", "SendInputString")
    private fun remote(cmd: String, data: String, type: String, option: String? = null): String = buildJsonObject {
        put("method", "ms.remote.control")
        putJsonObject("params") {
            put("Cmd", cmd); put("DataOfCmd", data)
            option?.let { put("Option", it) }
            put("TypeOfRemote", type)
        }
    }.toString()
    fun installedApps() = emit("ed.installedApp.get", null)
    fun launch(app: TvApp, type: Int) = emit("ed.apps.launch", buildJsonObject {
        put("appId", app.id); put("action_type", if (type == 2) "DEEP_LINK" else "NATIVE_LAUNCH")
    })
    private fun emit(event: String, data: JsonObject?) = buildJsonObject {
        put("method", "ms.channel.emit")
        putJsonObject("params") { put("event", event); put("to", "host"); data?.let { put("data", it) } }
    }.toString()
    sealed interface Event {
        data class Connected(val token: String?) : Event
        data object Denied : Event
        data class Apps(val apps: List<Pair<TvApp, Int>>) : Event
        data object Other : Event
    }
    fun event(message: String): Event {
        val root = runCatching { Json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return Event.Other
        return when (root["event"]?.jsonPrimitive?.contentOrNull) {
            "ms.channel.connect" -> Event.Connected(root["data"]?.jsonObjectOrNull()?.get("token")?.jsonPrimitive?.contentOrNull)
            "ms.channel.unauthorized", "ms.channel.timeOut" -> Event.Denied
            "ed.installedApp.get" -> Event.Apps(root["data"]?.jsonObjectOrNull()?.get("data")?.jsonArrayOrNull().orEmpty().mapNotNull {
                val app = it.jsonObjectOrNull() ?: return@mapNotNull null
                val id = app["appId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                TvApp(id, app["name"]?.jsonPrimitive?.contentOrNull ?: id) to (app["app_type"]?.jsonPrimitive?.intOrNull ?: 2)
            })
            else -> Event.Other
        }
    }
}

/** LG webOS SSAP. Registration is approved by the viewer on the TV screen; navigation uses the pointer socket. */
object LgProtocol {
    private val permissions = listOf(
        "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK",
        "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD",
        "CONTROL_POWER", "READ_APP_STATUS", "READ_INSTALLED_APPS", "READ_INPUT_DEVICE_LIST", "READ_TV_CHANNEL_LIST",
        "READ_CURRENT_CHANNEL", "READ_RUNNING_APPS", "WRITE_NOTIFICATION_TOAST",
    )
    fun register(clientKey: String?) = buildJsonObject {
        put("type", "register"); put("id", "register_0")
        putJsonObject("payload") {
            put("forcePairing", false); put("pairingType", "PROMPT")
            clientKey?.let { put("client-key", it) }
            putJsonObject("manifest") {
                put("manifestVersion", 1); put("appVersion", "1.0")
                putJsonArray("permissions") { permissions.forEach { add(it) } }
            }
        }
    }.toString()
    fun request(id: String, uri: String, payload: JsonObject? = null) = buildJsonObject {
        put("type", "request"); put("id", id); put("uri", uri); payload?.let { put("payload", it) }
    }.toString()
    /** SSAP request for a key, or null when the key is sent as a pointer-socket button. */
    fun ssap(key: RemoteKey): String? = when (key) {
        RemoteKey.POWER -> "ssap://system/turnOff"
        RemoteKey.VOLUME_UP -> "ssap://audio/volumeUp"; RemoteKey.VOLUME_DOWN -> "ssap://audio/volumeDown"
        RemoteKey.CHANNEL_UP -> "ssap://tv/channelUp"; RemoteKey.CHANNEL_DOWN -> "ssap://tv/channelDown"
        RemoteKey.PLAY -> "ssap://media.controls/play"; RemoteKey.PAUSE -> "ssap://media.controls/pause"
        RemoteKey.STOP -> "ssap://media.controls/stop"; RemoteKey.REWIND -> "ssap://media.controls/rewind"
        RemoteKey.FAST_FORWARD -> "ssap://media.controls/fastForward"
        else -> null
    }
    fun button(key: RemoteKey): String? = when (key) {
        RemoteKey.UP -> "UP"; RemoteKey.DOWN -> "DOWN"; RemoteKey.LEFT -> "LEFT"; RemoteKey.RIGHT -> "RIGHT"
        RemoteKey.OK -> "ENTER"; RemoteKey.BACK -> "BACK"; RemoteKey.HOME -> "HOME"; RemoteKey.MENU -> "MENU"
        RemoteKey.INFO -> "INFO"; RemoteKey.GUIDE -> "GUIDE"; RemoteKey.MUTE -> "MUTE"
        in RemoteKey.digits -> key.name.removePrefix("NUM_")
        else -> null
    }
    /** Pointer buttons need the pointer socket; without it only SSAP keys are offered. */
    fun capabilities(pointer: Boolean) = RemoteCapabilities(
        keys = RemoteKey.entries.filter { ssap(it) != null || (pointer && button(it) != null) }.toSet(),
        text = true, apps = true, pointer = pointer,
    )
    fun buttonFrame(name: String) = "type:button\nname:$name\n\n"
    fun moveFrame(dx: Int, dy: Int) = "type:move\ndx:$dx\ndy:$dy\ndown:0\n\n"
    const val CLICK_FRAME = "type:click\n\n"
    sealed interface Message {
        data class Registered(val clientKey: String) : Message
        data class Response(val id: String, val ok: Boolean, val payload: JsonObject) : Message
        data class Failure(val id: String) : Message
        data object Other : Message
    }
    fun message(text: String): Message {
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return Message.Other
        val id = root["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val payload = root["payload"]?.jsonObjectOrNull() ?: JsonObject(emptyMap())
        return when (root["type"]?.jsonPrimitive?.contentOrNull) {
            "registered" -> payload["client-key"]?.jsonPrimitive?.contentOrNull?.let { Message.Registered(it) } ?: Message.Failure(id)
            "response" -> Message.Response(id, payload["returnValue"]?.jsonPrimitive?.booleanOrNull != false, payload)
            "error" -> Message.Failure(id)
            else -> Message.Other
        }
    }
    fun apps(payload: JsonObject): List<TvApp> = payload["launchPoints"]?.jsonArrayOrNull().orEmpty().mapNotNull {
        val app = it.jsonObjectOrNull() ?: return@mapNotNull null
        val id = app["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        TvApp(id, app["title"]?.jsonPrimitive?.contentOrNull ?: id)
    }
}

private fun JsonElement.jsonObjectOrNull() = this as? JsonObject
private fun JsonElement.jsonArrayOrNull() = this as? JsonArray
