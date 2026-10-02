package pro.bixplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pro.bixplayer.player.data.db.EpgProgramEntity
import pro.bixplayer.player.data.epg.NowNext

class NowNextTest {

    private fun programme(title: String, start: Long, end: Long) =
        EpgProgramEntity(playlistId = 1, channelEpgId = "c1", startAt = start, endAt = end, title = title)

    @Test
    fun `current and next around now`() {
        val current = programme("Jornal", 1_000, 2_000)
        val next = programme("Novela", 2_000, 3_000)
        val result = NowNext.of(listOf(current, next), nowMs = 1_500)
        assertEquals(current, result.now)
        assertEquals(next, result.next)
    }

    @Test
    fun `nothing on yet, only next`() {
        val later = programme("Filme", 5_000, 6_000)
        val result = NowNext.of(listOf(later), nowMs = 1_500)
        assertNull(result.now)
        assertEquals(later, result.next)
    }

    @Test
    fun `a programme that just ended is not current`() {
        val ended = programme("Antes", 1_000, 1_500)
        val result = NowNext.of(listOf(ended), nowMs = 1_500)
        assertNull(result.now)
        assertNull(result.next)
    }

    @Test
    fun `empty guide`() {
        assertEquals(NowNext.EMPTY, NowNext.of(emptyList(), nowMs = 1_500))
    }
}
