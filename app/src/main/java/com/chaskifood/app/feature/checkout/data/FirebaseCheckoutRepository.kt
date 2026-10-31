package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.business.domain.getCurrentDayOfWeek
import com.chaskifood.app.feature.business.domain.getCurrentFormattedTime
import com.chaskifood.app.feature.cart.domain.AddToCartRejectionReason
import com.chaskifood.app.feature.cart.domain.AddToCartValidationResult
import com.chaskifood.app.feature.cart.domain.ValidateProductForCart
import com.chaskifood.app.feature.checkout.domain.CheckoutPricedItem
import com.chaskifood.app.feature.checkout.domain.CheckoutContext
import com.chaskifood.app.feature.checkout.domain.CheckoutContextState
import com.chaskifood.app.feature.checkout.domain.CheckoutRepository
import com.chaskifood.app.feature.checkout.domain.StorePurchaseEligibilityReader
import com.chaskifood.app.feature.checkout.domain.PurchaseEligibility
import com.chaskifood.app.feature.checkout.domain.PurchaseBlockReason
import com.chaskifood.app.feature.checkout.domain.CheckoutReviewResult
import com.chaskifood.app.feature.checkout.domain.CheckoutClock
import com.chaskifood.app.feature.checkout.domain.DeliveryQuoteFailure
import com.chaskifood.app.feature.checkout.domain.DeliveryQuoteReader
import com.chaskifood.app.feature.checkout.domain.DeliveryQuoteResult
import com.chaskifood.app.feature.checkout.domain.buildCheckoutQuote
import com.chaskifood.app.feature.checkout.domain.deliveryQuoteDeadlineOrNull
import com.chaskifood.app.feature.discover.data.toDiscoveryCandidate
import com.chaskifood.app.feature.discover.domain.StoreEligibilityResult
import com.chaskifood.app.feature.discover.domain.StoreExclusionReason
import com.chaskifood.app.feature.discover.domain.evaluateStoreEligibility
import com.chaskifood.app.feature.search.domain.ProductOfferReadResult
import com.chaskifood.app.feature.search.domain.ProductOfferReader
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import com.chaskifood.app.feature.checkout.domain.*
import com.chaskifood.app.feature.orders.domain.*
import com.chaskifood.app.feature.cart.data.CartSession
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

