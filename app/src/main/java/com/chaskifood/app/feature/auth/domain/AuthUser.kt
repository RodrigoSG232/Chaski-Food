package com.chaskifood.app.feature.auth.domain

data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val phoneNumber: String? = null,
    val isAdmin: Boolean = false,
)