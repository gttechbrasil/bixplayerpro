package pro.bixplayer.player.player

/**
 * Strips credentials from stream URLs before they reach a log line.
 *
 * Xtream URLs carry the user and password in the path (`/live/user/pass/1.ts`) and M3U links
 * often carry them as query parameters; both must never appear in logcat or crash reports.
 */
object UrlRedactor {
    private const val MASK = "***"

    /** Xtream-style paths: `/live|movie|series/<user>/<pass>/...` and the short `/<user>/<pass>/<id>` form. */
    private val XTREAM_PATH = Regex("""^/(?:(live|movie|series|timeshift)/)?([^/]+)/([^/]+)/(\d+[^/]*)$""")

    private val SENSITIVE_QUERY = setOf("username", "password", "user", "pass", "token", "key")

    fun redact(url: String?): String {
        if (url.isNullOrBlank()) return "(sem url)"
        return runCatching { redactStrict(url) }.getOrElse { MASK }
    }

    private fun redactStrict(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return MASK
        val scheme = url.substring(0, schemeEnd)
        var rest = url.substring(schemeEnd + 3)

        // user:pass@host
        val at = rest.indexOf('@')
        val slash = rest.indexOf('/').let { if (it < 0) rest.length else it }
        if (at in 0 until slash) rest = "$MASK@" + rest.substring(at + 1)

        val pathStart = rest.indexOf('/')
        if (pathStart < 0) return "$scheme://$rest"
        val host = rest.substring(0, pathStart)
        var pathAndQuery = rest.substring(pathStart)

        val query = pathAndQuery.substringAfter('?', "")
        var path = pathAndQuery.substringBefore('?')

        XTREAM_PATH.find(path)?.let { match ->
            val prefix = match.groupValues[1].takeIf { it.isNotEmpty() }?.let { "/$it" } ?: ""
            path = "$prefix/$MASK/$MASK/${match.groupValues[4]}"
        }

        pathAndQuery = if (query.isEmpty()) {
            path
        } else {
            val safe = query.split('&').joinToString("&") { param ->
                val name = param.substringBefore('=')
                if (name.lowercase() in SENSITIVE_QUERY) "$name=$MASK" else param
            }
            "$path?$safe"
        }
        return "$scheme://$host$pathAndQuery"
    }
}