/** Vuelve a leer Firestore desde el servidor; nunca interpreta un fallo como compra válida. */
internal class FirebaseCheckoutRepository @Inject constructor(
    private val contexts: CheckoutContextSource,
    private val offers: ProductOfferReader,
    private val firestore: FirebaseFirestore,
    private val administrativeEligibility: StorePurchaseEligibilityReader,
    private val deliveryQuotes: DeliveryQuoteReader,
    private val clock: CheckoutClock,
    private val buyers: CheckoutBuyerSnapshotSource? = null,
    private val orders: CheckoutOrderOperations? = null,
) : CheckoutRepository {
    override suspend fun confirm(context: CheckoutContext, quote: CheckoutQuote) =
        orders?.confirm(context, quote) ?: CheckoutConfirmation.PurchaseForbidden
    override suspend fun pending(ownerUid: String, generation: Long, intentId: String?) =
        orders?.pending(CartSession(ownerUid, generation), intentId) ?: CheckoutConfirmation.None
    override suspend fun recover(ownerUid: String, generation: Long, intentId: String) =
        orders?.resolve(CartSession(ownerUid, generation), intentId, false) ?: CheckoutConfirmation.SessionChanged
    override suspend fun retry(ownerUid: String, generation: Long, intentId: String) =
        orders?.resolve(CartSession(ownerUid, generation), intentId, true) ?: CheckoutConfirmation.PurchaseForbidden

    private val validateProduct = ValidateProductForCart()
    /** Cuenta las lecturas directas del local en las pruebas con Emulator. */
    internal var onStoreGet: (() -> Unit)? = null

    override fun observeContext(): Flow<CheckoutContextState> = contexts.observe()

    override suspend fun isContextCurrent(context: CheckoutContext): Boolean = contexts.isCurrent(context)

    override suspend fun review(context: CheckoutContext): CheckoutReviewResult = try {
        if (!contexts.isCurrentSession(context)) CheckoutReviewResult.SignedOut
        else if (!contexts.isCurrent(context)) CheckoutReviewResult.Error(
            "La compra cambió mientras la revisábamos. Vuelve a comprobarla.")
        else {
            val result = reviewVerified(context)
            if (result is CheckoutReviewResult.Ready && !contexts.isCurrent(context)) {
                CheckoutReviewResult.Error("La compra cambió mientras la revisábamos. Vuelve a comprobarla.")
            } else if (!contexts.isCurrentSession(context)) CheckoutReviewResult.SignedOut
            else result
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: FirebaseFirestoreException) {
        when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> CheckoutReviewResult.Error(
                "No tienes permiso para revisar esta compra. Vuelve a iniciar sesión.",
            )
            else -> CheckoutReviewResult.Error("No pudimos consultar la oferta actual. Comprueba tu conexión y reintenta.")
        }
    } catch (_: Exception) {
        CheckoutReviewResult.Error("No pudimos comprobar la compra. Reintenta antes de continuar.")
    }

    private suspend fun reviewVerified(context: CheckoutContext): CheckoutReviewResult {
        val uid = context.ownerUid
        val cart = context.cart
        if (cart.ownerUid != uid) return CheckoutReviewResult.SignedOut
        if (cart.items.isEmpty()) return CheckoutReviewResult.Blocked(listOf("Tu carrito está vacío."))
        val storeId = cart.storeId ?: return CheckoutReviewResult.Error("El carrito no tiene un local válido.")
        val address = context.selectedAddress ?: return CheckoutReviewResult.Blocked(
            listOf("Selecciona una dirección de entrega válida."), canChangeAddress = true)
        val document = serverStore(storeId)
        if (document.metadata.isFromCache || document.metadata.hasPendingWrites()) {
            return CheckoutReviewResult.Error("No pudimos verificar el local en el servidor.")
        }
        if (!document.exists()) return CheckoutReviewResult.Blocked(listOf("El local ya no está disponible."))
        val candidate = document.toDiscoveryCandidate()
            ?: return CheckoutReviewResult.Error("Los datos del local están incompletos. Intenta más tarde.")
        if (candidate.store.businessId != cart.businessId) {
            return CheckoutReviewResult.Error("Los productos y el local no corresponden al mismo negocio.")
        }
        val eligibility = evaluateStoreEligibility(candidate, address.location,
            getCurrentDayOfWeek(), getCurrentFormattedTime())
        if (eligibility is StoreEligibilityResult.Excluded) {
            return CheckoutReviewResult.Blocked(listOf(eligibility.reason.message()),
                canChangeAddress = eligibility.reason == StoreExclusionReason.OUTSIDE_COVERAGE ||
                    eligibility.reason == StoreExclusionReason.INVALID_ADDRESS)
        }

        // El lector normal conserva sus validaciones para Cart y ProductDetail. Checkout comparte
        // únicamente este local confirmado y las categorías de esta revisión.
        val reviewOffers = if (document.data?.get("deliveryRadiusKm") is Number) {
            offers.openCheckoutReview(candidate, address.location)
        } else null
        val pricedItems = mutableListOf<CheckoutPricedItem>()
        val orderItems = mutableListOf<OrderItem>()
        val reasons = mutableListOf<String>()
        for (item in cart.items) {
            when (val offer = reviewOffers?.read(item.productId)
                ?: offers.read(storeId, item.productId, address.location)) {
                is ProductOfferReadResult.Found -> {
                    when (val validated = validateProduct(storeId, offer.product,
                        offer.configuration, item.quantity)) {
                        is AddToCartValidationResult.Allowed -> {
                            val current = validated.item
                            if (current.businessId != item.businessId || current.storeId != item.storeId) {
                                return CheckoutReviewResult.Error("La oferta ya no corresponde al local del carrito.")
                            }
                            orderItems += OrderItem(item.productId, offer.product.categoryId, current.productName, item.quantity, current.unitPriceMinor)
                            pricedItems += CheckoutPricedItem(item.productId, current.productName,
                                item.quantity, item.unitPriceMinor, current.unitPriceMinor)
                        }
                        is AddToCartValidationResult.Rejected -> reasons +=
                            "${item.productName}: ${validated.reason.message()}"
                    }
                }
                ProductOfferReadResult.ProductNotFound -> reasons += "${item.productName}: el producto ya no existe."
                ProductOfferReadResult.Unavailable -> reasons +=
                    "${item.productName}: ya no está disponible en este local, horario o dirección."
                ProductOfferReadResult.PermissionDenied -> return CheckoutReviewResult.Error(
                    "No tienes permiso para verificar los productos. Vuelve a iniciar sesión.")
                ProductOfferReadResult.NetworkError,
                ProductOfferReadResult.Unverified -> return CheckoutReviewResult.Error(
                    "No pudimos verificar los productos en el servidor. Comprueba tu conexión y reintenta.")
                ProductOfferReadResult.InvalidData -> return CheckoutReviewResult.Error(
                    "Uno de los productos tiene datos inválidos. No se puede continuar con esta compra.")
            }
        }
        if (reasons.isNotEmpty()) return CheckoutReviewResult.Blocked(reasons)
        val latestDocument = serverStore(storeId)
        if (latestDocument.metadata.isFromCache || latestDocument.metadata.hasPendingWrites()) {
            return CheckoutReviewResult.Error("No pudimos volver a verificar el local en el servidor.")
        }
        if (!latestDocument.exists()) {
            return CheckoutReviewResult.Blocked(listOf("El local ya no está disponible."))
        }
        val latestCandidate = latestDocument.toDiscoveryCandidate()
            ?: return CheckoutReviewResult.Error("Los datos del local cambiaron. Vuelve a comprobar la compra.")
        if (latestCandidate.store.businessId != cart.businessId) {
            return CheckoutReviewResult.Error("El local cambió de negocio. Vuelve a comprobar la compra.")
        }
        val lastEligibility = evaluateStoreEligibility(latestCandidate, address.location,
            getCurrentDayOfWeek(), getCurrentFormattedTime())
        if (lastEligibility is StoreEligibilityResult.Excluded) {
            return CheckoutReviewResult.Blocked(listOf(lastEligibility.reason.message()),
                canChangeAddress = lastEligibility.reason == StoreExclusionReason.OUTSIDE_COVERAGE ||
                    lastEligibility.reason == StoreExclusionReason.INVALID_ADDRESS)
        }
        if (!contexts.isCurrent(context)) return CheckoutReviewResult.Error("La compra cambió. Vuelve a comprobarla.")
        val administrative = administrativeEligibility.check(storeId, context.ownerUid, context.sessionGeneration)
        if (!contexts.isCurrentSession(context)) return CheckoutReviewResult.SignedOut
        if (!contexts.isCurrent(context)) return CheckoutReviewResult.Error("La compra cambió. Vuelve a comprobarla.")
        when (administrative) {
            is PurchaseEligibility.Eligible -> if (administrative.businessId != cart.businessId) {
                return CheckoutReviewResult.Error("El local cambió de negocio. Vuelve a comprobar la compra.")
            }
            is PurchaseEligibility.Blocked -> return CheckoutReviewResult.Blocked(listOf(when (administrative.reason) {
                PurchaseBlockReason.BUSINESS_SUSPENDED -> "El negocio está suspendido y no puede recibir compras."
                PurchaseBlockReason.BUSINESS_NOT_APPROVED -> "El negocio todavía no está habilitado para recibir compras."
                PurchaseBlockReason.STORE_UNAVAILABLE -> "El local no está disponible para recibir compras."
            }))
            PurchaseEligibility.SessionChanged -> return CheckoutReviewResult.SignedOut
            PurchaseEligibility.AuthenticationRequired -> return CheckoutReviewResult.Error("Vuelve a iniciar sesión para comprobar la compra.")
            PurchaseEligibility.Unavailable -> return CheckoutReviewResult.Error("No pudimos comprobar la habilitación del negocio. Vuelve a comprobar la compra.")
        }
        val deliveryResult = deliveryQuotes.quote(storeId, address.id, uid, context.sessionGeneration)
        if (!contexts.isCurrentSession(context)) return CheckoutReviewResult.SignedOut
        if (!contexts.isCurrent(context)) return CheckoutReviewResult.Error("La compra cambió. Vuelve a comprobarla.")
        val deliveryQuote = when (deliveryResult) {
            is DeliveryQuoteResult.Quoted -> deliveryResult.quote
            is DeliveryQuoteResult.Failed -> return deliveryResult.reason.toReviewResult()
        }
        if (deliveryQuote.storeLocation.latitude != latestCandidate.store.latitude ||
            deliveryQuote.storeLocation.longitude != latestCandidate.store.longitude ||
            deliveryQuote.deliveryLocation != address.location) {
            return CheckoutReviewResult.Error(
                "La ubicación del local o la dirección cambió. Vuelve a comprobar la compra.")
        }
        val deadline = deliveryQuoteDeadlineOrNull(deliveryQuote, clock)
            ?: return CheckoutReviewResult.Error("La cotización de reparto venció. Vuelve a comprobar la compra.")
        val reviewed = buildCheckoutQuote(
            ownerUid = uid,
            storeId = storeId,
            storeName = latestCandidate.store.name,
            addressId = address.id,
            addressLabel = address.label,
            addressText = address.addressText,
            pricedItems = pricedItems,
            deliveryQuote = deliveryQuote,
            deliveryQuoteDeadlineMillis = deadline,
        )
        // Contact evidence is frozen during review, never re-read after acceptance.
        // Auth is the existing visible identity; foodApi still compares its authoritative profile.
        val buyer = buyers?.read(uid, context.sessionGeneration)
        if (!contexts.isCurrentSession(context)) return CheckoutReviewResult.SignedOut
        if (!contexts.isCurrent(context)) return CheckoutReviewResult.Error("La compra cambió. Vuelve a comprobarla.")
        val accepted = if (buyer == null) null else AcceptedPurchase(uid, administrative.businessId, storeId,
            requireNotNull(administrative.businessName), latestCandidate.store.name,
            latestDocument.getString("phone")?.takeIf { it.isNotBlank() }, buyer.name, buyer.phone,
            OrderLocationSnapshot(requireNotNull(latestDocument.getString("address")), deliveryQuote.storeLocation),
            OrderDeliverySnapshot(address.id, address.label, OrderLocationSnapshot(address.addressText, address.location, address.reference, address.instructions)),
            orderItems, OrderAmounts(reviewed.subtotalMinor, reviewed.deliveryFeeMinor, reviewed.totalMinor),
            AcceptedDeliveryQuote(requireNotNull(deliveryQuote.quoteId), deliveryQuote))
        return CheckoutReviewResult.Ready(reviewed.copy(acceptedPurchase = accepted))
    }

    private suspend fun serverStore(storeId: String): DocumentSnapshot {
        onStoreGet?.invoke()
        return firestore.collection("stores").document(storeId).get(Source.SERVER).await()
    }

}

