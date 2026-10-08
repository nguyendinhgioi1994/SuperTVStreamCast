package com.tuntech.supertvstreamcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuntech.supertvstreamcast.data.TvRepository
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.platform.AppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class UiError { CONNECTION, COMMAND, PLAYLIST, INVALID_IP, PLAYER, MIRROR, PERMISSION }
data class TvUiState(
    val step: Int = 0, val brand: TvBrand = TvBrand.SAMSUNG, val goal: Feature = Feature.REMOTE,
    val tab: Feature = Feature.HOME, val connectionOpen: Boolean = false, val connected: Boolean = false,
    val busy: Boolean = false, val error: UiError? = null, val channels: List<Channel> = emptyList(),
    val favorites: Set<String> = emptySet(), val player: Channel? = null,
)
class TvViewModel(private val prefs: AppPreferences, private val repository: TvRepository = TvRepository()) : ViewModel() {
    private val mutable = MutableStateFlow(TvUiState(
        step = if (prefs.onboardingDone) 3 else 0,
        brand = TvBrand.entries.firstOrNull { it.name == prefs.brand } ?: TvBrand.SAMSUNG,
        goal = Feature.entries.firstOrNull { it.name == prefs.goal } ?: Feature.REMOTE,
    ))
    val state: StateFlow<TvUiState> = mutable.asStateFlow()
    fun next() {
        mutable.update { it.copy(step = (it.step + 1).coerceAtMost(3)) }
        if (state.value.step == 3) {
            prefs.brand = state.value.brand.name; prefs.goal = state.value.goal.name; prefs.onboardingDone = true
            mutable.update { it.copy(tab = it.goal) }
        }
    }
    fun back() = mutable.update {
        when {
            it.connectionOpen -> if(it.busy) it else it.copy(connectionOpen=false,error=null)
            it.player!=null -> it.copy(player=null,error=null)
            it.step<3 -> it.copy(step=(it.step-1).coerceAtLeast(0))
            else -> it.copy(tab=Feature.HOME,error=null)
        }
    }
    fun brand(brand: TvBrand) {
        if (state.value.busy) return
        repository.disconnect(); prefs.brand = brand.name
        mutable.update { it.copy(brand = brand, connected = false, error = null) }
    }
    fun goal(goal: Feature) = mutable.update { it.copy(goal = goal) }
    fun tab(tab: Feature) = mutable.update { it.copy(tab = tab, error = null, player = null) }
    fun connection(open: Boolean) { if (!state.value.busy) mutable.update { it.copy(connectionOpen = open, error = null) } }
    fun disconnect() { repository.disconnect(); mutable.update { it.copy(connected = false) } }
    fun clearError() = mutable.update { it.copy(error = null) }
    fun report(error: UiError) = mutable.update { it.copy(error = error) }
    fun connect(ip: String, psk: String) {
        if (!isLocalIpv4(ip.trim())) { report(UiError.INVALID_IP); return }
        if (state.value.brand != TvBrand.SONY) return
        work(UiError.CONNECTION) {
            repository.connect(ip.trim(), psk)
            mutable.update { it.copy(connected = true, connectionOpen = false) }
        }
    }
    fun send(key: RemoteKey) = work(UiError.COMMAND) { repository.send(key) }
    fun importPlaylist(value: String) = work(UiError.PLAYLIST) {
        val channels = ParsePlaylistUseCase()(repository.loadPlaylist(value))
        mutable.update { it.copy(channels = channels, favorites = it.favorites.intersect(channels.map { c -> c.url }.toSet()), player = null) }
    }
    fun play(channel: Channel) = mutable.update { it.copy(player = channel, error = null) }
    fun favorite(url: String) = mutable.update { it.copy(favorites = if (url in it.favorites) it.favorites - url else it.favorites + url) }
    private fun work(error: UiError, action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { action() } catch (e: CancellationException) { throw e } catch (_: Exception) {
                if(error == UiError.COMMAND || error == UiError.CONNECTION) repository.disconnect()
                mutable.update { it.copy(error = error, connected = if (error == UiError.CONNECTION || error == UiError.COMMAND) false else it.connected) }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    override fun onCleared() { repository.close() }
}
