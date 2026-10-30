package com.chaskifood.app.feature.orders.presentation

import androidx.lifecycle.*
import com.chaskifood.app.feature.orders.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StoreOrdersUiState(val context:StoreOrdersContext?=null,val loading:Boolean=true,val loadingMore:Boolean=false,
    val storeName:String="",val orders:List<StoreOrderSummary> = emptyList(),val nextCursor:String?=null,
    val message:String?=null,val accessDenied:Boolean=false)

@HiltViewModel
class StoreOrdersViewModel @Inject constructor(private val repository:StoreOrdersRepository,savedState:SavedStateHandle):ViewModel() {
    private val mutable=MutableStateFlow(StoreOrdersUiState())
    val state=mutable.asStateFlow()
    private var context:StoreOrdersContext?=null
    private var request=0L
    private var job:Job?=null
    private val target=runCatching {StoreOrdersTarget(requireNotNull(savedState["businessId"]),requireNotNull(savedState["storeId"]))}.getOrNull()
    init {
        if(target==null)mutable.value=StoreOrdersUiState(loading=false,accessDenied=true,message="El local no es válido.")
        else viewModelScope.launch {
            repository.observeAuthorization(target).catch { e->if(e is CancellationException)throw e;emit(StoreOrdersAuthorization.Unavailable) }.collect { auth->
                val checked=if(auth is StoreOrdersAuthorization.Allowed && auth.context.target!=target) StoreOrdersAuthorization.Denied else auth
                val next=(checked as? StoreOrdersAuthorization.Allowed)?.context
                if(next!=null && next==context)return@collect
                job?.cancel();request++;context=next
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
    private fun fetch(expected:StoreOrdersContext,cursor:String?){
        val ticket=++request
        mutable.value=if(cursor==null)StoreOrdersUiState(context=expected) else mutable.value.copy(loadingMore=true,message=null)
        job=viewModelScope.launch {
            val result=try{repository.page(expected,cursor)}catch(e:CancellationException){throw e}catch(_:Exception){StoreOrdersResult.Unavailable}
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
                    else StoreOrdersUiState(expected,false,false,result.page.storeName,previous+result.page.orders,result.page.nextCursor)
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
