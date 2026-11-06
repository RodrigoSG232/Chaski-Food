package com.chaskifood.app.feature.orders.data

import com.chaskifood.app.feature.checkout.data.*
import com.chaskifood.app.feature.orders.domain.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class HttpStoreOrdersRepository @Inject constructor(private val api:StoreOrdersApi,
    private val tokens:BuyerTokenSource,private val access:StoreOrdersAccessSource):StoreOrdersRepository {
    override fun observeAuthorization(target:StoreOrdersTarget)=access.observe(target)
    override fun isCurrent(context:StoreOrdersContext)=access.current(context.target)==StoreOrdersAuthorization.Allowed(context)
    override suspend fun markReady(context:StoreOrdersContext,orderId:String):MarkOrderReadyResult {
        if(!isCurrent(context))return MarkOrderReadyResult.StaleContext
        if(!orderId.matches(Regex("[0-9a-f]{64}")))return MarkOrderReadyResult.NotFound
        return try { withTimeoutOrNull(15_000) {
            val token=tokens.token(context.ownerUid,context.sessionGeneration)
            val response=api.markReady(context.target.storeId,orderId,"Bearer $token",buildJsonObject{})
            if(!isCurrent(context)||!tokens.isCurrent(context.ownerUid,context.sessionGeneration))return@withTimeoutOrNull MarkOrderReadyResult.StaleContext
            when(response.code()){
                200->{val body=requireNotNull(response.body());require(body.contractVersion==1&&body.status=="READY_FOR_PICKUP"&&
                    body.pickupCode?.matches(Regex("[0-9]{4}"))==true&&body.readyAtEpochMillis!=null)
                    MarkOrderReadyResult.Ready(body.pickupCode,body.readyAtEpochMillis)}
                401->MarkOrderReadyResult.AuthenticationRequired;403->MarkOrderReadyResult.AccessDenied
                404->MarkOrderReadyResult.NotFound;409->MarkOrderReadyResult.StateChanged
                else->MarkOrderReadyResult.Unavailable
            }
        }?:MarkOrderReadyResult.Unavailable }catch(e:CancellationException){throw e}
        catch(_:BuyerSessionChanged){MarkOrderReadyResult.StaleContext}
        catch(_:BuyerAuthenticationRequired){MarkOrderReadyResult.AuthenticationRequired}
        catch(_:Exception){MarkOrderReadyResult.Unavailable}
    }
    override suspend fun updateEstimate(context:StoreOrdersContext,orderId:String,minutes:Int):PreparationEstimateResult {
        if(!isCurrent(context))return PreparationEstimateResult.StaleContext
        if(!orderId.matches(Regex("[0-9a-f]{64}"))||minutes !in 5..180)return PreparationEstimateResult.NotFound
        return try { withTimeoutOrNull(15_000) {
            val token=tokens.token(context.ownerUid,context.sessionGeneration)
            val response=api.updateEstimate(context.target.storeId,orderId,"Bearer $token",buildJsonObject{put("estimatedPreparationMinutes",minutes)})
            if(!isCurrent(context)||!tokens.isCurrent(context.ownerUid,context.sessionGeneration))return@withTimeoutOrNull PreparationEstimateResult.StaleContext
            when(response.code()){
                200->{val body=requireNotNull(response.body());require(body.contractVersion==1&&body.status=="PREPARING"&&body.estimatedReadyAtEpochMillis!=null)
                    PreparationEstimateResult.Updated(body.estimatedReadyAtEpochMillis)}
                401->PreparationEstimateResult.AuthenticationRequired;403->PreparationEstimateResult.AccessDenied
                404->PreparationEstimateResult.NotFound;409->PreparationEstimateResult.StateChanged
                else->PreparationEstimateResult.Unavailable
            }
        }?:PreparationEstimateResult.Unavailable }catch(e:CancellationException){throw e}catch(_:Exception){PreparationEstimateResult.Unavailable}
    }
    override suspend fun respond(context:StoreOrdersContext,orderId:String,decision:StoreOrderDecision):StoreOrderDecisionResult {
        if(!isCurrent(context))return StoreOrderDecisionResult.StaleContext
        if(!orderId.matches(Regex("[0-9a-f]{64}")))return StoreOrderDecisionResult.NotFound
        return try {
            withTimeoutOrNull(15_000) {
                val token=tokens.token(context.ownerUid,context.sessionGeneration)
                if(!isCurrent(context))return@withTimeoutOrNull StoreOrderDecisionResult.StaleContext
                val body=buildJsonObject { when(decision) {
                    is StoreOrderDecision.Accept->{put("action","ACCEPT");put("estimatedPreparationMinutes",decision.estimatedPreparationMinutes)}
                    is StoreOrderDecision.Reject->{put("action","REJECT");put("reason",decision.reason.name);decision.explanation?.let{put("explanation",it.trim())}}
                }}
                val response=api.respond(context.target.storeId,orderId,"Bearer $token",body)
                if(!isCurrent(context)||!tokens.isCurrent(context.ownerUid,context.sessionGeneration))
                    return@withTimeoutOrNull StoreOrderDecisionResult.StaleContext
                when(response.code()) {
                    200->{val value=requireNotNull(response.body());require(value.contractVersion==1)
                        when(value.outcome){
                            "ACCEPTED"->{require(value.status=="PREPARING" && value.estimatedReadyAtEpochMillis!=null)
                                StoreOrderDecisionResult.Accepted(value.estimatedReadyAtEpochMillis)}
                            "REJECTED"->{require(value.status=="REJECTED" && value.estimatedReadyAtEpochMillis==null);StoreOrderDecisionResult.Rejected}
                            else->StoreOrderDecisionResult.Unavailable}}
                    401->StoreOrderDecisionResult.AuthenticationRequired
                    403->StoreOrderDecisionResult.AccessDenied
                    404->StoreOrderDecisionResult.NotFound
                    409->StoreOrderDecisionResult.StateChanged
                    423->StoreOrderDecisionResult.AcceptanceSuspended
                    else->StoreOrderDecisionResult.Unavailable
                }
            }?:StoreOrderDecisionResult.Unavailable
        }catch(cancelled:CancellationException){throw cancelled}
         catch(_:BuyerSessionChanged){StoreOrderDecisionResult.StaleContext}
         catch(_:BuyerAuthenticationRequired){StoreOrderDecisionResult.AuthenticationRequired}
         catch(_:Exception){StoreOrderDecisionResult.Unavailable}
    }
    override suspend fun page(context:StoreOrdersContext,cursor:String?,status:OrderStatus):StoreOrdersResult {
        if(!isCurrent(context))return StoreOrdersResult.StaleContext
        return try {
            withTimeoutOrNull(15_000) {
                val token=tokens.token(context.ownerUid,context.sessionGeneration)
                if(!isCurrent(context))return@withTimeoutOrNull StoreOrdersResult.StaleContext
                val response=api.page(context.target.storeId,"Bearer $token",cursor,status.name)
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
                            require(o.status==status.name && o.currency=="PEN" && o.paymentMethod=="CASH_ON_DELIVERY")
                            StoreOrderSummary(o.orderId,o.createdAtEpochMillis,o.items.map {i->
                                StoreOrderLine(i.productId,i.name,i.quantity,i.unitPriceMinor,i.lineTotalMinor)},
                                OrderAmounts(o.subtotalMinor,o.deliveryFeeMinor,o.totalMinor,o.tipMinor),status,
                                estimatedPreparationMinutes=o.estimatedPreparationMinutes,estimatedReadyAtEpochMillis=o.estimatedReadyAtEpochMillis,
                                pickupCode=o.pickupCode,readyAtEpochMillis=o.readyAtEpochMillis)
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
