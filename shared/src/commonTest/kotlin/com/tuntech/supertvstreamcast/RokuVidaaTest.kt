package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.data.*
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.net.TlsChannel
import com.tuntech.supertvstreamcast.platform.MemorySecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

private const val ROKU_TV = """<?xml version="1.0" encoding="UTF-8" ?>
<device-info>
	<udn>29380000-0800-1025-80a4-d83134a2b5a5</udn>
	<vendor-name>TCL</vendor-name>
	<model-name>55R635</model-name>
	<friendly-model-name>TCL&#8226;Roku TV</friendly-model-name>
	<user-device-name>Living &amp; dining</user-device-name>
	<wifi-mac>d8:31:34:a2:b5:a5</wifi-mac>
	<is-tv>true</is-tv>
	<power-mode>PowerOn</power-mode>
</device-info>"""
private const val ROKU_STICK = """<device-info><vendor-name>Roku</vendor-name><model-name>3820X</model-name><is-tv>false</is-tv></device-info>"""
private const val ROKU_APPS = """<apps>
	<app id="12" type="appl" version="5.1.10">Netflix</app>
	<app id="tvinput.hdmi1" type="tvin" version="1.0.0">HDMI 1</app>
	<app id="837" type="appl" version="2.21.1">YouTube &amp; Kids</app>
</apps>"""

class RokuVidaaTest {
    @Test fun rokuDeviceInfoNamesTheVendorAndLimitsTvKeys() {
        val tv = assertNotNull(RokuProtocol.info("192.168.1.20", ROKU_TV))
        assertEquals(TvBrand.TCL, tv.device.brand)
        assertEquals(TvPlatform.ROKU, tv.device.platform)
        assertEquals("Living & dining", tv.device.name)
        assertEquals("d8:31:34:a2:b5:a5", tv.device.mac)
        assertTrue(RemoteKey.VOLUME_UP in RokuProtocol.capabilities(tv).keys)
        assertTrue(RemoteKey.POWER in RokuProtocol.capabilities(tv).keys)

        val stick = assertNotNull(RokuProtocol.info("192.168.1.21", ROKU_STICK))
        assertEquals(TvBrand.ROKU, stick.device.brand)
        val keys = RokuProtocol.capabilities(stick).keys
        assertTrue(RemoteKey.OK in keys && RemoteKey.HOME in keys)
        assertFalse(RemoteKey.VOLUME_UP in keys || RemoteKey.POWER in keys || RemoteKey.CHANNEL_UP in keys)
        assertFalse(RemoteKey.NUM_1 in keys)
        assertNull(RokuProtocol.info("192.168.1.22", "<html>router</html>"))
    }
    @Test fun rokuAppsAndTextEncoding() {
        assertEquals(listOf(TvApp("12", "Netflix"), TvApp("837", "YouTube & Kids")), RokuProtocol.apps(ROKU_APPS))
        assertEquals(listOf("Lit_a", "Lit_%20", "Lit_%E2%82%AC"), RokuProtocol.literals("a €"))
    }
    @Test fun rokuConnectsAfterProtocolRepliesAndReportsDisabledControl() = runTest {
        val requests = mutableListOf<String>()
        var iconStatus = HttpStatusCode.OK
        val http = HttpClient(MockEngine { request ->
            requests += "${request.method.value} ${request.url.encodedPath}"
            when (request.url.encodedPath) {
                "/query/device-info" -> respond(ROKU_TV, HttpStatusCode.OK)
                "/query/apps" -> respond(ROKU_APPS, HttpStatusCode.OK)
                "/query/icon/12" -> respond("", iconStatus)
                else -> respond("", HttpStatusCode.OK)
            }
        })
        val adapter = RokuEcpAdapter(http)
        val session = adapter.connect("192.168.1.20", "")
        assertEquals(TvBrand.TCL, session.device.brand)
        adapter.send(RemoteKey.OK)
        adapter.sendText("hi")
        assertEquals(listOf("POST /keypress/Select", "POST /keypress/Lit_h", "POST /keypress/Lit_i"), requests.takeLast(3))
        adapter.disconnect()
        assertFailsWith<IllegalStateException> { adapter.send(RemoteKey.OK) }

        iconStatus = HttpStatusCode.Forbidden
        assertFailsWith<RemoteControlDisabled> { adapter.connect("192.168.1.20", "") }
        assertFailsWith<IllegalArgumentException> { adapter.connect("8.8.8.8", "") }
    }

