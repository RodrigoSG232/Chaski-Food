package com.chaskifood.app.feature.address.presentation

import com.chaskifood.app.feature.address.domain.AddressBook
import com.chaskifood.app.feature.address.domain.AddressBookState
import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.feature.address.domain.AddressDraft
import com.chaskifood.app.feature.address.domain.AddressField
import com.chaskifood.app.feature.address.domain.AddressValidationError

data class AddressEditorState(
    val ownerUid: String,
    val addressId: String? = null,
    val operationId: String,
    val draft: AddressDraft = AddressDraft(),
    val dirty: Boolean = false,
    // Un alta sin respuesta concluyente solo puede reenviarse con el mismo ID y contenido.
    val pendingCreate: Boolean = false,
    val errors: Map<AddressField, AddressValidationError> = emptyMap(),
)

data class AddressDeleteState(val addressId: String, val replacementId: String? = null)

data class AddressMapRequest(val id: String, val initialPoint: AddressCoordinates?)

data class AddressUiState(
    val source: AddressBookState? = null,
    val choiceId: String? = null,
    val editor: AddressEditorState? = null,
    val optionsId: String? = null,
    val deletion: AddressDeleteState? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val deliveryNotice: String? = null,
    val showDiscard: Boolean = false,
    val mapRequest: AddressMapRequest? = null,
    val exitRequested: Boolean = false,
) {
    val book: AddressBook? get() = (source as? AddressBookState.Ready)?.book
}

sealed interface AddressAction {
    data object Retry : AddressAction
    data object Add : AddressAction
    data class Choose(val id: String) : AddressAction
    data object UseChosen : AddressAction
    data class Options(val id: String) : AddressAction
    data object CloseDialog : AddressAction
    data class Edit(val id: String) : AddressAction
    data class ChangeText(val field: AddressField, val value: String) : AddressAction
    data object Save : AddressAction
    data class SetDefault(val id: String) : AddressAction
    data class AskDelete(val id: String) : AddressAction
    data class ChooseReplacement(val id: String) : AddressAction
    data object ConfirmDelete : AddressAction
    data object Back : AddressAction
    data object Discard : AddressAction
    data object OpenMap : AddressAction
    data class CloseMap(val requestId: String) : AddressAction
    data class ConfirmMap(
        val requestId: String,
        val point: AddressCoordinates,
        val suggestedAddress: String?,
    ) : AddressAction
    data object DismissMessage : AddressAction
}
