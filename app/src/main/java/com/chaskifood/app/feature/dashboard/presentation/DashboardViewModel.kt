package com.chaskifood.app.feature.dashboard.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.access.data.SharedAccessContext
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.feature.cart.data.CartSessionSource
import com.chaskifood.app.feature.dashboard.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(val loading:Boolean=true,val days:Int=7,val storeId:String?=null,
    val data:DashboardData?=null,val message:String?=null,val denied:Boolean=false)

@HiltViewModel
internal class DashboardViewModel @Inject constructor(private val repository:DashboardRepository,
    private val access:SharedAccessContext,private val sessions:CartSessionSource,
    savedState:SavedStateHandle):ViewModel() {
    private val businessId:String?=savedState["businessId"]
    private val mutable=MutableStateFlow(DashboardUiState())
    val state=mutable.asStateFlow()
    private var context:DashboardContext?=null
    private var job:Job?=null
    init {
        if(businessId==null || !businessId.matches(Regex("[A-Za-z0-9_-]{1,128}"))) {
            mutable.value=DashboardUiState(loading=false,denied=true,message="Negocio inválido.")
        } else viewModelScope.launch {
            combine(access.states,sessions.sessions){_,_->currentContext()}.distinctUntilChanged().collect{next->
                job?.cancel();context=next
                if(next==null)mutable.value=DashboardUiState(loading=false,denied=true,
                    message="No hay autorización vigente para consultar este dashboard.")
                else {mutable.value=DashboardUiState(days=mutable.value.days);fetch(next)}
            }
        }
    }
    private fun currentContext():DashboardContext? {
        val id=businessId?:return null
        val session=sessions.current();val uid=session.uid?:return null
        val current=access.currentState();val resolved=current.resolution as? AccessResolution.Resolved?:return null
        val revision=current.serverRevision?:return null
        if(resolved.identity.session.uid!=uid || current.origin.session!=resolved.identity.session)return null
        val permitted=resolved.capabilities.grants.any{it.capability==AccessCapability.READ_BUSINESS_METRICS && when(val scope=it.scope){
            is AccessScope.Business->scope.businessId==id
            is AccessScope.Store->scope.businessId==id
            else->false
        }}
        return if(permitted)DashboardContext(uid,session.generation,id,revision,current.origin)else null
    }
    fun selectDays(days:Int){if(days !in setOf(7,30,90)||days==mutable.value.days)return
        mutable.value=mutable.value.copy(days=days);refresh()}
    fun selectStore(storeId:String?){if(storeId==mutable.value.storeId)return
        if(storeId!=null && mutable.value.data?.stores?.none{it.storeId==storeId}!=false)return
        mutable.value=mutable.value.copy(storeId=storeId);refresh()}
    fun refresh(){context?.let{fetch(it)}}
    private fun fetch(expected:DashboardContext){
        job?.cancel()
        val days=mutable.value.days;val store=mutable.value.storeId
        mutable.value=mutable.value.copy(loading=true,data=null,message=null,denied=false)
        job=viewModelScope.launch {
            val result=try{repository.read(expected,days,store)}catch(e:CancellationException){throw e}
                catch(_:Exception){DashboardResult.Unavailable}
            if(context!=expected || currentContext()!=expected)return@launch
            mutable.value=when(result){
                is DashboardResult.Loaded->mutable.value.copy(loading=false,data=result.data)
                DashboardResult.Denied,DashboardResult.Stale->DashboardUiState(loading=false,days=days,
                    denied=true,message="La autorización cambió. Vuelve a comprobar tu acceso.")
                DashboardResult.Unavailable->mutable.value.copy(loading=false,
                    message="No pudimos cargar el dashboard completo. Inténtalo nuevamente.")
            }
        }
    }
}
