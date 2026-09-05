package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pro.bixplayer.player.player.UrlRedactor

class UrlRedactorTest {

    @Test
    fun `hides user and password of an xtream live url`() {
        val redacted = UrlRedactor.redact("http://srv.tv:8080/live/joao/S3nh4/1234.ts")
        assertThat(redacted).isEqualTo("http://srv.tv:8080/live/***/***/1234.ts")
        assertThat(redacted).doesNotContain("joao")
        assertThat(redacted).doesNotContain("S3nh4")
    }

    @Test
    fun `hides the short xtream form without the live prefix`() {
        assertThat(UrlRedactor.redact("http://srv.tv/joao/S3nh4/99.m3u8"))
            .isEqualTo("http://srv.tv/***/***/99.m3u8")
    }

    @Test
    fun `hides credentials in query parameters and keeps the others`() {
        val redacted = UrlRedactor.redact("http://srv.tv/get.php?username=joao&password=abc&type=m3u")
        assertThat(redacted).isEqualTo("http://srv.tv/get.php?username=***&password=***&type=m3u")
    }

    @Test
    fun `hides basic auth in the authority`() {
        assertThat(UrlRedactor.redact("https://joao:abc@cdn.tv/stream.m3u8"))
            .isEqualTo("https://***@cdn.tv/stream.m3u8")
    }

    @Test
    fun `leaves public urls untouched`() {
        val url = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        assertThat(UrlRedactor.redact(url)).isEqualTo(url)
    }

    @Test
    fun `never throws on junk`() {
        assertThat(UrlRedactor.redact(null)).isEqualTo("(sem url)")
        assertThat(UrlRedactor.redact("")).isEqualTo("(sem url)")
        assertThat(UrlRedactor.redact("not a url")).isEqualTo("***")
    }
}
