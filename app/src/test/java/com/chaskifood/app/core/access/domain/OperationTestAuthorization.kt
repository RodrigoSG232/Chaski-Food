package com.chaskifood.app.core.access.domain

import kotlinx.coroutines.flow.emptyFlow

/** Los tests antiguos de operaciones aíslan sus reglas; los de autorización usan el adaptador real. */
object OperationTestAuthorization : StoreAuthorization {
    override val enforced = false
    override val changes = emptyFlow<Unit>()
    override fun hasAuthorizedStores() = true
    override fun allows(businessId: String, storeId: String) = true
}
