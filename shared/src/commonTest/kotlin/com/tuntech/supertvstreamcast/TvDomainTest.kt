package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.domain.*
import kotlin.test.*

class TvDomainTest {
    private val parser={content: String -> ParsePlaylistUseCase()(content).channels}
    @Test fun preservesQuotedCommasAndGroups() {
        val channels=parser("""#EXTM3U
#EXTINF:-1 tvg-name="News, Today" group-title="News, World",My News
https://example.com/live.m3u8?token=a
""")
        assertEquals("My News",channels.single().title)
        assertEquals("News, World",channels.single().group)
        assertEquals("https://example.com/live.m3u8?token=a",channels.single().url)
    }
    @Test fun deduplicatesAndDropsUnsupportedSchemesWithoutLeakingMetadata() {
        val channels=parser("""#EXTM3U
#EXTINF:-1 group-title="Private" tvg-logo="https://example.com/secret.png",Unsupported
#EXTVLCOPT:http-user-agent=Secret
file:///secret
https://example.com/a.m3u8
https://example.com/a.m3u8
""")
        assertEquals(Channel("https://example.com/a.m3u8","a.m3u8",""),channels.single())
    }
    @Test fun acceptsBomAndCrLf() {
        assertEquals(1,parser("\uFEFF#EXTM3U\r\n#EXTINF:-1,Live\r\n https://example.com/live \r\n").size)
    }
    @Test fun readsProviderStylePlaylists() {
        val library=ParsePlaylistUseCase()("""#extm3u x-tvg-url="ftp://x, https://example.com/guide.xml.gz"
#EXTINF:-1 tvg-id=one.tv tvg-logo='https://example.com/one.png' user-agent="Attr/1",One
#EXTVLCOPT:http-user-agent=Vlc/2
#EXTVLCOPT:http-referrer=https://example.com/ref
https://example.com/1
#EXTINF:-1 tvg-name="Two HD" tvg-logo="logo.png"
#EXTGRP:Sports
http://example.com/2|User-Agent=Pipe%2F3&Referer=https://example.com/
#EXTINF:0,Three
https://example.com/3
""")
        assertEquals("https://example.com/guide.xml.gz",library.guideUrl)
        val (one,two,three)=library.channels
        assertEquals(Channel("https://example.com/1","One","","one.tv","https://example.com/one.png","Vlc/2","https://example.com/ref"),one)
        assertEquals(Channel("http://example.com/2","Two HD","Sports","","","Pipe/3","https://example.com/"),two)
        assertEquals(Channel("https://example.com/3","Three",""),three)
    }
    @Test fun rejectsContentThatIsNotAChannelList() {
        listOf("not m3u","#EXTM3U\nfile:///private","<html><body>404</body></html>","{\"error\":1}","https://example.com/a\nhttps://example.com/b").forEach {
            assertFailsWith<NotPlaylistException>(it){parser(it)}
        }
        assertFailsWith<HlsStreamException>{parser("#EXTM3U\n#EXT-X-VERSION:3\n#EXTINF:6.0,\nsegment1.ts")}
    }
    @Test fun truncatesAtTheLibraryLimitInsteadOfFailing() {
        val playlist="#EXTM3U\n"+(1..12).joinToString("\n") {"https://example.com/$it"}
        val library=ParsePlaylistUseCase(limit=10)(playlist)
        assertEquals(10,library.channels.size)
        assertTrue(library.truncated)
        assertFalse(ParsePlaylistUseCase(limit=12)(playlist).truncated)
    }
    @Test fun splitsStreamedBytesIntoLinesAcrossChunks() {
        val bytes="#EXTM3U\r\n#EXTINF:-1,Kênh Một\nhttps://example.com/1\n#EXTINF:-1,Two\nhttps://example.com/2".encodeToByteArray()
        for(step in listOf(1,3,7,bytes.size)) {
            val lines=mutableListOf<String>()
            val splitter=LineSplitter()
            var offset=0
            while(offset<bytes.size) {
                val chunk=bytes.copyOfRange(offset,minOf(bytes.size,offset+step))
                assertTrue(splitter.feed(chunk,chunk.size){lines+=it.trim();true})
                offset+=step
            }
            splitter.finish{lines+=it;true}
            assertEquals(listOf("#EXTM3U","#EXTINF:-1,Kênh Một","https://example.com/1","#EXTINF:-1,Two","https://example.com/2"),lines,"step $step")
        }
        assertEquals(listOf("Kênh Một","Two"),parsePlaylistBytes(bytes).channels.map{it.title})
        assertFalse(LineSplitter().feed(bytes,bytes.size){false})
        assertFailsWith<NotPlaylistException>{LineSplitter(maxLine=8).feed(ByteArray(9){65},9){true}}
    }
    @Test fun decodesUtf8SplitAcrossChunks() {
        val text="Việt 日本 😀 end"
        val bytes=text.encodeToByteArray()
        for(step in 1..5) {
            val chunks=Utf8Chunks()
            val out=StringBuilder()
            var offset=0
            while(offset<bytes.size) {
                val chunk=bytes.copyOfRange(offset,minOf(bytes.size,offset+step))
                out.append(chunks.decode(chunk,chunk.size))
                offset+=step
            }
            assertEquals(text,out.toString(),"step $step")
        }
        assertEquals("a b/c%zz",decodeUrlPart("a%20b%2Fc%zz"))
    }
    @Test fun onlyAcceptsPrivateLocalIpv4Targets() {
        listOf("192.168.1.10","10.0.0.1","172.16.1.1","172.31.255.254").forEach{assertTrue(isLocalIpv4(it),it)}
        listOf("127.0.0.1","8.8.8.8","172.32.0.1","192.168.1.256","192.168.01.1","192.168.1.1/sony","192.168.1.1:80","").forEach{assertFalse(isLocalIpv4(it),it)}
    }
}
