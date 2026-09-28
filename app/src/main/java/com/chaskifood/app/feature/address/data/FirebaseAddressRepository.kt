package com.chaskifood.app.feature.address.data

import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.address.domain.Address
import com.chaskifood.app.feature.address.domain.AddressBook
import com.chaskifood.app.feature.address.domain.AddressBookState
import com.chaskifood.app.feature.address.domain.AddressDraft
import com.chaskifood.app.feature.address.domain.AddressRepository
import com.chaskifood.app.feature.address.domain.AddressValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseAddressRepository @Inject constructor(
    private val remote: AddressRemoteDataSource,
    private val selection: AddressSelectionStore,
    private val sessionSource: AddressSessionSource,
) : AddressRepository {
    override fun observeAddressBook(): Flow<AddressBookState> = sessionSource.sessions
        .flatMapLatest { session ->
            flow<Pair<AddressSession, AddressBookState>> {
                val uid = session.uid
                if (uid == null) {
                    emit(session to AddressBookState.SignedOut)
                } else {
                    emit(session to AddressBookState.Loading(uid))
                    emitAll(
                        merge(remote.changes(uid), selection.observe(uid).map { Unit }).mapLatest {
                            // También ante selección local releer: un catálogo anterior no debe
                            // descartar el ID de una dirección que acaba de confirmarse.
                            val state = try {
                                checkSession(session)
                                val book = remote.read(uid)
                                checkSession(session)
                                val selected = selection.reconcile(
                                    uid, book.addresses.map { it.id }.toSet(), book.defaultAddressId,
                                )
                                checkSession(session)
                                AddressBookState.Ready(AddressBook(uid, book.addresses, book.defaultAddressId, selected))
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                AddressBookState.Error(uid, "No se pudieron cargar las direcciones. Reintenta.")
                            }
                            session to state
                        },
                    )
                }
            }.catch { error ->
                if (error is CancellationException) throw error
                val uid = session.uid
                if (uid != null) emit(session to AddressBookState.Error(uid, "No se pudieron cargar las direcciones. Reintenta."))
            }
        }
        .buffer(0)
        .map { (session, state) ->
            if (sessionSource.isCurrent(session)) state else currentLoadingState()
        }

    override suspend fun createAddress(draft: AddressDraft, operationId: String): ApiResult<Address> =
        authenticated { session, uid ->
            val normalized = validDraft(draft)
            requireDocumentId(operationId)
            remote.create(uid, normalized, operationId) { checkSession(session) }
        }

    override suspend fun updateAddress(addressId: String, draft: AddressDraft): ApiResult<Address> =
        authenticated { session, uid ->
            requireDocumentId(addressId)
            remote.update(uid, addressId, validDraft(draft)) { checkSession(session) }
        }

    override suspend fun deleteAddress(addressId: String, replacementDefaultAddressId: String?): ApiResult<Unit> =
        authenticated { session, uid ->
            requireDocumentId(addressId)
            replacementDefaultAddressId?.let(::requireDocumentId)
            remote.delete(uid, addressId, replacementDefaultAddressId) { checkSession(session) }
        }

    override suspend fun setDefaultAddress(addressId: String): ApiResult<Unit> =
        authenticated { session, uid ->
            requireDocumentId(addressId)
            remote.setDefault(uid, addressId) { checkSession(session) }
        }

    override suspend fun selectAddress(addressId: String): ApiResult<Unit> =
        authenticated { session, uid ->
            requireDocumentId(addressId)
            val book = remote.read(uid)
            check(book.addresses.any { it.id == addressId }) { "La dirección ya no está disponible." }
            checkSession(session)
            selection.select(uid, addressId)
        }

    private suspend fun <T> authenticated(block: suspend (AddressSession, String) -> T): ApiResult<T> {
        val session = sessionSource.current()
        val uid = session.uid ?: return ApiResult.Failure("Inicia sesión para gestionar tus direcciones.")
        return try {
            requireDocumentId(uid)
            checkSession(session)
            val result = block(session, uid)
            checkSession(session)
            // La selección se revalida en el flujo observado. Un fallo local nunca revierte
            // una escritura remota confirmada ni convierte su resultado en un alta duplicada.
            ApiResult.Success(result)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // No propagar mensajes del SDK/formularios con posibles datos privados.
            ApiResult.Failure("No se pudo completar la operación. Revisa la sesión, los datos y la conexión.")
        }
    }

    private fun validDraft(draft: AddressDraft): AddressDraft {
        val result = AddressValidator.validate(draft)
        require(result.isValid) { "La dirección no es válida." }
        return result.normalizedDraft
    }

    private fun checkSession(session: AddressSession) {
        check(sessionSource.isCurrent(session)) { "La sesión cambió durante la operación." }
    }

    private fun currentLoadingState(): AddressBookState = sessionSource.current().uid
        ?.let { AddressBookState.Loading(it) } ?: AddressBookState.SignedOut
}
