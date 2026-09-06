package pro.bixplayer.player.data.playlist

/** What an M3U entry is, as far as the app can tell without asking the provider. */
enum class M3uKind { LIVE, MOVIE, SERIES }

/** `Nome da Série S01E02 - Título` split into its parts. */
data class EpisodeName(
    val series: String,
    val season: Int,
    val episode: Int,
    /** Text after the SxxExx marker, when the provider adds the episode title. */
    val title: String?,
)

/**
 * M3U has no notion of content type: everything is a line with a URL. Providers signal movies
 * and series through the file extension and the group title, and episodes through the
 * `SxxExx` convention in the name. These rules are what the reference apps use.
 */
object M3uClassifier {
    private val VOD_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "m4v", "wmv", "flv", "webm")

    private val MOVIE_GROUP = Regex("""\b(filmes?|movies?|vod|cinema)\b""", RegexOption.IGNORE_CASE)
    private val SERIES_GROUP = Regex("""\b(s[ée]ries?|seriados?|novelas?|animes?|shows?)\b""", RegexOption.IGNORE_CASE)

    /** `S01E02`, `S1 E2`, `1x02`, `T01E02` (Portuguese "temporada"). */
    private val EPISODE_MARKER = Regex(
        """^(.*?)[\s\-–_.\[(]*(?:[SsTt](\d{1,2})\s*[Ee][Pp]?\s*(\d{1,3})|(\d{1,2})x(\d{1,3}))[\])]?(?:\s*[-–:.]?\s*(.*))?$""",
    )

    fun classify(entry: M3uEntry): M3uKind {
        val group = entry.groupTitle.orEmpty()
        if (parseEpisode(entry.name) != null && (SERIES_GROUP.containsMatchIn(group) || isVodUrl(entry.url))) {
            return M3uKind.SERIES
        }
        if (isVodUrl(entry.url)) return M3uKind.MOVIE
        if (MOVIE_GROUP.containsMatchIn(group) && !isLiveUrl(entry.url)) return M3uKind.MOVIE
        return M3uKind.LIVE
    }

    /** Null when the name has no season/episode marker. */
    fun parseEpisode(name: String): EpisodeName? {
        val match = EPISODE_MARKER.find(name.trim()) ?: return null
        val series = match.groupValues[1].trim().trimEnd('-', '–', ':', '.', ' ')
        if (series.isEmpty()) return null
        val season = (match.groupValues[2].ifEmpty { match.groupValues[4] }).toIntOrNull() ?: return null
        val episode = (match.groupValues[3].ifEmpty { match.groupValues[5] }).toIntOrNull() ?: return null
        val title = match.groupValues[6].trim().takeIf { it.isNotEmpty() }
        return EpisodeName(series, season, episode, title)
    }

    fun isVodUrl(url: String): Boolean = extensionOf(url) in VOD_EXTENSIONS

    /** Xtream-style live paths and transport streams are never VOD. */
    private fun isLiveUrl(url: String): Boolean {
        val ext = extensionOf(url)
        return ext == "ts" || ext == "m3u8" || url.contains("/live/")
    }

    private fun extensionOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/')
        return if ('.' in last) last.substringAfterLast('.').lowercase() else ""
    }
}
