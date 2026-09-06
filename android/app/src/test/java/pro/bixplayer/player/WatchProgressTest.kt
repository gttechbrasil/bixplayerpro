package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pro.bixplayer.player.data.db.ContentKind
import pro.bixplayer.player.data.db.WatchProgressEntity
import pro.bixplayer.player.ui.screens.movies.MovieDetailViewModel
import pro.bixplayer.player.util.TimeFormat

class WatchProgressTest {

    private fun progress(positionMs: Long, durationMs: Long) = WatchProgressEntity(
        playlistId = 1, kind = ContentKind.MOVIE, itemRemoteId = "m1",
        positionMs = positionMs, durationMs = durationMs, updatedAt = 0L, title = "Filme",
    )

    @Test
    fun `a long movie resumes after one minute and stops resuming near the end`() {
        val twoHours = 2 * 3600_000L
        assertThat(progress(30_000, twoHours).resumable).isFalse()
        assertThat(progress(61_000, twoHours).resumable).isTrue()
        assertThat(progress((twoHours * 0.96).toLong(), twoHours).resumable).isFalse()
        assertThat(progress((twoHours * 0.96).toLong(), twoHours).finished).isTrue()
    }

    @Test
    fun `a short clip resumes after ten percent`() {
        assertThat(progress(1_000, 13_000).resumable).isFalse()
        assertThat(progress(2_000, 13_000).resumable).isTrue()
    }

    @Test
    fun `unknown duration is never resumable`() {
        assertThat(progress(120_000, 0).resumable).isFalse()
    }

    @Test
    fun `clock formatting`() {
        assertThat(TimeFormat.clock(0)).isEqualTo("00:00")
        assertThat(TimeFormat.clock(65_000)).isEqualTo("01:05")
        assertThat(TimeFormat.clock(3_725_000)).isEqualTo("1:02:05")
        assertThat(TimeFormat.duration(6_120)).isEqualTo("1h 42min")
        assertThat(TimeFormat.duration(540)).isEqualTo("9min")
        assertThat(TimeFormat.duration(null)).isNull()
    }

    @Test
    fun `xtream duration strings`() {
        assertThat(MovieDetailViewModel.parseDuration("01:42:10")).isEqualTo(6130)
        assertThat(MovieDetailViewModel.parseDuration("42:10")).isEqualTo(2530)
        assertThat(MovieDetailViewModel.parseDuration("")).isNull()
        assertThat(MovieDetailViewModel.parseDuration(null)).isNull()
    }
}
