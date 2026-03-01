package com.example.rustorecatalog.install

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object InstallManager {

    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val states: StateFlow<Map<String, InstallState>> = _states.asStateFlow()

    fun updateState(appId: String, state: InstallState) {
        _states.value = _states.value.toMutableMap().apply {
            this[appId] = state
        }
    }

    fun getState(appId: String): InstallState =
        _states.value[appId] ?: InstallState.NotInstalled
}

