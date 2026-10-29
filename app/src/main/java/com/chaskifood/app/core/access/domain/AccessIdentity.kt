package com.chaskifood.app.core.access.domain

enum class EmailVerification { VERIFIED, UNVERIFIED, UNKNOWN }

data class AccessSession(val uid: String, val generation: Long) {
    init { require(uid.isNotBlank()); require(generation >= 0) }
}

sealed interface AccessIdentity {
    data object SignedOut : AccessIdentity
    data class Authenticated(
        val session: AccessSession,
        val email: String?,
        val verification: EmailVerification,
        val isAdmin: Boolean = false,
    ) : AccessIdentity
}
