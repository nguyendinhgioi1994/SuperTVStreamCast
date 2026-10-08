package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*
import kotlinx.io.readByteArray

class TvRepository(private val client: HttpClient = HttpClient {
        followRedirects = false
        install(HttpTimeout) { requestTimeoutMillis = 8_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 5_000 }
    }) {
    private val json = Json { ignoreUnknownKeys = true }
    private var ip = ""
    private var psk = ""
    private var codes = emptyMap<String, String>()

    suspend fun connect(address: String, key: String) {
        require(isLocalIpv4(address))
        disconnect()
        val response = client.post("http://$address/sony/system") {
            header("X-Auth-PSK", key)
            contentType(ContentType.Application.Json)
            setBody("""{"method":"getRemoteControllerInfo","params":[],"id":1,"version":"1.0"}""")
        }
        check(response.status.isSuccess())
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        check(root["error"] == null)
        val commands = root.getValue("result").jsonArray[1].jsonArray
        val discovered = commands.associate { it.jsonObject.getValue("name").jsonPrimitive.content to it.jsonObject.getValue("value").jsonPrimitive.content }
        check(discovered.containsKey("Confirm"))
        ip = address; psk = key; codes = discovered
    }
    suspend fun send(key: RemoteKey) {
        val code = codes[key.sonyName] ?: error("Unsupported key")
        check(Regex("[A-Za-z0-9+/=]+").matches(code))
        val response = client.post("http://$ip/sony/ircc") {
            header("X-Auth-PSK", psk)
            header("SOAPACTION", "\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"")
            contentType(ContentType.Text.Xml)
            setBody("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:X_SendIRCC xmlns:u="urn:schemas-sony-com:service:IRCC:1"><IRCCCode>$code</IRCCCode></u:X_SendIRCC></s:Body></s:Envelope>""")
        }
        check(response.status.isSuccess())
    }
    suspend fun loadPlaylist(value: String): String {
        if (!value.trim().startsWith("https://")) return value
        require(isStreamUrl(value.trim()))
        val response = client.get(value.trim())
        check(response.status.isSuccess())
        val channel = response.bodyAsChannel()
        val bytes = channel.readBuffer(2_000_001L).readByteArray()
        check(bytes.size <= 2_000_000)
        return bytes.decodeToString()
    }
    fun disconnect() { ip = ""; psk = ""; codes = emptyMap() }
    fun close() { disconnect(); client.close() }
}
