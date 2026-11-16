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
/** E3: logistics data the store may see. Never the rider id or phone, and never the pickup code. */
data class StoreRiderAssignment(val assignmentId:String,val assignmentVersion:Int,val riderDisplayName:String?,
    val vehicleType:String?,val vehiclePlate:String?) {
    init { require(assignmentId.matches(Regex("[A-Za-z0-9_-]{1,100}")) && assignmentVersion>=1) }
    val key get()=assignmentId+":"+assignmentVersion
}
/** E3: pickup security as projected by foodApi for READY_FOR_PICKUP orders. */
data class StorePickup(val assignment:StoreRiderAssignment?,val verified:Boolean,val locked:Boolean,val attemptsRemaining:Int) {
    init { require(attemptsRemaining in 0..5 && (!verified || assignment!=null) && (!locked || attemptsRemaining==0)) }
    val canVerify get()=assignment!=null && !verified && !locked
}
/**
 * HU21-B: the code typed by the store, as dictated by the rider. A new code is a new [operationId]; a network retry of
 * the same attempt reuses it. [toString] never reveals the code.
 */
class PickupVerificationAttempt(val operationId:String,val code:String,val assignmentId:String,val assignmentVersion:Int) {
    init {
        require(operationId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
        require(code.matches(Regex("[0-9]{4}")) && assignmentId.matches(Regex("[A-Za-z0-9_-]{1,100}")) && assignmentVersion>=1)
    }
    override fun toString()="PickupVerificationAttempt(operationId="+operationId+", assignment="+assignmentId+":"+assignmentVersion+")"
}
sealed interface PickupVerificationResult {
    /** Code accepted for the current assignment. The order stays READY_FOR_PICKUP until Rider confirms PICKED_UP. */
    data object Verified:PickupVerificationResult
    data object AlreadyVerified:PickupVerificationResult
    data class InvalidCode(val attemptsRemaining:Int):PickupVerificationResult
    data object Locked:PickupVerificationResult
    data object AssignmentChanged:PickupVerificationResult
    data object StateChanged:PickupVerificationResult
    data object OperationConflict:PickupVerificationResult
    data object AccessDenied:PickupVerificationResult
    data object AuthenticationRequired:PickupVerificationResult
    data object NotFound:PickupVerificationResult
    data object StaleContext:PickupVerificationResult
    /** Timeout or network failure: the server may have processed it; only the same operation may be retried. */
    data object Unavailable:PickupVerificationResult
}
class StoreOrderSummary(val orderId:String,val createdAtEpochMillis:Long,items:List<StoreOrderLine>,val amounts:OrderAmounts,
    val status:OrderStatus=OrderStatus.PENDING_CONFIRMATION,val paymentMethod:OrderPaymentMethod=OrderPaymentMethod.CASH_ON_DELIVERY,
    val estimatedPreparationMinutes:Int?=null,val estimatedReadyAtEpochMillis:Long?=null,
    val readyAtEpochMillis:Long?=null,val simulationStage:String?=null,
    val estimateRevision:Long=0,val preparationExtendedMinutes:Int=0,val pickup:StorePickup?=null,
    /** E4: null on older backends; PENDING carries what the rider still has to collect. */
    val paymentStatus:OrderPaymentStatus?=null,val amountToCollectMinor:Long?=null) {
    val items:List<StoreOrderLine> = frozenOrderList(items)
    init {
        orderId(orderId);require(createdAtEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS)
        require(this.items.isNotEmpty() && this.items.map{it.productId}.distinct().size==this.items.size)
        require(this.items.fold(0L){sum,i->orderMinor(Math.addExact(sum,i.lineTotalMinor))}==amounts.subtotalMinor)
        if(status==OrderStatus.PREPARING || status==OrderStatus.READY_FOR_PICKUP)require(estimatedPreparationMinutes!=null&&estimatedPreparationMinutes in 5..180 &&
            estimatedReadyAtEpochMillis!=null&&estimatedReadyAtEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS)
        else require(estimatedPreparationMinutes==null&&estimatedReadyAtEpochMillis==null)
        // E3: the pickup code is never required (nor kept) on the store side; the rider dictates it.
        if(status==OrderStatus.READY_FOR_PICKUP)require(readyAtEpochMillis!=null&&readyAtEpochMillis in 1..MAX_ORDER_EPOCH_MILLIS)
        else require(readyAtEpochMillis==null&&pickup==null)
        require(simulationStage==null || (status==OrderStatus.IN_DELIVERY && simulationStage in setOf("ON_THE_WAY","AT_CUSTOMER")))
        require(estimateRevision>=0 && preparationExtendedMinutes>=0)
        when(paymentStatus){
            OrderPaymentStatus.PENDING->require(amountToCollectMinor==amounts.totalMinor)
            OrderPaymentStatus.PAID->require(amountToCollectMinor==0L)
            null->require(amountToCollectMinor==null)
        }
    }
    fun withExtension(estimatedReadyAtEpochMillis:Long,estimateRevision:Long,preparationExtendedMinutes:Int)=StoreOrderSummary(orderId,
        createdAtEpochMillis,items,amounts,status,paymentMethod,estimatedPreparationMinutes,estimatedReadyAtEpochMillis,
        readyAtEpochMillis,simulationStage,estimateRevision,preparationExtendedMinutes,pickup,paymentStatus,amountToCollectMinor)
    /** Rider manages the order once assigned, or once picked up without a Food simulation stage (UI hint only, not security). */
    val riderManaged get()=pickup?.assignment!=null || (status==OrderStatus.IN_DELIVERY && simulationStage==null)
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
enum class StoreOrderRejectionReason { STORE_BUSY, PRODUCT_UNAVAILABLE, CLOSING_SOON, OTHER }
sealed interface StoreOrderDecision {
    data class Accept(val estimatedPreparationMinutes:Int):StoreOrderDecision {
        init { require(estimatedPreparationMinutes in 5..180) }
    }
    data class Reject(val reason:StoreOrderRejectionReason,val explanation:String?=null):StoreOrderDecision {
        init {
            if(reason==StoreOrderRejectionReason.OTHER)require(!explanation.isNullOrBlank() && explanation.trim().length in 3..300)
            else require(explanation==null)
        }
    }
}
sealed interface StoreOrderDecisionResult {
    data class Accepted(val estimatedReadyAtEpochMillis:Long):StoreOrderDecisionResult
    data object Rejected:StoreOrderDecisionResult
    data object StateChanged:StoreOrderDecisionResult
    data object AcceptanceSuspended:StoreOrderDecisionResult
    data object AccessDenied:StoreOrderDecisionResult
    data object AuthenticationRequired:StoreOrderDecisionResult
    data object NotFound:StoreOrderDecisionResult
    data object StaleContext:StoreOrderDecisionResult
    data object Unavailable:StoreOrderDecisionResult
}
/**
 * HU20: one explicit extension of the current preparation estimate. [operationId] identifies this intent: a retry
 * reuses it so the server never adds the minutes twice; a new intent always uses a new ID.
 */
data class PreparationExtension(val operationId:String,val additionalMinutes:Int,val expectedEstimateRevision:Long) {
    init {
        require(operationId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
        require(additionalMinutes in MIN_MINUTES..MAX_MINUTES && expectedEstimateRevision>=0)
    }
    companion object { const val MIN_MINUTES=5; const val MAX_MINUTES=60 }
}
sealed interface PreparationExtensionResult {
    data class Extended(val estimatedReadyAtEpochMillis:Long,val estimateRevision:Long,val preparationExtendedMinutes:Int):PreparationExtensionResult
    /** Another change moved the estimate first; the screen must reload and ask again. */
    data object RevisionChanged:PreparationExtensionResult
    data object OperationConflict:PreparationExtensionResult
    data object StateChanged:PreparationExtensionResult
    data object AccessDenied:PreparationExtensionResult
    data object AuthenticationRequired:PreparationExtensionResult
    data object NotFound:PreparationExtensionResult
    data object StaleContext:PreparationExtensionResult
    /** Timeout or network failure: the outcome is unknown, so only the same operation may be retried. */
    data object Unavailable:PreparationExtensionResult
}
sealed interface MarkOrderReadyResult {
    data class Ready(val readyAtEpochMillis:Long):MarkOrderReadyResult
    data object PaymentRequired:MarkOrderReadyResult
    data object StateChanged:MarkOrderReadyResult
    data object AccessDenied:MarkOrderReadyResult
    data object AuthenticationRequired:MarkOrderReadyResult
    data object NotFound:MarkOrderReadyResult
    data object StaleContext:MarkOrderReadyResult
    data object Unavailable:MarkOrderReadyResult
}
sealed interface SimulatedStoreDeliveryResult {
    data object Updated:SimulatedStoreDeliveryResult
    data object StateChanged:SimulatedStoreDeliveryResult
    data object AccessDenied:SimulatedStoreDeliveryResult
    data object AuthenticationRequired:SimulatedStoreDeliveryResult
    data object NotFound:SimulatedStoreDeliveryResult
    data object StaleContext:SimulatedStoreDeliveryResult
    data object Unavailable:SimulatedStoreDeliveryResult
}
interface StoreOrdersRepository {
    fun observeAuthorization(target:StoreOrdersTarget):Flow<StoreOrdersAuthorization>
    fun isCurrent(context:StoreOrdersContext):Boolean
    suspend fun page(context:StoreOrdersContext,cursor:String?=null,status:OrderStatus=OrderStatus.PENDING_CONFIRMATION):StoreOrdersResult
    suspend fun respond(context:StoreOrdersContext,orderId:String,decision:StoreOrderDecision):StoreOrderDecisionResult
    suspend fun extendPreparation(context:StoreOrdersContext,orderId:String,extension:PreparationExtension):PreparationExtensionResult
    suspend fun markReady(context:StoreOrdersContext,orderId:String):MarkOrderReadyResult
    suspend fun simulatePickup(context:StoreOrdersContext,orderId:String):SimulatedStoreDeliveryResult = SimulatedStoreDeliveryResult.Unavailable
    suspend fun simulateArrival(context:StoreOrdersContext,orderId:String):SimulatedStoreDeliveryResult = SimulatedStoreDeliveryResult.Unavailable
    suspend fun verifyPickup(context:StoreOrdersContext,orderId:String,attempt:PickupVerificationAttempt):PickupVerificationResult =
        PickupVerificationResult.Unavailable
}
