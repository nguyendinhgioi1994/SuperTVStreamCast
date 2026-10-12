package com.tuntech.supertvstreamcast.domain

/**
 * Panasonic Viera network remote: UPnP SOAP over plain HTTP on port 55000 (as used by the
 * panasonic_viera client). Models that list `X_GetEncryptSessionId` need an encrypted PIN session,
 * which is not implemented, so they are reported as unsupported instead of failing silently.
 */
object PanasonicProtocol {
    const val PORT = 55000
    const val SSDP_TARGET = "urn:panasonic-com:service:p00NetworkControl:1"
    private const val URN_REMOTE = "panasonic-com:service:p00NetworkControl:1"
    private const val URN_RENDERING = "schemas-upnp-org:service:RenderingControl:1"

    fun base(host: String) = "http://$host:$PORT"
    fun description(host: String) = "${base(host)}/nrc/ddd.xml"
    fun actions(host: String) = "${base(host)}/nrc/sdd_0.xml"
    fun control(host: String) = "${base(host)}/nrc/control_0"
    fun rendering(host: String) = "${base(host)}/dmr/control_0"

    fun keyName(key: RemoteKey): String = when (key) {
        RemoteKey.POWER -> "NRC_POWER"; RemoteKey.UP -> "NRC_UP"; RemoteKey.DOWN -> "NRC_DOWN"
        RemoteKey.LEFT -> "NRC_LEFT"; RemoteKey.RIGHT -> "NRC_RIGHT"; RemoteKey.OK -> "NRC_ENTER"
        RemoteKey.BACK -> "NRC_RETURN"; RemoteKey.HOME -> "NRC_HOME"; RemoteKey.MENU -> "NRC_MENU"
        RemoteKey.INFO -> "NRC_INFO"; RemoteKey.GUIDE -> "NRC_EPG"; RemoteKey.INPUT -> "NRC_CHG_INPUT"
        RemoteKey.VOLUME_UP -> "NRC_VOLUP"; RemoteKey.VOLUME_DOWN -> "NRC_VOLDOWN"; RemoteKey.MUTE -> "NRC_MUTE"
        RemoteKey.CHANNEL_UP -> "NRC_CH_UP"; RemoteKey.CHANNEL_DOWN -> "NRC_CH_DOWN"
        RemoteKey.PLAY -> "NRC_PLAY"; RemoteKey.PAUSE -> "NRC_PAUSE"; RemoteKey.STOP -> "NRC_STOP"
        RemoteKey.REWIND -> "NRC_REW"; RemoteKey.FAST_FORWARD -> "NRC_FF"
        else -> "NRC_D" + key.name.removePrefix("NUM_")
    } + "-ONOFF"
    val capabilities = RemoteCapabilities(RemoteKey.entries.toSet())

    data class Soap(val action: String, val soapAction: String, val body: String)
    fun sendKey(key: RemoteKey) = soap(URN_REMOTE, "X_SendKey", "<X_KeyEvent>${keyName(key)}</X_KeyEvent>")
    fun getVolume() = soap(URN_RENDERING, "GetVolume", "<InstanceID>0</InstanceID><Channel>Master</Channel>")
    private fun soap(urn: String, action: String, args: String) = Soap(
        action, "\"urn:$urn#$action\"",
        """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$action xmlns:u="urn:$urn">$args</u:$action></s:Body></s:Envelope>""",
    )

    /** `nrc/ddd.xml` of a Panasonic TV; anything else is not a Viera network remote. */
    fun identity(host: String, xml: String): TvDevice? {
        if (!xml.contains("Panasonic", ignoreCase = true) || !xml.contains(URN_REMOTE)) return null
        val name = element(xml, "friendlyName").ifBlank { "Panasonic Viera" }
        val model = listOf(element(xml, "modelName"), element(xml, "modelNumber")).filter { it.isNotBlank() }.joinToString(" ")
        return TvDevice(TvBrand.PANASONIC, host, name, model, platform = TvPlatform.PANASONIC_VIERA)
    }
    /** 2019+ models need an encrypted session for every command. */
    fun needsEncryption(actionsXml: String) = actionsXml.contains("X_GetEncryptSessionId")
    fun volume(xml: String): Int? = element(xml, "CurrentVolume").toIntOrNull()

    private fun element(xml: String, name: String) =
        Regex("<(?:\\w+:)?$name>([^<]*)</(?:\\w+:)?$name>").find(xml)?.groupValues?.get(1)?.let(::unescape)?.trim().orEmpty()
}
