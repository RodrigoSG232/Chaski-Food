package com.chaskifood.app.feature.address.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaskifood.app.R
import com.chaskifood.app.feature.address.domain.AddressBook
import com.chaskifood.app.feature.address.domain.AddressBookState
import com.chaskifood.app.feature.address.domain.AddressCoordinates
import com.chaskifood.app.feature.address.domain.AddressField
import com.chaskifood.app.feature.address.domain.AddressLimits
import com.chaskifood.app.feature.address.domain.AddressValidationError
import com.chaskifood.app.feature.address.domain.AddressValidator
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiDivider
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiSurfaceVariant
import com.chaskifood.app.ui.theme.ChaskiTextDisabled
import com.chaskifood.app.ui.theme.ChaskiTextPrimary
import com.chaskifood.app.ui.theme.ChaskiTextTertiary

@Composable
fun AddressRoute(viewModel: AddressViewModel, onBack: () -> Unit, onSignIn: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exitRequested) {
        if (state.exitRequested) {
            viewModel.acknowledgeExit()
            onBack()
        }
    }
    val mapRequest = state.mapRequest
    val editor = state.editor
    if (mapRequest != null && editor != null) {
        key(mapRequest.id) {
            AddressMapRoute(
                mapRequest, editor.draft.addressText,
                onCancel = { viewModel.onAction(AddressAction.CloseMap(mapRequest.id)) },
                onConfirm = { point, suggestedAddress ->
                    viewModel.onAction(AddressAction.ConfirmMap(mapRequest.id, point, suggestedAddress))
                },
            )
        }
    } else AddressScreen(state, viewModel::onAction, onSignIn)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressScreen(
    state: AddressUiState,
    onAction: (AddressAction) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { if (!state.busy) onAction(AddressAction.Back) }
    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = ChaskiSurface,
        contentColor = ChaskiTextPrimary,
        topBar = {
            CenterAlignedTopAppBar(
                expandedHeight = 80.dp,
                title = {
                    Text(
                        stringResource(
                            when {
                                state.editor?.addressId != null -> R.string.address_edit
                                state.editor != null -> R.string.address_add
                                else -> R.string.address_title
                            },
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onAction(AddressAction.Back) }, enabled = !state.busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.address_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ChaskiSurface),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding).consumeWindowInsets(padding),
        ) {
            state.message?.let { message ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { onAction(AddressAction.DismissMessage) }) {
                        Text(stringResource(R.string.address_understood))
                    }
                }
            }
            when {
                state.editor != null -> AddressForm(state, onAction)
                state.source is AddressBookState.SignedOut -> AddressStatus(
                    stringResource(R.string.address_signed_out),
                    stringResource(R.string.address_sign_in), onSignIn,
                )
                state.source is AddressBookState.Error -> AddressStatus(
                    stringResource(R.string.address_load_error),
                    stringResource(R.string.address_retry), { onAction(AddressAction.Retry) },
                )
                state.book != null -> AddressList(state, onAction)
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = ChaskiPrimary)
                    Text(stringResource(R.string.address_loading), Modifier.padding(top = 16.dp))
                }
            }
        }
    }
    AddressDialogs(state, onAction)
}

@Composable
private fun AddressStatus(message: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        AddressButton(button, onClick)
    }
}

