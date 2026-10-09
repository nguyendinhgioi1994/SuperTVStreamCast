package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.data.SonyBraviaAdapter
import com.tuntech.supertvstreamcast.data.DownloadException
import com.tuntech.supertvstreamcast.data.TvRepository
import com.tuntech.supertvstreamcast.domain.HlsStreamException
import com.tuntech.supertvstreamcast.domain.NotPlaylistException
import com.tuntech.supertvstreamcast.domain.RemoteKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TvRepositoryTest {
    @Test fun authenticatesAndUsesCodesReturnedByTv() = runTest {
        var calls=0
        val repository=SonyBraviaAdapter(HttpClient(MockEngine {request ->
            calls++
            if(calls!=2) assertEquals("session-secret",request.headers["X-Auth-PSK"])
            if(calls==2) {
                assertEquals(null,request.headers["X-Auth-PSK"])
                respond("""{"result":[{"productCategory":"tv","productName":"BRAVIA","modelName":"XR-55A80L"}]}""",HttpStatusCode.OK)
            } else if(calls==1) {
                assertEquals("/sony/system",request.url.encodedPath)
                respond("""{"result":[{"type":"IRCC"},[{"name":"Confirm","value":"AAAAAQAAAAEAAABlAw=="}]]}""",HttpStatusCode.OK)
            } else if(calls==3) {
                assertTrue((request.body as TextContent).text.contains("getNetworkSettings"))
                respond("""{"result":[[{"netif":"wlan0","hwAddr":"AA:BB:CC:00:11:22","ipAddrV4":"192.168.1.2"}]]}""",HttpStatusCode.OK)
            } else {
                assertEquals("/sony/ircc",request.url.encodedPath)
                assertTrue((request.body as TextContent).text.contains("AAAAAQAAAAEAAABlAw=="))
                assertEquals("\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"",request.headers["SOAPACTION"])
                respond("",HttpStatusCode.OK)
            }
        }))
        try {
            val session=repository.connect("192.168.1.2","session-secret")
            assertEquals("XR-55A80L",session.device.model)
            assertEquals("AA:BB:CC:00:11:22",session.device.mac)
            assertEquals(setOf(RemoteKey.OK),session.capabilities.keys)
            repository.send(RemoteKey.OK)
            assertEquals(4,calls)
            assertFailsWith<IllegalStateException>{repository.send(RemoteKey.HOME)}
            repository.disconnect()
            assertFailsWith<IllegalStateException>{repository.send(RemoteKey.OK)}
            assertEquals(4,calls)
        } finally {repository.disconnect()}
    }
    @Test fun rejectedAuthenticationNeverCreatesASession() = runTest {
        val repository=SonyBraviaAdapter(HttpClient(MockEngine {respond("",HttpStatusCode.Forbidden)}))
        assertFailsWith<IllegalStateException>{repository.connect("192.168.1.2","wrong")}
        assertFailsWith<IllegalStateException>{repository.send(RemoteKey.OK)}
    }
    @Test fun apiErrorsAndMissingCommandCapabilitiesFailConnection() = runTest {
        for(body in listOf("""{"error":[403,"Forbidden"]}""","""{"result":[{},[]]}""")) {
            val repository=SonyBraviaAdapter(HttpClient(MockEngine {respond(body,HttpStatusCode.OK)}))
            assertFailsWith<IllegalStateException>{repository.connect("192.168.1.2","key")}
        }
    }
    @Test fun playlistDownloadsStreamFollowRedirectsAndReportProgress() = runTest {
        val body="#EXTM3U\n"+(1..1200).joinToString("\n") {"#EXTINF:-1 group-title=\"G\",Channel $it\nhttps://example.com/$it.m3u8"}
        val repository=TvRepository(HttpClient(MockEngine {request ->
            if(request.url.encodedPath=="/short") respond("",HttpStatusCode.Found,headersOf(HttpHeaders.Location,"https://example.com/list.m3u"))
            else respond(body,HttpStatusCode.OK)
        }))
        try {
            val progress=mutableListOf<Int>()
            val library=repository.loadPlaylist(" https://example.com/short ") {progress+=it}
            assertEquals(1200,library.channels.size)
            assertEquals("Channel 1200",library.channels.last().title)
            assertTrue(500 in progress&&1000 in progress)
            assertEquals(1,repository.loadPlaylist("#EXTM3U\nhttps://example.com/pasted").channels.size)
        } finally {repository.close()}
    }
    @Test fun playlistDownloadFailuresAreToldApart() = runTest {
        suspend fun load(body: String,status: HttpStatusCode=HttpStatusCode.OK): Throwable {
            val repository=TvRepository(HttpClient(MockEngine {respond(body,status)}))
            return try {assertFails{repository.loadPlaylist("https://example.com/list.m3u")}} finally {repository.close()}
        }
        assertIs<DownloadException>(load("",HttpStatusCode.NotFound))
        assertIs<NotPlaylistException>(load("<html>blocked</html>"))
        assertIs<HlsStreamException>(load("#EXTM3U\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg.ts"))
        val offline=TvRepository(HttpClient(MockEngine {throw kotlinx.io.IOException("offline")}))
        assertFailsWith<DownloadException>{offline.loadPlaylist("https://example.com/list.m3u")}
    }
}
