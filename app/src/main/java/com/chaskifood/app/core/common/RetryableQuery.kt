package com.chaskifood.app.core.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** A retry replaces the failed listener and exposes loading before the new result. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> retryableQuery(retries: Flow<Int>, query: () -> Flow<ApiResult<T>>): Flow<ApiResult<T>?> =
    retries.flatMapLatest {
        flow<ApiResult<T>?> {
            emit(null)
            emitAll(query())
        }.catch { emit(ApiResult.Failure("No se pudieron cargar los datos. Revisa tu conexión y vuelve a intentar.", it)) }
    }
