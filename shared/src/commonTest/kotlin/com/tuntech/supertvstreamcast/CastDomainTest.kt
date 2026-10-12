package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.domain.cast.*
import kotlin.test.*

class CastDomainTest {
    private val description = """<?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0"><device>
        <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
        <friendlyName>[TV] Living Room &amp; Kitchen</friendlyName><manufacturer>Samsung Electronics</manufacturer><modelName>QE55Q80</modelName>
        <UDN>uuid:abc-123</UDN><serviceList>
        <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType><controlURL>/upnp/control/RenderingControl1</controlURL></service>
        <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType><controlURL>upnp/control/AVTransport1</controlURL></service>
        </serviceList></device></root>"""

    @Test fun readsRendererDescription() {
        val renderer = Upnp.description(description, "http://192.168.1.20:9197/dmr")!!
        assertEquals("uuid:abc-123", renderer.udn)
        assertEquals("[TV] Living Room & Kitchen", renderer.name)
        assertEquals("http://192.168.1.20:9197/upnp/control/AVTransport1", renderer.avTransportUrl)
        assertEquals("http://192.168.1.20:9197/upnp/control/RenderingControl1", renderer.renderingControlUrl)
    }

    @Test fun ignoresDevicesWithoutAvTransport() {
        assertNull(Upnp.description(description.replace("AVTransport", "ConnectionManager"), "http://192.168.1.20/"))
    }

    @Test fun ssdpLocationMustBePrivate() {
        val reply = "HTTP/1.1 200 OK\r\nST: urn:schemas-upnp-org:device:MediaRenderer:1\r\nLocation: http://192.168.1.20:9197/dmr\r\n\r\n"
        assertEquals("http://192.168.1.20:9197/dmr", Upnp.location(reply))
        assertNull(Upnp.location(reply.replace("192.168.1.20", "8.8.8.8")))
        assertNull(Upnp.location("NOTIFY * HTTP/1.1\r\nLOCATION: http://192.168.1.2/\r\n"))
    }

    @Test fun envelopeAndMetadataAreEscaped() {
        val media = CastMedia("http://192.168.1.5:4000/a/media.mp4?x=1&y=2", "Tom & Jerry <1>", "video/mp4", MediaKind.VIDEO)
        val didl = Upnp.didl(media)
        assertTrue("Tom &amp; Jerry &lt;1&gt;" in didl)
        assertTrue("x=1&amp;y=2" in didl)
        val soap = Upnp.envelope(Upnp.AV_TRANSPORT, "SetAVTransportURI", listOf("InstanceID" to "0", "CurrentURIMetaData" to didl))
        // The metadata is XML inside XML, so it is escaped a second time.
        assertTrue("&lt;DIDL-Lite" in soap)
        assertTrue("<u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" in soap)
    }

    @Test fun parsesReplies() {
        val reply = "<s:Envelope><s:Body><u:GetPositionInfoResponse><TrackDuration>0:03:25</TrackDuration><RelTime>0:01:02.500</RelTime></u:GetPositionInfoResponse></s:Body></s:Envelope>"
        assertEquals(205_000, Upnp.parseTime(Upnp.value(reply, "TrackDuration")))
        assertEquals(62_000, Upnp.parseTime(Upnp.value(reply, "RelTime")))
        assertEquals(0, Upnp.parseTime("NOT_IMPLEMENTED"))
        assertEquals("1:01:01", Upnp.formatTime(3_661_000))
        assertEquals(PlaybackState.PAUSED, Upnp.transportState("PAUSED_PLAYBACK"))
        assertNull(Upnp.faultCode(reply))
        assertEquals(714, Upnp.faultCode("<s:Fault><detail><UPnPError><errorCode>714</errorCode></UPnPError></detail></s:Fault>"))
    }

    @Test fun rangeRequests() {
        assertEquals(ByteSpan(0, 99), HttpRange.parse("bytes=0-99", 1000))
        assertEquals(ByteSpan(500, 999), HttpRange.parse("bytes=500-", 1000))
        assertEquals(ByteSpan(900, 999), HttpRange.parse("bytes=-100", 1000))
        assertEquals(ByteSpan(10, 999), HttpRange.parse("bytes=10-5000", 1000))
        assertEquals(HttpRange.UNSATISFIABLE, HttpRange.parse("bytes=1000-", 1000))
        assertNull(HttpRange.parse(null, 1000))
        val head = HttpRange.responseHead("video/mp4", 1000, ByteSpan(0, 99), "DLNA.ORG_OP=01")
        assertTrue(head.startsWith("HTTP/1.1 206"))
        assertTrue("Content-Range: bytes 0-99/1000" in head)
        assertEquals(MediaRequest("GET", "/tok/media.mp4", "bytes=0-"), HttpRange.request(listOf("GET /tok/media.mp4 HTTP/1.1", "Host: x", "range: bytes=0-")))
        assertNull(HttpRange.request(listOf("POST / HTTP/1.1")))
    }

    @Test fun webLinks() {
        val media = webMedia(" https://cdn.example.com/live/index.m3u8?token=1 ")!!
        assertEquals("application/x-mpegURL", media.mime)
        assertEquals("index.m3u8", media.title)
        assertEquals(MediaKind.AUDIO, webMedia("http://x.com/song.mp3")!!.kind)
        assertEquals("video/mp4", webMedia("https://example.com/watch")!!.mime)
        assertNull(webMedia("ftp://example.com/a.mp4"))
        assertNull(webMedia("not a link"))
    }

    @Test fun mimeAndClock() {
        assertEquals("image/jpeg", mimeFor("IMG_0001.JPG"))
        assertEquals(MediaKind.PHOTO, kindFor("image/png"))
        assertNull(mimeFor("notes.txt"))
        assertEquals("1:05", formatClock(65_000))
        assertEquals("1:00:05", formatClock(3_605_000))
    }
}
