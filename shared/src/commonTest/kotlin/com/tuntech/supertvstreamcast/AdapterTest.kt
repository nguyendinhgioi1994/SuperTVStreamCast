package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.data.*
import com.tuntech.supertvstreamcast.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Scripted TV: each sent frame may queue replies. */
private class FakeSocket(val url: String, private val reply: (String) -> List<String> = {emptyList()}, initial: List<String> = emptyList()) : TextSocket {
    val sent = mutableListOf<String>()
    var closed = false
    private val inbox = Channel<String>(Channel.UNLIMITED).also { c -> initial.forEach { c.trySend(it) } }
    override suspend fun send(text: String) { check(!closed); sent += text; reply(text).forEach { inbox.send(it) } }
    override suspend fun receive(): String = inbox.receive()
    override fun close() { closed = true; inbox.close() }
}
private val samsungInfo = """{"device":{"type":"Samsung SmartTV","name":"Living room","modelName":"QE55","TokenAuthSupport":"true"}}"""

class AdapterTest {
    @Test fun samsungConnectsOnlyAfterTvAcceptsAndReusesToken() = runTest {
        val sockets = mutableListOf<FakeSocket>()
        val http = HttpClient(MockEngine { respond(samsungInfo, HttpStatusCode.OK) })
        val adapter = SamsungTizenAdapter(http) { _, url ->
            FakeSocket(url, initial = listOf("""{"event":"ms.channel.ready"}""", """{"event":"ms.channel.connect","data":{"token":"777"}}""")).also { sockets += it }
        }
        val session = adapter.connect("192.168.1.9", "")
        assertEquals("Living room", session.device.name)
        assertTrue(sockets[0].url.startsWith("wss://192.168.1.9:8002/"))
        adapter.send(RemoteKey.HOME)
        assertTrue(sockets[0].sent.single().contains("KEY_HOME"))
        adapter.disconnect()
        assertTrue(sockets[0].closed)
        assertFailsWith<IllegalStateException> { adapter.send(RemoteKey.HOME) }
        adapter.connect("192.168.1.9", "")
        assertTrue(sockets[1].url.endsWith("&token=777"))
    }
    @Test fun samsungDeniedPairingFailsAndClosesSocket() = runTest {
        var socket: FakeSocket? = null
        val adapter = SamsungTizenAdapter(HttpClient(MockEngine { respond(samsungInfo, HttpStatusCode.OK) })) { _, url ->
            FakeSocket(url, initial = listOf("""{"event":"ms.channel.unauthorized"}""")).also { socket = it }
        }
        assertFailsWith<IllegalStateException> { adapter.connect("192.168.1.9", "") }
        assertTrue(socket!!.closed)
        assertFailsWith<IllegalStateException> { adapter.send(RemoteKey.OK) }
    }
    @Test fun samsungRejectsNonTvAndPublicHosts() = runTest {
        val adapter = SamsungTizenAdapter(HttpClient(MockEngine { respond("""{"device":{"type":"Speaker"}}""", HttpStatusCode.OK) })) { _, _ -> error("no socket") }
        assertFailsWith<IllegalStateException> { adapter.connect("192.168.1.9", "") }
        assertFailsWith<IllegalArgumentException> { adapter.connect("8.8.8.8", "") }
    }
    @Test fun samsungListsAppsFromInstalledAppEvent() = runTest {
        lateinit var socket: FakeSocket
        val adapter = SamsungTizenAdapter(HttpClient(MockEngine { respond(samsungInfo, HttpStatusCode.OK) })) { _, url ->
            FakeSocket(url, reply = { if (it.contains("ed.installedApp.get")) listOf("""{"event":"other"}""", """{"event":"ed.installedApp.get","data":{"data":[{"appId":"11101200001","name":"Netflix","app_type":4}]}}""") else emptyList() },
                initial = listOf("""{"event":"ms.channel.connect"}""")).also { socket = it }
        }
        adapter.connect("192.168.1.9", "")
        val apps = adapter.apps()
        assertEquals(listOf(TvApp("11101200001", "Netflix")), apps)
        adapter.launch(apps.single())
        assertTrue(socket.sent.last().contains("NATIVE_LAUNCH"))
    }
    private fun lgTv(registerReply: String, opened: MutableList<FakeSocket>, wssFails: Boolean = false): SocketOpener = { _, url ->
        if (wssFails && url.startsWith("wss://192.168.1.7:3001")) error("refused")
        if (url.contains("pointer")) FakeSocket(url).also { opened += it }
        else FakeSocket(url, reply = { frame ->
            val id = Regex("\"id\":\"([^\"]+)\"").find(frame)?.groupValues?.get(1).orEmpty()
            when {
                frame.contains("\"register\"") -> listOf("""{"type":"response","id":"register_0","payload":{"pairingType":"PROMPT"}}""", registerReply)
                frame.contains("getPointerInputSocket") -> listOf("""{"type":"response","id":"$id","payload":{"returnValue":true,"socketPath":"ws://192.168.1.7:3000/resources/x/netinput.pointer.sock"}}""")
                frame.contains("volumeUp") -> listOf("""{"type":"response","id":"other","payload":{}}""", """{"type":"response","id":"$id","payload":{"returnValue":true}}""")
                frame.contains("turnOff") -> listOf("""{"type":"error","id":"$id","error":"401 insufficient permissions"}""")
                else -> emptyList()
            }
        }).also { opened += it }
    }
    @Test fun lgRegistersUsesPointerSocketAndChecksResponses() = runTest {
        val opened = mutableListOf<FakeSocket>()
        val adapter = LgWebOsAdapter(lgTv("""{"type":"registered","id":"register_0","payload":{"client-key":"ck"}}""", opened, wssFails = true))
        val session = adapter.connect("192.168.1.7", "")
        assertTrue(session.capabilities.pointer)
        assertEquals("ws://192.168.1.7:3000", opened[0].url)
        adapter.send(RemoteKey.UP)
        assertEquals("type:button\nname:UP\n\n", opened[1].sent.single())
        adapter.send(RemoteKey.VOLUME_UP)
        assertFailsWith<IllegalStateException> { adapter.send(RemoteKey.POWER) }
        adapter.disconnect()
        assertTrue(opened.all { it.closed })
        adapter.connect("192.168.1.7", "")
        assertTrue(opened[2].sent.first().contains("\"client-key\":\"ck\""))
    }
    @Test fun lgRejectedRegistrationNeverConnects() = runTest {
        val opened = mutableListOf<FakeSocket>()
        val adapter = LgWebOsAdapter(lgTv("""{"type":"error","id":"register_0","error":"403 cancelled"}""", opened))
        assertFailsWith<IllegalStateException> { adapter.connect("192.168.1.7", "") }
        assertTrue(opened.single().closed)
        assertEquals("wss://192.168.1.7:3001", opened.single().url)
    }
    @Test fun discoveryListsOnlyHostsThatAnswerVendorProbes() = runTest {
        val http = HttpClient(MockEngine { request ->
            when {
                request.url.host == "192.168.0.3" && request.url.port == 8001 -> respond(samsungInfo, HttpStatusCode.OK)
                request.url.host == "192.168.0.4" && request.url.encodedPath == "/sony/system" ->
                    respond("""{"result":[{"productCategory":"tv","productName":"BRAVIA","modelName":"KD-1"}]}""", HttpStatusCode.OK)
                else -> respond("", HttpStatusCode.NotFound)
            }
        })
        val discovery = TvDiscovery(http, { host, url ->
            if (host == "192.168.0.5") FakeSocket(url, reply = { listOf("""{"type":"error","id":"probe","error":"401"}""") }) else error("refused")
        })
        val found = mutableListOf<TvDevice>()
        val devices = discovery.scan("192.168.0.2") { found += it }
        assertEquals(listOf(TvBrand.SAMSUNG, TvBrand.SONY, TvBrand.LG), devices.map { it.brand })
        assertEquals(listOf("192.168.0.3", "192.168.0.4", "192.168.0.5"), devices.map { it.host })
        assertEquals(3, found.size)
        assertTrue(discovery.scan("8.8.8.8").isEmpty())
    }
}
