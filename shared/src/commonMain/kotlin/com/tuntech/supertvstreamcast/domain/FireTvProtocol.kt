package com.tuntech.supertvstreamcast.domain

import kotlinx.serialization.json.*

/**
 * Amazon Fire TV remote service ("Lightning"), as documented by the home-automation community; Amazon
 * publishes no documentation. A DIAL request on port 8009 starts the service, which then serves HTTPS
 * (self-signed) on port 8080. Pairing shows a 4-digit PIN on the TV and returns a client token.
 * [API_KEY] is the fixed value every client of this service sends; it is not a per-user secret.
 */
object FireTvProtocol {
    const val API_KEY = "0987654321"
    const val CODE_LENGTH = 4
    const val DIAL_APP = "FireTVRemote"

    fun dial(host: String) = "http://$host:8009/apps/$DIAL_APP"
    fun base(host: String) = "https://$host:8080"
    fun displayPin(host: String) = "${base(host)}/v1/FireTV/pin/display"
    fun verifyPin(host: String) = "${base(host)}/v1/FireTV/pin/verify"
    fun apps(host: String) = "${base(host)}/v1/FireTV/appsV2"
    fun keyboard(host: String) = "${base(host)}/v1/FireTV/keyboard"
    fun launch(host: String, app: TvApp) = "${base(host)}/v1/FireTV/app/${percentEncode(app.id)}"

    /** `/v1/FireTV?action=` names. Fire TV has no volume or channel control over this service. */
    fun action(key: RemoteKey): String? = when (key) {
        RemoteKey.UP -> "dpad_up"; RemoteKey.DOWN -> "dpad_down"; RemoteKey.LEFT -> "dpad_left"; RemoteKey.RIGHT -> "dpad_right"
        RemoteKey.OK -> "select"; RemoteKey.HOME -> "home"; RemoteKey.BACK -> "back"; RemoteKey.MENU -> "menu"
        RemoteKey.POWER -> "sleep"
        else -> null
    }
    /** `/v1/media?action=` request: play is a play/pause toggle; scan needs a direction. */
    fun media(key: RemoteKey): Pair<String, String?>? = when (key) {
        RemoteKey.PLAY, RemoteKey.PAUSE -> "play" to null
        RemoteKey.REWIND -> "scan" to scan("back")
        RemoteKey.FAST_FORWARD -> "scan" to scan("forward")
        else -> null
    }
    fun keyUrl(host: String, key: RemoteKey): Pair<String, String?>? =
        action(key)?.let { "${base(host)}/v1/FireTV?action=$it" to null }
            ?: media(key)?.let { (action, body) -> "${base(host)}/v1/media?action=$action" to body }
    private fun scan(direction: String) = buildJsonObject {
        put("direction", direction)
        putJsonObject("keyAction") { put("keyActionType", "keyDown") }
    }.toString()
    val capabilities = RemoteCapabilities(
        keys = RemoteKey.entries.filter { action(it) != null || media(it) != null }.toSet(), text = true, apps = true,
    )

    fun displayPinBody(name: String) = buildJsonObject { put("friendlyName", name) }.toString()
    fun verifyPinBody(code: String) = buildJsonObject { put("pin", code) }.toString()
    fun textBody(text: String) = buildJsonObject { put("text", text) }.toString()
    fun validCode(code: String) = code.length == CODE_LENGTH && code.all { it.isDigit() }

    /** `{"description": "<token>"}` from pin/verify; "OK" or an error sentence is not a token. */
    fun token(body: String): String? = description(body)?.takeIf { TOKEN.matches(it) && it != "OK" }
    fun description(body: String): String? = runCatching {
        (Json.parseToJsonElement(body) as? JsonObject)?.get("description")?.primitiveOrNull()?.contentOrNull
    }.getOrNull()
    /** DIAL application description of a Fire TV (answered without starting anything). */
    fun isFireTv(dialXml: String) = dialXml.contains("<name>$DIAL_APP</name>")

    /**
     * Installed apps from `appsV2`. The reply is not documented, so any array of objects with a package
     * and a name is accepted, top-level or under one key.
     */
    fun installedApps(body: String): List<TvApp> {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val array = root as? JsonArray ?: (root as? JsonObject)?.values?.firstOrNull { it is JsonArray } as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val app = element.objectOrNull() ?: return@mapNotNull null
            fun field(vararg names: String) = names.firstNotNullOfOrNull { app[it]?.primitiveOrNull()?.contentOrNull?.ifBlank { null } }
            val id = field("packageName", "appId", "id", "package") ?: return@mapNotNull null
            TvApp(id, field("name", "appName", "displayName", "title") ?: id)
        }.distinctBy { it.id }
    }
    /** Shortcuts offered when the TV does not list its apps; a missing app fails and is reported. */
    val shortcuts = listOf(
        TvApp("com.amazon.firetv.youtube", "YouTube"), TvApp("com.netflix.ninja", "Netflix"),
        TvApp("com.amazon.cloud9", "Prime Video"), TvApp("com.disney.disneyplus", "Disney+"),
        TvApp("com.hulu.plus", "Hulu"), TvApp("com.apple.atve.amazon.appletv", "Apple TV"),
        TvApp("com.amazon.bueller.music", "Amazon Music"), TvApp("com.spotify.tv.android", "Spotify"),
    )
    private val TOKEN = Regex("[A-Za-z0-9._~+/=-]{4,256}")
}
