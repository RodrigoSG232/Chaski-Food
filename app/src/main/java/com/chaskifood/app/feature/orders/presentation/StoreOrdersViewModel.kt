package com.chaskifood.app.feature.orders.presentation

import androidx.lifecycle.*
import com.chaskifood.app.feature.orders.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** HU20: an extension whose outcome is unknown (timeout/network). Retrying reuses its operation ID. */
data class PendingPreparationExtension(val orderId:String,val extension:PreparationExtension)

/** HU21-B dialog phases (Pencil HU21/03–06). */
enum class PickupVerificationPhase { EDITING, SENDING, INVALID_CODE, CONNECTION_ERROR }
/**
 * HU21-B: the verification dialog of one order. [inputGeneration] changes when the typed code must be cleared
 * (after a wrong code). The code itself never lives in UI state.
 */
data class PickupVerificationUi(val orderId:String,val assignment:StoreRiderAssignment,val attemptsRemaining:Int,
    val phase:PickupVerificationPhase=PickupVerificationPhase.EDITING,val inputGeneration:Int=0)

data class StoreOrdersUiState(val context:StoreOrdersContext?=null,val loading:Boolean=true,val loadingMore:Boolean=false,
    val storeName:String="",val orders:List<StoreOrderSummary> = emptyList(),val nextCursor:String?=null,
    val message:String?=null,val accessDenied:Boolean=false,val respondingOrderId:String?=null,val selectedStatus:OrderStatus=OrderStatus.PENDING_CONFIRMATION,
    val pendingExtension:PendingPreparationExtension?=null,
    /** HU20: the order whose extension request is in flight (progress indicator; actions stay disabled). */
    val extendingOrderId:String?=null,
    val pickupDialog:PickupVerificationUi?=null,
    /** One-shot confirmation shown as a snackbar (HU21/07). */
    val snackbar:String?=null,
    /** Orders whose rider changed since the store last saw them (HU21/09). */
    val riderChangedOrderIds:Set<String> = emptySet())

