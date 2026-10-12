package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.data.*
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.platform.MemorySecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

private const val VIERA_DDD = """<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><device>
<deviceType>urn:panasonic-com:device:p00RemoteController:1</deviceType><friendlyName>Living &amp; TV</friendlyName>
<manufacturer>Panasonic</manufacturer><modelName>Panasonic VIErA</modelName><modelNumber>TX-55AS650</modelNumber>
<serviceList><service><serviceType>urn:panasonic-com:service:p00NetworkControl:1</serviceType></service></serviceList></device></root>"""
private const val VIERA_SDD = """<scpd><actionList><action><name>X_SendKey</name></action></actionList></scpd>"""
private const val VIERA_SDD_ENCRYPTED = """<scpd><actionList><action><name>X_GetEncryptSessionId</name></action><action><name>X_SendKey</name></action></actionList></scpd>"""
private const val VIERA_VOLUME = """<s:Envelope><s:Body><u:GetVolumeResponse><CurrentVolume>12</CurrentVolume></u:GetVolumeResponse></s:Body></s:Envelope>"""

private fun body(request: HttpRequestData) = (request.body as? TextContent)?.text.orEmpty()
private fun json(text: String) = headersOf(HttpHeaders.ContentType, "application/json")

class NewTvPlatformsTest {
    @Test fun brandsMapToPlatformsWithSilentProbesFirstAndGoogleTvLast() {
        assertEquals(listOf(TvPlatform.FIRE_TV), TvBrand.AMAZON.platforms)
        assertEquals(listOf(TvPlatform.VIZIO), TvBrand.VIZIO.platforms)
        assertEquals(listOf(TvPlatform.GOOGLE_TV), TvBrand.XIAOMI.platforms)
        for (brand in TvBrand.entries) {
            val platforms = brand.platforms
            assertTrue(platforms.isNotEmpty(), "$brand")
            assertEquals(platforms.distinct(), platforms)
            if (TvPlatform.GOOGLE_TV in platforms) assertEquals(TvPlatform.GOOGLE_TV, platforms.last(), "$brand")
        }
        assertEquals(TvBrand.OTHER, TvBrand.entries.last())
        val http = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) })
        assertIs<FireTvAdapter>(createAdapter(TvBrand.AMAZON, http, { _, _ -> error("no socket") }))
        assertIs<VizioAdapter>(createAdapter(TvBrand.VIZIO, http, { _, _ -> error("no socket") }))
        assertIs<AutoDetectAdapter>(createAdapter(TvBrand.PANASONIC, http, { _, _ -> error("no socket") }))
        assertIs<PanasonicVieraAdapter>(createAdapter(TvBrand.OTHER, http, { _, _ -> error("no socket") }, platform = TvPlatform.PANASONIC_VIERA))
    }

    @Test fun vizioMessagesFollowSmartCast() {
        assertEquals("""{"KEYLIST":[{"CODESET":3,"CODE":8,"ACTION":"KEYPRESS"}]}""", VizioProtocol.keyPress(RemoteKey.UP))
        assertEquals(5 to 1, VizioProtocol.code(RemoteKey.VOLUME_UP))
        assertNull(VizioProtocol.code(RemoteKey.NUM_1))
        assertFalse(RemoteKey.NUM_1 in VizioProtocol.capabilities.keys)
        val challenge = assertNotNull(VizioProtocol.challenge("""{"STATUS":{"RESULT":"SUCCESS"},"ITEM":{"PAIRING_REQ_TOKEN":53921,"CHALLENGE_TYPE":1}}"""))
        assertEquals(VizioProtocol.Challenge(53921, 1), challenge)
        assertEquals("""{"DEVICE_ID":"d1","CHALLENGE_TYPE":1,"RESPONSE_VALUE":"1234","PAIRING_REQ_TOKEN":53921}""", VizioProtocol.pair("d1", challenge, "1234"))
        assertEquals("Zm9v", VizioProtocol.authToken("""{"STATUS":{"RESULT":"SUCCESS"},"ITEM":{"AUTH_TOKEN":"Zm9v"}}"""))
        assertNull(VizioProtocol.authToken("""{"STATUS":{"RESULT":"CHALLENGE_INCORRECT"}}"""))
        assertTrue(VizioProtocol.wrongCode("""{"STATUS":{"RESULT":"CHALLENGE_INCORRECT"}}"""))
        assertFalse(VizioProtocol.wrongCode("""{"STATUS":{"RESULT":"BLOCKED"}}"""))
        assertFalse(VizioProtocol.validCode("12a4"))
    }

    @Test fun vizioPairsWithThePinAndReusesTheAcceptedToken() = runTest {
        val requests = mutableListOf<String>()
        var pin = "1234"
        val http = HttpClient(MockEngine { request ->
            requests += "${request.method.value} ${request.url.port}${request.url.encodedPath} ${request.headers["AUTH"].orEmpty()}".trim()
            if (request.url.port == 7345) throw IllegalStateException("refused")
            val reply = when (request.url.encodedPath) {
                "/pairing/start" -> """{"STATUS":{"RESULT":"SUCCESS"},"ITEM":{"PAIRING_REQ_TOKEN":7,"CHALLENGE_TYPE":1}}"""
                "/pairing/pair" -> if (body(request).contains("\"$pin\"")) """{"STATUS":{"RESULT":"SUCCESS"},"ITEM":{"AUTH_TOKEN":"tok"}}""" else """{"STATUS":{"RESULT":"CHALLENGE_INCORRECT"}}"""
                else -> if (request.headers["AUTH"] == "tok") """{"STATUS":{"RESULT":"SUCCESS"},"ITEMS":[{"VALUE":1}]}""" else """{"STATUS":{"RESULT":"BLOCKED"}}"""
            }
            respond(reply, HttpStatusCode.OK, json(reply))
        })
        val secrets = MemorySecretStore()
        val adapter = VizioAdapter({ http }, secrets)
        assertEquals(4, assertFailsWith<PairingCodeRequired> { adapter.connect("192.168.1.30", "") }.length)
        assertFailsWith<PairingCodeInvalid> { adapter.connect("192.168.1.30", "9999") }
        val session = adapter.connect("192.168.1.30", pin)
        assertEquals(TvPlatform.VIZIO, session.device.platform)
        assertEquals("9000:tok", secrets.get("vizio.token.192.168.1.30"))
        adapter.send(RemoteKey.OK)
        assertEquals("PUT 9000/key_command/ tok", requests.last())

        // A new session with the stored token connects without pairing.
        requests.clear()
        val again = VizioAdapter({ http }, secrets)
        again.connect("192.168.1.30", "")
        assertTrue(requests.none { it.contains("pairing") })
        pin = "0000"
        assertFailsWith<IllegalArgumentException> { again.connect("8.8.8.8", "") }
    }

    @Test fun fireTvMessagesAndAppList() {
        assertEquals("https://192.168.1.40:8080/v1/FireTV?action=select" to null, FireTvProtocol.keyUrl("192.168.1.40", RemoteKey.OK))
        val (url, scan) = assertNotNull(FireTvProtocol.keyUrl("192.168.1.40", RemoteKey.FAST_FORWARD))
        assertEquals("https://192.168.1.40:8080/v1/media?action=scan", url)
        assertEquals("""{"direction":"forward","keyAction":{"keyActionType":"keyDown"}}""", scan)
        assertFalse(RemoteKey.VOLUME_UP in FireTvProtocol.capabilities.keys)
        assertEquals("Wj9PP8I", FireTvProtocol.token("""{"description":"Wj9PP8I"}"""))
        assertNull(FireTvProtocol.token("""{"description":"OK"}"""))
        assertNull(FireTvProtocol.token("""{"description":"Bad arguments supplied. Please check inputs."}"""))
        assertEquals(listOf(TvApp("com.netflix.ninja", "Netflix")),
            FireTvProtocol.installedApps("""{"apps":[{"packageName":"com.netflix.ninja","name":"Netflix"},{"name":"no id"}]}"""))
        assertTrue(FireTvProtocol.isFireTv("""<service xmlns="urn:dial-multiscreen-org:schemas:dial"><name>FireTVRemote</name><state>stopped</state></service>"""))
        assertFalse(FireTvProtocol.isFireTv("<service><name>YouTube</name></service>"))
    }

    @Test fun fireTvPairsWithThePinAndSendsTheClientToken() = runTest {
        val requests = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            requests += "${request.method.value} ${request.url.encodedPath}${request.url.encodedQuery.let { if (it.isEmpty()) "" else "?$it" }} ${request.headers["X-Client-Token"].orEmpty()}".trim()
            if (request.headers["X-Api-Key"] != FireTvProtocol.API_KEY && request.url.port == 8080) return@MockEngine respond("", HttpStatusCode.Unauthorized)
            when (request.url.encodedPath) {
                "/apps/FireTVRemote" -> respond("", HttpStatusCode.Created)
                "/v1/FireTV/pin/display" -> respond("""{"description":"OK"}""", HttpStatusCode.OK)
                "/v1/FireTV/pin/verify" -> if (body(request).contains("4321")) respond("""{"description":"tok123"}""", HttpStatusCode.OK)
                    else respond("""{"description":"Bad arguments supplied. Please check inputs."}""", HttpStatusCode.BadRequest)
                "/v1/FireTV/appsV2" -> if (request.headers["X-Client-Token"] == "tok123") respond("[]", HttpStatusCode.OK) else respond("", HttpStatusCode.Unauthorized)
                else -> respond("""{"description":"OK"}""", HttpStatusCode.OK)
            }
        })
        val secrets = MemorySecretStore()
        val adapter = FireTvAdapter(http, { http }, secrets)
        assertEquals(4, assertFailsWith<PairingCodeRequired> { adapter.connect("192.168.1.40", "") }.length)
        assertFailsWith<PairingCodeInvalid> { adapter.connect("192.168.1.40", "1111") }
        assertEquals(TvBrand.AMAZON, adapter.connect("192.168.1.40", "4321").device.brand)
        adapter.send(RemoteKey.HOME)
        assertEquals("POST /v1/FireTV?action=home tok123", requests.last())
        assertEquals(FireTvProtocol.shortcuts, adapter.apps())

        requests.clear()
        FireTvAdapter(http, { http }, secrets).connect("192.168.1.40", "")
        assertTrue(requests.none { it.contains("pin") })
    }

    @Test fun panasonicConnectsAfterVolumeReplyAndRefusesEncryptedModels() = runTest {
        var actions = VIERA_SDD
        val soap = mutableListOf<String>()
        val http = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/nrc/ddd.xml" -> respond(VIERA_DDD, HttpStatusCode.OK)
                "/nrc/sdd_0.xml" -> respond(actions, HttpStatusCode.OK)
                "/dmr/control_0" -> respond(VIERA_VOLUME, HttpStatusCode.OK)
                "/nrc/control_0" -> { soap += request.headers["SOAPACTION"] + " " + body(request); respond("<ok/>", HttpStatusCode.OK) }
                else -> respond("", HttpStatusCode.NotFound)
            }
        })
        val adapter = PanasonicVieraAdapter(http)
        val session = adapter.connect("192.168.1.50", "")
        assertEquals("Living & TV", session.device.name)
        assertEquals("Panasonic VIErA TX-55AS650", session.device.model)
        adapter.send(RemoteKey.VOLUME_UP)
        assertTrue(soap.single().startsWith("\"urn:panasonic-com:service:p00NetworkControl:1#X_SendKey\""))
        assertTrue(soap.single().contains("<X_KeyEvent>NRC_VOLUP-ONOFF</X_KeyEvent>"))
        assertEquals("NRC_D7-ONOFF", PanasonicProtocol.keyName(RemoteKey.NUM_7))

        actions = VIERA_SDD_ENCRYPTED
        assertFailsWith<UnsupportedTvModel> { adapter.connect("192.168.1.50", "") }
        assertNull(PanasonicProtocol.identity("192.168.1.51", "<root><manufacturer>Other</manufacturer></root>"))
    }

    @Test fun autoDetectStopsAtAnUnsupportedModel() = runTest {
        var googleTried = false
        val detect = AutoDetectAdapter(listOf(
            { object : FakeAdapter() { override suspend fun connect(host: String, secret: String): RemoteSession = throw UnsupportedTvModel() } },
            { googleTried = true; FakeAdapter() },
        ))
        assertFailsWith<UnsupportedTvModel> { detect.connect("192.168.1.60", "") }
        assertFalse(googleTried)
    }
}

private open class FakeAdapter : RemoteAdapter {
    override suspend fun connect(host: String, secret: String) = RemoteSession(TvDevice(TvBrand.OTHER, host, "TV"), RemoteCapabilities())
    override suspend fun send(key: RemoteKey) = Unit
    override suspend fun sendText(text: String) = Unit
    override suspend fun apps() = emptyList<TvApp>()
    override suspend fun launch(app: TvApp) = Unit
    override fun disconnect() = Unit
}
