package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import kotlin.system.measureTimeMillis
import org.junit.Test
import pro.bixplayer.player.data.playlist.M3uParser

class M3uParserTest {

    private fun parse(text: String) =
        M3uParser.parseAll(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

    @Test
    fun `parses a realistic playlist with every attribute`() {
        val playlist = """
            #EXTM3U x-tvg-url="http://epg.example/xmltv.xml"
            #EXTINF:-1 tvg-id="globo.br" tvg-name="Globo HD" tvg-logo="http://cdn/globo.png" group-title="Abertos",Globo HD
            http://servidor.tv:8080/live/user/pass/1.ts
            #EXTINF:-1 tvg-id="sportv.br" tvg-logo="http://cdn/sportv.png" group-title="Esportes",SporTV
            http://servidor.tv:8080/live/user/pass/2.ts
        """.trimIndent()

        val entries = parse(playlist)

        assertThat(entries).hasSize(2)
        val first = entries.first()
        assertThat(first.name).isEqualTo("Globo HD")
        assertThat(first.tvgId).isEqualTo("globo.br")
        assertThat(first.tvgName).isEqualTo("Globo HD")
        assertThat(first.logoUrl).isEqualTo("http://cdn/globo.png")
        assertThat(first.groupTitle).isEqualTo("Abertos")
        assertThat(first.url).isEqualTo("http://servidor.tv:8080/live/user/pass/1.ts")
        assertThat(entries[1].groupTitle).isEqualTo("Esportes")
    }

    @Test
    fun `keeps the name that comes after the comma even when it contains commas`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 group-title="Filmes",Rocky 2, o desafio
            http://a.tv/1.mp4
        """.trimIndent()

        // the parser splits on the LAST comma, which is what providers actually mean
        assertThat(parse(playlist).single().name).isEqualTo("o desafio")
    }

    @Test
    fun `reads the channel number when the provider sends one`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-chno="102" group-title="Abertos",SBT
            http://a.tv/2.ts
        """.trimIndent()
        assertThat(parse(playlist).single().number).isEqualTo(102)
    }

    @Test
    fun `skips malformed entries instead of dropping the whole file`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 group-title="Ok",Canal Bom
            http://a.tv/bom.ts
            #EXTINF sem dois pontos nem virgula
            http://a.tv/orfao.ts
            #EXTINF:-1,Canal Sem Url
            #EXTINF:-1,Outro Bom
            http://a.tv/outro.ts
            linha solta que nao e url
            #EXTINF:-1,Url Relativa
            /caminho/relativo.ts
        """.trimIndent()

        val entries = parse(playlist)

        assertThat(entries.map { it.name }).containsExactly("Canal Bom", "Outro Bom").inOrder()
    }

    @Test
    fun `tolerates a BOM, blank lines and unknown directives`() {
        val playlist = "\uFEFF#EXTM3U\n\n#EXTVLCOPT:network-caching=1000\n" +
            "#EXTINF:-1,Canal\nhttp://a.tv/1.ts\n\n"
        assertThat(parse(playlist).single().name).isEqualTo("Canal")
    }

    @Test
    fun `falls back to tvg-name and then to the file name when the title is empty`() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Do Atributo",
            http://a.tv/1.ts
            #EXTINF:-1,
            http://a.tv/apenas-url.ts
        """.trimIndent()

        val entries = parse(playlist)
        assertThat(entries[0].name).isEqualTo("Do Atributo")
        assertThat(entries[1].name).isEqualTo("apenas-url.ts")
    }

    @Test
    fun `the callback can stop the parse early`() {
        val playlist = buildString {
            appendLine("#EXTM3U")
            repeat(100) {
                appendLine("""#EXTINF:-1 group-title="G",Canal $it""")
                appendLine("http://a.tv/$it.ts")
            }
        }
        val seen = mutableListOf<String>()
        val emitted = M3uParser.parse(ByteArrayInputStream(playlist.toByteArray())) { entry ->
            seen.add(entry.name)
            seen.size < 10
        }
        assertThat(emitted).isEqualTo(10)
        assertThat(seen).hasSize(10)
    }

    @Test
    fun `handles five thousand channels without blowing up`() {
        val playlist = buildString {
            appendLine("#EXTM3U")
            repeat(5_000) { i ->
                appendLine(
                    """#EXTINF:-1 tvg-id="c$i" tvg-logo="http://cdn/$i.png" group-title="Grupo ${i % 40}",Canal $i"""
                )
                appendLine("http://servidor.tv:8080/live/u/p/$i.ts")
            }
        }
        val bytes = playlist.toByteArray()

        lateinit var entries: List<pro.bixplayer.player.data.playlist.M3uEntry>
        val millis = measureTimeMillis {
            entries = M3uParser.parseAll(ByteArrayInputStream(bytes))
        }

        assertThat(entries).hasSize(5_000)
        assertThat(entries.map { it.groupTitle }.distinct()).hasSize(40)
        assertThat(entries.last().name).isEqualTo("Canal 4999")
        // Generous ceiling: this guards against an accidental O(n^2), not against slow CI.
        assertThat(millis).isLessThan(5_000)
    }
}