    @Test fun mqttPacketsRoundTrip() = runTest {
        assertContentEquals(byteArrayOf(0xC1.toByte(), 0x02), Mqtt.remainingLength(321))
        val bytes = Mqtt.publish("/a/b", "KEY_OK")
        var offset = 0
        val packet = Mqtt.read { count -> bytes.copyOfRange(offset, offset + count).also { offset += count } }
        assertEquals(Mqtt.PUBLISH, packet.type)
        assertEquals(Mqtt.Publish("/a/b", "KEY_OK") to null, Mqtt.publish(packet))
        val connect = Mqtt.connect("id", VidaaProtocol.USERNAME, VidaaProtocol.PASSWORD, 30)
        assertEquals(0x10, connect[0].toInt())
        assertTrue(connect.decodeToString().contains("MQTT"))
    }
    @Test fun vidaaIdentifiersAndCodes() {
        val id = VidaaProtocol.newClientId(kotlin.random.Random(1))
        assertNotNull(macBytes(id))
        assertEquals(0x02, id.substring(0, 2).toInt(16) and 0x03)
        assertEquals("""{"authNum":1234}""", VidaaProtocol.authCode("1234"))
        assertNull(VidaaProtocol.authCode("12a4"))
        assertEquals("/remoteapp/tv/remote_service/$id\$normal/actions/sendkey", VidaaProtocol.key(id))
    }

    @Test fun vidaaKnownPhoneConnectsOnSourceList() = runTest {
        withContext(Dispatchers.Default) {
            val broker = FakeBroker { (topic, _) -> if (topic.endsWith("/actions/sourcelist")) listOf(reply("sourcelist", "[]")) else emptyList() }
            val adapter = VidaaAdapter({ _, _ -> broker }, MemorySecretStore(), waitMillis = 300)
            val session = adapter.connect("192.168.1.30", "")
            assertEquals(TvPlatform.VIDAA, session.device.platform)
            adapter.send(RemoteKey.VOLUME_UP)
            assertEquals("KEY_VOLUMEUP", broker.published.last { it.first.endsWith("/sendkey") }.second)
            adapter.disconnect()
            assertTrue(broker.closed)
        }
    }
    @Test fun vidaaAsksForTheCodeShownOnTv() = runTest {
        withContext(Dispatchers.Default) {
            val secrets = MemorySecretStore()
            val broker = FakeBroker { (topic, payload) ->
                when {
                    topic.endsWith("/actions/gettvstate") -> listOf(reply("authentication", ""))
                    topic.endsWith("/actions/authenticationcode") -> listOf(reply("authenticationcode", if (payload.contains("1234")) """{"result":1,"info":""}""" else """{"result":0}"""))
                    topic.endsWith("/actions/sourcelist") -> listOf(reply("sourcelist", "[]"))
                    else -> emptyList()
                }
            }
            val adapter = VidaaAdapter({ _, _ -> broker }, secrets, waitMillis = 300)
            assertEquals(4, assertFailsWith<PairingCodeRequired> { adapter.connect("192.168.1.30", "") }.length)
            assertFailsWith<PairingCodeInvalid> { adapter.connect("192.168.1.30", "12") }
            assertFailsWith<PairingCodeInvalid> { adapter.connect("192.168.1.30", "9999") }
            adapter.connect("192.168.1.30", "1234")
            assertNotNull(secrets.get("vidaa.client"))
        }
    }
    @Test fun vidaaRefusedCredentialsFail() = runTest {
        withContext(Dispatchers.Default) {
            val broker = FakeBroker(connack = 5) { emptyList() }
            assertFails { VidaaAdapter({ _, _ -> broker }, MemorySecretStore()).connect("192.168.1.30", "") }
            assertTrue(broker.closed)
        }
    }

