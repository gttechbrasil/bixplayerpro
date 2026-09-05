package pro.bixplayer.player.data.playlist

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import timber.log.Timber

/** One entry of an M3U playlist, already normalised. */
data class M3uEntry(
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    /** `tvg-chno` when the provider numbers its channels. */
    val number: Int? = null,
)

/**
 * Streaming parser for M3U / M3U8 playlists.
 *
 * Reads line by line and hands each entry to a callback, so a 200 MB list with 50.000 channels
 * never sits in memory as a whole. Malformed entries are skipped instead of aborting the file:
 * providers routinely ship a few broken lines and the user would rather lose one channel than
 * the whole playlist.
 */
object M3uParser {

    private val ATTRIBUTE_REGEX = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    /** Providers routinely ship the file with a byte-order mark on the first line. */
    private const val BOM = "\uFEFF"

    /**
     * Parses [input] and calls [onEntry] for every valid entry.
     * Returns how many entries were emitted; [onEntry] may stop the parse by returning false.
     */
    fun parse(input: InputStream, onEntry: (M3uEntry) -> Boolean): Int {
        var emitted = 0
        var skipped = 0
        var pendingInfo: ExtInf? = null

        BufferedReader(InputStreamReader(input, Charsets.UTF_8), DEFAULT_BUFFER_SIZE).use { reader ->
            while (true) {
                val raw = reader.readLine() ?: break
                val line = raw.trim().removePrefix(BOM)
                when {
                    line.isEmpty() -> Unit

                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        pendingInfo = parseExtInf(line)
                        if (pendingInfo == null) skipped++
                    }

                    // Directives we do not use; they must not be mistaken for a URL.
                    line.startsWith("#") -> Unit

                    else -> {
                        val info = pendingInfo
                        pendingInfo = null
                        if (info == null || !isPlayableUrl(line)) {
                            skipped++
                        } else {
                            emitted++
                            if (!onEntry(info.toEntry(line))) return emitted
                        }
                    }
                }
            }
        }
        if (skipped > 0) Timber.w("m3u: %d entradas ignoradas, %d válidas", skipped, emitted)
        return emitted
    }

    /** Convenience for tests and small lists. */
    fun parseAll(input: InputStream, limit: Int = Int.MAX_VALUE): List<M3uEntry> {
        val out = ArrayList<M3uEntry>()
        parse(input) { entry ->
            out.add(entry)
            out.size < limit
        }
        return out
    }

    private data class ExtInf(
        val title: String,
        val attributes: Map<String, String>,
    ) {
        fun toEntry(url: String): M3uEntry {
            val tvgName = attributes["tvg-name"]?.takeIf { it.isNotBlank() }
            return M3uEntry(
                // The text after the comma wins; some providers only fill tvg-name.
                name = title.ifBlank { tvgName ?: url.substringAfterLast('/') },
                url = url,
                logoUrl = attributes["tvg-logo"]?.takeIf { it.isNotBlank() },
                groupTitle = attributes["group-title"]?.takeIf { it.isNotBlank() },
                tvgId = attributes["tvg-id"]?.takeIf { it.isNotBlank() },
                tvgName = tvgName,
                number = attributes["tvg-chno"]?.trim()?.toIntOrNull(),
            )
        }
    }

    /**
     * `#EXTINF:-1 tvg-id="x" tvg-logo="http://..." group-title="Esportes",Nome do Canal`
     * Returns null when the line has no comma, which is what makes it unusable.
     */
    private fun parseExtInf(line: String): ExtInf? {
        val afterPrefix = line.substringAfter(':', missingDelimiterValue = "")
        if (afterPrefix.isEmpty()) return null
        val commaIndex = afterPrefix.lastIndexOf(',')
        if (commaIndex < 0) return null

        val head = afterPrefix.substring(0, commaIndex)
        val title = afterPrefix.substring(commaIndex + 1).trim()
        val attributes = ATTRIBUTE_REGEX.findAll(head)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        return ExtInf(title, attributes)
    }

    /** Only absolute http(s) URLs are playable; relative paths and junk lines are dropped. */
    private fun isPlayableUrl(line: String): Boolean =
        (line.startsWith("http://", true) || line.startsWith("https://", true)) &&
            line.length > "http://".length + 3
}
