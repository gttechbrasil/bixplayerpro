package pro.bixplayer.player.ui.screens.parental

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.data.db.CategoryDao
import pro.bixplayer.player.data.db.CategoryRuleDao
import pro.bixplayer.player.data.db.CategoryRuleEntity
import pro.bixplayer.player.data.db.ContentKind
import pro.bixplayer.player.domain.parental.ParentalGate

data class ParentalCategory(
    val kind: String,
    val remoteId: String,
    val name: String,
    val count: Int,
    val hidden: Boolean,
    val locked: Boolean,
)

enum class PinStep { NONE, CURRENT, NEW, CONFIRM }

data class ParentalUiState(
    val playlistId: Long? = null,
    val kind: String = ContentKind.LIVE,
    val categories: List<ParentalCategory> = emptyList(),
    val pinStep: PinStep = PinStep.NONE,
    val notice: String? = null,
)

/** Settings → Controle parental: change the PIN, hide or lock categories of the active playlist. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ParentalViewModel @Inject constructor(
    store: DeviceStore,
    private val categoryDao: CategoryDao,
    private val ruleDao: CategoryRuleDao,
    private val gate: ParentalGate,
) : ViewModel() {

    private val kind = MutableStateFlow(ContentKind.LIVE)
    private val pinStep = MutableStateFlow(PinStep.NONE)
    private val notice = MutableStateFlow<String?>(null)
    private var pendingNewPin: String? = null

    val uiState: StateFlow<ParentalUiState> = store.activePlaylistId.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(ParentalUiState())
        combine(kind, ruleDao.observeByPlaylist(id), pinStep, notice) { k, rules, step, msg ->
            val cats = categoryDao.allByPlaylist(id).filter { it.kind == k }
            val byId = rules.filter { it.kind == k }.associateBy { it.remoteId }
            ParentalUiState(
                playlistId = id,
                kind = k,
                categories = cats.map {
                    val rule = byId[it.remoteId]
                    ParentalCategory(k, it.remoteId, it.name, it.channelCount, rule?.hidden == true, rule?.locked == true)
                },
                pinStep = step,
                notice = msg,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ParentalUiState())

    fun setKind(value: String) {
        kind.value = value
    }

    fun toggleHidden(category: ParentalCategory) = update(category) { it.copy(hidden = !category.hidden) }

    fun toggleLocked(category: ParentalCategory) = update(category) { it.copy(locked = !category.locked) }

    private fun update(category: ParentalCategory, change: (CategoryRuleEntity) -> CategoryRuleEntity) {
        val id = uiState.value.playlistId ?: return
        viewModelScope.launch {
            val current = ruleDao.get(id, category.kind, category.remoteId)
                ?: CategoryRuleEntity(id, category.kind, category.remoteId)
            val next = change(current)
            if (!next.hidden && !next.locked) ruleDao.delete(id, category.kind, category.remoteId) else ruleDao.upsert(next)
        }
    }

    // ---- PIN change: current → new → confirm -------------------------------------------------

    fun startPinChange() {
        pendingNewPin = null
        notice.value = null
        pinStep.value = PinStep.CURRENT
    }

    fun cancelPinChange() {
        pendingNewPin = null
        pinStep.value = PinStep.NONE
    }

    /** Called by the dialog at each step; returns whether the typed PIN was accepted. */
    suspend fun submitPin(pin: String, changedMessage: String, mismatchMessage: String): Boolean = when (pinStep.value) {
        PinStep.CURRENT -> {
            val ok = pin == gate.currentPin()
            if (ok) pinStep.value = PinStep.NEW
            ok
        }
        PinStep.NEW -> {
            pendingNewPin = pin
            pinStep.value = PinStep.CONFIRM
            true
        }
        PinStep.CONFIRM -> {
            if (pin == pendingNewPin) {
                gate.changePin(gate.currentPin(), pin)
                pinStep.value = PinStep.NONE
                notice.value = changedMessage
                true
            } else {
                pendingNewPin = null
                pinStep.value = PinStep.NEW
                notice.value = mismatchMessage
                false
            }
        }
        PinStep.NONE -> false
    }
}
