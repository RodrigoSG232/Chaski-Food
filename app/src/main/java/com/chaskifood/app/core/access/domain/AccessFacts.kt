package com.chaskifood.app.core.access.domain

import java.util.Collections

/** Solo el futuro adaptador autoritativo puede afirmar Checked; cache o error no son ausencia. */
sealed interface AccessFacts {
    data object Loading : AccessFacts
    data object Unverifiable : AccessFacts
    data object Failed : AccessFacts
    /** Respuesta autoritativa de conflicto, sin fabricar propiedad ni asignaciones. */
    data class ConfirmedConflict(val session: AccessSession) : AccessFacts
    class Checked(
        val session: AccessSession,
        val ownership: AccessOwnership?,
        assignments: List<AccessAssignment>,
        stores: List<AccessStore>,
        val exclusivity: AccessExclusivity = AccessExclusivity.UNVERIFIABLE,
    ) : AccessFacts {
        val assignments: List<AccessAssignment> = Collections.unmodifiableList(assignments.toList())
        val stores: List<AccessStore> = Collections.unmodifiableList(stores.toList())
        override fun equals(other: Any?): Boolean = other is Checked && session == other.session &&
            ownership == other.ownership && assignments.toSet() == other.assignments.toSet() &&
            stores.groupingBy { it }.eachCount() == other.stores.groupingBy { it }.eachCount() && exclusivity == other.exclusivity
        override fun hashCode(): Int = listOf(session, ownership, assignments.toSet(), stores.groupingBy { it }.eachCount(), exclusivity).hashCode()
    }
}

enum class AccessExclusivity { VERIFIED, UNVERIFIABLE }
enum class AccessBusinessStatus { PENDING_REVIEW, APPROVED, OBSERVED, REJECTED, SUSPENDED }
enum class AccessStoreStatus { ACTIVE, INACTIVE, SUSPENDED }
enum class AccessOperationalStatus { OPEN, CLOSED, PAUSED }
enum class AccessBinding { VERIFIED, PENDING, RECONCILIATION_REQUIRED, UNVERIFIABLE }

data class AccessOwnership(
    val businessId: String,
    val ownerUid: String,
    val status: AccessBusinessStatus,
    val suspensionReason: String? = null,
)

/** Una fila por local; no transfiere permisos por coincidencia de correo. */
data class AccessAssignment(
    val businessId: String,
    val storeId: String,
    val boundUid: String?,
    val authorizedEmail: String,
    val binding: AccessBinding,
)

data class AccessStore(
    val businessId: String,
    val storeId: String,
    val businessStatus: AccessBusinessStatus,
    val status: AccessStoreStatus,
    val operationalStatus: AccessOperationalStatus,
)
