package com.example.rustorecatalog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rustorecatalog.data.AppRepository
import com.example.rustorecatalog.data.NetworkAppRepository
import com.example.rustorecatalog.model.AppCategory
import com.example.rustorecatalog.model.AppInfo
import com.example.rustorecatalog.network.BackendModule
import com.example.rustorecatalog.ui.state.LoadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class AppStoreUiState(
    val category: AppCategory? = null,
    val appsState: LoadState<List<AppInfo>> = LoadState.Idle,
    val isRefreshing: Boolean = false
)

class AppStoreViewModel(
    private val repository: AppRepository = NetworkAppRepository(BackendModule.api)
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppStoreUiState())
    val uiState: StateFlow<AppStoreUiState> = _uiState

    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    fun loadApps(category: AppCategory?) {
        if (_uiState.value.appsState is LoadState.Loading) return
        refreshJob?.cancel()
        _uiState.value = _uiState.value.copy(
            category = category,
            appsState = LoadState.Loading,
            isRefreshing = false
        )

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = repository.getAppsByCategory(category)
            _uiState.value = result.fold(
                onSuccess = { apps ->
                    _uiState.value.copy(
                        appsState = LoadState.Success(apps),
                        isRefreshing = false
                    )
                },
                onFailure = { throwable ->
                    val msg = when (throwable) {
                        is kotlinx.serialization.SerializationException ->
                            "Получен некорректный ответ от сервера"
                        else -> throwable.message ?: "Не удалось загрузить приложения"
                    }
                    _uiState.value.copy(
                        appsState = LoadState.Error(msg),
                        isRefreshing = false
                    )
                }
            )
        }
    }

    fun refresh() {
        val category = _uiState.value.category
        if (_uiState.value.isRefreshing) return

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val result = withTimeoutOrNull(15000) {
                    repository.getAppsByCategory(category)
                } ?: Result.failure(IllegalStateException("Таймаут обновления. Проверьте сеть и повторите."))
                _uiState.value = result.fold(
                    onSuccess = { apps ->
                        _uiState.value.copy(appsState = LoadState.Success(apps))
                    },
                    onFailure = { throwable ->
                        val msg = when (throwable) {
                            is kotlinx.serialization.SerializationException ->
                                "Получен некорректный ответ от сервера"
                            else -> throwable.message ?: "Не удалось обновить список"
                        }
                        _uiState.value.copy(appsState = LoadState.Error(msg))
                    }
                )
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }
}
