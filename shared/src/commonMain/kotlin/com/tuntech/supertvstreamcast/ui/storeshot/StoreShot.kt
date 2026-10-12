package com.tuntech.supertvstreamcast.ui.storeshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.tuntech.common.util.AppEnvironment
import com.tuntech.common.widget.dialog.ModalController
import com.tuntech.supertvstreamcast.theme.TvAdTheme
import com.tuntech.supertvstreamcast.ui.paywall.PaywallScreen
import com.tuntech.supertvstreamcast.domain.Channel
import com.tuntech.supertvstreamcast.domain.ContentKind
import com.tuntech.supertvstreamcast.domain.Feature
import com.tuntech.supertvstreamcast.domain.IptvSource
import com.tuntech.supertvstreamcast.domain.LibraryEntry
import com.tuntech.supertvstreamcast.domain.Programme
import com.tuntech.supertvstreamcast.domain.Reminder
import com.tuntech.supertvstreamcast.domain.reminderId
import com.tuntech.supertvstreamcast.domain.RemoteCapabilities
import com.tuntech.supertvstreamcast.domain.RemoteKey
import com.tuntech.supertvstreamcast.domain.SourceType
import com.tuntech.supertvstreamcast.domain.TvBrand
import com.tuntech.supertvstreamcast.domain.TvDevice
import com.tuntech.supertvstreamcast.theme.TvTheme
import com.tuntech.supertvstreamcast.theme.TvThemeMode
import com.tuntech.supertvstreamcast.ui.TvActions
import com.tuntech.supertvstreamcast.ui.TvScaffold
import com.tuntech.supertvstreamcast.ui.TvUiState
import com.tuntech.supertvstreamcast.ui.epochSeconds
import com.tuntech.supertvstreamcast.ui.cast.isIos
import com.tuntech.supertvstreamcast.ui.iptv.IptvScreen
import com.tuntech.supertvstreamcast.ui.iptv.IptvUiState
import com.tuntech.supertvstreamcast.ui.iptv.PlayerState

/**
 * App Store screenshot scenes. Debug builds only: the iOS entry point opens one of these instead of
 * the app when launched with `-StoreShot <name>`. Each scene is the real tab UI ([TvScaffold]) fed
 * with fixed sample state, so screenshots need no TV, account or playlist. The sample channels and
 * artwork are served from the machine taking the screenshots ([assets], e.g. `http://127.0.0.1:8765`);
 * none of it ships with the app.
 */
