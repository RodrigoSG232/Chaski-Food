package com.chaskifood.app.feature.address.presentation

import androidx.lifecycle.SavedStateHandle
import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.feature.address.domain.AddressDraft

/** Estado pequeño del formulario, no caché del catálogo. Nunca se escribe en logs. */
internal class AddressEditorSavedState(private val handle: SavedStateHandle) {
    fun write(editor: AddressEditorState?) {
        if (editor == null) {
            handle.remove<ArrayList<String>>(KEY)
            return
        }
        handle[KEY] = arrayListOf(
            editor.ownerUid, editor.addressId.orEmpty(), editor.operationId,
            editor.draft.label, editor.draft.addressText, editor.draft.reference,
            editor.draft.instructions, editor.draft.location?.latitude?.toString().orEmpty(),
            editor.draft.location?.longitude?.toString().orEmpty(),
            editor.draft.isLocationConfirmed.toString(), editor.dirty.toString(),
            editor.pendingCreate.toString(),
        )
    }

    fun read(): AddressEditorState? {
        val values = handle.get<ArrayList<String>>(KEY) ?: return null
        if (values.size != 12 || values[0].isBlank() || values[2].isBlank()) return null
        val latitude = values[7].toDoubleOrNull()
        val longitude = values[8].toDoubleOrNull()
        val location = if (latitude != null && longitude != null) {
            AddressCoordinates(latitude, longitude).takeIf { it.isValid }
        } else null
        return AddressEditorState(
            ownerUid = values[0], addressId = values[1].ifEmpty { null }, operationId = values[2],
            draft = AddressDraft(
                label = values[3], addressText = values[4], reference = values[5],
                instructions = values[6], location = location,
                isLocationConfirmed = location != null && values[9] == "true",
            ),
            dirty = values[10] == "true", pendingCreate = values[11] == "true",
        )
    }

    private companion object { const val KEY = "address_editor_v1" }
}
