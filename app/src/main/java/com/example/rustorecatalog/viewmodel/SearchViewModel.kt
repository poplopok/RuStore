package com.example.rustorecatalog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rustorecatalog.data.AppRepository
import com.example.rustorecatalog.data.NetworkAppRepository
import com.example.rustorecatalog.network.BackendModule
import com.example.rustorecatalog.model.AppInfo
import com.example.rustorecatalog.ui.state.LoadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val resultsState: LoadState<List<AppInfo>> = LoadState.Idle,
    val popularState: LoadState<List<AppInfo>> = LoadState.Idle,
    val isRefreshing: Boolean = false
)

class SearchViewModel(
    private val repository: AppRepository = NetworkAppRepository(BackendModule.api)
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    private var searchJob: Job? = null
    private var refreshJob: Job? = null
    private var lastSearchQuery: String = ""

    fun onQueryChange(newQuery: String) {
        _uiState.value = _uiState.value.copy(
            query = newQuery
        )

        if (newQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(
                resultsState = LoadState.Success(emptyList())
            )
            loadPopularIfNeeded()
            return
        }

        _uiState.value = _uiState.value.copy(
            resultsState = LoadState.Loading
        )

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(250)
            if (newQuery == lastSearchQuery) return@launch
            val result = repository.searchApps(newQuery)
            lastSearchQuery = newQuery
            _uiState.value = _uiState.value.copy(
                resultsState = result.fold(
                    onSuccess = { apps -> LoadState.Success(apps) },
                    onFailure = { throwable ->
                        val msg = when (throwable) {
                            is kotlinx.serialization.SerializationException ->
                                "Получен некорректный ответ от сервера"
                            else -> throwable.message ?: "Не удалось выполнить поиск"
                        }
                        LoadState.Error(msg)
                    }
                )
            )
            if (result.getOrNull().isNullOrEmpty()) {
                loadPopularIfNeeded()
            }
        }
    }

    fun loadPopularIfNeeded() {
        if (_uiState.value.popularState is LoadState.Success) return
        if (_uiState.value.popularState is LoadState.Loading) return

        _uiState.value = _uiState.value.copy(popularState = LoadState.Loading)
        viewModelScope.launch {
            val result = repository.getPopularApps()
            _uiState.value = _uiState.value.copy(
                popularState = result.fold(
                    onSuccess = { apps -> LoadState.Success(apps) },
                    onFailure = { throwable ->
                        val msg = when (throwable) {
                            is kotlinx.serialization.SerializationException ->
                                "Получен некорректный ответ от сервера"
                            else -> throwable.message ?: "Не удалось загрузить популярные приложения"
                        }
                        LoadState.Error(msg)
                    }
                )
            )
        }
    }

    fun refresh() {
        val query = _uiState.value.query
        _uiState.value = _uiState.value.copy(isRefreshing = true)

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (query.isBlank()) {
                val result = repository.getPopularApps()
                _uiState.value = _uiState.value.copy(
                    resultsState = LoadState.Success(emptyList()),
                    popularState = result.fold(
                        onSuccess = { apps -> LoadState.Success(apps) },
                        onFailure = { throwable ->
                            val msg = when (throwable) {
                                is kotlinx.serialization.SerializationException ->
                                    "Получен некорректный ответ от сервера"
                                else ->
                                    throwable.message ?: "Не удалось загрузить популярные приложения"
                            }
                            LoadState.Error(msg)
                        }
                    ),
                    isRefreshing = false
                )
                return@launch
            }

            val result = repository.searchApps(query)
            val apps = result.getOrNull().orEmpty()
            _uiState.value = _uiState.value.copy(
                resultsState = result.fold(
                    onSuccess = { list -> LoadState.Success(list) },
                    onFailure = { throwable ->
                        val msg = when (throwable) {
                            is kotlinx.serialization.SerializationException ->
                                "Получен некорректный ответ от сервера"
                            else -> throwable.message ?: "Не удалось выполнить поиск"
                        }
                        LoadState.Error(msg)
                    }
                )
            )
            if (apps.isEmpty()) {
                val popularResult = repository.getPopularApps()
                _uiState.value = _uiState.value.copy(
                    popularState = popularResult.fold(
                        onSuccess = { list -> LoadState.Success(list) },
                        onFailure = { throwable ->
                            val msg = when (throwable) {
                                is kotlinx.serialization.SerializationException ->
                                    "Получен некорректный ответ от сервера"
                                else -> throwable.message ?: "Не удалось загрузить популярные приложения"
                            }
                            LoadState.Error(msg)
                        }
                    )
                )
            }

            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }
}
