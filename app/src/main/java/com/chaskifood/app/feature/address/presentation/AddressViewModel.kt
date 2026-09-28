package com.chaskifood.app.feature.address.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.address.domain.AddressBook
import com.chaskifood.app.feature.address.domain.AddressBookState
import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.feature.address.domain.AddressField
import com.chaskifood.app.feature.address.domain.AddressRepository
import com.chaskifood.app.feature.address.domain.AddressValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AddressViewModel @Inject constructor(
    private val repository: AddressRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val savedEditor = AddressEditorSavedState(savedStateHandle)
    private var restoredEditor = savedEditor.read()
    private val _uiState = MutableStateFlow(AddressUiState())
    val uiState = _uiState.asStateFlow()
    private var observation: Job? = null
    private var operation: Job? = null
    private var generation = 0L
    private var previousBook: AddressBook? = null

    init { observe() }

    fun onAction(action: AddressAction) {
        if (action == AddressAction.DismissMessage) {
            change(_uiState.value.copy(message = null, deliveryNotice = null))
            return
        }
        if (_uiState.value.busy) return
        when (action) {
            AddressAction.Retry -> observe()
            AddressAction.Add -> openEditor(null)
            is AddressAction.Edit -> openEditor(action.id)
            is AddressAction.ChangeText -> changeText(action)
            AddressAction.Save -> save()
            is AddressAction.Choose -> if (hasAddress(action.id)) {
                change(_uiState.value.copy(choiceId = action.id))
            }
            AddressAction.UseChosen -> useChosen()
            is AddressAction.Options -> if (hasAddress(action.id)) {
                change(_uiState.value.copy(optionsId = action.id))
            }
            is AddressAction.SetDefault -> if (hasAddress(action.id)) {
                launchOperation(
                    request = { repository.setDefaultAddress(action.id) },
                    success = { change(_uiState.value.copy(optionsId = null,
                        message = "Predeterminada actualizada. La selección para esta compra se conserva.")) },
                )
            }
            is AddressAction.AskDelete -> if (hasAddress(action.id)) {
                change(_uiState.value.copy(optionsId = null, deletion = AddressDeleteState(action.id)))
            }
            is AddressAction.ChooseReplacement -> {
                val deletion = _uiState.value.deletion ?: return
                if (hasAddress(action.id) && action.id != deletion.addressId) {
                    change(_uiState.value.copy(deletion = deletion.copy(replacementId = action.id)))
                }
            }
            AddressAction.ConfirmDelete -> delete()
            AddressAction.Back -> back()
            AddressAction.Discard -> if (_uiState.value.editor?.pendingCreate != true) {
                change(_uiState.value.copy(editor = null, showDiscard = false, message = null))
            }
            AddressAction.CloseDialog -> change(_uiState.value.copy(
                optionsId = null, deletion = null, showDiscard = false, showMapInfo = false,
            ))
            AddressAction.MapInfo -> change(_uiState.value.copy(showMapInfo = true))
            AddressAction.DismissMessage -> Unit
        }
    }

    fun acknowledgeExit() { change(_uiState.value.copy(exitRequested = false)) }

    /** Punto de integración del próximo selector. Cambiar punto siempre invalida confirmación. */
    internal fun changeLocation(location: AddressCoordinates?) {
        val editor = editableEditor() ?: return
        change(_uiState.value.copy(editor = editor.copy(
            draft = editor.draft.withLocation(location), dirty = true, errors = emptyMap(),
        )))
    }

    internal fun confirmLocation() {
        val editor = editableEditor() ?: return
        change(_uiState.value.copy(editor = editor.copy(
            draft = editor.draft.confirmLocation(), dirty = true, errors = emptyMap(),
        )))
    }

    private fun observe() {
        if (_uiState.value.busy) return
        restoredEditor = _uiState.value.editor ?: restoredEditor
        observation?.cancel()
        observation = viewModelScope.launch {
            repository.observeAddressBook().collect(::receive)
        }
    }

    private fun receive(source: AddressBookState) {
        val old = _uiState.value
        when (source) {
            AddressBookState.SignedOut, is AddressBookState.Loading -> {
                generation++
                operation?.cancel()
                previousBook = null
                if (source == AddressBookState.SignedOut ||
                    (source is AddressBookState.Loading && restoredEditor?.ownerUid != source.ownerUid)) {
                    restoredEditor = null
                }
                // Loading también representa una sesión nueva del mismo UID.
                _uiState.value = AddressUiState(source = source)
                savedEditor.write(restoredEditor)
            }
            is AddressBookState.Error -> {
                // No mantener un domicilio visible como si fuera una lectura confirmada.
                if (ownerOf(old.source) != source.ownerUid) {
                    generation++
                    operation?.cancel()
                    previousBook = null
                    restoredEditor = null
                    change(AddressUiState(source = source))
                } else change(old.copy(source = source, optionsId = null, deletion = null))
            }
            is AddressBookState.Ready -> {
                val book = source.book
                val previous = previousBook?.takeIf { it.ownerUid == book.ownerUid }
                val sameOwner = ownerOf(old.source) == book.ownerUid
                if (!sameOwner) {
                    generation++
                    operation?.cancel()
                }
                val base = if (sameOwner) old else AddressUiState()
                val editor = (base.editor ?: restoredEditor)?.takeIf { it.ownerUid == book.ownerUid }
                restoredEditor = null
                val lostSelection = previous?.selectedAddressId != null &&
                    book.addresses.none { it.id == previous.selectedAddressId }
                val deletedEditor = editor?.addressId != null &&
                    book.addresses.none { it.id == editor.addressId }
                val message = when {
                    lostSelection && book.selectedAddressId != null ->
                        "Cambió tu dirección de entrega. Se seleccionó la predeterminada; revísala antes de comprar."
                    lostSelection -> "Ya no tienes una dirección seleccionada. Agrega o elige una antes de comprar."
                    deletedEditor -> "Esta dirección ya no existe. No se volverá a crear al guardar."
                    else -> base.message
                }
                change(base.copy(
                    source = source,
                    choiceId = base.choiceId?.takeIf { id -> book.addresses.any { it.id == id } }
                        ?: book.selectedAddressId,
                    editor = if (deletedEditor) null else editor,
                    optionsId = base.optionsId?.takeIf { id -> book.addresses.any { it.id == id } },
                    deletion = base.deletion?.takeIf { d -> book.addresses.any { it.id == d.addressId } }
                        ?.let { d -> d.copy(replacementId = d.replacementId?.takeIf { id ->
                            book.addresses.any { it.id == id && id != d.addressId }
                        }) },
                    message = message,
                    deliveryNotice = if (lostSelection) message else base.deliveryNotice,
                ))
                previousBook = book
            }
        }
    }

    private fun openEditor(addressId: String?) {
        val state = _uiState.value
        val book = state.book ?: return
        if (state.editor?.pendingCreate == true) return
        val address = addressId?.let { id -> book.addresses.find { it.id == id } ?: return }
        change(state.copy(
            editor = AddressEditorState(
                ownerUid = book.ownerUid, addressId = addressId,
                operationId = UUID.randomUUID().toString(),
                draft = address?.toDraft() ?: com.chaskifood.app.feature.address.domain.AddressDraft(),
            ), optionsId = null, deletion = null, message = null,
        ))
    }

    private fun changeText(action: AddressAction.ChangeText) {
        val editor = editableEditor() ?: return
        val draft = when (action.field) {
            AddressField.LABEL -> editor.draft.copy(label = action.value)
            AddressField.ADDRESS_TEXT -> editor.draft.copy(addressText = action.value)
            AddressField.REFERENCE -> editor.draft.copy(reference = action.value)
            AddressField.INSTRUCTIONS -> editor.draft.copy(instructions = action.value)
            AddressField.LOCATION -> return
        }
        change(_uiState.value.copy(editor = editor.copy(draft = draft, dirty = true,
            errors = AddressValidator.validate(draft).errors), message = null))
    }

    private fun editableEditor(): AddressEditorState? = _uiState.value.editor?.takeIf {
        !_uiState.value.busy && !it.pendingCreate && _uiState.value.book?.ownerUid == it.ownerUid
    }

    private fun save() {
        val editor = _uiState.value.editor ?: return
        if (_uiState.value.book?.ownerUid != editor.ownerUid) return
        val validation = AddressValidator.validate(editor.draft)
        if (!validation.isValid) {
            change(_uiState.value.copy(editor = editor.copy(errors = validation.errors)))
            return
        }
        val pending = editor.copy(draft = validation.normalizedDraft,
            pendingCreate = editor.addressId == null, errors = emptyMap())
        change(_uiState.value.copy(editor = pending))
        launchOperation(
            request = {
                if (pending.addressId == null) repository.createAddress(pending.draft, pending.operationId)
                else repository.updateAddress(pending.addressId, pending.draft)
            },
            success = { change(_uiState.value.copy(editor = null, message = "Dirección guardada.")) },
            failure = if (pending.addressId == null)
                "No pudimos confirmar el alta. Reintenta con los mismos datos para evitar duplicados."
                else "No se pudo guardar. Tus cambios siguen en el formulario; puedes reintentar.",
        )
    }

    private fun useChosen() {
        val id = _uiState.value.choiceId ?: return
        if (!hasAddress(id)) return
        launchOperation(
            request = { repository.selectAddress(id) },
            success = { change(_uiState.value.copy(exitRequested = true)) },
        )
    }

    private fun delete() {
        val state = _uiState.value
        val book = state.book ?: return
        val deletion = state.deletion ?: return
        if (!hasAddress(deletion.addressId)) return
        val needsReplacement = book.defaultAddressId == deletion.addressId && book.addresses.size > 1
        if (needsReplacement && (deletion.replacementId == null || !hasAddress(deletion.replacementId))) {
            change(state.copy(message = "Elige una nueva predeterminada antes de eliminar."))
            return
        }
        launchOperation(
            request = { repository.deleteAddress(deletion.addressId,
                if (needsReplacement) deletion.replacementId else null) },
            success = { change(_uiState.value.copy(deletion = null,
                message = _uiState.value.message ?: "Dirección eliminada.")) },
            failure = "No pudimos confirmar el borrado. Revisa la lista antes de reintentar.",
        )
    }

    private fun back() {
        val state = _uiState.value
        when {
            state.editor?.pendingCreate == true -> change(state.copy(
                message = "Reintenta el alta pendiente antes de cerrar el formulario. No cambiaremos su identificador.",
            ))
            state.editor?.dirty == true -> change(state.copy(showDiscard = true))
            state.editor != null -> change(state.copy(editor = null, message = null))
            else -> change(state.copy(exitRequested = true))
        }
    }

    private fun <T> launchOperation(
        request: suspend () -> ApiResult<T>,
        success: (T) -> Unit,
        failure: String = "No se pudo completar la operación. Comprueba la conexión y vuelve a intentarlo.",
    ) {
        val owner = _uiState.value.book?.ownerUid ?: return
        val token = generation
        change(_uiState.value.copy(busy = true, message = null))
        operation = viewModelScope.launch {
            try {
                val result = request()
                if (token != generation || ownerOf(_uiState.value.source) != owner) return@launch
                when (result) {
                    is ApiResult.Success -> success(result.data)
                    is ApiResult.Failure -> change(_uiState.value.copy(message = failure))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (token == generation) change(_uiState.value.copy(message = failure))
            } finally {
                if (token == generation) change(_uiState.value.copy(busy = false))
            }
        }
    }

    private fun hasAddress(id: String) = _uiState.value.book?.addresses?.any { it.id == id } == true

    private fun change(state: AddressUiState) {
        _uiState.value = state
        savedEditor.write(state.editor ?: restoredEditor)
    }

    private fun ownerOf(source: AddressBookState?): String? = when (source) {
        is AddressBookState.Ready -> source.book.ownerUid
        is AddressBookState.Loading -> source.ownerUid
        is AddressBookState.Error -> source.ownerUid
        else -> null
    }
}
