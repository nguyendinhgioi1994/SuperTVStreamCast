package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.data.DownloadException
import com.tuntech.supertvstreamcast.data.TvRepository
import com.tuntech.supertvstreamcast.data.parseGuideBytes
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
        assertEquals(XtreamLogin("http://host.example:8080","user name","p@ss"),
            XtreamApi.credentials("http://host.example:8080/get.php?username=user+name&password=p%40ss&type=m3u_plus"))
        assertNull(XtreamApi.credentials("http://host.example:8080"))
        assertNull(XtreamApi.credentials("http://host.example/get.php?username=a"))
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
        val streams="""[{"stream_id":7,"name":"Seven {\"HD\"} ]","category_id":"1","epg_channel_id":"seven.tv","stream_icon":"https://example.com/7.png","extra":{"a":[1,{"b":"}"}]}},
            {"stream_id":7,"name":"Duplicate"},{"stream_id":"../x","name":"Bad"},{"name":"No id"},{"stream_id":8,"name":"","category_id":null}]"""
        val channels=XtreamApi.parseLive(login,"m3u8",categories,streams)
        assertEquals(listOf("Seven {\"HD\"} ]","8"),channels.map{it.title})
        assertEquals("https://example.com/7.png",channels[0].logo)
        // The same listing fed one character at a time gives the same library.
        val reader=XtreamApi.LiveReader(login,"m3u8",XtreamApi.categories(categories))
        streams.forEach {assertTrue(reader.feed(it.toString()))}
        assertEquals(channels,reader.finish().channels)
        assertFailsWith<IllegalArgumentException>{XtreamApi.parseLive(login,"ts","[]","""{"user_info":{"auth":0}}""")}
        assertEquals("News",channels[0].group)
        assertEquals("seven.tv",channels[0].guideId)
        assertEquals("",channels[1].group)
        assertEquals(1,XtreamApi.parseLive(login,"ts","[]",streams,limit=1).size)
        assertTrue(XtreamApi.LiveReader(login,"ts",emptyMap(),1).apply{feed(streams)}.finish().truncated)
        assertFailsWith<IllegalArgumentException>{XtreamApi.parseLive(login,"ts","[]","[]")}
    }
    @Test fun repositoryVerifiesAccountThenListsLiveMoviesAndSeries() = runTest {
        val paths=mutableListOf<String>()
        val repository=TvRepository(HttpClient(MockEngine {request ->
            val action=request.url.parameters["action"]
            paths+=action ?: "account"
            when(action) {
                null -> respond("""{"user_info":{"auth":1,"status":"Active","exp_date":"1800000000","max_connections":"2","active_cons":1,"allowed_output_formats":["ts"]}}""",HttpStatusCode.OK)
                "get_live_categories" -> respond("""[{"category_id":"2","category_name":"Sport"}]""",HttpStatusCode.OK)
                "get_live_streams" -> respond("""[{"stream_id":1,"name":"One","category_id":"2","epg_channel_id":"one.tv"}]""",HttpStatusCode.OK)
                "get_vod_categories" -> respond("""[{"category_id":"9","category_name":"Drama"}]""",HttpStatusCode.OK)
                "get_vod_streams" -> respond("""[{"stream_id":5,"name":"Film","category_id":"9","stream_icon":"https://example.com/f.jpg","container_extension":"mkv"},{"stream_id":6,"name":"Odd","container_extension":"../x"}]""",HttpStatusCode.OK)
                "get_series_categories" -> respond("",HttpStatusCode.InternalServerError)
                "get_series" -> respond("""[{"series_id":3,"name":"Show","cover":"https://example.com/s.jpg","category_id":"1"}]""",HttpStatusCode.OK)
                else -> respond("",HttpStatusCode.NotFound)
            }
        }))
        val library=repository.loadXtream(login)
        assertEquals(listOf("account","get_live_categories","get_live_streams","get_vod_categories","get_vod_streams","get_series_categories","get_series"),paths)
        val (live,film,odd,show)=library.channels
        assertEquals(Channel("http://panel.example.com:8080/live/user%20name/p%40ss%2Fword/1.ts","One","Sport","one.tv"),live)
        assertEquals(Channel("http://panel.example.com:8080/movie/user%20name/p%40ss%2Fword/5.mkv","Film","Drama",logo="https://example.com/f.jpg",kind=ContentKind.MOVIE),film)
        assertTrue(odd.url.endsWith("/6.mp4"))
        assertEquals(Channel("http://panel.example.com:8080/series/3","Show","",logo="https://example.com/s.jpg",kind=ContentKind.SERIES),show)
        assertEquals("3",XtreamApi.seriesId(show))
        assertEquals(XtreamAccount("Active",1_800_000_000,2,1,false,"ts"),library.account)
        assertEquals(XtreamApi.guideUrl(login),library.guideUrl)
        assertFalse(library.truncated)

        val rejected=TvRepository(HttpClient(MockEngine {respond("""{"user_info":{"auth":0}}""",HttpStatusCode.OK)}))
        assertFailsWith<XtreamAuthException>{rejected.loadXtream(login)}
        // A panel with movies only still imports; one that lists nothing reports the live listing's failure.
        val vodOnly=TvRepository(HttpClient(MockEngine {request -> when(request.url.parameters["action"]) {
            null -> respond("""{"user_info":{"auth":1}}""",HttpStatusCode.OK)
            "get_vod_streams" -> respond("""[{"stream_id":5,"name":"Film"}]""",HttpStatusCode.OK)
            else -> respond("[]",HttpStatusCode.OK)
        }}))
        assertEquals(listOf(ContentKind.MOVIE),vodOnly.loadXtream(login).channels.map{it.kind})
        val empty=TvRepository(HttpClient(MockEngine {request ->
            if(request.url.parameters["action"]==null) respond("""{"user_info":{"auth":1}}""",HttpStatusCode.OK) else respond("",HttpStatusCode.BadGateway)
        }))
        assertFailsWith<DownloadException>{empty.loadXtream(login)}
    }
    @Test fun readsSeriesEpisodesFromObjectOrArrayLayouts() = runTest {
        val series=Channel(XtreamApi.seriesKey(login,"3"),"Show","",logo="https://example.com/s.jpg",kind=ContentKind.SERIES)
        val keyed="""{"info":{"plot":"A plot","genre":"Drama","releaseDate":"2024","rating":"7.5","cover":"https://example.com/c.jpg"},
            "episodes":{"2":[{"id":"21","episode_num":1,"title":"Return","season":2,"container_extension":"mkv","info":{"duration_secs":"1800","plot":"p","movie_image":"https://example.com/e.jpg"}}],
            "1":[{"id":"12","episode_num":"2","title":"Show S1E2","season":1},{"id":11,"episode_num":1,"title":"","season":1},{"title":"No id"},{"id":"../x"}]}}"""
        val info=XtreamApi.parseSeries(login,keyed)
        assertEquals(listOf(1,2),info.seasons)
        assertEquals(listOf("11.mp4","12.mp4","21.mkv"),info.episodes.map{it.url.substringAfterLast('/')})
        assertEquals("http://panel.example.com:8080/series/user%20name/p%40ss%2Fword/21.mkv",info.episodes.last().url)
        assertEquals(1800,info.episodes.last().durationSecs)
        assertEquals("A plot" to "https://example.com/c.jpg",info.plot to info.cover)
        assertEquals(listOf("Show S1E1","Show S1E2","S2E1 · Return"),info.episodes.map{it.toChannel(series).title})
        val episode=info.episodes.first().toChannel(series)
        assertEquals(ContentKind.MOVIE to "Show",episode.kind to episode.group)
        assertEquals("https://example.com/s.jpg",episode.logo)
        assertEquals("https://example.com/e.jpg",info.episodes.last().toChannel(series).logo)
        val array=XtreamApi.parseSeries(login,"""{"episodes":[[{"id":1,"episode_num":1,"season":1}],[{"id":2,"episode_num":1,"season":2}]]}""")
        assertEquals(listOf(1,2),array.seasons)
        listOf("""{"episodes":{}}""","""{"info":{}}""","[]").forEach {assertFailsWith<IllegalArgumentException>(it){XtreamApi.parseSeries(login,it)}}
        val repository=TvRepository(HttpClient(MockEngine {request ->
            assertEquals("get_series_info" to "3",request.url.parameters["action"] to request.url.parameters["series_id"])
            respond(keyed,HttpStatusCode.OK)
        }))
        assertEquals(info,repository.loadSeries(login,series))
    }
    @Test fun readsPlaylistGuideIdsAndHeaderGuideUrl() {
        val content="#EXTM3U url-tvg=\"ftp://x, https://example.com/guide.xml\"\n#EXTINF:-1 tvg-id=\"one.tv\" group-title=\"News\",One\nhttps://example.com/1\n"
        val library=ParsePlaylistUseCase()(content)
        assertEquals("one.tv",library.channels.single().guideId)
        assertEquals("https://example.com/guide.xml",library.guideUrl)
        assertEquals("",ParsePlaylistUseCase()("#EXTM3U\nhttps://example.com/1").guideUrl)
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
        // Fed whole or in tiny chunks, the streaming reader gives the same guide.
        val chunked=GuideParser(channels,now).apply{xml.chunked(7).forEach(::feed)}.finish()
        assertEquals(ParseGuideUseCase()(xml,channels,now),chunked)
        assertEquals(chunked,parseGuideBytes(xml.encodeToByteArray(),channels,now))
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
        assertFailsWith<IllegalArgumentException>{ParseGuideUseCase()("<html><programme channel=\"x\"/></html>",channels,0)}
        assertTrue(isGzip(byteArrayOf(0x1F,0x8B.toByte(),8)))
        assertFalse(isGzip("<tv>".encodeToByteArray()))
        assertFailsWith<IllegalArgumentException>{parseGuideBytes(byteArrayOf(0x1F,0x8B.toByte(),8,0,1,2,3),channels,0)}
    }
    @Test fun guideDownloadReadsGzipAndRejectsOversize() = runTest {
        val now=parseXmltvTime("20261008120000 +0000")!!
        val channels=listOf(Channel("https://e.com/1","One","","one.tv"))
        val zipped=TvRepository(HttpClient(MockEngine {respond(GZIP_GUIDE,HttpStatusCode.OK)}))
        assertEquals("News",zipped.loadGuide("https://example.com/guide.xml.gz",channels,now).getValue("https://e.com/1").single().title)
        val big=TvRepository(HttpClient(MockEngine {respond("a".repeat(101),HttpStatusCode.OK)}))
        assertFailsWith<IllegalStateException>{big.loadText("https://example.com/guide.xml",100)}
        assertFailsWith<IllegalArgumentException>{big.loadText("file:///guide.xml",100)}
        val missing=TvRepository(HttpClient(MockEngine {respond("",HttpStatusCode.NotFound)}))
        assertFailsWith<DownloadException>{missing.loadGuide("https://example.com/guide.xml",channels,now)}
    }
    /** gzip of `<tv><programme start="20261008113000 +0000" stop="20261008123000 +0000" channel="one.tv"><title>News</title></programme></tv>`. */
    private val GZIP_GUIDE="1f8b08000000000002ffb32929b3b32928ca4f2f4acccd4d55282e492c2ab15532323032333430b030343436303050d00612064a40c9fc0224392364b9e48cc4bcbcd41c5ba5fcbc54bd9232253b9b92cc929c543bbfd4f2621b7d08db461f6e11900db4180018f6a26b7d000000".chunked(2).map{it.toInt(16).toByte()}.toByteArray()
}
