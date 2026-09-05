package pro.bixplayer.player.data.playlist

/**
 * Stable identifiers for M3U entries, which have none of their own.
 *
 * The id has to survive a resync (favourites are keyed by it) and be unique inside a
 * playlist. Hashing only the URL is not enough: providers list the same stream under several
 * names and categories, and the unique index would silently keep just the last one. Name and
 * URL together cover the common case; an occurrence counter covers exact duplicates.
 */
class M3uRemoteIds {
    private val seen = HashMap<String, Int>()

    fun next(name: String, url: String): String {
        val base = (name.trim().lowercase() + SEPARATOR + url.trim()).hashCode().toUInt().toString(16)
        val occurrence = seen.merge(base, 1, Int::plus) ?: 1
        return if (occurrence == 1) base else "$base-$occurrence"
    }

    private companion object {
        /** Cannot appear in a URL, so "ab" + "c" and "a" + "bc" never hash the same input. */
        const val SEPARATOR = "|"
    }
}
