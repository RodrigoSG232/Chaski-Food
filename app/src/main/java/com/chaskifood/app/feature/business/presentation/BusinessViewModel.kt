package com.chaskifood.app.feature.business.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.BuildConfig
import com.chaskifood.app.core.access.data.SharedAccessContext
import com.chaskifood.app.core.access.domain.*
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.business.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BusinessViewModel @Inject constructor(
    private val businessRepository: BusinessRepository,
    authRepository: AuthRepository,
    private val stores: StoreRepository,
    private val context: SharedAccessContext,
) : ViewModel() {
    private data class RequestSnapshot(val origin: AccessProvenance, val result: ApiResult<BusinessRequest?>)
    private val ownRequest = context.states.flatMapLatest { state ->
        val access = state.resolution as? AccessResolution.Resolved
        val permitted = access?.capabilities?.grants.orEmpty().any {
            it.capability == AccessCapability.READ_OWN_BUSINESS_STATUS || it.capability == AccessCapability.REQUEST_BUSINESS
        }
        if (BuildConfig.FIREBASE_MODE != "LOCAL" || access == null || !permitted) flowOf(null)
        else businessRepository.getBusinessRequest(access.identity.session.uid)
            .map { RequestSnapshot(state.origin, it) }
            .catch { if (it is CancellationException) throw it; emit(RequestSnapshot(state.origin, ApiResult.Failure("No se pudo consultar el negocio."))) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), null)

    val businessRequest: StateFlow<ApiResult<BusinessRequest?>?> = (if (BuildConfig.FIREBASE_MODE == "LOCAL") {
        combine(context.states, ownRequest) { access, request -> request?.takeIf { it.origin == access.origin }?.result }
    } else authRepository.currentUserFlow.flatMapLatest { user ->
        if (user == null) flowOf(ApiResult.Success(null)) else businessRepository.getBusinessRequest(user.uid)
    }).stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), null)

    private val selection = MutableStateFlow<String?>(null)
    private var selectedIdentity: AccessIdentity.Authenticated? = null
    private var lastDisplayed: String? = null
    private var permissionNotice = false
    private val clockTicks = flow {
        while (currentCoroutineContext().isActive) { emit(Unit); delay(30_000) }
    }
    val commercialHome: StateFlow<CommercialHomeState> = context.states.transformLatest { snapshot ->
        emit(CommercialHomeState.Loading)
        val access = snapshot.resolution as? AccessResolution.Resolved ?: return@transformLatest
        if (selectedIdentity != access.identity) {
            selectedIdentity = access.identity; selection.value = null; lastDisplayed = null; permissionNotice = false
        }
        when (access.experience) {
            CommercialExperience.CLIENT -> Unit
            CommercialExperience.OWNER -> {
                val owner = access.ownership ?: return@transformLatest
                val suspended = owner.status == AccessBusinessStatus.SUSPENDED
                val scope = AccessScope.Business(owner.businessId)
                val storeFlow: Flow<ApiResult<List<BusinessStore>>> = when {
                    suspended -> flowOf(ApiResult.Success(emptyList()))
                    access.capabilities.allows(AccessCapability.LIST_OWN_STORES, scope) -> storeResults { stores.getStoresByBusiness(owner.businessId) }
                    else -> flowOf(ApiResult.Failure("No tienes autorización para consultar los locales."))
                }
                combine(ownRequest, storeFlow) { request, localResult ->
                    if (request?.origin != snapshot.origin) CommercialHomeState.Loading
                    else when (val result = request.result) {
                        is ApiResult.Failure -> CommercialHomeState.Error(snapshot.origin, "No pudimos consultar tu negocio. Vuelve a comprobar.")
                        is ApiResult.Success -> {
                            val business = result.data
                            if (business == null || business.ownerUid != access.identity.session.uid || business.id != owner.businessId ||
                                business.status.name != owner.status.name) CommercialHomeState.Error(snapshot.origin,
                                    "Los datos del negocio cambiaron. Vuelve a comprobar tus permisos.")
                            else CommercialHomeState.Owner(snapshot.origin, business, suspended,
                                owner.suspensionReason?.takeIf { it.isNotBlank() } ?: business.suspensionReason,
                                (localResult as? ApiResult.Success)?.data?.filter { it.businessId == owner.businessId }?.toList(),
                                if (localResult is ApiResult.Failure) "No pudimos cargar los locales." else null,
                                access.capabilities.allows(AccessCapability.MANAGE_OWN_STORES, scope),
                                access.capabilities.allows(AccessCapability.MANAGE_BUSINESS, scope),
                                access.capabilities.grants.any { it.capability == AccessCapability.OPERATE_HOURS_AND_OFFERS })
                        }
                    }
                }.collect { if (context.currentState().origin == snapshot.origin) emit(it) }
            }
            CommercialExperience.MANAGER -> {
                val readable = access.capabilities.grants.filter {
                    it.capability == AccessCapability.READ_STORE_ORDERS || it.capability == AccessCapability.OPERATE_HOURS_AND_OFFERS
                }.mapNotNull { it.scope as? AccessScope.Store }.toSet()
                val email = access.identity.email ?: return@transformLatest
                combine(storeResults { stores.getStoresForManager(email) }, selection, clockTicks) { result, requested, _ ->
                    if (context.currentState().origin != snapshot.origin) return@combine CommercialHomeState.Loading
                    when (result) {
                        is ApiResult.Failure -> CommercialHomeState.Error(snapshot.origin, "No pudimos consultar tus locales autorizados.")
                        is ApiResult.Success -> {
                            val authorized = result.data.filter { AccessScope.Store(it.businessId, it.id) in readable }.distinctBy { it.id }
                            if (lastDisplayed != null && authorized.none { it.id == lastDisplayed }) permissionNotice = true
                            val id = requested?.takeIf { wanted -> authorized.any { it.id == wanted } } ?: authorized.firstOrNull()?.id
                            lastDisplayed = id
                            val day = getCurrentDayOfWeek(); val time = getCurrentFormattedTime()
                            CommercialHomeState.Manager(snapshot.origin, authorized.map { store ->
                                val hours = store.operatingHours.singleOrNull { it.dayOfWeek == day && it.enabled }
                                CommercialLocal(store, access.capabilities.allows(AccessCapability.OPERATE_HOURS_AND_OFFERS,
                                    AccessScope.Store(store.businessId, store.id)), store.isWithinOperatingHours(time, day),
                                    hours?.let { "${it.openTime}–${it.closeTime}" } ?: "Sin atención programada hoy")
                            }, id, permissionNotice)
                        }
                    }
                }.collect { if (context.currentState().origin == snapshot.origin) emit(it) }
            }
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), CommercialHomeState.Loading)

    private fun storeResults(source: () -> Flow<ApiResult<List<BusinessStore>>>): Flow<ApiResult<List<BusinessStore>>> =
        flow { emitAll(source()) }.catch {
            if (it is CancellationException) throw it
            emit(ApiResult.Failure("No se pudieron consultar los locales."))
        }

    fun selectCommercialStore(storeId: String) {
        val current = context.currentState()
        val access = current.resolution as? AccessResolution.Resolved ?: return
        val state = commercialHome.value as? CommercialHomeState.Manager ?: return
        if (state.origin != current.origin || selectedIdentity != access.identity || state.locals.none { it.store.id == storeId }) return
        permissionNotice = false
        selection.value = storeId
    }
}