private fun DeliveryQuoteFailure.toReviewResult(): CheckoutReviewResult = when (this) {
    DeliveryQuoteFailure.SESSION_CHANGED -> CheckoutReviewResult.SignedOut
    DeliveryQuoteFailure.AUTHENTICATION_REQUIRED -> CheckoutReviewResult.Error(
        "Vuelve a iniciar sesión para cotizar el reparto.")
    DeliveryQuoteFailure.ADDRESS_NOT_FOUND, DeliveryQuoteFailure.INVALID_LOCATION -> CheckoutReviewResult.Blocked(
        listOf("La dirección seleccionada ya no es válida. Elige otra dirección."), canChangeAddress = true)
    DeliveryQuoteFailure.OUTSIDE_COVERAGE -> CheckoutReviewResult.Blocked(
        listOf("La dirección seleccionada está fuera de la cobertura de reparto."), canChangeAddress = true)
    DeliveryQuoteFailure.STORE_UNAVAILABLE -> CheckoutReviewResult.Blocked(
        listOf("El local ya no está disponible para recibir compras."))
    DeliveryQuoteFailure.BUSINESS_SUSPENDED -> CheckoutReviewResult.Blocked(
        listOf("El negocio está suspendido y no puede recibir compras."))
    DeliveryQuoteFailure.BUSINESS_NOT_APPROVED -> CheckoutReviewResult.Blocked(
        listOf("El negocio todavía no está habilitado para recibir compras."))
    DeliveryQuoteFailure.NO_ROUTE -> CheckoutReviewResult.Blocked(
        listOf("No encontramos una ruta de reparto para esta dirección. Elige otra dirección."), canChangeAddress = true)
    DeliveryQuoteFailure.ROUTING_QUOTA_EXCEEDED -> CheckoutReviewResult.Error(
        "La cotización de reparto está temporalmente limitada. Reintenta más tarde.")
    DeliveryQuoteFailure.ROUTING_UNAVAILABLE, DeliveryQuoteFailure.SERVER_UNAVAILABLE,
    DeliveryQuoteFailure.CONNECTIVITY, DeliveryQuoteFailure.TIMEOUT -> CheckoutReviewResult.Error(
        "No pudimos cotizar el reparto. Comprueba tu conexión y reintenta.")
    else -> CheckoutReviewResult.Error("No pudimos validar la cotización de reparto. Reintenta más tarde.")
}

