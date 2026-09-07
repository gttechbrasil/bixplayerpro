package pro.bixplayer.player.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pro.bixplayer.player.BuildConfig
import pro.bixplayer.player.di.DeviceApiHolder
import pro.bixplayer.player.data.api.dto.DiagnosticRequest
import pro.bixplayer.player.player.PlayerSession
import pro.bixplayer.player.player.UrlRedactor
import timber.log.Timber

/**
 * Crash capture and diagnostics bundles (M5-013). The client has no adb, so the app keeps
 * its own evidence on disk and ships it to `POST /device/diagnostics`:
 *
 * - `install()` plants a Timber tree that keeps the last [RING_LINES] log lines in memory and
 *   appends them to `files/diagnostics/app.log` (rotated at [LOG_MAX_BYTES]), and wraps the
 *   default uncaught-exception handler to write `crash-<ts>.txt` before the process dies.
 * - Native crashes (libVLC in ARM), OOM kills and ANRs never reach a Java handler; on the next
 *   start [collectExitReasons] reads `ApplicationExitInfo` (Android 11+) and writes
 *   `exit-<ts>.txt` with the reason, memory figures and, when the system kept one, the
 *   tombstone/ANR trace.
 * - [buildReport] concatenates device info, player state, pending crash/exit files, the log
 *   tail and the process' own `logcat -d`, capped at [MAX_BODY] bytes (head + tail kept).
 */
