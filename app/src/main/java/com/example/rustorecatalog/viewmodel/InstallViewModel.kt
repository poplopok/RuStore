package com.example.rustorecatalog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rustorecatalog.install.InstallManager
import com.example.rustorecatalog.install.InstallState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class InstallViewModel : ViewModel() {

    private val _state = MutableStateFlow<InstallState>(InstallState.NotInstalled)
    val state: StateFlow<InstallState> = _state
    private var observeJob: Job? = null

    fun observe(appId: String) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            InstallManager.states.collectLatest { map ->
                _state.value = map[appId] ?: InstallState.NotInstalled
            }
        }
    }
}
