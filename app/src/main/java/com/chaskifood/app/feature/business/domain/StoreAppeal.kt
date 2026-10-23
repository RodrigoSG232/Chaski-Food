package com.chaskifood.app.feature.business.domain

import com.chaskifood.app.core.common.ApiResult
import kotlinx.coroutines.flow.Flow

data class StoreAppeal(
    val id: String = "", val storeId: String = "", val businessId: String = "",
    val ownerUid: String = "", val storeName: String = "", val suspensionId: String = "",
    val suspensionReason: String = "", val explanation: String = "",
    val status: String = "PENDING", val response: String = "", val createdAt: Long = 0,
)

interface StoreAppealRepository {
    fun observeAppeals(admin: Boolean): Flow<ApiResult<List<StoreAppeal>>>
    suspend fun submit(storeId: String, explanation: String): ApiResult<Unit>
    suspend fun resolve(id: String, accepted: Boolean, response: String): ApiResult<Unit>
}

internal fun validateAppealText(value: String): String = value.trim().also {
    require(it.length in 10..2000) { "Escribe entre 10 y 2000 caracteres." }
}