@HiltViewModel
class StoreOrdersViewModel @Inject constructor(private val repository:StoreOrdersRepository,savedState:SavedStateHandle):ViewModel() {
    private val mutable=MutableStateFlow(StoreOrdersUiState())
    val state=mutable.asStateFlow()
    private var context:StoreOrdersContext?=null
    private var request=0L
    private var job:Job?=null
    private var decisionJob:Job?=null
    /** The attempt whose outcome is unknown (timeout/network); retrying reuses it so the server never counts it twice. */
    private var pendingPickup:PickupVerificationAttempt?=null
    /** Last assignment seen per ready order, to tell the store that the rider changed. */
    private val seenAssignments=mutableMapOf<String,String?>()
    private val target=runCatching {StoreOrdersTarget(requireNotNull(savedState["businessId"]),requireNotNull(savedState["storeId"]))}.getOrNull()
    init {
        if(target==null)mutable.value=StoreOrdersUiState(loading=false,accessDenied=true,message="El local no es válido.")
        else viewModelScope.launch {
            repository.observeAuthorization(target).catch { e->if(e is CancellationException)throw e;emit(StoreOrdersAuthorization.Unavailable) }.collect { auth->
                val checked=if(auth is StoreOrdersAuthorization.Allowed && auth.context.target!=target) StoreOrdersAuthorization.Denied else auth
                val next=(checked as? StoreOrdersAuthorization.Allowed)?.context
                if(next!=null && next==context)return@collect
                job?.cancel();decisionJob?.cancel();request++;context=next
                mutable.value=when(checked){
                    is StoreOrdersAuthorization.Allowed->StoreOrdersUiState(context=checked.context)
                    StoreOrdersAuthorization.Verifying->StoreOrdersUiState()
                    StoreOrdersAuthorization.SignedOut->StoreOrdersUiState(loading=false,accessDenied=true,message="Inicia sesión para consultar los pedidos.")
                    StoreOrdersAuthorization.Denied->StoreOrdersUiState(loading=false,accessDenied=true,message="Tu acceso a este local ya no está autorizado.")
                    StoreOrdersAuthorization.Unavailable->StoreOrdersUiState(loading=false,accessDenied=true,message="No pudimos comprobar los permisos. Vuelve a comprobar tu acceso.")
                }
                if(next!=null)fetch(next,null)
            }
        }
    }
    private fun usableContext():StoreOrdersContext? {
        val current=context?:return null
        if(repository.isCurrent(current))return current
        job?.cancel();request++;context=null
        mutable.value=StoreOrdersUiState(loading=false,accessDenied=true,message="La autorización cambió.")
        return null
    }
    /** A notification is never satisfied by an earlier in-flight HTTP read. */
    fun refreshFromNotification(){job?.cancel();job=null;usableContext()?.let{fetch(it,null)}}
    fun refresh(){if(job?.isActive==true)return;usableContext()?.let {fetch(it,null)}}
    fun loadMore(){
        if(job?.isActive==true)return
        val c=usableContext()?:return
        val cursor=mutable.value.nextCursor?:return
        fetch(c,cursor)
    }
    fun selectStatus(status:OrderStatus){if(status==mutable.value.selectedStatus)return;mutable.update{it.copy(selectedStatus=status)};usableContext()?.let{fetch(it,null)}}
    fun accept(orderId:String,estimatedPreparationMinutes:Int)=respond(orderId,StoreOrderDecision.Accept(estimatedPreparationMinutes))
    fun reject(orderId:String,reason:StoreOrderRejectionReason,explanation:String?=null)=
        respond(orderId,StoreOrderDecision.Reject(reason,explanation?.trim()))
    fun markReady(orderId:String){
        val expected=usableContext()?:return
        if(decisionJob?.isActive==true || mutable.value.respondingOrderId!=null ||
            mutable.value.orders.none{it.orderId==orderId&&it.status==OrderStatus.PREPARING})return
        val ticket=request
        mutable.update{it.copy(respondingOrderId=orderId,message=null)}
        decisionJob=viewModelScope.launch{
            val result=try{repository.markReady(expected,orderId)}catch(e:CancellationException){throw e}
                catch(_:Exception){MarkOrderReadyResult.Unavailable}
            currentCoroutineContext().ensureActive()
            if(ticket!=request||context!=expected)return@launch
            when(result){
                is MarkOrderReadyResult.Ready->mutable.update{it.copy(orders=it.orders.filterNot{o->o.orderId==orderId},
                    respondingOrderId=null,message="Pedido listo. Chaski Rider buscará un repartidor.")}
                MarkOrderReadyResult.PaymentRequired->mutable.update{it.copy(respondingOrderId=null,
                    message="El servidor aún exige la regla anterior de pago anticipado. Actualiza foodApi para usar efectivo contra entrega.")}
                MarkOrderReadyResult.StateChanged,MarkOrderReadyResult.NotFound->mutable.update{it.copy(
                    orders=it.orders.filterNot{o->o.orderId==orderId},respondingOrderId=null,
                    message="El pedido cambió y ya no está en preparación.")}
                MarkOrderReadyResult.AccessDenied,MarkOrderReadyResult.AuthenticationRequired,MarkOrderReadyResult.StaleContext->{
                    context=null;mutable.value=StoreOrdersUiState(loading=false,accessDenied=true,message="Tu autorización cambió.")}
                MarkOrderReadyResult.Unavailable->mutable.update{it.copy(respondingOrderId=null,
                    message="No pudimos marcar el pedido como listo. Inténtalo nuevamente.")}
            }
        }
    }
    fun simulatePickup(orderId:String)=simulateDelivery(orderId,true)
    fun simulateArrival(orderId:String)=simulateDelivery(orderId,false)
    private fun simulateDelivery(orderId:String,pickup:Boolean){
        val expected=usableContext()?:return
        if(decisionJob?.isActive==true || mutable.value.respondingOrderId!=null ||
            mutable.value.orders.none{it.orderId==orderId &&
                (if(pickup)it.status==OrderStatus.READY_FOR_PICKUP
                else it.status==OrderStatus.IN_DELIVERY&&it.simulationStage=="ON_THE_WAY")})return
        val ticket=request
        mutable.update{it.copy(respondingOrderId=orderId,message=null)}
        decisionJob=viewModelScope.launch{
            val result=try{if(pickup)repository.simulatePickup(expected,orderId)else repository.simulateArrival(expected,orderId)}
                catch(e:CancellationException){throw e}catch(_:Exception){SimulatedStoreDeliveryResult.Unavailable}
            currentCoroutineContext().ensureActive()
            if(ticket!=request||context!=expected)return@launch
            when(result){
                SimulatedStoreDeliveryResult.Updated->{mutable.update{it.copy(respondingOrderId=null)};fetch(expected,null)}
                SimulatedStoreDeliveryResult.StateChanged,SimulatedStoreDeliveryResult.NotFound->
                    mutable.update{it.copy(respondingOrderId=null,message="El pedido cambió. Actualiza la bandeja.")}
                SimulatedStoreDeliveryResult.AccessDenied,SimulatedStoreDeliveryResult.AuthenticationRequired,
                SimulatedStoreDeliveryResult.StaleContext->{context=null;mutable.value=StoreOrdersUiState(loading=false,
                    accessDenied=true,message="Tu autorización cambió.")}
                SimulatedStoreDeliveryResult.Unavailable->mutable.update{it.copy(respondingOrderId=null,
                    message="No pudimos registrar la simulación. Inténtalo nuevamente.")}
            }
        }
    }
    fun snackbarShown(){mutable.update{it.copy(snackbar=null)}}
    fun dismissRiderChange(orderId:String){mutable.update{it.copy(riderChangedOrderIds=it.riderChangedOrderIds-orderId)}}
    /** HU21-B: only for a ready order with a current, unverified and unlocked assignment. */
    fun openPickupVerification(orderId:String){
        if(decisionJob?.isActive==true || mutable.value.respondingOrderId!=null || mutable.value.pickupDialog!=null)return
        val order=mutable.value.orders.firstOrNull{it.orderId==orderId&&it.status==OrderStatus.READY_FOR_PICKUP}?:return
        val pickup=order.pickup?.takeIf{it.canVerify}?:return
        pendingPickup=null
        mutable.update{it.copy(pickupDialog=PickupVerificationUi(orderId,pickup.assignment!!,pickup.attemptsRemaining),
            riderChangedOrderIds=it.riderChangedOrderIds-orderId,message=null)}
    }
    /** Every typed code is a new attempt with a new operation ID. */
    fun submitPickupCode(code:String){
        val dialog=mutable.value.pickupDialog?:return
        if(dialog.phase==PickupVerificationPhase.SENDING || dialog.phase==PickupVerificationPhase.CONNECTION_ERROR)return
        val attempt=runCatching{PickupVerificationAttempt(newOperationId(),code,dialog.assignment.assignmentId,
            dialog.assignment.assignmentVersion)}.getOrNull()?:return
        sendPickup(dialog,attempt)
    }
    /** Retries the unconfirmed attempt with the SAME operation ID: the server returns its original outcome. */
    fun retryPickupVerification(){
        val dialog=mutable.value.pickupDialog?:return
        val attempt=pendingPickup?:return
        if(dialog.phase!=PickupVerificationPhase.CONNECTION_ERROR)return
        sendPickup(dialog,attempt)
    }
    /**
     * Closing is ignored while sending. After an unknown outcome the attempt is dropped and the inbox reloads, so the
     * server state (verified, attempts left or locked) is shown; reopening starts a fresh attempt.
     */
    fun dismissPickupVerification(){
        val dialog=mutable.value.pickupDialog?:return
        if(dialog.phase==PickupVerificationPhase.SENDING)return
        val unknown=dialog.phase==PickupVerificationPhase.CONNECTION_ERROR
        pendingPickup=null
        mutable.update{it.copy(pickupDialog=null)}
        if(unknown)usableContext()?.let{fetch(it,null)}
    }
    private fun sendPickup(dialog:PickupVerificationUi,attempt:PickupVerificationAttempt){
        val expected=usableContext()?:return
        if(decisionJob?.isActive==true || mutable.value.respondingOrderId!=null)return
        val ticket=request
        pendingPickup=attempt
        mutable.update{it.copy(respondingOrderId=dialog.orderId,message=null,pickupDialog=dialog.copy(phase=PickupVerificationPhase.SENDING))}
        decisionJob=viewModelScope.launch{
            val result=try{repository.verifyPickup(expected,dialog.orderId,attempt)}catch(e:CancellationException){throw e}
                catch(_:Exception){PickupVerificationResult.Unavailable}
            currentCoroutineContext().ensureActive()
            if(ticket!=request||context!=expected)return@launch
            if(result!=PickupVerificationResult.Unavailable)pendingPickup=null
            when(result){
                PickupVerificationResult.Verified,PickupVerificationResult.AlreadyVerified->{
                    mutable.update{it.copy(respondingOrderId=null,pickupDialog=null,snackbar="Código verificado correctamente")}
                    fetch(expected,null)}
                is PickupVerificationResult.InvalidCode->mutable.update{it.copy(respondingOrderId=null,
                    pickupDialog=dialog.copy(phase=PickupVerificationPhase.INVALID_CODE,attemptsRemaining=result.attemptsRemaining,
                        inputGeneration=dialog.inputGeneration+1))}
                PickupVerificationResult.Locked->closePickupAndReload(expected,
                    "Se agotaron los intentos. El recojo quedó bloqueado hasta que un administrador de Chaski lo revise.")
                PickupVerificationResult.AssignmentChanged->closePickupAndReload(expected,
                    "El repartidor cambió. Verifica el código del nuevo repartidor.")
                PickupVerificationResult.StateChanged,PickupVerificationResult.NotFound,PickupVerificationResult.OperationConflict->
                    closePickupAndReload(expected,"El pedido cambió. Revisa la bandeja.")
                PickupVerificationResult.AccessDenied,PickupVerificationResult.AuthenticationRequired,
                PickupVerificationResult.StaleContext->{context=null;pendingPickup=null
                    mutable.value=StoreOrdersUiState(loading=false,accessDenied=true,message="Tu autorización cambió.")}
                PickupVerificationResult.Unavailable->mutable.update{it.copy(respondingOrderId=null,
                    pickupDialog=dialog.copy(phase=PickupVerificationPhase.CONNECTION_ERROR))}
            }
        }
    }
    private fun closePickupAndReload(expected:StoreOrdersContext,notice:String){
        pendingPickup=null
        mutable.update{it.copy(respondingOrderId=null,pickupDialog=null)}
        fetch(expected,null,notice)
    }
    /** A new, explicit extension: always a new operation ID and the revision the responsible person is looking at. */
    fun extendPreparation(orderId:String,additionalMinutes:Int){
        val order=mutable.value.orders.firstOrNull{it.orderId==orderId&&it.status==OrderStatus.PREPARING}?:return
        val extension=runCatching{PreparationExtension(newOperationId(),additionalMinutes,order.estimateRevision)}.getOrNull()?:return
        sendExtension(PendingPreparationExtension(orderId,extension))
    }
    /** Retries only an unconfirmed operation, with the same ID, so the server can never add the minutes twice. */
    fun retryPendingExtension(){ mutable.value.pendingExtension?.let(::sendExtension) }
    fun discardPendingExtension(){
        if(decisionJob?.isActive==true)return
        mutable.update{it.copy(pendingExtension=null)}
        usableContext()?.let{fetch(it,null)}
    }
    private fun sendExtension(pending:PendingPreparationExtension){
        val expected=usableContext()?:return
        if(decisionJob?.isActive==true || mutable.value.respondingOrderId!=null)return
        val ticket=request
        mutable.update{it.copy(respondingOrderId=pending.orderId,message=null,pendingExtension=pending,extendingOrderId=pending.orderId)}
        decisionJob=viewModelScope.launch{
            val result=try{repository.extendPreparation(expected,pending.orderId,pending.extension)}catch(e:CancellationException){throw e}
                catch(_:Exception){PreparationExtensionResult.Unavailable}
            currentCoroutineContext().ensureActive()
            if(ticket!=request||context!=expected)return@launch
            when(result){
                is PreparationExtensionResult.Extended->mutable.update{state->state.copy(respondingOrderId=null,pendingExtension=null,
                    extendingOrderId=null,
                    message="Tiempo ampliado. Revisa la nueva hora estimada.",
                    orders=state.orders.map{if(it.orderId==pending.orderId)it.withExtension(result.estimatedReadyAtEpochMillis,
                        result.estimateRevision,result.preparationExtendedMinutes)else it})}
                PreparationExtensionResult.Unavailable->mutable.update{it.copy(respondingOrderId=null,extendingOrderId=null,
                    message="No se confirmó la ampliación. Puedes reintentarla sin que se sume dos veces.")}
                PreparationExtensionResult.RevisionChanged->reloadAfterExtension(expected,
                    "Otro cambio actualizó la hora estimada. Revísala y vuelve a confirmar si aún necesitas ampliar.")
                PreparationExtensionResult.OperationConflict->reloadAfterExtension(expected,
                    "No pudimos confirmar esa ampliación. Revisa la hora estimada y vuelve a intentarlo.")
                PreparationExtensionResult.StateChanged,PreparationExtensionResult.NotFound->reloadAfterExtension(expected,
                    "El pedido cambió y ya no está en preparación.")
                PreparationExtensionResult.AccessDenied,PreparationExtensionResult.AuthenticationRequired,
                PreparationExtensionResult.StaleContext->{context=null
                    mutable.value=StoreOrdersUiState(loading=false,accessDenied=true,message="Tu autorización cambió.")}
            }
        }
    }
    private fun reloadAfterExtension(expected:StoreOrdersContext,notice:String){
        mutable.update{it.copy(respondingOrderId=null,pendingExtension=null,extendingOrderId=null)}
        fetch(expected,null,notice)
    }
    private fun newOperationId()=java.util.UUID.randomUUID().toString()
    private fun respond(orderId:String,decision:StoreOrderDecision) {
        val expected=usableContext()?:return
        if(decisionJob?.isActive==true || mutable.value.respondingOrderId!=null || mutable.value.orders.none{it.orderId==orderId})return
        val ticket=request
        mutable.update{it.copy(respondingOrderId=orderId,message=null)}
        decisionJob=viewModelScope.launch {
            val result=try{repository.respond(expected,orderId,decision)}catch(e:CancellationException){throw e}
                catch(_:Exception){StoreOrderDecisionResult.Unavailable}
            currentCoroutineContext().ensureActive()
            if(ticket!=request || context!=expected)return@launch
            when(result) {
                is StoreOrderDecisionResult.Accepted->mutable.update{it.copy(orders=it.orders.filterNot{o->o.orderId==orderId},
                    respondingOrderId=null,message="Pedido aceptado. Ya está en preparación.")}
                StoreOrderDecisionResult.Rejected->mutable.update{it.copy(orders=it.orders.filterNot{o->o.orderId==orderId},
                    respondingOrderId=null,message="Pedido rechazado y motivo registrado.")}
                StoreOrderDecisionResult.StateChanged->mutable.update{it.copy(orders=it.orders.filterNot{o->o.orderId==orderId},
                    respondingOrderId=null,message="El pedido cambió y ya no está pendiente.")}
                StoreOrderDecisionResult.AcceptanceSuspended->mutable.update{it.copy(respondingOrderId=null,
                    message="El negocio o local está suspendido. Puedes rechazar el pedido, pero no aceptarlo.")}
                StoreOrderDecisionResult.AccessDenied,StoreOrderDecisionResult.AuthenticationRequired,StoreOrderDecisionResult.StaleContext->{
                    context=null;mutable.value=StoreOrdersUiState(loading=false,accessDenied=true,message="Tu autorización cambió.")}
                StoreOrderDecisionResult.NotFound->mutable.update{it.copy(orders=it.orders.filterNot{o->o.orderId==orderId},
                    respondingOrderId=null,message="El pedido ya no está disponible.")}
                StoreOrderDecisionResult.Unavailable->mutable.update{it.copy(respondingOrderId=null,
                    message="No pudimos responder el pedido. Comprueba la conexión e inténtalo nuevamente.")}
            }
        }
    }
    /** HU21/09: an order whose assignment differs from the one previously seen gets a visible notice. */
    private fun riderChanges(current:Set<String>,orders:List<StoreOrderSummary>):Set<String> {
        val changed=current.toMutableSet()
        orders.filter{it.status==OrderStatus.READY_FOR_PICKUP&&it.pickup!=null}.forEach{order->
            val now=order.pickup?.assignment?.key
            if(seenAssignments.containsKey(order.orderId)){
                val before=seenAssignments[order.orderId]
                if(before!=null&&before!=now)changed+=order.orderId
            }
            seenAssignments[order.orderId]=now
            if(order.pickup?.verified==true)changed-=order.orderId
        }
        return changed
    }
    private fun fetch(expected:StoreOrdersContext,cursor:String?,notice:String?=null){
        val ticket=++request
        val selected=mutable.value.selectedStatus
        // An unconfirmed extension survives reloads; only its explicit retry or discard clears it.
        val pending=mutable.value.pendingExtension
        // The pickup dialog, its snackbar and rider-change notices also survive reloads (e.g. a notification refresh).
        val dialog=mutable.value.pickupDialog;val snackbar=mutable.value.snackbar;val changed=mutable.value.riderChangedOrderIds
        mutable.value=if(cursor==null)StoreOrdersUiState(context=expected,selectedStatus=selected,pendingExtension=pending,
            pickupDialog=dialog,snackbar=snackbar,riderChangedOrderIds=changed)
            else mutable.value.copy(loadingMore=true,message=null)
        job=viewModelScope.launch {
            val result=try{repository.page(expected,cursor,selected)}catch(e:CancellationException){throw e}catch(_:Exception){StoreOrdersResult.Unavailable}
            currentCoroutineContext().ensureActive()
            if(ticket!=request || context!=expected)return@launch
            if(!repository.isCurrent(expected)) {
                context=null
                mutable.value=StoreOrdersUiState(loading=false,accessDenied=true,message="La autorización cambió.")
                return@launch
            }
            when(result){
                is StoreOrdersResult.Loaded->{
                    val previous=if(cursor==null)emptyList()else mutable.value.orders
                    val duplicate=previous.any {old->result.page.orders.any {it.orderId==old.orderId}}
                    mutable.value=if(duplicate)mutable.value.copy(loading=false,loadingMore=false,nextCursor=null,message="La página cambió. Actualiza la bandeja.")
                    else StoreOrdersUiState(expected,false,false,result.page.storeName,previous+result.page.orders,result.page.nextCursor,
                        message=notice,selectedStatus=selected,pendingExtension=pending,pickupDialog=dialog,snackbar=snackbar,
                        riderChangedOrderIds=riderChanges(changed,result.page.orders))
                }
                StoreOrdersResult.AccessDenied,StoreOrdersResult.AuthenticationRequired,StoreOrdersResult.StoreMissing->
                    mutable.value=StoreOrdersUiState(context=expected,loading=false,accessDenied=true,message=when(result){
                        StoreOrdersResult.AuthenticationRequired->"Vuelve a iniciar sesión para consultar la bandeja."
                        StoreOrdersResult.StoreMissing->"El local ya no está disponible."
                        else->"Tu asignación ya no permite consultar este local."})
                StoreOrdersResult.StaleContext->mutable.value=StoreOrdersUiState(loading=false,accessDenied=true,message="La autorización cambió.")
                StoreOrdersResult.InvalidCursor->mutable.value=mutable.value.copy(loading=false,loadingMore=false,nextCursor=null,message="La página ya no es válida. Actualiza la bandeja.")
                StoreOrdersResult.Unavailable->mutable.value=mutable.value.copy(loading=false,loadingMore=false,message="No pudimos consultar los pedidos. Comprueba la conexión y actualiza.")
            }
        }
    }
}
