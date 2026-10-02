package pro.bixplayer.player.data.epg

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pro.bixplayer.player.data.db.ChannelEntity
import pro.bixplayer.player.data.db.EpgDao
import pro.bixplayer.player.data.db.EpgProgramEntity

/** What is on a channel now and what comes next, from the local guide. */
data class NowNext(val now: EpgProgramEntity?, val next: EpgProgramEntity?) {
    companion object {
        val EMPTY = NowNext(null, null)

        /** Splits programmes sorted by start around [nowMs]. */
        fun of(programmes: List<EpgProgramEntity>, nowMs: Long): NowNext = NowNext(
            now = programmes.firstOrNull { it.startAt <= nowMs && it.endAt > nowMs },
            next = programmes.firstOrNull { it.startAt > nowMs },
        )
    }
}

/**
 * Now/next for [channel], read again every [tickMs] so "Agora" moves on when a programme ends
 * (the preview used to freeze on whatever was on when the channel got the focus). Shared by the
 * channel preview and the player overlay, which used to show "em breve" whatever the guide said.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun EpgDao.observeNowNext(channel: ChannelEntity?, tickMs: Long = 60_000): Flow<NowNext> {
    val epgId = channel?.epgChannelId?.takeIf { it.isNotBlank() } ?: return flowOf(NowNext.EMPTY)
    return flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(tickMs)
        }
    }.flatMapLatest { nowMs ->
        observeUpcoming(channel.playlistId, epgId, nowMs, 2).map { NowNext.of(it, nowMs) }
    }.distinctUntilChanged()
}
