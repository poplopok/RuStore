package com.example.rustorecatalog.ui.state

sealed interface LoadState<out T> {
    object Idle : LoadState<Nothing>
    object Loading : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

