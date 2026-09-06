package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pro.bixplayer.player.data.playlist.M3uClassifier
import pro.bixplayer.player.data.playlist.M3uEntry
import pro.bixplayer.player.data.playlist.M3uKind

class M3uClassifierTest {

    private fun entry(name: String, url: String, group: String? = null) =
        M3uEntry(name = name, url = url, groupTitle = group)

    @Test
    fun `ts and m3u8 streams are live even inside a movie-looking group`() {
        assertThat(M3uClassifier.classify(entry("Globo", "http://a/live/u/p/1.ts", "Abertos"))).isEqualTo(M3uKind.LIVE)
        assertThat(M3uClassifier.classify(entry("Telecine", "http://a/live/u/p/2.m3u8", "Filmes"))).isEqualTo(M3uKind.LIVE)
    }

    @Test
    fun `video file extensions are movies`() {
        assertThat(M3uClassifier.classify(entry("Rocky", "http://a/movie/u/p/10.mp4", "Ação"))).isEqualTo(M3uKind.MOVIE)
        assertThat(M3uClassifier.classify(entry("Rocky", "http://a/movie/u/p/10.MKV?token=x", null))).isEqualTo(M3uKind.MOVIE)
        assertThat(M3uClassifier.classify(entry("Rocky", "http://a/vod/10.avi", null))).isEqualTo(M3uKind.MOVIE)
    }

    @Test
    fun `a movie group with an extensionless url is still a movie`() {
        assertThat(M3uClassifier.classify(entry("Rocky", "http://a/vod/10", "Filmes | Ação"))).isEqualTo(M3uKind.MOVIE)
    }

    @Test
    fun `episodes are series`() {
        assertThat(M3uClassifier.classify(entry("Breaking Bad S01E02", "http://a/series/u/p/5.mp4", "Séries"))).isEqualTo(M3uKind.SERIES)
        assertThat(M3uClassifier.classify(entry("Dark 1x03 - Passado", "http://a/series/u/p/6.mkv", null))).isEqualTo(M3uKind.SERIES)
    }

    @Test
    fun `an SxxExx live channel name without a vod url stays live`() {
        // Some providers name event channels like "Copa S01E01"; a .ts stream is never an episode.
        assertThat(M3uClassifier.classify(entry("Copa S01E01", "http://a/live/u/p/9.ts", "Esportes"))).isEqualTo(M3uKind.LIVE)
    }

    @Test
    fun `parses the common episode name shapes`() {
        val a = M3uClassifier.parseEpisode("Breaking Bad S01E02 - Cat's in the Bag")!!
        assertThat(a.series).isEqualTo("Breaking Bad")
        assertThat(a.season).isEqualTo(1)
        assertThat(a.episode).isEqualTo(2)
        assertThat(a.title).isEqualTo("Cat's in the Bag")

        val b = M3uClassifier.parseEpisode("Dark 1x03")!!
        assertThat(b.series).isEqualTo("Dark")
        assertThat(b.season).isEqualTo(1)
        assertThat(b.episode).isEqualTo(3)
        assertThat(b.title).isNull()

        val c = M3uClassifier.parseEpisode("Chaves T02 EP15")!!
        assertThat(c.series).isEqualTo("Chaves")
        assertThat(c.season).isEqualTo(2)
        assertThat(c.episode).isEqualTo(15)

        val d = M3uClassifier.parseEpisode("The Office [S09E23]")!!
        assertThat(d.series).isEqualTo("The Office")
        assertThat(d.season).isEqualTo(9)
        assertThat(d.episode).isEqualTo(23)
    }

    @Test
    fun `returns null when there is no marker or no series name`() {
        assertThat(M3uClassifier.parseEpisode("Rocky IV")).isNull()
        assertThat(M3uClassifier.parseEpisode("S01E02")).isNull()
        assertThat(M3uClassifier.parseEpisode("Globo HD")).isNull()
    }
}
