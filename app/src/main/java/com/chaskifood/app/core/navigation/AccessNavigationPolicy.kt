package com.chaskifood.app.core.navigation

import com.chaskifood.app.core.access.domain.*

/** Traduce destinos a capacidades ya resueltas; no resuelve roles ni consulta servicios. */
object AccessNavigationPolicy {
    val publicRoutes = setOf(ChaskiDestinations.SPLASH, ChaskiDestinations.ONBOARDING,
        ChaskiDestinations.SIGN_IN, ChaskiDestinations.SIGN_UP, ChaskiDestinations.SIGN_UP_PHONE,
        ChaskiDestinations.VERIFY_PHONE, ChaskiDestinations.FORGOT_PASSWORD,
        ChaskiDestinations.CHECK_EMAIL, ChaskiDestinations.LOCATION, ChaskiDestinations.LOCATION_SEARCH)
    private val purchaseRoutes = setOf(ChaskiDestinations.RESTAURANTS, ChaskiDestinations.RESTAURANT_DETAIL,
        ChaskiDestinations.PRODUCT_DETAIL, ChaskiDestinations.CART, ChaskiDestinations.CHECKOUT,
        ChaskiDestinations.SEARCH_CATEGORIES, ChaskiDestinations.FOODS_PER_CATEGORY,
        ChaskiDestinations.FILTER, ChaskiDestinations.MENU_TOPPING, ChaskiDestinations.YOUR_ORDER,
        ChaskiDestinations.PAYMENT, ChaskiDestinations.ADD_CARD,
        ChaskiDestinations.ORDER_PLACED, ChaskiDestinations.ORDER_TRACKING, ChaskiDestinations.ORDER_PROGRESS,
        ChaskiDestinations.ORDER_DELIVERED, ChaskiDestinations.ORDER_RATING)
    private val personalPurchaseRoutes = setOf(ChaskiDestinations.MY_ORDERS, ChaskiDestinations.ORDER_DETAIL)
    private val adminRoutes = setOf(ChaskiDestinations.ADMIN_BUSINESS_REVIEW,
        ChaskiDestinations.ADMIN_BUSINESS_SUPERVISION, ChaskiDestinations.ADMIN_AUDIT_LOG)
    private val personalRoutes = setOf(ChaskiDestinations.MAIN, ChaskiDestinations.PROFILE_INFO,
        ChaskiDestinations.ADDRESS_BOOK, ChaskiDestinations.PROFILE_PAYMENTS,
        ChaskiDestinations.REFER_FRIEND, ChaskiDestinations.SOCIAL_ACCOUNTS,
        ChaskiDestinations.ADD_LOCATION, ChaskiDestinations.LOCATIONS)
    fun allows(resolution: AccessResolution?, route: String?, businessId: String? = null,
        storeId: String? = null, resubmit: Boolean = false): Boolean {
        if (route in publicRoutes) return true
        if (route == ChaskiDestinations.CHECKOUT_RECOVERY && businessId == null && storeId == null) {
            val personal = when (resolution) {
                is AccessResolution.Resolved -> resolution.identity.session.uid to resolution.capabilities
                is AccessResolution.Conflict -> resolution.session.uid to resolution.capabilities
                else -> return false
            }
            return personal.second.allows(AccessCapability.READ_PERSONAL_PURCHASES, AccessScope.Account(personal.first))
        }
        val access = resolution as? AccessResolution.Resolved ?: return false
        val grants = access.capabilities.grants
        fun has(capability: AccessCapability) = grants.any {
            it.capability == capability && when (val scope = it.scope) {
                is AccessScope.Account -> scope.uid == access.identity.session.uid && businessId == null && storeId == null
                is AccessScope.Business -> storeId == null && (businessId == null || scope.businessId == businessId)
                is AccessScope.Store -> (businessId == null || scope.businessId == businessId) &&
                    (storeId == null || scope.storeId == storeId)
            }
        }
        return when {
            // HU17: lectura de compras propias en cualquier experiencia; no concede START_PURCHASE.
            route in personalPurchaseRoutes -> has(AccessCapability.READ_PERSONAL_PURCHASES)
            route?.startsWith(ChaskiDestinations.STORE_ORDERS + "/") == true ->
                businessId != null && storeId != null && has(AccessCapability.READ_STORE_ORDERS)
            route in purchaseRoutes -> has(AccessCapability.START_PURCHASE)
            route in adminRoutes -> access.identity.isAdmin
            route in personalRoutes -> true
            route == ChaskiDestinations.BUSINESS_STATUS -> has(AccessCapability.READ_OWN_BUSINESS_STATUS) ||
                has(AccessCapability.REQUEST_BUSINESS)
            route?.substringBefore('?') == ChaskiDestinations.REGISTER_BUSINESS ->
                if (resubmit) has(AccessCapability.RESUBMIT_BUSINESS) else has(AccessCapability.REQUEST_BUSINESS)
            route == ChaskiDestinations.MANAGE_STORES -> has(AccessCapability.MANAGE_OWN_STORES)
            route == ChaskiDestinations.MANAGE_CATALOG -> has(AccessCapability.MANAGE_BUSINESS)
            route?.substringBefore('?') == ChaskiDestinations.STORE_OPERATIONS || route?.substringBefore('?') == ChaskiDestinations.STORE_PRODUCT_CONFIGURATION ->
                has(AccessCapability.OPERATE_HOURS_AND_OFFERS)
            else -> false
        }
    }
    fun storeScopes(resolution: AccessResolution?): Set<AccessScope.Store> =
        (resolution as? AccessResolution.Resolved)?.capabilities?.grants.orEmpty()
            .filter { it.capability == AccessCapability.OPERATE_HOURS_AND_OFFERS }
            .mapNotNull { it.scope as? AccessScope.Store }.toSet()
    /** Un permiso añadido no invalida una pantalla todavía autorizada (p. ej. estado tras registrar negocio). */
    fun requiresReset(previous: AccessResolution.Resolved?, current: AccessResolution.Resolved, route: String): Boolean {
        if (previous == null) return false
        if (previous.identity != current.identity) return true
        return route.substringBefore('?') in setOf(ChaskiDestinations.STORE_OPERATIONS, ChaskiDestinations.STORE_PRODUCT_CONFIGURATION) &&
            previous.capabilities.grants.any { it.scope is AccessScope.Store && it !in current.capabilities.grants }
    }    fun matchesSharedFrame(current: String, destination: String?, main: String?, uid: String?, ownerUid: String?): Boolean =
        uid != null && uid == ownerUid && current == destination && current == main}
