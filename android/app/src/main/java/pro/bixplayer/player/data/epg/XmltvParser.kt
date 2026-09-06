package pro.bixplayer.player.data.epg

import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import timber.log.Timber

/** One `<programme>` of an XMLTV file, times in epoch millis. */
data class XmltvProgramme(
    val channelId: String,
    val startAt: Long,
    val endAt: Long,
    val title: String,
    val description: String?,
)

/**
 * Streaming XMLTV parser. Guides run to hundreds of megabytes for big providers, so the file
 * is never held in memory: each programme goes to [onProgramme] as soon as its closing tag is
 * read, and the caller drops what falls outside its window.
 */
object XmltvParser {

    /**
     * Parses [input], keeping only programmes that overlap `[from, to]`. Returns how many
     * programmes were emitted; [onProgramme] may stop the parse by returning false.
     */
    fun parse(
        input: InputStream,
        from: Long = Long.MIN_VALUE,
        to: Long = Long.MAX_VALUE,
        newParser: () -> XmlPullParser = ::platformParser,
        onProgramme: (XmltvProgramme) -> Boolean,
    ): Int {
        val parser = newParser()
        parser.setInput(input, null)

        var emitted = 0
        var skipped = 0
        var channel: String? = null
        var start = 0L
        var end = 0L
        var title: String? = null
        var description: String? = null
        var text: StringBuilder? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        channel = parser.getAttributeValue(null, "channel")
                        start = parseTime(parser.getAttributeValue(null, "start"))
                        end = parseTime(parser.getAttributeValue(null, "stop"))
                        title = null
                        description = null
                    }
                    "title", "desc" -> if (channel != null) text = StringBuilder()
                }

                XmlPullParser.TEXT -> text?.append(parser.text)

                XmlPullParser.END_TAG -> when (parser.name) {
                    "title" -> if (channel != null && title == null) title = text?.toString()?.trim()?.takeIf { it.isNotEmpty() }.also { text = null }
                    "desc" -> if (channel != null && description == null) description = text?.toString()?.trim()?.takeIf { it.isNotEmpty() }.also { text = null }
                    "programme" -> {
                        val id = channel
                        val name = title
                        channel = null
                        if (id.isNullOrBlank() || name == null || start <= 0L || end <= start) {
                            skipped++
                        } else if (end > from && start < to) {
                            emitted++
                            if (!onProgramme(XmltvProgramme(id, start, end, name, description))) return emitted
                        }
                    }
                }
            }
            event = parser.next()
        }
        if (skipped > 0) Timber.w("xmltv: %d programas ignorados, %d válidos", skipped, emitted)
        return emitted
    }

    /** The platform parser (kxml2 on Android); unit tests inject kxml2 directly. */
    private fun platformParser(): XmlPullParser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }.newPullParser()

    private val formats = ThreadLocal.withInitial {
        listOf("yyyyMMddHHmmss Z", "yyyyMMddHHmmss", "yyyyMMddHHmm Z", "yyyyMMddHHmm").map {
            SimpleDateFormat(it, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
        }
    }

    /** `20260906123000 +0000` → epoch millis; 0 when unparseable. Times without a zone are UTC. */
    fun parseTime(raw: String?): Long {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return 0L
        for (format in formats.get()) {
            try {
                return format.parse(value)?.time ?: continue
            } catch (_: ParseException) {
                // try the next shape
            }
        }
        return 0L
    }
}
