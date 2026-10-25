package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.address.domain.model.Address
import com.chaskifood.app.feature.address.domain.model.AddressBookState
import com.chaskifood.app.feature.address.domain.repository.AddressRepository
import com.chaskifood.app.feature.auth.domain.AuthRepository
import com.chaskifood.app.feature.business.domain.getCurrentDayOfWeek
import com.chaskifood.app.feature.business.domain.getCurrentFormattedTime
import com.chaskifood.app.feature.cart.domain.AddToCartRejectionReason
import com.chaskifood.app.feature.cart.domain.AddToCartValidationResult
import com.chaskifood.app.feature.cart.domain.Cart
import com.chaskifood.app.feature.cart.domain.CartState
import com.chaskifood.app.feature.cart.domain.CartRepository
import com.chaskifood.app.feature.cart.domain.ValidateProductForCart
import com.chaskifood.app.feature.checkout.domain.CheckoutPricedItem
import com.chaskifood.app.feature.checkout.domain.CheckoutRepository
import com.chaskifood.app.feature.checkout.domain.CheckoutReviewResult
import com.chaskifood.app.feature.checkout.domain.DELIVERY_FEE_MINOR
import com.chaskifood.app.feature.checkout.domain.buildCheckoutQuote
import com.chaskifood.app.feature.discover.data.toDiscoveryCandidate
import com.chaskifood.app.feature.discover.domain.StoreEligibilityResult
import com.chaskifood.app.feature.discover.domain.StoreExclusionReason
import com.chaskifood.app.feature.discover.domain.evaluateStoreEligibility
import com.chaskifood.app.feature.search.domain.ProductOfferReadResult
import com.chaskifood.app.feature.search.domain.ProductOfferReader
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/** Vuelve a leer Firestore desde el servidor; nunca interpreta un fallo como compra válida. */
internal class FirebaseCheckoutRepository @Inject constructor(
    private val auth: AuthRepository,
    private val carts: CartRepository,
    private val addresses: AddressRepository,
    private val offers: ProductOfferReader,
    private val firestore: FirebaseFirestore,
) : CheckoutRepository {
    private val validateProduct = ValidateProductForCart()

    override suspend fun review(): CheckoutReviewResult = try {
        reviewVerified()
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

    private suspend fun reviewVerified(): CheckoutReviewResult {
        val uid = auth.currentUserFlow.first()?.uid ?: return CheckoutReviewResult.SignedOut
        val cart = when (val state = carts.observeCart().first { it !is CartState.Loading }) {
            is CartState.Ready -> state.cart
            CartState.SignedOut -> return CheckoutReviewResult.SignedOut
            CartState.ReadError -> return CheckoutReviewResult.Error("No pudimos leer tu carrito.")
            CartState.Loading -> error("Loading ya fue filtrado")
        }
        if (cart.ownerUid != uid) return CheckoutReviewResult.SignedOut
        if (cart.items.isEmpty()) return CheckoutReviewResult.Blocked(listOf("Tu carrito está vacío."))
        val storeId = cart.storeId ?: return CheckoutReviewResult.Error("El carrito no tiene un local válido.")
        val address = when (val result = selectedAddress(uid)) {
            is AddressResult.Ready -> result.address
            is AddressResult.Failed -> return result.result
        }
        val document = firestore.collection("stores").document(storeId).get(Source.SERVER).await()
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

        val pricedItems = mutableListOf<CheckoutPricedItem>()
        val reasons = mutableListOf<String>()
        for (item in cart.items) {
            when (val offer = offers.read(storeId, item.productId, address.location)) {
                is ProductOfferReadResult.Found -> {
                    when (val validated = validateProduct(storeId, offer.product,
                        offer.configuration, item.quantity)) {
                        is AddToCartValidationResult.Allowed -> {
                            val current = validated.item
                            if (current.businessId != item.businessId || current.storeId != item.storeId) {
                                return CheckoutReviewResult.Error("La oferta ya no corresponde al local del carrito.")
                            }
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
        // Una edición del carrito, dirección o sesión durante las lecturas invalida la revisión.
        if (auth.currentUserFlow.first()?.uid != uid ||
            (carts.observeCart().first { it !is CartState.Loading } as? CartState.Ready)?.cart != cart ||
            (selectedAddress(uid) as? AddressResult.Ready)?.address != address) {
            return CheckoutReviewResult.Error("La compra cambió mientras la revisábamos. Vuelve a comprobarla.")
        }
        val latestDocument = firestore.collection("stores").document(storeId).get(Source.SERVER).await()
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
            return CheckoutReviewResult.Blocked(listOf(lastEligibility.reason.message()))
        }
        return CheckoutReviewResult.Ready(buildCheckoutQuote(
            ownerUid = uid,
            storeId = storeId,
            storeName = latestCandidate.store.name,
            addressId = address.id,
            addressLabel = address.label,
            addressText = address.addressText,
            pricedItems = pricedItems,
            deliveryFeeMinor = DELIVERY_FEE_MINOR,
        ))
    }

    private suspend fun selectedAddress(uid: String): AddressResult = when (
        val state = addresses.observeAddressBook().first { it !is AddressBookState.Loading }
    ) {
        AddressBookState.SignedOut -> AddressResult.Failed(CheckoutReviewResult.SignedOut)
        is AddressBookState.Error -> AddressResult.Failed(CheckoutReviewResult.Error(
            "No pudimos comprobar tu dirección de entrega."))
        is AddressBookState.Ready -> {
            if (state.book.ownerUid != uid) AddressResult.Failed(CheckoutReviewResult.SignedOut)
            else state.book.addresses.singleOrNull { it.id == state.book.selectedAddressId }
                ?.let(AddressResult::Ready)
                ?: AddressResult.Failed(CheckoutReviewResult.Blocked(
                    listOf("Selecciona una dirección de entrega válida."), canChangeAddress = true))
        }
        is AddressBookState.Loading -> error("Loading ya fue filtrado")
    }

    private sealed interface AddressResult {
        data class Ready(val address: Address) : AddressResult
        data class Failed(val result: CheckoutReviewResult) : AddressResult
    }
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
