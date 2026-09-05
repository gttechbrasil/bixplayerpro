package pro.bixplayer.player.domain.model

/** Result of a configuration refresh, as consumed by the UI. */
sealed interface ConfigState {
    data object Loading : ConfigState

    data class Ready(val config: AppConfig) : ConfigState

    /**
     * The network failed and there was no cache to fall back to.
     * [message] is already translated for the user.
     */
    data class Failed(val message: String, val cause: Throwable? = null) : ConfigState
}