    @Test fun autoDetectTriesPlatformsInOrderAndKeepsTheCodeSession() = runTest {
        val tried = mutableListOf<String>()
        fun failing(name: String) = { object : StubAdapter() { override suspend fun connect(host: String, secret: String): RemoteSession { tried += name; error("no") } } }
        val pairing = object : StubAdapter() {
            override suspend fun connect(host: String, secret: String): RemoteSession {
                tried += "pairing:$secret"
                if (secret.isEmpty()) throw PairingCodeRequired(4)
                return RemoteSession(TvDevice(TvBrand.HISENSE, host, "TV"), RemoteCapabilities())
            }
        }
        val auto = AutoDetectAdapter(listOf(failing("roku"), { null }, { pairing }, failing("never")))
        assertFailsWith<PairingCodeRequired> { auto.connect("192.168.1.30", "") }
        auto.connect("192.168.1.30", "1234")
        assertEquals(listOf("roku", "pairing:", "pairing:1234"), tried)

        val disabled = AutoDetectAdapter(listOf({ object : StubAdapter() { override suspend fun connect(host: String, secret: String): RemoteSession = throw RemoteControlDisabled() } }, failing("after")))
        assertFailsWith<RemoteControlDisabled> { disabled.connect("192.168.1.30", "") }
        assertFalse("after" in tried)
    }
    @Test fun brandsMapToTheirPlatforms() {
        assertEquals(listOf(TvPlatform.ROKU, TvPlatform.FIRE_TV, TvPlatform.GOOGLE_TV), TvBrand.TCL.platforms)
        assertTrue(TvPlatform.VIDAA in TvBrand.HISENSE.platforms)
        assertTrue(TvBrand.entries.all { it.platforms.isNotEmpty() })
        val http = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) })
        assertIs<RokuEcpAdapter>(createAdapter(TvBrand.ROKU, http, { _, _ -> error("no socket") }))
        assertIs<AutoDetectAdapter>(createAdapter(TvBrand.TCL, http, { _, _ -> error("no socket") }))
        assertIs<RokuEcpAdapter>(createAdapter(TvBrand.HISENSE, http, { _, _ -> error("no socket") }, platform = TvPlatform.ROKU))
    }
}

private fun reply(action: String, payload: String) = "/remoteapp/mobile/ID\$normal/ui_service/data/$action" to payload

private abstract class StubAdapter : RemoteAdapter {
    override suspend fun send(key: RemoteKey) = Unit
    override suspend fun sendText(text: String) = Unit
    override suspend fun apps() = emptyList<TvApp>()
    override suspend fun launch(app: TvApp) = Unit
    override fun disconnect() = Unit
}

/** Scripted VIDAA broker: answers CONNECT/SUBSCRIBE and replies to publishes via [replies]. */
private class FakeBroker(private val connack: Int = 0, private val replies: (Pair<String, String>) -> List<Pair<String, String>>) : TlsChannel {
    override val localCertificate = ByteArray(0)
    override val peerCertificate = ByteArray(0)
    val published = mutableListOf<Pair<String, String>>()
    var closed = false
    private val inbound = Channel<ByteArray>(Channel.UNLIMITED)
    private var pending = ByteArray(0)

    override suspend fun write(bytes: ByteArray) {
        check(!closed)
        var offset = 0
        val packet = Mqtt.read { count -> bytes.copyOfRange(offset, offset + count).also { offset += count } }
        when (packet.type) {
            1 -> inbound.send(byteArrayOf(0x20, 2, 0, connack.toByte()))
            8 -> inbound.send(byteArrayOf(0x90.toByte(), 3, packet.body[0], packet.body[1], 0))
            Mqtt.PUBLISH -> {
                val message = Mqtt.publish(packet).first.let { it.topic to it.payload }
                published += message
                replies(message).forEach { (topic, payload) -> inbound.send(Mqtt.publish(topic, payload)) }
            }
        }
    }
    override suspend fun read(count: Int): ByteArray {
        while (pending.size < count) pending += inbound.receive()
        return pending.copyOfRange(0, count).also { pending = pending.copyOfRange(count, pending.size) }
    }
    override fun close() { closed = true; inbound.close() }
}
