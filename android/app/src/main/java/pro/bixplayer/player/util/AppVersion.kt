package pro.bixplayer.player.util

/** Semantic-ish version comparison used for the forced-update check. */
object AppVersion {

    /**
     * Returns true when [current] is older than [minimum].
     * Missing or malformed values never force an update: a broken setting must not brick the app.
     */
    fun isOutdated(current: String, minimum: String): Boolean {
        if (minimum.isBlank() || current.isBlank()) return false
        val a = parse(current) ?: return false
        val b = parse(minimum) ?: return false
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left < right
        }
        return false
    }

    private fun parse(value: String): List<Int>? {
        val parts = value.trim().split(".")
        val numbers = parts.mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
        return numbers.takeIf { it.isNotEmpty() && it.size == parts.size }
    }
}
