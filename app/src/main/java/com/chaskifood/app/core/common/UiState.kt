package com.chaskifood.app.core.common

/**
 * Estado de una pantalla con los tres estados del patrón
 * Loading / Error / Content.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String?, val retry: (() -> Unit)? = null) : UiState<Nothing>
}