@Composable
private fun AddressList(state: AddressUiState, onAction: (AddressAction) -> Unit) {
    val book = state.book ?: return
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .weight(1f)
                .selectableGroup(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (book.addresses.isEmpty()) {
                item {
                    Text(stringResource(R.string.address_empty_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.address_empty_body), Modifier.padding(top = 16.dp),
                        color = ChaskiTextTertiary,
                    )
                    Text(
                        stringResource(R.string.address_first_default), Modifier.padding(top = 16.dp),
                        style = MaterialTheme.typography.bodySmall, color = ChaskiTextTertiary,
                    )
                }
            } else {
                item { Text(stringResource(R.string.address_choose_hint), color = ChaskiTextTertiary) }
                items(book.addresses, key = { it.id }) { address ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        state.choiceId == address.id, enabled = !state.busy,
                                        role = Role.RadioButton, onClick = { onAction(AddressAction.Choose(address.id)) },
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    state.choiceId == address.id, onClick = null,
                                    enabled = !state.busy,
                                    colors = RadioButtonDefaults.colors(selectedColor = ChaskiPrimary),
                                )
                                Text(
                                    address.label.ifBlank { stringResource(R.string.address_unlabelled) },
                                    Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold,
                                )
                            }
                            IconButton(onClick = { onAction(AddressAction.Options(address.id)) }, enabled = !state.busy) {
                                Icon(
                                    Icons.Default.MoreVert, stringResource(
                                        R.string.address_options_for,
                                        address.label.ifBlank { address.addressText },
                                    ),
                                )
                            }
                        }
                        Text(address.addressText, style = MaterialTheme.typography.bodyMedium, color = ChaskiTextTertiary)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            TextButton(
                                onClick = { onAction(AddressAction.Edit(address.id)) },
                                enabled = !state.busy,
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = ChaskiPrimary,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Editar punto en el Mapa",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChaskiPrimary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (book.selectedAddressId == address.id) Text(
                            stringResource(R.string.address_selected),
                            Modifier.padding(top = 4.dp), color = ChaskiPrimary, style = MaterialTheme.typography.bodySmall,
                        )
                        if (book.defaultAddressId == address.id) Text(
                            stringResource(R.string.address_default),
                            Modifier.padding(top = 4.dp), color = ChaskiTextTertiary, style = MaterialTheme.typography.bodySmall,
                        )
                        HorizontalDivider(Modifier.padding(top = 16.dp), color = ChaskiDivider)
                    }
                }
                item {
                    Text(
                        stringResource(R.string.address_default_explanation),
                        style = MaterialTheme.typography.bodySmall, color = ChaskiTextTertiary,
                    )
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.busy) BusyIndicator()
            AddressButton(
                stringResource(if (book.addresses.isEmpty()) R.string.address_add_first else R.string.address_add),
                { onAction(AddressAction.Add) }, enabled = !state.busy, outlined = book.addresses.isNotEmpty(),
            )
            if (book.addresses.isNotEmpty()) AddressButton(
                stringResource(R.string.address_use),
                { onAction(AddressAction.UseChosen) }, enabled = !state.busy && state.choiceId != null,
            )
        }
    }
}

@Composable
private fun AddressForm(state: AddressUiState, onAction: (AddressAction) -> Unit) {
    val editor = state.editor ?: return
    val editable = !state.busy && !editor.pendingCreate && state.book != null
    val draft = editor.draft
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ChaskiDimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
    ) {
        AddressInput(R.string.address_label, draft.label, AddressLimits.LABEL, AddressField.LABEL, editor, editable, onAction)
        AddressInput(R.string.address_text, draft.addressText, AddressLimits.ADDRESS_TEXT, AddressField.ADDRESS_TEXT, editor, editable, onAction)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.address_point), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            AddressButton(
                stringResource(if (draft.location == null) R.string.address_choose_point else R.string.address_change_point),
                { onAction(AddressAction.OpenMap) },
                enabled = editable,
                outlined = true,
            )
            editor.errors[AddressField.LOCATION]?.let {
                Text(stringResource(R.string.address_point_required), color = ChaskiPrimary, style = MaterialTheme.typography.bodySmall)
            }
        }

        AddressInput(R.string.address_reference, draft.reference, AddressLimits.REFERENCE, AddressField.REFERENCE, editor, editable, onAction)
        AddressInput(R.string.address_instructions, draft.instructions, AddressLimits.INSTRUCTIONS, AddressField.INSTRUCTIONS, editor, editable, onAction)

        if (state.source is AddressBookState.Error) {
            Text(stringResource(R.string.address_load_error), color = ChaskiPrimary)
            AddressButton(stringResource(R.string.address_retry), { onAction(AddressAction.Retry) }, enabled = !state.busy)
        }

        if (state.busy) BusyIndicator()

        Spacer(Modifier.height(8.dp))

        AddressButton(
            stringResource(
                when {
                    editor.pendingCreate -> R.string.address_retry_save
                    editor.addressId != null -> R.string.address_save_changes
                    else -> R.string.address_save
                },
            ),
            { onAction(AddressAction.Save) },
            enabled = !state.busy && state.book != null && AddressValidator.validate(draft).isValid,
        )
    }
}

