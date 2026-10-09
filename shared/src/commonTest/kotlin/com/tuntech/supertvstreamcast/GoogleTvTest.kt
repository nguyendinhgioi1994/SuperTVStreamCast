package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.data.*
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.net.TlsChannel
import com.tuntech.supertvstreamcast.platform.MemorySecretStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.test.*

private fun certificate(seed: Int): ByteArray {
    val key = RsaPublicKey(ByteArray(256) { (it * 7 + seed).toByte() }.also { it[0] = 0xC1.toByte() }, byteArrayOf(1, 0, 1))
    return Der.selfSignedCertificate(Der.rsaPublicKeyDer(key), "atvremote", byteArrayOf(seed.toByte())) { ByteArray(256) { 5 } }
}
private val PHONE = certificate(1)
private val TV = certificate(2)
/** A code whose check byte matches the two certificates, as the TV would display it. */
private fun codeFor(client: ByteArray, server: ByteArray, tail: String = "b2c3"): String {
    val a = Der.certificateKey(client)
    val b = Der.certificateKey(server)
    return byteArrayOf(sha256(a.modulus + a.exponent + b.modulus + b.exponent + hexBytes(tail)!!)[0]).toHex() + tail
}

/** Scripted TV end of a TLS channel: every framed message written may queue framed replies. */
private class FakeTls(val port: Int, peer: ByteArray = TV, private val reply: (ByteArray) -> List<ByteArray> = { emptyList() }, initial: List<ByteArray> = emptyList()) : TlsChannel {
    override val localCertificate = PHONE
    override val peerCertificate = peer
    val sent = Channel<ByteArray>(Channel.UNLIMITED)
    var closed = false
    private val inbox = Channel<Byte>(Channel.UNLIMITED)
    private fun queue(message: ByteArray) = (protoVarint(message.size) + message).forEach { inbox.trySend(it) }
    init { initial.forEach(::queue) }
    override suspend fun write(bytes: ByteArray) {
        check(!closed)
        val message = bytes.copyOfRange(bytes.indexOfFirst { it >= 0 } + 1, bytes.size)
        sent.trySend(message)
        reply(message).forEach(::queue)
    }
    override suspend fun read(count: Int) = ByteArray(count) { inbox.receive() }
    override fun close() { closed = true; inbox.close() }
}
private fun ok(body: ProtoWriter.() -> Unit) = proto { varint(1, 2); varint(2, 200); body() }
private val CONFIGURE = proto { message(1) { varint(1, 639); message(2) { string(1, "Chromecast"); string(2, "Google") } } }

