package com.chaskifood.app.feature.address.data

import com.chaskifood.app.feature.address.domain.Address
import com.chaskifood.app.feature.address.domain.AddressDraft
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

class FirestoreAddressRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) : AddressRemoteDataSource {
    override fun changes(ownerUid: String): Flow<Unit> = callbackFlow {
        val user = user(ownerUid)
        // Los listeners invalidan la lectura; solo read(SERVER) puede confirmar datos.
        val addressesListener = user.collection("addresses").addSnapshotListener(MetadataChanges.INCLUDE) { _, error ->
            if (error != null) close(error) else trySend(Unit)
        }
        val preferencesListener = user.collection("preferences").document("delivery")
            .addSnapshotListener(MetadataChanges.INCLUDE) { _, error ->
                if (error != null) close(error) else trySend(Unit)
            }
        trySend(Unit)
        awaitClose {
            addressesListener.remove()
            preferencesListener.remove()
        }
    }.buffer(Channel.CONFLATED)

    override suspend fun read(ownerUid: String): RemoteAddressBook {
        val user = user(ownerUid)
        val preferences = user.collection("preferences").document("delivery")
        // Todos los cambios del protocolo actualizan este documento. Comparar antes/después
        // evita combinar una lista de una revisión con la predeterminada de otra.
        repeat(3) {
            val before = preferences.get(Source.SERVER).await()
            val snapshot = user.collection("addresses").get(Source.SERVER).await()
            val after = preferences.get(Source.SERVER).await()
            check(!before.metadata.hasPendingWrites() && !snapshot.metadata.hasPendingWrites() &&
                !after.metadata.hasPendingWrites()) { "Hay escrituras pendientes de confirmar." }
            if (before.data != after.data) return@repeat
            val metadata = DeliveryMetadata.fromDocument(after.data)
            val addresses = snapshot.documents.map { AddressDocumentMapper.fromDocument(it.id, requireNotNull(it.data)) }
            check(addresses.size.toLong() == metadata.count) { "El contador requiere revisión; no se reparará automáticamente." }
            check(metadata.defaultId == null || addresses.any { it.id == metadata.defaultId }) { "La predeterminada no existe." }
            return RemoteAddressBook(addresses.sortedWith(compareBy({ it.createdAtEpochMillis }, { it.id })), metadata.defaultId)
        }
        error("Las direcciones cambiaron durante la lectura. Reintenta.")
    }

    override suspend fun create(
        ownerUid: String,
        draft: AddressDraft,
        operationId: String,
        checkSession: () -> Unit,
    ): Address {
        requireDocumentId(operationId)
        val fields = AddressDocumentMapper.createFields(draft)
        val hash = addressPayloadHash(draft)
        val user = user(ownerUid)
        val address = user.collection("addresses").document(operationId)
        val preference = user.collection("preferences").document("delivery")
        val operation = user.collection("address_operations").document(operationId)
        read(ownerUid) // Detectar estado legado/inconsistente antes de permitir nuevas escrituras.
        checkSession()
        transact(preference, checkSession) { transaction, metadata ->
            checkSession()
            val receipt = transaction.get(operation)
            val existing = transaction.get(address)
            if (receipt.exists()) {
                check(receipt.getString("kind") == "create" && receipt.getString("addressId") == operationId &&
                    receipt.getString("payloadHash") == hash) { "La operación ya se utilizó con otros datos." }
                check(existing.exists()) { "La dirección de esta operación ya fue eliminada." }
            } else {
                check(!existing.exists()) { "El identificador de dirección ya está ocupado." }
                metadata.defaultId?.let { id ->
                    check(transaction.get(user.collection("addresses").document(id)).exists()) { "La predeterminada no existe." }
                }
                checkSession()
                transaction.set(address, fields)
                transaction.set(preference, metadata.afterCreate(operationId).fields(operationId))
                transaction.set(operation, receipt("create", operationId, hash))
            }
        }
        checkSession()
        return confirmedAddress(address)
    }

