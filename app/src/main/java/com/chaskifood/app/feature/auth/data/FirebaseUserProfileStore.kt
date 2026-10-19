package com.chaskifood.app.feature.auth.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Datos propios de Chaski Food; Firebase Authentication sigue siendo la fuente de identidad. */
@Singleton
class FirebaseUserProfileStore @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {
    suspend fun ensure(user: FirebaseUser) {
        ensureTask(user).await()
    }

    internal fun ensureTask(user: FirebaseUser) = firestore.runTransaction { transaction ->
        val reference = firestore.collection("users").document(user.uid)
        check(auth.currentUser?.uid == user.uid) { "La sesión cambió." }
        val existing = transaction.get(reference)
        val authName = user.displayName?.trim().orEmpty().take(200)
        if (!existing.exists()) {
            transaction.set(reference, initialFields(user, authName))
        } else if (authName.isNotBlank() && existing.getString("displayName").isNullOrBlank()) {
            transaction.update(reference, mapOf(
                "displayName" to authName,
                "updatedAt" to FieldValue.serverTimestamp(),
            ))
        }
    }

    suspend fun updateDisplayName(user: FirebaseUser, displayName: String) {
        val reference = firestore.collection("users").document(user.uid)
        firestore.runTransaction { transaction ->
            check(auth.currentUser?.uid == user.uid) { "La sesión cambió." }
            if (transaction.get(reference).exists()) {
                transaction.update(reference, mapOf(
                    "displayName" to displayName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ))
            } else {
                transaction.set(reference, initialFields(user, displayName))
            }
        }.await()
    }

    private fun initialFields(user: FirebaseUser, displayName: String): Map<String, Any> = mapOf(
        "uid" to user.uid,
        "email" to user.email.orEmpty(),
        "displayName" to displayName,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}
