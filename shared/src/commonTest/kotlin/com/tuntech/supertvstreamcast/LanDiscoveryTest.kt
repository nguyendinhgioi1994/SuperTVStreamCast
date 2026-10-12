package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.domain.*
import kotlin.test.*

class LanDiscoveryTest {
    @Test fun searchRequestNamesTargetAndGroup() {
        val request = Ssdp.search("urn:dial-multiscreen-org:service:dial:1")
        assertTrue(request.startsWith("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n"))
        assertTrue("\r\nMAN: \"ssdp:discover\"\r\n" in request)
        assertTrue("\r\nST: urn:dial-multiscreen-org:service:dial:1\r\n" in request)
        assertTrue(request.endsWith("\r\n\r\n"))
    }
    @Test fun responseUsesPrivateLocationHostElseSender() {
        val reply = "HTTP/1.1 200 OK\r\nCache-Control: max-age=1800\r\nlocation: http://192.168.1.40:52235/rcr/RemoteControlReceiver.xml\r\n" +
            "Server: SHP, UPnP/1.0, Samsung UPnP SDK/1.0\r\nst: urn:samsung.com:device:RemoteControlReceiver:1\r\n\r\n"
        assertEquals(LanAnnouncement("192.168.1.40", "ssdp:urn:samsung.com:device:RemoteControlReceiver:1", "SHP, UPnP/1.0, Samsung UPnP SDK/1.0"),
            Ssdp.announcement("192.168.1.41", reply))
        val publicLocation = reply.replace("192.168.1.40", "52.1.2.3")
        assertEquals("192.168.1.41", Ssdp.announcement("192.168.1.41", publicLocation)?.host)
    }
    @Test fun ignoresNotificationsErrorsAndPublicSenders() {
        val reply = "HTTP/1.1 200 OK\nST: upnp:rootdevice\n\n"
        assertNotNull(Ssdp.announcement("10.1.2.3", reply))
        assertNull(Ssdp.announcement("8.8.8.8", reply))
        assertNull(Ssdp.announcement("10.1.2.3", "NOTIFY * HTTP/1.1\r\nNT: upnp:rootdevice\r\n\r\n"))
        assertNull(Ssdp.announcement("10.1.2.3", "HTTP/1.1 200 OK\r\nLOCATION: http://10.1.2.3/\r\n\r\n"))
        assertNull(Ssdp.announcement("10.1.2.3", ""))
    }
    @Test fun devicesOrderByFullAddressThenBrand() {
        val devices = listOf(
            TvDevice(TvBrand.SONY, "192.168.1.20", "a"), TvDevice(TvBrand.GOOGLE, "10.0.0.9", "b"),
            TvDevice(TvBrand.GOOGLE, "192.168.1.20", "c"), TvDevice(TvBrand.LG, "192.168.1.3", "d"),
        )
        assertEquals(listOf("b", "d", "a", "c"), devices.sortedWith(deviceOrder).map { it.name })
    }
}