    override suspend fun update(
        ownerUid: String,
        addressId: String,
        draft: AddressDraft,
        checkSession: () -> Unit,
    ): Address {
        requireDocumentId(addressId)
        val fields = AddressDocumentMapper.updateFields(draft)
        val user = user(ownerUid)
        val address = user.collection("addresses").document(addressId)
        val preference = user.collection("preferences").document("delivery")
        val operationId = UUID.randomUUID().toString()
        read(ownerUid)
        checkSession()
        transact(preference, checkSession) { transaction, metadata ->
            checkSession()
            val current = transaction.get(address)
            check(metadata.count > 0 && current.exists()) { "La dirección ya no existe." }
            AddressDocumentMapper.fromDocument(addressId, requireNotNull(current.data))
            checkSession()
            transaction.update(address, fields)
            transaction.set(preference, metadata.fields(operationId))
            transaction.set(user.collection("address_operations").document(operationId), receipt("update", addressId))
        }
        checkSession()
        return confirmedAddress(address)
    }

    override suspend fun delete(ownerUid: String, addressId: String, replacementId: String?, checkSession: () -> Unit) {
        requireDocumentId(addressId)
        replacementId?.let(::requireDocumentId)
        val user = user(ownerUid)
        val address = user.collection("addresses").document(addressId)
        val preference = user.collection("preferences").document("delivery")
        val operationId = UUID.randomUUID().toString()
        read(ownerUid)
        checkSession()
        transact(preference, checkSession) { transaction, metadata ->
            checkSession()
            val current = transaction.get(address)
            check(current.exists()) { "La dirección ya no existe." }
            val next = metadata.afterDelete(addressId, replacementId)
            replacementId?.let { replacement ->
                val target = transaction.get(user.collection("addresses").document(replacement))
                check(target.exists()) { "El reemplazo ya no existe." }
                AddressDocumentMapper.fromDocument(replacement, requireNotNull(target.data))
            }
            checkSession()
            transaction.delete(address)
            transaction.set(preference, next.fields(operationId))
            transaction.set(user.collection("address_operations").document(operationId), receipt("delete", addressId))
        }
        checkSession()
    }

    override suspend fun setDefault(ownerUid: String, addressId: String, checkSession: () -> Unit) {
        requireDocumentId(addressId)
        val user = user(ownerUid)
        val preference = user.collection("preferences").document("delivery")
        val operationId = UUID.randomUUID().toString()
        read(ownerUid)
        checkSession()
        transact(preference, checkSession) { transaction, metadata ->
            checkSession()
            val current = transaction.get(user.collection("addresses").document(addressId))
            check(metadata.count > 0 && current.exists()) { "La dirección ya no existe." }
            AddressDocumentMapper.fromDocument(addressId, requireNotNull(current.data))
            checkSession()
            transaction.set(preference, metadata.copy(defaultId = addressId).fields(operationId))
            transaction.set(user.collection("address_operations").document(operationId), receipt("default", addressId))
        }
        checkSession()
    }

    private fun user(uid: String): DocumentReference {
        requireDocumentId(uid)
        return firestore.collection("users").document(uid)
    }

    private suspend fun transact(
        preference: DocumentReference,
        checkSession: () -> Unit,
        block: (Transaction, DeliveryMetadata) -> Unit,
    ) {
        repeat(3) { attempt ->
            var preferenceRead = false
            var observed: Map<String, Any>? = null
            try {
                firestore.runTransaction { transaction ->
                    checkSession()
                    observed = transaction.get(preference).data
                    preferenceRead = true
                    block(transaction, DeliveryMetadata.fromDocument(observed))
                }.await()
                return
            } catch (error: FirebaseFirestoreException) {
                // Las reglas pueden rechazar un contador obsoleto antes de que el SDK
                // reciba ABORTED. Nunca reintentar permisos sin una revisión diferente.
                checkSession()
                if (error.code != FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                    !preferenceRead || attempt == 2) throw error
                val current = preference.get(Source.SERVER).await()
                checkSession()
                if (current.metadata.hasPendingWrites() || current.data == observed) throw error
            }
        }
    }

    private suspend fun confirmedAddress(reference: DocumentReference): Address {
        val snapshot = reference.get(Source.SERVER).await()
        check(!snapshot.metadata.hasPendingWrites()) { "La escritura aún no está confirmada." }
        return AddressDocumentMapper.fromDocument(snapshot.id, checkNotNull(snapshot.data) { "La dirección ya no existe." })
    }

    private fun receipt(kind: String, addressId: String, hash: String = ""): Map<String, Any> = mapOf(
        "kind" to kind,
        "addressId" to addressId,
        "payloadHash" to hash,
        "createdAt" to FieldValue.serverTimestamp(),
    )
}