class GoogleTvTest {
    @Test fun sha256MatchesPublishedVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256(ByteArray(0)).toHex())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256("abc".encodeToByteArray()).toHex())
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).toHex())
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", sha256(ByteArray(1_000_000) { 'a'.code.toByte() }).toHex())
    }
    @Test fun protobufRoundTripsNestedMessagesAndRejectsTruncation() {
        val bytes = proto { varint(1, 300); message(10) { string(1, "atvremote"); varint(2, 3) }; bytes(15, byteArrayOf(1, 2)) }
        assertEquals("08ac02520d0a0961747672656d6f746510037a020102", bytes.toHex())
        val fields = parseProto(bytes)
        assertEquals(300L, fields.varints[1])
        assertEquals("atvremote", fields.message(10)!!.string(1))
        assertEquals(3L, fields.message(10)!!.varints[2])
        assertFailsWith<IllegalArgumentException> { parseProto(bytes.copyOf(bytes.size - 1)) }
        assertEquals(listOf<Byte>(0xAC.toByte(), 2), protoVarint(300).toList())
    }
    @Test fun pairingMessagesUseHexadecimalInputRole() {
        assertEquals("080210c8015215" + "0a0961747672656d6f7465" + "12085456205370616365", GoogleTvProtocol.pairingRequest("TV Space").toHex())
        assertEquals("080210c801a201080a04080310061801", GoogleTvProtocol.pairingOption().toHex())
        assertEquals("080210c801f201080a04080310061001", GoogleTvProtocol.pairingConfiguration().toHex())
        assertEquals(GoogleTvProtocol.PairingStep.CONFIGURATION_ACK, GoogleTvProtocol.pairingStep(ok { message(31) {} }))
        assertFailsWith<IllegalStateException> { GoogleTvProtocol.pairingStep(proto { varint(1, 2); varint(2, 402) }) }
    }
    @Test fun secretRequiresMatchingCheckByte() {
        val client = Der.certificateKey(PHONE)
        val server = Der.certificateKey(TV)
        assertEquals(256, client.modulus.size)
        assertEquals(listOf<Byte>(1, 0, 1), client.exponent.toList())
        val code = codeFor(PHONE, TV)
        val secret = assertNotNull(GoogleTvProtocol.secret(" ${code.uppercase()} ", client, server))
        assertEquals(32, secret.size)
        val wrong = ((code.take(2).toInt(16) + 1) % 256).toString(16).padStart(2, '0') + code.drop(2)
        assertNull(GoogleTvProtocol.secret(wrong, client, server))
        assertNull(GoogleTvProtocol.secret("12345", client, server))
        assertNull(GoogleTvProtocol.secret("zzzzzz", client, server))
    }
    @Test fun remoteMessagesAndKeyCodes() {
        assertEquals(GoogleTvProtocol.Event.Configure(639, "Google", "Chromecast"), GoogleTvProtocol.event(CONFIGURE))
        assertEquals(611, GoogleTvProtocol.features(639))
        assertEquals(GoogleTvProtocol.Event.Ping(7), GoogleTvProtocol.event(proto { message(8) { varint(1, 7) } }))
        assertEquals("4a020807", GoogleTvProtocol.pong(7).toHex())
        assertEquals("520408031003", GoogleTvProtocol.key(RemoteKey.HOME).toHex())
        assertEquals(7, GoogleTvProtocol.keyCode(RemoteKey.NUM_0))
        assertEquals(16, GoogleTvProtocol.keyCode(RemoteKey.NUM_9))
        assertEquals(RemoteKey.entries.size, RemoteKey.entries.map(GoogleTvProtocol::keyCode).toSet().size)
    }
    @Test fun wakePacketRepeatsTheAddressSixteenTimes() {
        val packet = assertNotNull(magicPacket("AA-bb:0C:0d:0e:0F"))
        assertEquals(102, packet.size)
        assertTrue(packet.take(6).all { it == 0xFF.toByte() })
        assertEquals("aabb0c0d0e0f", packet.copyOfRange(96, 102).toHex())
        assertNull(magicPacket("00:00:00:00:00:00"))
        assertNull(magicPacket("aa:bb:cc:dd:ee"))
        assertNull(magicPacket("aabbccddeeff"))
        assertEquals("192.168.1.255", subnetBroadcast("192.168.1.20"))
        assertNull(subnetBroadcast("8.8.8.8"))
    }

    private fun pairingTv(opened: MutableList<FakeTls>, remotePeer: ByteArray = TV): suspend (String, Int) -> TlsChannel = { _, port ->
        val channel = if (port == GoogleTvProtocol.PAIRING_PORT) FakeTls(port, reply = { message ->
            val fields = parseProto(message)
            when {
                10 in fields.bytes -> listOf(ok { message(11) { string(1, "tv") } })
                20 in fields.bytes -> listOf(ok { message(20) {} })
                30 in fields.bytes -> listOf(ok { message(31) {} })
                40 in fields.bytes -> listOf(ok { message(41) { bytes(1, fields.message(40)!!.bytes.getValue(1)) } })
                else -> emptyList()
            }
        }) else FakeTls(port, remotePeer, initial = listOf(CONFIGURE), reply = { message ->
            if (1 in parseProto(message).bytes) listOf(proto { message(2) { varint(1, 639) } }, proto { message(8) { varint(1, 42) } }) else emptyList()
        })
        channel.also { opened += it }
    }
    @Test fun pairsWithCodeThenControlsAndPinsTheTv() = runTest {
        val opened = mutableListOf<FakeTls>()
        val secrets = MemorySecretStore()
        val adapter = GoogleTvAdapter(pairingTv(opened), secrets)
        assertFailsWith<PairingCodeRequired> { adapter.connect("192.168.1.30", "") }
        assertEquals(listOf(GoogleTvProtocol.PAIRING_PORT), opened.map { it.port })
        assertFalse(opened[0].closed)
        assertFailsWith<IllegalStateException> { adapter.send(RemoteKey.OK) }
        val code = codeFor(PHONE, TV)
        val mistyped = ((code.take(2).toInt(16) + 1) % 256).toString(16).padStart(2, '0') + code.drop(2)
        assertFailsWith<PairingCodeInvalid> { adapter.connect("192.168.1.30", mistyped) }
        assertFalse(opened[0].closed)
        assertNull(secrets.get("google.cert.192.168.1.30"))

        val session = adapter.connect("192.168.1.30", code)
        assertEquals("Google Chromecast", session.device.name)
        assertEquals(TvBrand.GOOGLE, session.device.brand)
        assertFalse(session.capabilities.text || session.capabilities.apps)
        assertTrue(opened[0].closed)
        assertEquals(sha256(TV).toHex(), secrets.get("google.cert.192.168.1.30"))
        val remote = opened[1]
        assertEquals(GoogleTvProtocol.REMOTE_PORT, remote.port)
        assertEquals(GoogleTvProtocol.configure(611).toHex(), remote.sent.receive().toHex())
        assertEquals(GoogleTvProtocol.active(611).toHex(), remote.sent.receive().toHex())
        // The ping queued behind the activation is answered by the session reader.
        assertEquals(GoogleTvProtocol.pong(42).toHex(), remote.sent.receive().toHex())
        adapter.send(RemoteKey.VOLUME_UP)
        assertEquals(GoogleTvProtocol.key(RemoteKey.VOLUME_UP).toHex(), remote.sent.receive().toHex())
        adapter.disconnect()
        assertTrue(remote.closed)
        assertFailsWith<IllegalStateException> { adapter.send(RemoteKey.OK) }

        // A paired TV reconnects without a code, from a new adapter using the stored pin.
        val again = GoogleTvAdapter(pairingTv(opened), secrets)
        again.connect("192.168.1.30", "")
        assertEquals(GoogleTvProtocol.REMOTE_PORT, opened.last().port)
        again.disconnect()
    }
    @Test fun changedTvCertificateIsNotTrustedAndPairsAgain() = runTest {
        val opened = mutableListOf<FakeTls>()
        val secrets = MemorySecretStore().apply { put("google.cert.192.168.1.30", sha256(TV).toHex()) }
        val adapter = GoogleTvAdapter(pairingTv(opened, remotePeer = certificate(9)), secrets)
        assertFailsWith<PairingCodeRequired> { adapter.connect("192.168.1.30", "") }
        assertEquals(listOf(GoogleTvProtocol.REMOTE_PORT, GoogleTvProtocol.PAIRING_PORT), opened.map { it.port })
        assertTrue(opened[0].closed)
        assertTrue(opened[0].sent.tryReceive().isFailure)
        assertFailsWith<IllegalArgumentException> { adapter.connect("8.8.8.8", "") }
        adapter.disconnect()
    }
    @Test fun rejectedSecretNeverConnects() = runTest {
        val opened = mutableListOf<FakeTls>()
        val secrets = MemorySecretStore()
        val adapter = GoogleTvAdapter({ _, port ->
            FakeTls(port, reply = { message ->
                val fields = parseProto(message)
                when {
                    10 in fields.bytes -> listOf(ok { message(11) {} })
                    20 in fields.bytes -> listOf(ok { message(20) {} })
                    30 in fields.bytes -> listOf(ok { message(31) {} })
                    else -> listOf(proto { varint(1, 2); varint(2, 402) })
                }
            }).also { opened += it }
        }, secrets)
        assertFailsWith<PairingCodeRequired> { adapter.connect("192.168.1.30", "") }
        assertFailsWith<IllegalStateException> { adapter.connect("192.168.1.30", codeFor(PHONE, TV)) }
        assertTrue(opened.single().closed)
        assertNull(secrets.get("google.cert.192.168.1.30"))
    }
}
