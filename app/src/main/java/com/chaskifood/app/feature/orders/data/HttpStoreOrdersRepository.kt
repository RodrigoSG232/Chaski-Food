package com.chaskifood.app.feature.orders.data

import com.chaskifood.app.feature.checkout.data.*
import com.chaskifood.app.feature.orders.domain.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class HttpStoreOrdersRepository @Inject constructor(private val api:StoreOrdersApi,
    private val tokens:BuyerTokenSource,private val access:StoreOrdersAccessSource):StoreOrdersRepository {
    override fun observeAuthorization(target:StoreOrdersTarget)=access.observe(target)
    override fun isCurrent(context:StoreOrdersContext)=access.current(context.target)==StoreOrdersAuthorization.Allowed(context)
    override suspend fun simulatePickup(context:StoreOrdersContext,orderId:String)=simulateDelivery(context,orderId,true)
    override suspend fun simulateArrival(context:StoreOrdersContext,orderId:String)=simulateDelivery(context,orderId,false)
    private suspend fun simulateDelivery(context:StoreOrdersContext,orderId:String,pickup:Boolean):SimulatedStoreDeliveryResult {
        if(!isCurrent(context))return SimulatedStoreDeliveryResult.StaleContext
        if(!orderId.matches(Regex("[0-9a-f]{64}")))return SimulatedStoreDeliveryResult.NotFound
        return try { withTimeoutOrNull(15_000) {
            val token=tokens.token(context.ownerUid,context.sessionGeneration)
            val response=if(pickup)api.simulatePickup(context.target.storeId,orderId,"Bearer $token",buildJsonObject{})
                else api.simulateArrival(context.target.storeId,orderId,"Bearer $token",buildJsonObject{})
            if(!isCurrent(context)||!tokens.isCurrent(context.ownerUid,context.sessionGeneration))
                return@withTimeoutOrNull SimulatedStoreDeliveryResult.StaleContext
            when(response.code()){
                200->{val body=requireNotNull(response.body());require(body.contractVersion==1&&body.status=="IN_DELIVERY"&&
                    body.outcome in setOf("UPDATED","UNCHANGED"));SimulatedStoreDeliveryResult.Updated}
                401->SimulatedStoreDeliveryResult.AuthenticationRequired
                403->SimulatedStoreDeliveryResult.AccessDenied
                404->SimulatedStoreDeliveryResult.NotFound
                409->SimulatedStoreDeliveryResult.StateChanged
                else->SimulatedStoreDeliveryResult.Unavailable
            }
        }?:SimulatedStoreDeliveryResult.Unavailable }catch(e:CancellationException){throw e}
        catch(_:BuyerSessionChanged){SimulatedStoreDeliveryResult.StaleContext}
        catch(_:BuyerAuthenticationRequired){SimulatedStoreDeliveryResult.AuthenticationRequired}
        catch(_:Exception){SimulatedStoreDeliveryResult.Unavailable}
    }
    override suspend fun markReady(context:StoreOrdersContext,orderId:String):MarkOrderReadyResult {
        if(!isCurrent(context))return MarkOrderReadyResult.StaleContext
        if(!orderId.matches(Regex("[0-9a-f]{64}")))return MarkOrderReadyResult.NotFound
        return try { withTimeoutOrNull(15_000) {
            val token=tokens.token(context.ownerUid,context.sessionGeneration)
            val response=api.markReady(context.target.storeId,orderId,"Bearer $token",buildJsonObject{})
            if(!isCurrent(context)||!tokens.isCurrent(context.ownerUid,context.sessionGeneration))return@withTimeoutOrNull MarkOrderReadyResult.StaleContext
            when(response.code()){
                200->{val body=requireNotNull(response.body());require(body.contractVersion==1&&body.status=="READY_FOR_PICKUP"&&
                    body.readyAtEpochMillis!=null)
                    MarkOrderReadyResult.Ready(body.readyAtEpochMillis)}
                401->MarkOrderReadyResult.AuthenticationRequired;403->MarkOrderReadyResult.AccessDenied
                402->MarkOrderReadyResult.PaymentRequired;404->MarkOrderReadyResult.NotFound;409->MarkOrderReadyResult.StateChanged
                else->MarkOrderReadyResult.Unavailable
            }
        }?:MarkOrderReadyResult.Unavailable }catch(e:CancellationException){throw e}
        catch(_:BuyerSessionChanged){MarkOrderReadyResult.StaleContext}
        catch(_:BuyerAuthenticationRequired){MarkOrderReadyResult.AuthenticationRequired}
        catch(_:Exception){MarkOrderReadyResult.Unavailable}
    }
    override suspend fun extendPreparation(context:StoreOrdersContext,orderId:String,extension:PreparationExtension):PreparationExtensionResult {
        if(!isCurrent(context))return PreparationExtensionResult.StaleContext
        if(!orderId.matches(Regex("[0-9a-f]{64}")))return PreparationExtensionResult.NotFound
        return try { withTimeoutOrNull(15_000) {
            val token=tokens.token(context.ownerUid,context.sessionGeneration)
            val body=buildJsonObject{put("operationId",extension.operationId);put("additionalMinutes",extension.additionalMinutes)
                put("expectedEstimateRevision",extension.expectedEstimateRevision)}
            val response=api.extendPreparation(context.target.storeId,orderId,"Bearer $token",body)
            if(!isCurrent(context)||!tokens.isCurrent(context.ownerUid,context.sessionGeneration))
                return@withTimeoutOrNull PreparationExtensionResult.StaleContext
            when(response.code()){
                200->{val value=requireNotNull(response.body())
                    require(value.contractVersion==1&&value.outcome=="EXTENDED"&&value.status=="PREPARING"&&
                        value.estimatedReadyAtEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS&&value.estimateRevision>=1&&
                        value.preparationExtendedMinutes>=extension.additionalMinutes)
                    PreparationExtensionResult.Extended(value.estimatedReadyAtEpochMillis,value.estimateRevision,value.preparationExtendedMinutes)}
                401->PreparationExtensionResult.AuthenticationRequired
                403->PreparationExtensionResult.AccessDenied
                404->PreparationExtensionResult.NotFound
                409->when(errorCode(response.errorBody()?.string())){
                    "ESTIMATE_REVISION_CHANGED"->PreparationExtensionResult.RevisionChanged
                    "OPERATION_CONFLICT"->PreparationExtensionResult.OperationConflict
                    else->PreparationExtensionResult.StateChanged
                }
                else->PreparationExtensionResult.Unavailable
            }
        }?:PreparationExtensionResult.Unavailable }catch(e:CancellationException){throw e}
        catch(_:BuyerSessionChanged){PreparationExtensionResult.StaleContext}
        catch(_:BuyerAuthenticationRequired){PreparationExtensionResult.AuthenticationRequired}
        catch(_:Exception){PreparationExtensionResult.Unavailable}
    }
    /** E3: one attempt; never logs nor stores the code. 409/423 bodies carry the business outcome. */
    override suspend fun verifyPickup(context:StoreOrdersContext,orderId:String,attempt:PickupVerificationAttempt):PickupVerificationResult {
        if(!isCurrent(context))return PickupVerificationResult.StaleContext
        if(!orderId.matches(Regex("[0-9a-f]{64}")))return PickupVerificationResult.NotFound
        return try { withTimeoutOrNull(15_000) {
            val token=tokens.token(context.ownerUid,context.sessionGeneration)
            val body=buildJsonObject{put("operationId",attempt.operationId);put("code",attempt.code)
                put("assignmentId",attempt.assignmentId);put("assignmentVersion",attempt.assignmentVersion)}
            val response=api.verifyPickup(context.target.storeId,orderId,"Bearer $token",body)
            if(!isCurrent(context)||!tokens.isCurrent(context.ownerUid,context.sessionGeneration))
                return@withTimeoutOrNull PickupVerificationResult.StaleContext
            when(response.code()){
                200->{val value=requireNotNull(response.body());require(value.contractVersion==1)
                    when(value.outcome){"VERIFIED"->PickupVerificationResult.Verified
                        "ALREADY_VERIFIED"->PickupVerificationResult.AlreadyVerified
                        else->PickupVerificationResult.Unavailable}}
                401->PickupVerificationResult.AuthenticationRequired
                403->PickupVerificationResult.AccessDenied
                404->PickupVerificationResult.NotFound
                423->PickupVerificationResult.Locked
                409->{val error=response.errorBody()?.string()
                    when(errorCode(error)){
                        "PICKUP_CODE_INVALID"->PickupVerificationResult.InvalidCode(requireNotNull(attemptsRemaining(error)).also{require(it in 0..5)})
                        "ASSIGNMENT_CHANGED"->PickupVerificationResult.AssignmentChanged
                        "IDEMPOTENCY_CONFLICT"->PickupVerificationResult.OperationConflict
                        else->PickupVerificationResult.StateChanged}}
                else->PickupVerificationResult.Unavailable
            }
        }?:PickupVerificationResult.Unavailable }catch(e:CancellationException){throw e}
        catch(_:BuyerSessionChanged){PickupVerificationResult.StaleContext}
        catch(_:BuyerAuthenticationRequired){PickupVerificationResult.AuthenticationRequired}
        catch(_:Exception){PickupVerificationResult.Unavailable}
    }
    private fun attemptsRemaining(body:String?):Int?=runCatching{
        Json.parseToJsonElement(requireNotNull(body)).jsonObject["attemptsRemaining"]?.jsonPrimitive?.content?.toInt()}.getOrNull()
    private fun StorePickupResponse.toDomain()=StorePickup(assignment?.let{StoreRiderAssignment(it.assignmentId,it.assignmentVersion,
        it.riderDisplayName,it.vehicleType,it.vehiclePlate)},verified,locked,attemptsRemaining)
    private fun errorCode(body:String?):String?=runCatching{
        Json.parseToJsonElement(requireNotNull(body)).jsonObject["error"]?.jsonPrimitive?.content}.getOrNull()
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
                                readyAtEpochMillis=o.readyAtEpochMillis,
                                simulationStage=o.simulationStage,estimateRevision=o.estimateRevision,
                                preparationExtendedMinutes=o.preparationExtendedMinutes,
                                pickup=if(status==OrderStatus.READY_FOR_PICKUP)o.pickup?.toDomain() else null)
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
