package pro.bixplayer.player.util

/** Clock-style formatting used by the player overlay and the "continue from" labels. */
object TimeFormat {
    /** `1:02:03` past one hour, `02:03` below it. */
    fun clock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** `1h 42min` / `42min`, for durations in the detail screens. */
    fun duration(seconds: Int?): String? {
        if (seconds == null || seconds <= 0) return null
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}min" else "${m}min"
    }
}
