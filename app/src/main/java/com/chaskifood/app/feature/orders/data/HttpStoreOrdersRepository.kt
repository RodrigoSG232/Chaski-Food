package com.chaskifood.app.feature.orders.data

import com.chaskifood.app.feature.checkout.data.*
import com.chaskifood.app.feature.orders.domain.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal class HttpStoreOrdersRepository @Inject constructor(private val api:StoreOrdersApi,
    private val tokens:BuyerTokenSource,private val access:StoreOrdersAccessSource):StoreOrdersRepository {
    override fun observeAuthorization(target:StoreOrdersTarget)=access.observe(target)
    override fun isCurrent(context:StoreOrdersContext)=access.current(context.target)==StoreOrdersAuthorization.Allowed(context)
    override suspend fun page(context:StoreOrdersContext,cursor:String?):StoreOrdersResult {
        if(!isCurrent(context))return StoreOrdersResult.StaleContext
        return try {
            withTimeoutOrNull(15_000) {
                val token=tokens.token(context.ownerUid,context.sessionGeneration)
                if(!isCurrent(context))return@withTimeoutOrNull StoreOrdersResult.StaleContext
                val response=api.page(context.target.storeId,"Bearer $token",cursor)
                if(!isCurrent(context)||!tokens.isCurrent(context.ownerUid,context.sessionGeneration))
                    return@withTimeoutOrNull StoreOrdersResult.StaleContext
                when(response.code()) {
                    401->StoreOrdersResult.AuthenticationRequired
                    403->StoreOrdersResult.AccessDenied
                    404->StoreOrdersResult.StoreMissing
                    400->StoreOrdersResult.InvalidCursor
                    200->{
                        val body=requireNotNull(response.body())
                        require(body.contractVersion==1 && body.storeId==context.target.storeId && body.orders.size<=20)
                        val orders=body.orders.map { o->
                            require(o.status=="PENDING_CONFIRMATION" && o.currency=="PEN" && o.paymentMethod=="CASH_ON_DELIVERY")
                            StoreOrderSummary(o.orderId,o.createdAtEpochMillis,o.items.map {i->
                                StoreOrderLine(i.productId,i.name,i.quantity,i.unitPriceMinor,i.lineTotalMinor)},
                                OrderAmounts(o.subtotalMinor,o.deliveryFeeMinor,o.totalMinor))
                        }
                        require(cursor==null || body.nextCursor!=cursor)
                        StoreOrdersResult.Loaded(StoreOrdersPage(body.storeId,body.storeName,orders,body.nextCursor))
                    }
                    else->StoreOrdersResult.Unavailable
                }
            }?:StoreOrdersResult.Unavailable
        }catch(cancelled:CancellationException){throw cancelled}
         catch(_:BuyerSessionChanged){StoreOrdersResult.StaleContext}
         catch(_:BuyerAuthenticationRequired){StoreOrdersResult.AuthenticationRequired}
         catch(_:Exception){StoreOrdersResult.Unavailable}
    }
}
