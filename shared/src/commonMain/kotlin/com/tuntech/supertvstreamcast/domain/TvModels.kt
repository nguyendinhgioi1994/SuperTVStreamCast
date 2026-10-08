package com.tuntech.supertvstreamcast.domain

enum class TvBrand(val title: String) {
    SAMSUNG("Samsung"), LG("LG"), SONY("Sony BRAVIA"), GOOGLE("Google / Android TV"),
    TCL("TCL"), HISENSE("Hisense"), ROKU("Roku"), OTHER("TV")
}
enum class Feature { HOME, REMOTE, MIRROR, IPTV, SETTINGS }
enum class RemoteKey(val sonyName: String) {
    POWER("Power"), UP("Up"), DOWN("Down"), LEFT("Left"), RIGHT("Right"), OK("Confirm"),
    BACK("Return"), HOME("Home"), VOLUME_UP("VolumeUp"), VOLUME_DOWN("VolumeDown"), MUTE("Mute")
}
data class Channel(val url: String, val title: String, val group: String)

fun isLocalIpv4(value: String): Boolean {
    val parts = value.split('.')
    if (parts.size != 4 || parts.any { it.isEmpty() || it.any { c -> !c.isDigit() } || (it.length > 1 && it.startsWith('0')) }) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    return octets[0] == 10 || (octets[0] == 192 && octets[1] == 168) || (octets[0] == 172 && octets[1] in 16..31)
}

/** Bound imports; preserve quoted metadata and ignore non-HTTP protocols. */
class ParsePlaylistUseCase {
    operator fun invoke(content: String): List<Channel> {
        require(content.length <= 2_000_000)
        val lines = content.removePrefix("\uFEFF").lineSequence().map { it.trim() }
        require(lines.firstOrNull()?.let { it == "#EXTM3U" || it.startsWith("#EXTM3U ") } == true)
        val result = linkedMapOf<String, Channel>()
        var title = ""
        var group = ""
        content.removePrefix("\uFEFF").lineSequence().drop(1).forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF:") -> {
                    var quoted = false
                    val split = line.indexOfFirst { c -> if (c == '"') quoted = !quoted; c == ',' && !quoted }
                    title = if (split >= 0) line.substring(split + 1).trim() else ""
                    group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1).orEmpty()
                }
                line.isNotBlank() && !line.startsWith('#') -> {
                    if (isStreamUrl(line) && !result.containsKey(line)) {
                        require(result.size < 5_000)
                        result[line] = Channel(line, title.ifBlank { line.substringBefore('?').substringAfterLast('/') }, group)
                    }
                    title = ""; group = ""
                }
            }
        }
        require(result.isNotEmpty())
        return result.values.toList()
    }
}
fun isStreamUrl(value: String): Boolean = Regex("https?://[^\\s/]+(?:/[^\\s]*)?", RegexOption.IGNORE_CASE).matches(value)
