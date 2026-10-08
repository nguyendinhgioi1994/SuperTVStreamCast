package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.data.TvRepository
import com.tuntech.supertvstreamcast.domain.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class IptvSourcesTest {
    private val login=XtreamLogin("http://panel.example.com:8080","user name","p@ss/word")

    @Test fun normalizesXtreamServerInput() {
        assertEquals("http://host.example:8080",XtreamApi.normalizeServer("host.example:8080/"))
        assertEquals("https://host.example",XtreamApi.normalizeServer(" HTTPS://host.example/player_api.php?username=a&password=b "))
        assertEquals("http://host.example/panel",XtreamApi.normalizeServer("http://host.example/panel/get.php?username=a"))
        listOf("","ftp://host","http://","http://bad host").forEach {assertNull(XtreamApi.normalizeServer(it),it)}
        assertNull(XtreamApi.login("host","","secret"))
        assertNull(XtreamApi.login("host","user",""))
    }
    @Test fun encodesCredentialsInXtreamUrlsAndHidesThemFromToString() {
        assertEquals("http://panel.example.com:8080/player_api.php?username=user%20name&password=p%40ss%2Fword&action=get_live_streams",XtreamApi.streamsUrl(login))
        assertEquals("http://panel.example.com:8080/live/user%20name/p%40ss%2Fword/42.m3u8",XtreamApi.streamUrl(login,"42","m3u8"))
        assertEquals("%C3%A1%20~",encodeUrlPart("á ~"))
        assertFalse(login.toString().contains("p@ss"))
    }
    @Test fun acceptsOnlyAuthenticatedActiveAccounts() {
        assertEquals("m3u8",XtreamApi.parseAccount("""{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["m3u8","ts"]}}"""))
        assertEquals("ts",XtreamApi.parseAccount("""{"user_info":{"auth":"1","status":"Active","allowed_output_formats":["ts"]}}"""))
        assertFailsWith<XtreamAuthException>{XtreamApi.parseAccount("""{"user_info":{"auth":0}}""")}
        assertFailsWith<XtreamAuthException>{XtreamApi.parseAccount("""{"user_info":{"auth":1,"status":"Expired"}}""")}
        assertFailsWith<XtreamAuthException>{XtreamApi.parseAccount("""{"server_info":{}}""")}
        assertFails{XtreamApi.parseAccount("<html>")}
    }
    @Test fun mapsLiveStreamsToCategoriesAndCaps() {
        val categories="""[{"category_id":"1","category_name":"News"}]"""
        val streams="""[{"stream_id":7,"name":"Seven","category_id":"1","epg_channel_id":"seven.tv"},
            {"stream_id":7,"name":"Duplicate"},{"stream_id":"../x","name":"Bad"},{"name":"No id"},{"stream_id":8,"name":"","category_id":null}]"""
        val channels=XtreamApi.parseLive(login,"m3u8",categories,streams)
        assertEquals(listOf("Seven","8"),channels.map{it.title})
        assertEquals("News",channels[0].group)
        assertEquals("seven.tv",channels[0].guideId)
        assertEquals("",channels[1].group)
        assertEquals(1,XtreamApi.parseLive(login,"ts","[]",streams,limit=1).size)
        assertFailsWith<IllegalArgumentException>{XtreamApi.parseLive(login,"ts","[]","[]")}
    }
    @Test fun repositoryVerifiesAccountBeforeListing() = runTest {
        val paths=mutableListOf<String>()
        val repository=TvRepository(HttpClient(MockEngine {request ->
            val action=request.url.parameters["action"]
            paths+=action ?: "account"
            when(action) {
                null -> respond("""{"user_info":{"auth":1,"status":"Active","allowed_output_formats":["m3u8"]}}""",HttpStatusCode.OK)
                "get_live_categories" -> respond("""[{"category_id":"2","category_name":"Sport"}]""",HttpStatusCode.OK)
                else -> respond("""[{"stream_id":1,"name":"One","category_id":"2"}]""",HttpStatusCode.OK)
            }
        }))
        val library=repository.loadXtream(login)
        assertEquals(listOf("account","get_live_categories","get_live_streams"),paths)
        assertEquals("Sport",library.channels.single().group)
        assertEquals(XtreamApi.guideUrl(login),library.guideUrl)
        assertFalse(library.truncated)

        val rejected=TvRepository(HttpClient(MockEngine {respond("""{"user_info":{"auth":0}}""",HttpStatusCode.OK)}))
        assertFailsWith<XtreamAuthException>{rejected.loadXtream(login)}
    }
    @Test fun readsPlaylistGuideIdsAndHeaderGuideUrl() {
        val content="#EXTM3U url-tvg=\"ftp://x, https://example.com/guide.xml\"\n#EXTINF:-1 tvg-id=\"one.tv\" group-title=\"News\",One\nhttps://example.com/1\n"
        assertEquals("one.tv",ParsePlaylistUseCase()(content).single().guideId)
        assertEquals("https://example.com/guide.xml",playlistGuideUrl(content))
        assertNull(playlistGuideUrl("#EXTM3U\nhttps://example.com/1"))
    }
    @Test fun singleStreamNeedsHttpUrl() {
        assertEquals("live.m3u8",singleStream(" https://example.com/live.m3u8?t=1 ","")?.title)
        assertEquals("Match",singleStream("http://example.com/a","Match")?.title)
        listOf("rtmp://example.com/a","file:///a","example.com/a","").forEach {assertNull(singleStream(it,""),it)}
    }
    @Test fun parsesXmltvTimesWithOffsets() {
        assertEquals(0L,parseXmltvTime("19700101000000 +0000"))
        assertEquals(1_791_460_800L,parseXmltvTime("20261008120000 +0000"))
        assertEquals(1_791_460_800L-7*3600,parseXmltvTime("20261008120000 +0700"))
        assertEquals(1_791_460_800L+3600,parseXmltvTime("202610081200 -0100"))
        assertNull(parseXmltvTime("20261308120000"))
        assertNull(parseXmltvTime("today"))
    }
    @Test fun matchesGuideByIdAndNameAndKeepsUpcomingOnly() {
        val now=parseXmltvTime("20261008120000 +0000")!!
        val channels=listOf(Channel("https://e.com/1","One","","one.tv"),Channel("https://e.com/2","Two HD",""),Channel("https://e.com/3","Three",""))
        val xml="""<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="one.tv"><display-name>Channel One</display-name></channel>
  <channel id="c2"><display-name lang="en">Two-HD</display-name></channel>
  <programme start="20261008100000 +0000" stop="20261008110000 +0000" channel="one.tv"><title>Ended</title></programme>
  <programme start="20261008113000 +0000" stop="20261008123000 +0000" channel="one.tv"><title lang="en">News &amp; Weather</title><desc>Live</desc></programme>
  <programme start="20261008123000 +0000" stop="20261008130000 +0000" channel="one.tv"><title><![CDATA[Late <Show>]]></title></programme>
  <programme start='20261008120000 +0000' stop='20261008140000 +0000' channel='c2'><title>Film &#233;</title></programme>
  <programme start="20261020120000 +0000" stop="20261020130000 +0000" channel="c2"><title>Too far</title></programme>
  <programme start="20261008120000 +0000" stop="20261008130000 +0000" channel="other"><title>Unmatched</title></programme>
</tv>"""
        val guide=ParseGuideUseCase()(xml,channels,now)
        assertEquals(setOf("https://e.com/1","https://e.com/2"),guide.keys)
        assertEquals(listOf("News & Weather","Late <Show>"),guide.getValue("https://e.com/1").map{it.title})
        assertEquals("Live",guide.getValue("https://e.com/1")[0].description)
        assertEquals(listOf("Film é"),guide.getValue("https://e.com/2").map{it.title})
        val next=upcoming(guide["https://e.com/1"],now)
        assertTrue(isAiring(next[0],now))
        assertFalse(isAiring(next[1],now))
        assertTrue(upcoming(null,now).isEmpty())
    }
    @Test fun rejectsNonXmltvAndUnmatchedGuides() {
        val channels=listOf(Channel("https://e.com/1","One",""))
        assertFailsWith<IllegalArgumentException>{ParseGuideUseCase()("#EXTM3U",channels,0)}
        assertFailsWith<IllegalArgumentException>{ParseGuideUseCase()("<tv></tv>",channels,0)}
        assertTrue(isGzip(byteArrayOf(0x1F,0x8B.toByte(),8)))
        assertFalse(isGzip("<tv>".encodeToByteArray()))
    }
    @Test fun guideDownloadRejectsGzipAndOversize() = runTest {
        val gzip=TvRepository(HttpClient(MockEngine {respond(byteArrayOf(0x1F,0x8B.toByte(),8,0),HttpStatusCode.OK)}))
        assertFailsWith<CompressedGuideException>{gzip.loadText("https://example.com/guide.xml.gz",GUIDE_MAX_BYTES)}
        val big=TvRepository(HttpClient(MockEngine {respond("a".repeat(101),HttpStatusCode.OK)}))
        assertFailsWith<IllegalStateException>{big.loadText("https://example.com/guide.xml",100)}
        assertFailsWith<IllegalArgumentException>{big.loadText("file:///guide.xml",100)}
    }
}
