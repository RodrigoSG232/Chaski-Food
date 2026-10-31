package com.chaskifood.app.feature.orders.domain

import com.chaskifood.app.core.access.domain.AccessProvenance
import kotlinx.coroutines.flow.Flow

data class StoreOrdersTarget(val businessId:String,val storeId:String) {
    init { require(listOf(businessId,storeId).all { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }) }
}
data class StoreOrdersContext(val ownerUid:String,val sessionGeneration:Long,val origin:AccessProvenance,
    val authorizationRevision:Long,val target:StoreOrdersTarget) {
    init { require(ownerUid.isNotBlank() && sessionGeneration>=0 && authorizationRevision>=0 && origin.session?.uid==ownerUid) }
}
sealed interface StoreOrdersAuthorization {
    data object Verifying:StoreOrdersAuthorization
    data object SignedOut:StoreOrdersAuthorization
    data object Denied:StoreOrdersAuthorization
    data object Unavailable:StoreOrdersAuthorization
    data class Allowed(val context:StoreOrdersContext):StoreOrdersAuthorization
}
data class StoreOrderLine(val productId:String,val name:String,val quantity:Int,val unitPriceMinor:Long,val lineTotalMinor:Long) {
    init {
        orderId(productId);orderText(name);require(quantity>0 && orderMinor(unitPriceMinor)>0)
        require(orderMinor(lineTotalMinor)==Math.multiplyExact(quantity.toLong(),unitPriceMinor))
    }
}
class StoreOrderSummary(val orderId:String,val createdAtEpochMillis:Long,items:List<StoreOrderLine>,val amounts:OrderAmounts,
    val status:OrderStatus=OrderStatus.PENDING_CONFIRMATION,val paymentMethod:OrderPaymentMethod=OrderPaymentMethod.CASH_ON_DELIVERY) {
    val items:List<StoreOrderLine> = frozenOrderList(items)
    init {
        orderId(orderId);require(createdAtEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS)
        require(this.items.isNotEmpty() && this.items.map{it.productId}.distinct().size==this.items.size)
        require(this.items.fold(0L){sum,i->orderMinor(Math.addExact(sum,i.lineTotalMinor))}==amounts.subtotalMinor)
    }
}
class StoreOrdersPage(val storeId:String,val storeName:String,orders:List<StoreOrderSummary>,val nextCursor:String?) {
    val orders:List<StoreOrderSummary> = frozenOrderList(orders)
    init {
        orderId(storeId);orderText(storeName)
        require(this.orders.map{it.orderId}.distinct().size==this.orders.size)
        require(nextCursor==null || (nextCursor.length<=1024 && nextCursor.matches(Regex("[A-Za-z0-9_-]+"))))
        require(this.orders.isNotEmpty() || nextCursor==null)
    }
}
sealed interface StoreOrdersResult {
    data class Loaded(val page:StoreOrdersPage):StoreOrdersResult
    data object AccessDenied:StoreOrdersResult
    data object AuthenticationRequired:StoreOrdersResult
    data object StoreMissing:StoreOrdersResult
    data object StaleContext:StoreOrdersResult
    data object InvalidCursor:StoreOrdersResult
    data object Unavailable:StoreOrdersResult
}
interface StoreOrdersRepository {
    fun observeAuthorization(target:StoreOrdersTarget):Flow<StoreOrdersAuthorization>
    fun isCurrent(context:StoreOrdersContext):Boolean
    suspend fun page(context:StoreOrdersContext,cursor:String?=null):StoreOrdersResult
}