enum class StoreShot { HOME, REMOTE, IPTV, CHANNELS, GUIDE, PLAYER, MIRROR, SETTINGS,
    /** The player on the live sports channel. */
    SPORTS,
    /** The Movies tab of the demo playlist as a poster grid. */
    MOVIES,
    /** The real paywall with the live store products, for the in-app purchase review screenshot. */
    PAYWALL;
    companion object {
        fun parse(value: String?): StoreShot? = entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

@Composable
fun StoreShotApp(shot: StoreShot, assets: String, theme: TvThemeMode = TvThemeMode.DARK, brand: TvBrand = TvBrand.SAMSUNG) {
    val now = remember { epochSeconds() }
    val demo = remember(assets, brand) { StoreShotDemo(assets, now, brand) }
    AppEnvironment {
        TvTheme(theme) {
            if (shot == StoreShot.PAYWALL) TvAdTheme { ModalController { PaywallScreen(isInitial = false, source = "store_shot") } }
            // Android shows the Cast settings steps only when a share action exists, as in the app.
            else TvScaffold(demo.tv(shot), TvActions(share = if (isIos) null else ({})), demo.iptv(shot))
        }
    }
}

internal class StoreShotDemo(private val assets: String, private val now: Long, private val brand: TvBrand = TvBrand.SAMSUNG) {
    private val device = when (brand) {
        TvBrand.LG -> TvDevice(TvBrand.LG, "192.168.1.31", "Bedroom TV", "OLED55C4")
        TvBrand.SONY -> TvDevice(TvBrand.SONY, "192.168.1.42", "BRAVIA 8", "K-65XR80")
        TvBrand.GOOGLE -> TvDevice(TvBrand.GOOGLE, "192.168.1.57", "Google TV Streamer", "GZRNL")
        else -> TvDevice(TvBrand.SAMSUNG, "192.168.1.24", "Living Room TV", "QN65S90D")
    }

    fun tv(shot: StoreShot) = TvUiState(
        brand = device.brand,
        tab = when (shot) {
            StoreShot.HOME -> Feature.HOME
            StoreShot.REMOTE -> Feature.REMOTE
            StoreShot.MIRROR -> Feature.MIRROR
            StoreShot.SETTINGS -> Feature.SETTINGS
            else -> Feature.IPTV
        },
        connected = true, device = device, lastHost = device.host,
        capabilities = RemoteCapabilities(keys = RemoteKey.entries.toSet(), text = true, apps = true, pointer = true),
    )

    private val live = listOf(
        Channel("$assets/football.m3u8", "Arena Sports 1", "Sports", guideId = "Arena Sports 1", logo = "$assets/logo_sport.png"),
        live("World News 24", "News", "news"), live("City Sports HD", "Sports", "sport"),
        live("Cinema Max", "Movies", "movie"), live("Kids Planet", "Kids", "kids"),
        live("Pop Hits TV", "Music", "music"), live("Wild Earth", "Documentary", "nature"),
        live("Chef's Table TV", "Lifestyle", "cook"), live("Travel Now", "Lifestyle", "travel"),
        live("Morning Live", "News", "news"), live("Arena Sports 2", "Sports", "sport"),
    )
    private val movies = listOf("Red Dunes" to 1, "Lantern Forest" to 2, "Neon Rain" to 3, "The Big Wave" to 4, "Aurora Ridge" to 5, "Canyon Run" to 6,
        "Koi Skies" to 7, "Nebula Run" to 8, "Seaside Summer" to 9)
        .map { (title, i) -> Channel("$assets/video.m3u8?m=$i", title, "Movies", logo = "$assets/poster_$i.png", kind = ContentKind.MOVIE) }

    private fun live(title: String, group: String, logo: String) =
        Channel("$assets/video.m3u8?c=${title.hashCode()}", title, group, guideId = title, logo = "$assets/logo_$logo.png")

    private val playlist = IptvSource("demo", SourceType.PLAYLIST, "My Family TV", "https://iptv.example.com/family.m3u",
        addedAt = now - 86_400, syncedAt = now - 3_600, channelCount = live.size + movies.size)
    private val sources = listOf(
        playlist,
        IptvSource("xtream", SourceType.XTREAM, "Home Provider", "https://provider.example.com", "demo", "demo",
            addedAt = now - 86_400 * 3, syncedAt = now - 7_200, channelCount = 1_284),
        IptvSource("file", SourceType.FILE, "Sports pack", addedAt = now - 86_400 * 5, syncedAt = now - 86_400 * 5, channelCount = 86),
    )

    private val entries = listOf(
        LibraryEntry(movies[0], playlist.id, watchedAt = now - 600, positionMs = 2_700_000, durationMs = 6_300_000),
        LibraryEntry(movies[2], playlist.id, watchedAt = now - 5_000, positionMs = 1_200_000, durationMs = 5_800_000),
        LibraryEntry(movies[3], playlist.id, watchedAt = now - 9_000, positionMs = 4_000_000, durationMs = 6_000_000),
        LibraryEntry(movies[1], playlist.id, favorite = true),
        LibraryEntry(movies[4], playlist.id, favorite = true),
        LibraryEntry(movies[5], playlist.id, favorite = true),
        LibraryEntry(live[0], playlist.id, favorite = true, watchedAt = now - 20_000),
    )

    /** Fictional fixtures and titles for the headline channels, so the guide shows the match being played and the premieres. */
    private val fixtures = mapOf(
        "Arena Sports 1" to listOf("Pre-match Show", "Red Lions vs Blue Hawks", "Golden Eagles vs Silver Wolves", "Post-match Analysis", "Goals of the Week",
            "Matchday Live", "Champions Recap", "Late Kick-off", "Classic Matches", "Football Tonight"),
        "City Sports HD" to listOf("Matchday Live", "Silver Wolves vs Green Foxes", "Tennis Highlights", "Night Racing", "Champions Recap",
            "Basketball Tonight", "Goals of the Week", "Late Show", "Classic Matches", "Sports Desk"),
        "Arena Sports 2" to listOf("Goals of the Week", "Blue Hawks vs Golden Eagles", "Studio Talk", "Cup Draw Live", "Matchday Live",
            "Night Racing", "Champions Recap", "Late Kick-off", "Classic Matches", "Sports Desk"),
        "Cinema Max" to listOf("Red Dunes", "Premiere: Neon Rain", "Koi Skies", "Nebula Run", "Aurora Ridge",
            "The Big Wave", "Canyon Run", "Seaside Summer", "Lantern Forest", "Late Movie"),
        "Wild Earth" to listOf("Ocean Giants", "Hidden Waterfalls", "Island Hopping", "Mountain Life", "Rainforest Secrets",
            "Ocean Giants", "Wild Islands", "Night Safari", "Mountain Life", "Planet Blue"),
        "Kids Planet" to listOf("Cartoon Parade", "Lantern Forest", "Koi Skies", "Space Pups", "Cartoon Parade",
            "Story Time", "Space Pups", "Koi Skies", "Cartoon Parade", "Good Night Tales"),
    )
    private val shows = listOf(
        "Morning Briefing", "Matchday Live", "Night at the Movies", "Cartoon Parade", "Top 20 Countdown", "Ocean Giants",
        "Street Food Stories", "Island Hopping", "World Report", "Champions Recap", "Weekend Debate", "Kitchen Heroes",
        "Mountain Life", "Late Show", "Classic Hits", "Evening Edition",
    )

    /** Half-hour aligned programmes around now, different lengths per channel so the grid looks lived in. */
    private val guide: Map<String, List<Programme>> = live.mapIndexed { index, channel ->
        val step = 1_800L
        var start = now - now % step - step * (1 + index % 2)
        channel.url to List(10) { slot ->
            val length = step * (1 + (index + slot) % 3)
            val title = fixtures[channel.title]?.get(slot) ?: shows[(index * 3 + slot) % shows.size]
            Programme(start, start + length, title).also { start += length }
        }
    }.toMap()

    /** Reminders on the next big match and a premiere, so the guide shows them marked. */
    private val reminders = listOf("Arena Sports 1" to 2, "Arena Sports 2" to 2, "Cinema Max" to 1, "Cinema Max" to 2).mapNotNull { (title, slot) ->
        val channel = live.first { it.title == title }
        guide[channel.url]?.getOrNull(slot)?.takeIf { it.start > now }?.let {
            Reminder(reminderId(channel.url, it.start), channel.url, channel.title, it.start, it.title, it.start - 600)
        }
    }

    fun iptv(shot: StoreShot): IptvUiState {
        val base = IptvUiState(loaded = true, sources = sources, guide = guide, reminders = reminders).withEntries(entries)
        return when (shot) {
            StoreShot.CHANNELS -> base.copy(stack = listOf(IptvScreen.Hub, IptvScreen.Source), activeId = playlist.id, channels = live + movies)
            StoreShot.GUIDE -> base.copy(stack = listOf(IptvScreen.Hub, IptvScreen.Source, IptvScreen.Guide), activeId = playlist.id, channels = live + movies)
            StoreShot.MOVIES -> base.copy(stack = listOf(IptvScreen.Hub, IptvScreen.Source), activeId = playlist.id, channels = movies)
            StoreShot.SPORTS -> base.copy(stack = listOf(IptvScreen.Hub, IptvScreen.Source), activeId = playlist.id, channels = live + movies,
                player = PlayerState(live[0], playlist.id, live))
            StoreShot.PLAYER -> base.copy(stack = listOf(IptvScreen.Hub, IptvScreen.Source), activeId = playlist.id, channels = live + movies,
                player = PlayerState(live[6], playlist.id, live))
            else -> base
        }
    }
}
