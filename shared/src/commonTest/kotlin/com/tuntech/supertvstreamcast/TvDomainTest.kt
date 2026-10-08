package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.domain.*
import kotlin.test.*

class TvDomainTest {
    private val parser=ParsePlaylistUseCase()
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
#EXTINF:-1 group-title="Private",Unsupported
file:///secret
https://example.com/a.m3u8
https://example.com/a.m3u8
""")
        assertEquals(1,channels.size)
        assertEquals("",channels.single().group)
        assertEquals("a.m3u8",channels.single().title)
    }
    @Test fun acceptsBomAndCrLf() {
        assertEquals(1,parser("\uFEFF#EXTM3U\r\n#EXTINF:-1,Live\r\nhttps://example.com/live\r\n").size)
    }
    @Test fun rejectsEmptyInvalidAndOversizeImports() {
        assertFailsWith<IllegalArgumentException>{parser("not m3u")}
        assertFailsWith<IllegalArgumentException>{parser("#EXTM3U\nfile:///private")}
        assertFailsWith<IllegalArgumentException>{parser("#EXTM3U\n"+"a".repeat(2_000_000))}
    }
    @Test fun rejectsOversizeChannelCountAndAcceptsHeaderAttributes() {
        assertEquals(1,parser("#EXTM3U x-tvg-url=\"https://example.com/guide.xml\"\nhttps://example.com/live").size)
        val playlist="#EXTM3U\n"+(1..5001).joinToString("\n") {"https://example.com/$it"}
        assertFailsWith<IllegalArgumentException>{parser(playlist)}
    }
    @Test fun onlyAcceptsPrivateLocalIpv4Targets() {
        listOf("192.168.1.10","10.0.0.1","172.16.1.1","172.31.255.254").forEach{assertTrue(isLocalIpv4(it),it)}
        listOf("127.0.0.1","8.8.8.8","172.32.0.1","192.168.1.256","192.168.01.1","192.168.1.1/sony","192.168.1.1:80","").forEach{assertFalse(isLocalIpv4(it),it)}
    }
}
