package com.chaskifood.app.core.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAccessControl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {
    fun requireUser(): FirebaseUser = checkNotNull(auth.currentUser) { "Inicia sesión para continuar." }

    suspend fun requireAdmin(): FirebaseUser {
        val user = requireUser()
        check(user.getIdToken(true).await().claims["admin"] == true) { "No tienes permisos administrativos." }
        check(auth.currentUser?.uid == user.uid) { "La sesión cambió." }
        return user
    }

    suspend fun requireBusinessOwner(businessId: String, approved: Boolean = true) {
        val user = requireUser()
        val business = firestore.collection("business_requests").document(businessId).get(Source.SERVER).await()
        check(business.getString("ownerUid") == user.uid) { "No tienes permisos sobre este negocio." }
        check(!approved || business.getString("status") == "APPROVED") { "El negocio no está habilitado." }
        check(auth.currentUser?.uid == user.uid) { "La sesión cambió." }
    }

    suspend fun requireStoreOperator(storeId: String) {
        val user = requireUser()
        val store = firestore.collection("stores").document(storeId).get(Source.SERVER).await()
        val businessId = checkNotNull(store.getString("businessId")) { "Local no disponible." }
        val business = firestore.collection("business_requests").document(businessId).get(Source.SERVER).await()
        check(business.getString("status") == "APPROVED" && store.getString("status") == "ACTIVE") {
            "El negocio o local no está habilitado."
        }
        if (business.getString("ownerUid") != user.uid) {
            val email = user.email?.lowercase() ?: error("No tienes una cuenta asignada.")
            check(user.isEmailVerified) { "Verifica tu correo antes de operar un local asignado." }
            val assignment = firestore.collection("store_managers").document(businessId + "_" + email)
                .get(Source.SERVER).await()
            check((assignment.get("assignedStoreIds") as? List<*>)?.contains(storeId) == true) {
                "No tienes permisos sobre este local."
            }
        }
        check(auth.currentUser?.uid == user.uid) { "La sesión cambió." }
    }
}
