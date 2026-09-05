package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pro.bixplayer.player.data.playlist.M3uRemoteIds

class M3uRemoteIdsTest {

    @Test
    fun `same url under different names gives different ids`() {
        val ids = M3uRemoteIds()
        val a = ids.next("Canal 1 HD", "https://cdn/x.m3u8")
        val b = ids.next("Canal 2 HD", "https://cdn/x.m3u8")
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `exact duplicates get an occurrence suffix instead of colliding`() {
        val ids = M3uRemoteIds()
        val first = ids.next("Globo", "http://a/1.ts")
        val second = ids.next("Globo", "http://a/1.ts")
        val third = ids.next("Globo", "http://a/1.ts")
        assertThat(setOf(first, second, third)).hasSize(3)
        assertThat(second).isEqualTo("$first-2")
        assertThat(third).isEqualTo("$first-3")
    }

    @Test
    fun `ids are stable across resyncs so favourites survive`() {
        val run1 = M3uRemoteIds()
        val run2 = M3uRemoteIds()
        val entries = listOf("Globo" to "http://a/1.ts", "SBT" to "http://a/2.ts", "Globo" to "http://a/1.ts")
        val first = entries.map { (n, u) -> run1.next(n, u) }
        val second = entries.map { (n, u) -> run2.next(n, u) }
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `name case and surrounding spaces do not change the id`() {
        assertThat(M3uRemoteIds().next(" GLOBO ", "http://a/1.ts"))
            .isEqualTo(M3uRemoteIds().next("globo", "http://a/1.ts"))
    }
}
