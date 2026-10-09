package com.tuntech.supertvstreamcast.ui.app_loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


/** Counts down until a blocking loading may be closed by the user. */
class AppLoadingViewModel : ViewModel() {
    companion object {
        const val TIME_OUT = 30000L
        const val INTERVAL = 1000L
    }

    private val _remainingTime = MutableStateFlow(TIME_OUT)
    val remainingTime = _remainingTime.asStateFlow()

    private var _timerJob: Job? = null

    fun start() {
        _remainingTime.update { TIME_OUT }
        _timerJob?.takeIf { it.isActive }?.cancel()
        _timerJob = viewModelScope.launch(Dispatchers.Default) {
            while (_remainingTime.value > 0) {
                delay(INTERVAL)
                _remainingTime.update { it - INTERVAL }
            }
        }
    }

    fun stop() {
        _timerJob?.takeIf { it.isActive }?.cancel()
        _remainingTime.update { 0 }
    }
}
