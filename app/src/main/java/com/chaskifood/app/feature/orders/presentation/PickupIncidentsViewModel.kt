package com.chaskifood.app.feature.orders.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.feature.orders.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Admin 07a–d: each error is shown on its own. */
enum class PickupUnlockError { INVALID_REASON, NOT_LOCKED, CONNECTION, PERMISSION }
enum class PickupUnlockPhase { EDITING, CONFIRMING, SENDING }

data class PickupIncidentsUiState(val loading:Boolean=true,val incidents:List<PickupIncident> = emptyList(),val truncated:Boolean=false,
    val loadError:String?=null,val accessDenied:Boolean=false,val selected:PickupIncident?=null,
    val phase:PickupUnlockPhase=PickupUnlockPhase.EDITING,val error:PickupUnlockError?=null,
    /** Admin 06: the unlock succeeded; the new code is never shown. */
    val unlockedCycle:Int?=null)

@HiltViewModel
class PickupIncidentsViewModel @Inject constructor(private val repository:PickupIncidentsRepository):ViewModel() {
    private val mutable=MutableStateFlow(PickupIncidentsUiState())
    val state=mutable.asStateFlow()
    private var job:Job?=null
    /** The unlock whose outcome is unknown; retrying reuses its operation ID so no second code is generated. */
    private var pending:PickupUnlockRequest?=null

    init { load() }

    fun load(){
        if(job?.isActive==true)return
        mutable.update{it.copy(loading=true,loadError=null)}
        job=viewModelScope.launch{
            when(val result=try{repository.incidents()}catch(e:CancellationException){throw e}catch(_:Exception){PickupIncidentsResult.Unavailable}){
                is PickupIncidentsResult.Loaded->mutable.update{it.copy(loading=false,incidents=result.incidents,truncated=result.truncated)}
                PickupIncidentsResult.AccessDenied,PickupIncidentsResult.AuthenticationRequired->
                    mutable.value=PickupIncidentsUiState(loading=false,accessDenied=true)
                PickupIncidentsResult.Unavailable->mutable.update{it.copy(loading=false,loadError="No pudimos consultar las incidencias. Comprueba la conexión.")}
            }
        }
    }
    fun select(orderId:String){
        val incident=mutable.value.incidents.firstOrNull{it.orderId==orderId}?:return
        pending=null
        mutable.update{it.copy(selected=incident,phase=PickupUnlockPhase.EDITING,error=null,unlockedCycle=null)}
    }
    fun backToList(){
        if(mutable.value.phase==PickupUnlockPhase.SENDING)return
        // An unknown outcome (connection) may already have unlocked it: the list is reloaded from the server.
        val reload=mutable.value.unlockedCycle!=null || mutable.value.error in setOf(PickupUnlockError.NOT_LOCKED,PickupUnlockError.CONNECTION)
        pending=null
        mutable.update{it.copy(selected=null,phase=PickupUnlockPhase.EDITING,error=null,unlockedCycle=null)}
        if(reload)load()
    }
    /** Admin 04 → 05: validates the trimmed reason and prepares ONE new operation for confirmation. */
    fun requestUnlock(rawReason:String){
        if(mutable.value.selected==null||mutable.value.phase!=PickupUnlockPhase.EDITING)return
        val request=PickupUnlockRequest.create(java.util.UUID.randomUUID().toString(),rawReason)
        if(request==null){mutable.update{it.copy(error=PickupUnlockError.INVALID_REASON)};return}
        pending=request
        mutable.update{it.copy(phase=PickupUnlockPhase.CONFIRMING,error=null)}
    }
    fun cancelConfirmation(){
        if(mutable.value.phase!=PickupUnlockPhase.CONFIRMING)return
        pending=null
        mutable.update{it.copy(phase=PickupUnlockPhase.EDITING)}
    }
    fun confirmUnlock()=send()
    /** Only after a connection error: same operation, so the server returns its original outcome. */
    fun retryUnlock(){ if(mutable.value.error==PickupUnlockError.CONNECTION)send() }

    private fun send(){
        val incident=mutable.value.selected?:return
        val request=pending?:return
        if(mutable.value.phase==PickupUnlockPhase.SENDING)return
        mutable.update{it.copy(phase=PickupUnlockPhase.SENDING,error=null)}
        viewModelScope.launch{
            val result=try{repository.unlock(incident.orderId,request)}catch(e:CancellationException){throw e}catch(_:Exception){PickupUnlockResult.Unavailable}
            if(result!=PickupUnlockResult.Unavailable)pending=null
            when(result){
                is PickupUnlockResult.Unlocked->mutable.update{it.copy(phase=PickupUnlockPhase.EDITING,unlockedCycle=result.cycle)}
                PickupUnlockResult.NotLocked,PickupUnlockResult.StateChanged,PickupUnlockResult.NotFound,PickupUnlockResult.OperationConflict->
                    mutable.update{it.copy(phase=PickupUnlockPhase.EDITING,error=PickupUnlockError.NOT_LOCKED)}
                PickupUnlockResult.InvalidReason->mutable.update{it.copy(phase=PickupUnlockPhase.EDITING,error=PickupUnlockError.INVALID_REASON)}
                PickupUnlockResult.AccessDenied,PickupUnlockResult.AuthenticationRequired->
                    mutable.update{it.copy(phase=PickupUnlockPhase.EDITING,error=PickupUnlockError.PERMISSION)}
                PickupUnlockResult.Unavailable->mutable.update{it.copy(phase=PickupUnlockPhase.CONFIRMING,error=PickupUnlockError.CONNECTION)}
            }
        }
    }
}
