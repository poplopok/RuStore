package com.example.rustorecatalog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rustorecatalog.data.AppRepository
import com.example.rustorecatalog.data.NetworkAppRepository
import com.example.rustorecatalog.model.AppInfo
import com.example.rustorecatalog.network.BackendModule
import com.example.rustorecatalog.ui.state.LoadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AppDetailsUiState(
    val appState: LoadState<AppInfo> = LoadState.Idle,
    val appId: String? = null,
    val isRefreshing: Boolean = false
)

class AppDetailsViewModel(
    private val repository: AppRepository = NetworkAppRepository(BackendModule.api)
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppDetailsUiState())
    val uiState: StateFlow<AppDetailsUiState> = _uiState

    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    fun loadApp(appId: String) {
        refreshJob?.cancel()
        _uiState.value = _uiState.value.copy(
            appId = appId,
            appState = LoadState.Loading,
            isRefreshing = false
        )

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = repository.getAppById(appId)
            _uiState.value = _uiState.value.copy(
                appState = result.fold(
                    onSuccess = { app -> LoadState.Success(app) },
                    onFailure = { throwable ->
                        val msg = when (throwable) {
                            is kotlinx.serialization.SerializationException ->
                                "Получен некорректный ответ от сервера"
                            else -> throwable.message ?: "Не удалось загрузить приложение"
                        }
                        LoadState.Error(msg)
                    }
                ),
                isRefreshing = false
            )
        }
    }

    fun refresh() {
        val appId = _uiState.value.appId ?: return
        if (_uiState.value.isRefreshing) return

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val result = repository.getAppById(appId)
                _uiState.value = _uiState.value.copy(
                    appState = result.fold(
                        onSuccess = { app -> LoadState.Success(app) },
                        onFailure = { throwable ->
                            val msg = when (throwable) {
                                is kotlinx.serialization.SerializationException ->
                                    "Получен некорректный ответ от сервера"
                                else -> throwable.message ?: "Не удалось обновить приложение"
                            }
                            LoadState.Error(msg)
                        }
                    )
                )
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }
}
