package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import org.junit.Test
import pro.bixplayer.player.data.epg.XmltvParser
import pro.bixplayer.player.data.epg.XmltvProgramme
import org.kxml2.io.KXmlParser

class XmltvParserTest {

    private fun parse(xml: String, from: Long = Long.MIN_VALUE, to: Long = Long.MAX_VALUE): List<XmltvProgramme> {
        val out = ArrayList<XmltvProgramme>()
        XmltvParser.parse(ByteArrayInputStream(xml.toByteArray()), from, to, ::KXmlParser) { out.add(it); true }
        return out
    }

    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv generator-info-name="teste">
          <channel id="globo.br"><display-name>Globo</display-name></channel>
          <programme start="20260906120000 +0000" stop="20260906130000 +0000" channel="globo.br">
            <title lang="pt">Jornal Hoje</title><desc>Notícias do dia.</desc>
          </programme>
          <programme start="20260906130000 -0300" stop="20260906140000 -0300" channel="globo.br">
            <title>Sessão da Tarde</title>
          </programme>
          <programme start="20260906120000 +0000" stop="20260906110000 +0000" channel="globo.br">
            <title>Fim antes do início</title>
          </programme>
          <programme start="20260906120000 +0000" stop="20260906130000 +0000" channel="">
            <title>Sem canal</title>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun `parses programmes with their zone and skips broken ones`() {
        val programmes = parse(sample)
        assertThat(programmes.map { it.title }).containsExactly("Jornal Hoje", "Sessão da Tarde").inOrder()
        assertThat(programmes[0].description).isEqualTo("Notícias do dia.")
        assertThat(programmes[0].startAt).isEqualTo(XmltvParser.parseTime("20260906120000 +0000"))
        // -0300 means 13:00 local = 16:00 UTC
        assertThat(programmes[1].startAt).isEqualTo(XmltvParser.parseTime("20260906160000 +0000"))
        assertThat(programmes[1].description).isNull()
    }

    @Test
    fun `the window drops programmes that do not overlap it`() {
        val from = XmltvParser.parseTime("20260906153000 +0000")
        val programmes = parse(sample, from = from)
        assertThat(programmes.map { it.title }).containsExactly("Sessão da Tarde")
    }

    @Test
    fun `time parsing accepts the common shapes`() {
        assertThat(XmltvParser.parseTime("20260906120000 +0000")).isEqualTo(1788696000000L)
        assertThat(XmltvParser.parseTime("20260906120000")).isEqualTo(1788696000000L)
        assertThat(XmltvParser.parseTime("202609061200 +0000")).isEqualTo(1788696000000L)
        assertThat(XmltvParser.parseTime("garbage")).isEqualTo(0L)
        assertThat(XmltvParser.parseTime(null)).isEqualTo(0L)
    }

    @Test
    fun `the callback can stop early on a big file`() {
        val big = buildString {
            append("<tv>")
            repeat(1000) { i ->
                append("""<programme start="2026090612${"%02d".format(i % 60)}00 +0000" stop="20260907000000 +0000" channel="c$i"><title>P$i</title></programme>""")
            }
            append("</tv>")
        }
        var seen = 0
        val emitted = XmltvParser.parse(ByteArrayInputStream(big.toByteArray()), newParser = ::KXmlParser) { seen++; seen < 10 }
        assertThat(emitted).isEqualTo(10)
    }
}
