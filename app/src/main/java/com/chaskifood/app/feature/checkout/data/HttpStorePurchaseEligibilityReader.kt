package com.chaskifood.app.feature.checkout.data

import com.chaskifood.app.feature.checkout.data.remote.StoreEligibilityApi
import com.chaskifood.app.feature.checkout.domain.PurchaseBlockReason
import com.chaskifood.app.feature.checkout.domain.PurchaseEligibility
import com.chaskifood.app.feature.checkout.domain.StorePurchaseEligibilityReader
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal class HttpStorePurchaseEligibilityReader @Inject constructor(
    private val api: StoreEligibilityApi,
    private val tokens: BuyerTokenSource,
) : StorePurchaseEligibilityReader {
    override suspend fun check(storeId: String, ownerUid: String, sessionGeneration: Long): PurchaseEligibility {
        return try {
            withTimeoutOrNull(10_000) {
                val token = tokens.token(ownerUid, sessionGeneration)
                val response = api.check(storeId, "Bearer $token")
                if (!tokens.isCurrent(ownerUid, sessionGeneration)) return@withTimeoutOrNull PurchaseEligibility.SessionChanged
                if (response.code() == 401) return@withTimeoutOrNull PurchaseEligibility.AuthenticationRequired
                if (!response.isSuccessful) return@withTimeoutOrNull PurchaseEligibility.Unavailable
                val body = response.body() ?: return@withTimeoutOrNull PurchaseEligibility.Unavailable
                if (body.storeId != storeId || body.checkedAt.isBlank()) return@withTimeoutOrNull PurchaseEligibility.Unavailable
                when (body.decision) {
                    "ELIGIBLE" -> body.businessId?.takeIf { it.isNotBlank() && '/' !in it }
                        ?.let { PurchaseEligibility.Eligible(it, body.businessName?.takeIf(String::isNotBlank)) } ?: PurchaseEligibility.Unavailable
                    "BUSINESS_SUSPENDED" -> PurchaseEligibility.Blocked(PurchaseBlockReason.BUSINESS_SUSPENDED)
                    "BUSINESS_NOT_APPROVED" -> PurchaseEligibility.Blocked(PurchaseBlockReason.BUSINESS_NOT_APPROVED)
                    "STORE_UNAVAILABLE" -> PurchaseEligibility.Blocked(PurchaseBlockReason.STORE_UNAVAILABLE)
                    else -> PurchaseEligibility.Unavailable
                }
            } ?: PurchaseEligibility.Unavailable
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: BuyerSessionChanged) {
            PurchaseEligibility.SessionChanged
        } catch (_: BuyerAuthenticationRequired) {
            PurchaseEligibility.AuthenticationRequired
        } catch (_: Exception) {
            PurchaseEligibility.Unavailable
        }
    }
}
