package com.chaskifood.app.feature.orders.presentation

import androidx.lifecycle.*
import com.chaskifood.app.feature.orders.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StoreOrdersUiState(val context:StoreOrdersContext?=null,val loading:Boolean=true,val loadingMore:Boolean=false,
    val storeName:String="",val orders:List<StoreOrderSummary> = emptyList(),val nextCursor:String?=null,
    val message:String?=null,val accessDenied:Boolean=false,val respondingOrderId:String?=null,val selectedStatus:OrderStatus=OrderStatus.PENDING_CONFIRMATION)

@HiltViewModel
class StoreOrdersViewModel @Inject constructor(private val repository:StoreOrdersRepository,savedState:SavedStateHandle):ViewModel() {
    private val mutable=MutableStateFlow(StoreOrdersUiState())
    val state=mutable.asStateFlow()
    private var context:StoreOrdersContext?=null
    private var request=0L
    private var job:Job?=null
    private var decisionJob:Job?=null
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
                    respondingOrderId=null,message="Pedido listo. Código de recojo: ${result.pickupCode}")}
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
    fun updateEstimate(orderId:String,minutes:Int){
        val expected=usableContext()?:return;if(decisionJob?.isActive==true)return
        mutable.update{it.copy(respondingOrderId=orderId,message=null)}
        decisionJob=viewModelScope.launch{when(val result=repository.updateEstimate(expected,orderId,minutes)){
            is PreparationEstimateResult.Updated->mutable.update{state->state.copy(respondingOrderId=null,message="Estimación actualizada.",
                orders=state.orders.map{if(it.orderId==orderId)StoreOrderSummary(it.orderId,it.createdAtEpochMillis,it.items,it.amounts,
                    OrderStatus.PREPARING,it.paymentMethod,minutes,result.estimatedReadyAtEpochMillis)else it})}
            PreparationEstimateResult.StateChanged,PreparationEstimateResult.NotFound->mutable.update{it.copy(respondingOrderId=null,message="El pedido cambió y ya no permite actualizar la estimación.")}
            PreparationEstimateResult.AccessDenied,PreparationEstimateResult.AuthenticationRequired,PreparationEstimateResult.StaleContext->{context=null;mutable.value=StoreOrdersUiState(loading=false,accessDenied=true,message="Tu autorización cambió.")}
            PreparationEstimateResult.Unavailable->mutable.update{it.copy(respondingOrderId=null,message="No pudimos actualizar la estimación.")}
        }}
    }
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
    private fun fetch(expected:StoreOrdersContext,cursor:String?){
        val ticket=++request
        val selected=mutable.value.selectedStatus
        mutable.value=if(cursor==null)StoreOrdersUiState(context=expected,selectedStatus=selected) else mutable.value.copy(loadingMore=true,message=null)
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
                    else StoreOrdersUiState(expected,false,false,result.page.storeName,previous+result.page.orders,result.page.nextCursor,selectedStatus=selected)
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
