package com.chaskifood.app.feature.orders.domain

/**
 * HU21-B (E3.3): administrative recovery of a locked pickup. A narrow exception to HU34-28: the admin only sees the
 * minimal incident data below and may unlock it. Never the pickup code, never buyer data.
 */
data class PickupIncident(val orderId:String,val storeName:String?,val status:String,val lockedAtEpochMillis:Long,
    val cycle:Int,val failures:Int,val hasAssignment:Boolean) {
    init { require(orderId.matches(Regex("[0-9a-f]{64}")) && cycle>=1 && failures in 0..5 && lockedAtEpochMillis>0) }
    val shortId get()=orderId.take(8).uppercase()
}

sealed interface PickupIncidentsResult {
    data class Loaded(val incidents:List<PickupIncident>,val truncated:Boolean):PickupIncidentsResult
    data object AccessDenied:PickupIncidentsResult
    data object AuthenticationRequired:PickupIncidentsResult
    data object Unavailable:PickupIncidentsResult
}

/**
 * One unlock intent. foodApi requires an already-trimmed reason of 5..200 characters, so [create] trims it first.
 * A retry after an unknown outcome reuses the same [operationId]: the server replays it and never creates two codes.
 */
class PickupUnlockRequest private constructor(val operationId:String,val reason:String) {
    companion object {
        const val MIN_REASON=5; const val MAX_REASON=200
        fun validReason(raw:String)=raw.trim().length in MIN_REASON..MAX_REASON
        fun create(operationId:String,rawReason:String):PickupUnlockRequest? {
            val reason=rawReason.trim()
            if(!operationId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))return null
            return if(reason.length in MIN_REASON..MAX_REASON)PickupUnlockRequest(operationId,reason) else null
        }
    }
}

sealed interface PickupUnlockResult {
    /** A new code exists (never returned to the app) and a new cycle of five attempts started. */
    data class Unlocked(val cycle:Int):PickupUnlockResult
    data object NotLocked:PickupUnlockResult
    data object StateChanged:PickupUnlockResult
    data object OperationConflict:PickupUnlockResult
    data object InvalidReason:PickupUnlockResult
    data object AccessDenied:PickupUnlockResult
    data object AuthenticationRequired:PickupUnlockResult
    data object NotFound:PickupUnlockResult
    /** Timeout or network failure: the unlock may have happened; only the same operation may be retried. */
    data object Unavailable:PickupUnlockResult
}

interface PickupIncidentsRepository {
    suspend fun incidents():PickupIncidentsResult
    suspend fun unlock(orderId:String,request:PickupUnlockRequest):PickupUnlockResult
}
