package pro.bixplayer.player.ui.components

import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Requests focus and keeps trying for a short while (M5-014).
 *
 * On slow TV boxes the first request often runs before the node is attached or before the
 * window has focus after a screen transition, and a single `runCatching { requestFocus() }`
 * silently leaves the screen with nothing focused. The user then needs one OK press just to
 * get initial focus and a second one to click. Retrying until the request succeeds keeps the
 * highlighted item and the focused item the same from the first frame.
 */
suspend fun FocusRequester.requestFocusWithRetry(attempts: Int = 15, intervalMs: Long = 100): Boolean {
    repeat(attempts) { attempt ->
        val ok = runCatching { requestFocus() }.getOrDefault(false)
        if (ok) return true
        if (attempt == 0) delay(intervalMs / 2) else delay(intervalMs)
    }
    Timber.w("initial focus not granted after %d attempts", attempts)
    return false
}

/** Non-suspending variant for callbacks: one attempt, never throws, returns whether it took. */
fun FocusRequester.requestFocusSafely(): Boolean = runCatching { requestFocus() }.getOrDefault(false)