private fun StoreExclusionReason.message(): String = when (this) {
    StoreExclusionReason.INVALID_ADDRESS -> "La dirección seleccionada no tiene una ubicación válida."
    StoreExclusionReason.INVALID_STORE_LOCATION,
    StoreExclusionReason.INVALID_COVERAGE -> "El local no tiene una ubicación o cobertura válida."
    StoreExclusionReason.STORE_NOT_ENABLED -> "El local ya no está habilitado para recibir pedidos."
    StoreExclusionReason.STORE_NOT_OPERATIONAL -> "El local está pausado o cerrado."
    StoreExclusionReason.OUTSIDE_OPERATING_HOURS -> "El local está fuera de su horario de atención."
    StoreExclusionReason.OUTSIDE_COVERAGE -> "La dirección seleccionada está fuera de la cobertura del local."
}

private fun AddToCartRejectionReason.message(): String = when (this) {
    AddToCartRejectionReason.OUT_OF_STOCK -> "se agotó. Elimínalo del carrito antes de continuar."
    AddToCartRejectionReason.PRODUCT_INACTIVE -> "ya no está a la venta."
    AddToCartRejectionReason.INVALID_QUANTITY -> "la cantidad no es válida."
    AddToCartRejectionReason.CONFIGURATION_MISSING,
    AddToCartRejectionReason.CONFIGURATION_INVALID -> "no tiene precio o disponibilidad válidos."
    else -> "ya no corresponde a la oferta de este local."
}
