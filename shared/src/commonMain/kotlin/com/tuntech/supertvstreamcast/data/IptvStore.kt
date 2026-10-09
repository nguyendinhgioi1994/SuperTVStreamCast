package com.tuntech.supertvstreamcast.data

import com.tuntech.supertvstreamcast.domain.Channel
import com.tuntech.supertvstreamcast.domain.IptvSource
import com.tuntech.supertvstreamcast.domain.LibraryEntry
import com.tuntech.supertvstreamcast.domain.Reminder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.Buffer

/** Everything about the IPTV library except the channel lists: sources, what the user did with channels, the hidden-channels passcode. */
@Serializable
data class IptvIndex(
    val sources: List<IptvSource> = emptyList(), val entries: List<LibraryEntry> = emptyList(),
    val passcodeHash: String = "", val passcodeSalt: String = "",
    val reminders: List<Reminder> = emptyList(),
    /** Playback seconds counted on [watchDay] (days since the epoch) for the free daily limit. */
    val watchDay: Long = 0, val watchSeconds: Int = 0,
)

/**
 * The IPTV library on disk, in an app-private directory that is left out of backups: `index.json`
 * plus one `catalog_<id>.jsonl` per source (a channel per line). Stream links and Xtream logins
 * live here and nowhere else, so every file is sealed by [cipher]; nothing in this class logs
 * them. Unreadable files count as missing instead of failing the app. Files written before
 * sealing existed are still read and are sealed the next time they are saved.
 */
class IptvStore(private val directory: () -> String, private val cipher: IptvCipher, private val files: FileSystem = FileSystem.SYSTEM,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    suspend fun loadIndex(): IptvIndex = io {
        try { json.decodeFromString(IptvIndex.serializer(), read(INDEX).readUtf8()) }
        catch (_: Exception) { IptvIndex() }
    }
    suspend fun saveIndex(index: IptvIndex) = io {
        replace(INDEX) { it.writeUtf8(json.encodeToString(IptvIndex.serializer(), index)) }
    }
    /** Channels of one source, or null when it was never saved or cannot be read. */
    suspend fun loadCatalog(sourceId: String): List<Channel>? = io {
        try {
            val source = read(catalog(sourceId))
            val channels = ArrayList<Channel>()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isNotEmpty()) channels += json.decodeFromString(Channel.serializer(), line)
            }
            channels
        } catch (_: Exception) { null }
    }
    suspend fun saveCatalog(sourceId: String, channels: List<Channel>) = io {
        replace(catalog(sourceId)) { sink ->
            channels.forEach { sink.writeUtf8(json.encodeToString(Channel.serializer(), it)).writeByte('\n'.code) }
        }
    }
    suspend fun deleteCatalog(sourceId: String) = io { files.delete(path(catalog(sourceId)), mustExist = false) }

    private fun read(name: String): Buffer {
        val bytes = files.read(path(name)) { readByteArray() }
        return Buffer().write(if (cipher.isSealed(bytes)) requireNotNull(cipher.open(bytes)) else bytes)
    }
    /** Seals the content, writes a temporary file and moves it into place, so a crash never leaves a half-written library. */
    private fun replace(name: String, write: (okio.BufferedSink) -> Unit) {
        files.createDirectories(directory().toPath())
        val temporary = path("$name.tmp")
        val sealed = cipher.seal(Buffer().also(write).readByteArray())
        files.write(temporary) { write(sealed) }
        files.atomicMove(temporary, path(name))
    }
    private fun path(name: String): Path = directory().toPath() / name
    private fun catalog(sourceId: String): String {
        require(sourceId.isNotEmpty() && sourceId.all(Char::isLetterOrDigit))
        return "catalog_$sourceId.jsonl"
    }
    private suspend fun <T> io(block: () -> T): T = lock.withLock { withContext(dispatcher) { block() } }

    private companion object { const val INDEX = "index.json" }
}

/** The passcode itself is never stored, only this salted digest. */
fun passcodeHash(pin: String, salt: String): String = "$salt:$pin".encodeUtf8().sha256().hex()
