package com.example.rustorecatalog.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rustorecatalog.data.AppRepository
import com.example.rustorecatalog.data.NetworkAppRepository
import com.example.rustorecatalog.network.BackendModule
import com.example.rustorecatalog.model.AppCategory
import com.example.rustorecatalog.ui.state.LoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CategoryInfo(
    val category: AppCategory,
    val count: Int
)

class CategoriesViewModel(
    private val repository: AppRepository = NetworkAppRepository(BackendModule.api)
) : ViewModel() {

    private val _categoriesState =
        MutableStateFlow<LoadState<List<CategoryInfo>>>(LoadState.Idle)
    val categoriesState: StateFlow<LoadState<List<CategoryInfo>>> = _categoriesState

    fun loadCategories() {
        if (_categoriesState.value is LoadState.Loading) return

        _categoriesState.value = LoadState.Loading
        viewModelScope.launch {
            val result = repository.getAllApps()
            _categoriesState.value = result.fold(
                onSuccess = { apps ->
                    val grouped = apps.groupBy { it.category }
                    val list = AppCategory.values().map { category ->
                        CategoryInfo(
                            category = category,
                            count = grouped[category]?.size ?: 0
                        )
                    }
                    LoadState.Success(list)
                },
                onFailure = { throwable ->
                    val msg = when (throwable) {
                        is kotlinx.serialization.SerializationException ->
                            "Получен некорректный ответ от сервера"
                        else -> throwable.message ?: "Не удалось загрузить категории"
                    }
                    LoadState.Error(msg)
                }
            )
        }
    }
}

