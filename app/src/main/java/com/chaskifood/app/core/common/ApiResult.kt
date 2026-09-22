package com.chaskifood.app.core.common

/**
 * Resultado de una operación en capa de datos (repo/red/base de datos).
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val message: String?, val cause: Throwable? = null) : ApiResult<Nothing>
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.data