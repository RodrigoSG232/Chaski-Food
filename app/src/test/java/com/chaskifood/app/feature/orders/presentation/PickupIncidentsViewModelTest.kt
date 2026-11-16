package com.chaskifood.app.feature.orders.presentation

import com.chaskifood.app.feature.orders.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PickupIncidentsViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){Dispatchers.resetMain()}
    private val incident=PickupIncident("c".repeat(64),"Chicken House Centro","READY_FOR_PICKUP",1_800_000_000_000,1,5,true)
    private class Repo:PickupIncidentsRepository {
        var list:PickupIncidentsResult=PickupIncidentsResult.Loaded(emptyList(),false);var loads=0
        var unlock:PickupUnlockResult=PickupUnlockResult.Unavailable
        val requests=mutableListOf<Pair<String,PickupUnlockRequest>>()
        override suspend fun incidents():PickupIncidentsResult{loads++;return list}
        override suspend fun unlock(orderId:String,request:PickupUnlockRequest):PickupUnlockResult{requests+=orderId to request;return unlock}
    }
    private fun loadedVm(repo:Repo):PickupIncidentsViewModel{repo.list=PickupIncidentsResult.Loaded(listOf(incident),false)
        return PickupIncidentsViewModel(repo)}

    @Test fun reasonIsTrimmedAndValidatedWithTheBackendLimits(){
        val op="0b8f1a52-3c4d-4e5f-8a6b-7c8d9e0f1a2b"
        assertEquals("Motivo",PickupUnlockRequest.create(op,"  Motivo  ")!!.reason)
        assertNull(PickupUnlockRequest.create(op,"   ab   "))
        assertNull(PickupUnlockRequest.create(op,"x".repeat(201)))
        assertNotNull(PickupUnlockRequest.create(op,"x".repeat(200)))
        assertNull(PickupUnlockRequest.create("not-a-uuid","Motivo válido"))
    }
    @Test fun listEmptyAndPermissionStates()=runTest{
        val repo=Repo();val vm=PickupIncidentsViewModel(repo);runCurrent()
        assertFalse(vm.state.value.loading);assertTrue(vm.state.value.incidents.isEmpty())
        repo.list=PickupIncidentsResult.AccessDenied;val denied=PickupIncidentsViewModel(repo);runCurrent()
        assertTrue(denied.state.value.accessDenied)
    }
    @Test fun invalidReasonNeverReachesTheServer()=runTest{
        val repo=Repo();val vm=loadedVm(repo);runCurrent();vm.select(incident.orderId)
        vm.requestUnlock("  no  ");vm.confirmUnlock();runCurrent()
        assertEquals(PickupUnlockError.INVALID_REASON,vm.state.value.error);assertTrue(repo.requests.isEmpty())
    }
    @Test fun aConnectionErrorRetriesTheSameOperationAndNeverShowsACode()=runTest{
        val repo=Repo();val vm=loadedVm(repo);runCurrent();vm.select(incident.orderId)
        vm.requestUnlock(" Repartidor confirmado ");assertEquals(PickupUnlockPhase.CONFIRMING,vm.state.value.phase)
        repo.unlock=PickupUnlockResult.Unavailable;vm.confirmUnlock();runCurrent()
        assertEquals(PickupUnlockError.CONNECTION,vm.state.value.error)
        repo.unlock=PickupUnlockResult.Unlocked(2);vm.retryUnlock();runCurrent()
        assertEquals(2,repo.requests.size);assertEquals(repo.requests[0].second.operationId,repo.requests[1].second.operationId)
        assertEquals("Repartidor confirmado",repo.requests[0].second.reason)
        assertEquals(2,vm.state.value.unlockedCycle)
        val loads=repo.loads;vm.backToList();runCurrent();assertTrue(repo.loads>loads);assertNull(vm.state.value.selected)
    }
    @Test fun notLockedAndPermissionErrorsAreDistinct()=runTest{
        for((result,error) in listOf(PickupUnlockResult.NotLocked to PickupUnlockError.NOT_LOCKED,
            PickupUnlockResult.StateChanged to PickupUnlockError.NOT_LOCKED,PickupUnlockResult.AccessDenied to PickupUnlockError.PERMISSION,
            PickupUnlockResult.InvalidReason to PickupUnlockError.INVALID_REASON)){
            val repo=Repo();val vm=loadedVm(repo);runCurrent();vm.select(incident.orderId)
            vm.requestUnlock("Motivo suficiente");repo.unlock=result;vm.confirmUnlock();runCurrent()
            assertEquals(error,vm.state.value.error);assertNull(vm.state.value.unlockedCycle)
        }
    }
    @Test fun aNewConfirmationAfterCancelUsesANewOperation()=runTest{
        val repo=Repo();val vm=loadedVm(repo);runCurrent();vm.select(incident.orderId)
        vm.requestUnlock("Motivo suficiente");vm.cancelConfirmation();vm.requestUnlock("Motivo suficiente")
        repo.unlock=PickupUnlockResult.NotLocked;vm.confirmUnlock();runCurrent()
        assertEquals(1,repo.requests.size)
    }
}