@Singleton
class Diagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiHolder: DeviceApiHolder,
    private val session: PlayerSession,
) {
    private val dir: File get() = File(context.filesDir, "diagnostics").apply { mkdirs() }
    private val logFile: File get() = File(dir, "app.log")
    private val ring = ArrayDeque<String>(RING_LINES)
    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun install() {
        Timber.plant(FileTree())
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
        runCatching { collectExitReasons() }.onFailure { Timber.w(it, "exit reasons") }
    }

    /** Files waiting to be shipped (crash-*.txt / exit-*.txt), newest first. */
    fun pendingEvidence(): List<File> =
        dir.listFiles { f -> f.name.startsWith("crash-") || f.name.startsWith("exit-") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()

    fun hasPendingEvidence(): Boolean = pendingEvidence().isNotEmpty()

    /** Sends a bundle; [kind] is `crash` when triggered automatically, `manual` from Settings. */
    suspend fun send(kind: String): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val api = apiHolder.api ?: error("api not ready")
            val evidence = pendingEvidence()
            val body = buildReport(kind, evidence)
            val response = api.sendDiagnostics(
                DiagnosticRequest(kind = kind, appVersion = BuildConfig.VERSION_NAME, deviceInfo = deviceInfo(), log = body),
            )
            evidence.forEach { it.delete() }
            Timber.i("diagnostics sent: id=%d bytes=%d kind=%s", response.id, body.length, kind)
            response.id
        }.onFailure { Timber.w(it, "diagnostics send failed") }
    }

    // ---- collection -------------------------------------------------------------------------

    fun buildReport(kind: String, evidence: List<File> = pendingEvidence()): String {
        val sb = StringBuilder()
        sb.appendLine("=== Bix Player Pro diagnostics ($kind) ${stamp.format(Date())}")
        deviceInfo().forEach { (k, v) -> sb.appendLine("$k: $v") }
        sb.appendLine("player: engine=${session.engineKind} compat=${session.compatibilityMode.value} state=${session.state.value}")
        sb.appendLine("stream: ${UrlRedactor.redact(session.currentUrl.value)}")
        sb.appendLine()
        evidence.forEach { file ->
            sb.appendLine("=== ${file.name}")
            sb.appendLine(runCatching { file.readText().take(EVIDENCE_MAX_CHARS) }.getOrElse { "unreadable: $it" })
            sb.appendLine()
        }
        sb.appendLine("=== app.log (tail)")
        sb.appendLine(runCatching { tail(logFile, LOG_TAIL_BYTES) }.getOrElse { "" })
        synchronized(ring) { if (ring.isNotEmpty()) { sb.appendLine("=== memory ring"); ring.forEach { sb.appendLine(it) } } }
        sb.appendLine("=== logcat -d (own process)")
        sb.appendLine(ownLogcat())
        return cap(sb.toString())
    }

    fun deviceInfo(): Map<String, String> {
        val am = context.getSystemService<ActivityManager>()
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val rt = Runtime.getRuntime()
        return linkedMapOf(
            "app" to "${context.packageName} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}",
            "manufacturer" to Build.MANUFACTURER,
            "brand" to Build.BRAND,
            "model" to Build.MODEL,
            "device" to "${Build.DEVICE}/${Build.BOARD}/${Build.HARDWARE}",
            "android" to "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "abi" to Build.SUPPORTED_ABIS.joinToString(","),
            "ui_signals" to UiModeDecider.signals(context).toString(),
            "mem_total_mb" to (mem.totalMem / MB).toString(),
            "mem_avail_mb" to (mem.availMem / MB).toString(),
            "low_memory" to mem.lowMemory.toString(),
            "heap_mb" to "${(rt.totalMemory() - rt.freeMemory()) / MB}/${rt.maxMemory() / MB}",
            "large_heap_mb" to (am?.largeMemoryClass ?: 0).toString(),
        )
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            appendLine("FATAL EXCEPTION on ${thread.name} at ${stamp.format(Date())}")
            deviceInfo().forEach { (k, v) -> appendLine("$k: $v") }
            appendLine("player: engine=${session.engineKind} compat=${session.compatibilityMode.value} state=${session.state.value}")
            appendLine(trace)
            appendLine("--- last log lines")
            synchronized(ring) { ring.forEach { appendLine(it) } }
        }
        File(dir, "crash-${System.currentTimeMillis()}.txt").writeText(text)
    }

    /** Android 11+: the system's own record of why the last processes died. */
    private fun collectExitReasons() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val am = context.getSystemService<ActivityManager>() ?: return
        val marker = File(dir, ".last-exit")
        val lastSeen = marker.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L
        val infos = am.getHistoricalProcessExitReasons(context.packageName, 0, 10)
        var newest = lastSeen
        for (info in infos) {
            if (info.timestamp <= lastSeen) continue
            newest = maxOf(newest, info.timestamp)
            if (info.reason in BENIGN_REASONS) continue
            val text = buildString {
                appendLine("PROCESS EXIT at ${stamp.format(Date(info.timestamp))}")
                appendLine("reason: ${reasonName(info.reason)} (${info.reason}) status=${info.status}")
                appendLine("description: ${info.description}")
                appendLine("importance: ${info.importance} pss_kb=${info.pss} rss_kb=${info.rss}")
                val trace = runCatching {
                    info.traceInputStream?.use { it.readBytes().toString(Charsets.UTF_8).take(TRACE_MAX_CHARS) }
                }.getOrNull()
                if (!trace.isNullOrBlank()) {
                    appendLine("--- trace")
                    appendLine(trace)
                }
            }
            File(dir, "exit-${info.timestamp}.txt").writeText(text)
            Timber.w("previous process exit: %s %s", reasonName(info.reason), info.description)
        }
        if (newest != lastSeen) marker.writeText(newest.toString())
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "REASON_$reason"
    }

    private fun ownLogcat(): String = runCatching {
        val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", LOGCAT_LINES.toString())
            .redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        text.take(LOGCAT_MAX_CHARS)
    }.getOrElse { "logcat unavailable: $it" }

    private fun tail(file: File, bytes: Int): String {
        if (!file.exists()) return ""
        val length = file.length()
        return file.inputStream().use { input ->
            if (length > bytes) input.skip(length - bytes)
            input.readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun cap(text: String): String {
        if (text.length <= MAX_BODY) return text
        val head = text.substring(0, MAX_BODY / 2)
        val tailPart = text.substring(text.length - MAX_BODY / 2 + 64)
        return head + "\n... [${text.length - MAX_BODY} chars omitted] ...\n" + tailPart
    }

    /** Timber tree: memory ring + rolling file, INFO and above in release, everything in debug. */
    private inner class FileTree : Timber.Tree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean =
            BuildConfig.DEBUG || priority >= android.util.Log.INFO

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            val line = "${stamp.format(Date())} ${priorityChar(priority)}/${tag ?: "app"}: $message" +
                (t?.let { "\n" + StringWriter().also { w -> it.printStackTrace(PrintWriter(w)) } } ?: "")
            synchronized(ring) {
                if (ring.size >= RING_LINES) ring.removeFirst()
                ring.addLast(line)
            }
            runCatching {
                if (logFile.length() > LOG_MAX_BYTES) {
                    val rotated = File(dir, "app.log.1")
                    rotated.delete()
                    logFile.renameTo(rotated)
                }
                logFile.appendText(line + "\n")
            }
        }

        private fun priorityChar(priority: Int) = when (priority) {
            android.util.Log.VERBOSE -> 'V'
            android.util.Log.DEBUG -> 'D'
            android.util.Log.INFO -> 'I'
            android.util.Log.WARN -> 'W'
            android.util.Log.ERROR -> 'E'
            else -> 'A'
        }
    }

    companion object {
        const val MAX_BODY = 512 * 1024
        private const val MB = 1024L * 1024L
        private const val RING_LINES = 300
        private const val LOG_MAX_BYTES = 256 * 1024L
        private const val LOG_TAIL_BYTES = 96 * 1024
        private const val EVIDENCE_MAX_CHARS = 96 * 1024
        private const val TRACE_MAX_CHARS = 64 * 1024
        private const val LOGCAT_LINES = 600
        private const val LOGCAT_MAX_CHARS = 160 * 1024
        private val BENIGN_REASONS = setOf(
            ApplicationExitInfo.REASON_EXIT_SELF,
            ApplicationExitInfo.REASON_USER_REQUESTED,
            ApplicationExitInfo.REASON_USER_STOPPED,
            ApplicationExitInfo.REASON_PERMISSION_CHANGE,
            ApplicationExitInfo.REASON_PACKAGE_UPDATED,
            ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE,
            ApplicationExitInfo.REASON_DEPENDENCY_DIED,
            ApplicationExitInfo.REASON_UNKNOWN,
        )
    }
}