@Composable
private fun AddressInput(
    label: Int, value: String, limit: Int, field: AddressField,
    editor: AddressEditorState, enabled: Boolean, onAction: (AddressAction) -> Unit,
) {
    val error = editor.errors[field]
    val labelText = stringResource(label)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        TextField(
            value = value,
            onValueChange = { onAction(AddressAction.ChangeText(field, it)) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = labelText },
            enabled = enabled,
            isError = error != null,
            shape = RoundedCornerShape(8.dp),
            minLines = if (field == AddressField.INSTRUCTIONS) 2 else 1,
            supportingText = {
                Text(
                    if (error == AddressValidationError.REQUIRED) stringResource(R.string.address_text_required)
                    else stringResource(R.string.address_counter, value.length, limit),
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = ChaskiSurfaceVariant,
                unfocusedContainerColor = ChaskiSurfaceVariant,
                disabledContainerColor = ChaskiSurfaceVariant,
                errorContainerColor = ChaskiSurfaceVariant,
                focusedTextColor = ChaskiTextPrimary,
                unfocusedTextColor = ChaskiTextPrimary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = ChaskiPrimary,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressDialogs(state: AddressUiState, onAction: (AddressAction) -> Unit) {
    val book = state.book
    val options = book?.addresses?.find { it.id == state.optionsId }
    val deletion = book?.addresses?.find { it.id == state.deletion?.addressId }
    if (options != null || deletion != null) {
        ModalBottomSheet(
            onDismissRequest = { if (!state.busy) onAction(AddressAction.CloseDialog) },
            containerColor = ChaskiSurface, contentColor = ChaskiTextPrimary,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (options != null) {
                    Text(
                        options.label.ifBlank { stringResource(R.string.address_unlabelled) },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(options.addressText, color = ChaskiTextTertiary)
                    Text(stringResource(R.string.address_default_explanation), style = MaterialTheme.typography.bodyMedium)
                    AddressButton(
                        stringResource(R.string.address_mark_default),
                        { onAction(AddressAction.SetDefault(options.id)) },
                        enabled = !state.busy && options.id != book.defaultAddressId, outlined = true,
                    )
                    AddressButton(
                        stringResource(R.string.address_edit), { onAction(AddressAction.Edit(options.id)) },
                        enabled = !state.busy, outlined = true,
                    )
                    AddressButton(
                        stringResource(R.string.address_delete), { onAction(AddressAction.AskDelete(options.id)) },
                        enabled = !state.busy, outlined = true,
                    )
                }
                if (deletion != null) {
                    val needsReplacement = deletion.id == book.defaultAddressId && book.addresses.size > 1
                    Text(
                        stringResource(
                            if (book.addresses.size == 1) R.string.address_delete_last_title
                            else R.string.address_delete_title,
                        ), style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(deletion.label.ifBlank { deletion.addressText }, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(
                            when {
                                book.addresses.size == 1 -> R.string.address_delete_last_body
                                needsReplacement -> R.string.address_replace_body
                                book.selectedAddressId == deletion.id -> R.string.address_delete_selected_body
                                else -> R.string.address_delete_body
                            },
                        ), color = ChaskiTextTertiary,
                    )
                    if (needsReplacement) Column(Modifier.selectableGroup()) {
                        book.addresses.filter { it.id != deletion.id }.forEach { replacement ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        state.deletion?.replacementId == replacement.id,
                                        enabled = !state.busy, role = Role.RadioButton,
                                        onClick = { onAction(AddressAction.ChooseReplacement(replacement.id)) },
                                    )
                                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    state.deletion?.replacementId == replacement.id, onClick = null,
                                    enabled = !state.busy,
                                    colors = RadioButtonDefaults.colors(selectedColor = ChaskiPrimary),
                                )
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(replacement.label.ifBlank { stringResource(R.string.address_unlabelled) })
                                    Text(replacement.addressText, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    AddressButton(
                        stringResource(if (needsReplacement) R.string.address_delete_replace else R.string.address_delete),
                        { onAction(AddressAction.ConfirmDelete) },
                        enabled = !state.busy && (!needsReplacement || state.deletion?.replacementId != null), destructive = true,
                    )
                }
                if (state.busy) BusyIndicator()
                state.message?.let { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                AddressButton(
                    stringResource(R.string.address_close), { onAction(AddressAction.CloseDialog) },
                    enabled = !state.busy, outlined = true,
                )
            }
        }
    }
    if (state.showDiscard) {
        AlertDialog(
            onDismissRequest = { onAction(AddressAction.CloseDialog) }, containerColor = ChaskiSurface,
            title = { Text(stringResource(R.string.address_discard_title)) },
            text = { Text(stringResource(R.string.address_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onAction(AddressAction.Discard)
                }) { Text(stringResource(R.string.address_discard)) }
            },
            dismissButton = {
                if (state.showDiscard) TextButton(onClick = { onAction(AddressAction.CloseDialog) }) {
                    Text(stringResource(R.string.address_keep_editing))
                }
            },
        )
    }
}

/** Variación HU04: radio de 8 dp, sin alterar botones de Auth/negocio. */
@Composable
internal fun AddressButton(
    text: String, onClick: () -> Unit, enabled: Boolean = true,
    outlined: Boolean = false, destructive: Boolean = false,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
    if (outlined) OutlinedButton(
        onClick, modifier, enabled = enabled,
        shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, ChaskiTextPrimary),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ChaskiTextPrimary),
    ) { Text(text) }
    else Button(
        onClick, modifier, enabled = enabled, shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) ChaskiPrimary else ChaskiTextPrimary,
            contentColor = Color.White,
        ),
    ) { Text(text) }
}

@Composable
private fun BusyIndicator() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), color = ChaskiPrimary, strokeWidth = 2.dp)
        Text(stringResource(R.string.address_working))
    }
}