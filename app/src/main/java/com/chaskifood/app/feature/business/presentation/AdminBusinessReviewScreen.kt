package com.chaskifood.app.feature.business.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.R
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.auth.presentation.AuthAppBar
import com.chaskifood.app.feature.business.domain.BusinessRequest
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.chaskifood.app.feature.business.domain.canBeEvaluated
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiStatusApprovedBg
import com.chaskifood.app.ui.theme.ChaskiStatusApprovedFg
import com.chaskifood.app.ui.theme.ChaskiStatusObservedBg
import com.chaskifood.app.ui.theme.ChaskiStatusObservedFg
import com.chaskifood.app.ui.theme.ChaskiStatusPendingBg
import com.chaskifood.app.ui.theme.ChaskiStatusPendingFg
import com.chaskifood.app.ui.theme.ChaskiStatusRejectedBg
import com.chaskifood.app.ui.theme.ChaskiStatusRejectedFg
import com.chaskifood.app.ui.theme.ChaskiStatusSuspendedBg
import com.chaskifood.app.ui.theme.ChaskiStatusSuspendedFg
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextPrimary

@Composable
fun AdminBusinessReviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminBusinessReviewViewModel = hiltViewModel(),
) {
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val filteredResult by viewModel.filteredRequests.collectAsState()
    val actionState by viewModel.actionState.collectAsState()

    var dialogRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var dialogTargetStatus by rememberSaveable { mutableStateOf(BusinessStatus.OBSERVED.name) }
    var dialogObservationInput by rememberSaveable { mutableStateOf("") }
    val activeDialogRequest = (filteredResult as? ApiResult.Success)?.data
        ?.find { it.id == dialogRequestId && it.status.canBeEvaluated }
        ?.let { it to BusinessStatus.valueOf(dialogTargetStatus) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AuthAppBar(onBackClick = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChaskiDimens.ScreenPadding),
        ) {
            Text(
                text = stringResource(R.string.admin_review_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ChaskiTextPrimary,
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingXs))

            Text(
                text = stringResource(R.string.admin_review_subtitle),
                fontSize = 14.sp,
                color = ChaskiTextMuted,
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingMd))

            // Filtros
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val filters = listOf(
                    BusinessStatus.PENDING_REVIEW to "Pendientes",
                    BusinessStatus.OBSERVED to "Observadas",
                    BusinessStatus.APPROVED to "Aprobadas",
                    BusinessStatus.REJECTED to "Rechazadas",
                    null to "Todas",
                )

                items(filters, key = { it.second }) { (status, label) ->
                    val isSelected = selectedFilter == status
                    Surface(
                        onClick = { viewModel.selectedFilter.value = status },
                        enabled = actionState !is UiState.Loading,
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Color(0xFF212121) else Color(0xFFEEEEEE),
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFF616161),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(ChaskiDimens.SpacingLg))

            if (actionState is UiState.Error && dialogRequestId == null) {
                Text(
                    text = (actionState as UiState.Error).message ?: "Error al procesar evaluación",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (actionState is UiState.Loading && dialogRequestId == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
                Text("Guardando evaluación…", style = MaterialTheme.typography.bodySmall)
            }
            if (filteredResult == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Color(0xFF388E3C),
                )
            } else if (filteredResult is ApiResult.Success) {
                val requests = (filteredResult as ApiResult.Success).data

                if (requests.isEmpty()) {
                    Text(
                        text = "No se encontraron solicitudes con el filtro seleccionado.",
                        fontSize = 14.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(top = ChaskiDimens.SpacingLg),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(requests, key = { it.id }) { request ->
                            AdminBusinessCard(
                                request = request,
                                actionsEnabled = actionState !is UiState.Loading,
                                onApprove = {
                                    viewModel.evaluateRequest(
                                        requestId = request.id,
                                        status = BusinessStatus.APPROVED,
                                        onSuccess = {},
                                    )
                                },
                                onObserve = {
                                    viewModel.clearActionError()
                                    dialogObservationInput = request.observations ?: ""
                                    dialogRequestId = request.id
                                    dialogTargetStatus = BusinessStatus.OBSERVED.name
                                },
                                onReject = {
                                    viewModel.clearActionError()
                                    dialogObservationInput = request.observations ?: ""
                                    dialogRequestId = request.id
                                    dialogTargetStatus = BusinessStatus.REJECTED.name
                                },
                            )
                        }
                    }
                }
            } else if (filteredResult is ApiResult.Failure) {
                Text((filteredResult as ApiResult.Failure).message ?: "No se pudieron cargar las solicitudes.",
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::retryLoading) { Text("REINTENTAR") }
            }
        }
    }

    // Modal para Observar / Rechazar con comentario obligatorio
    activeDialogRequest?.let { (request, targetStatus) ->
        val isObserve = targetStatus == BusinessStatus.OBSERVED
        AlertDialog(
            onDismissRequest = { if (actionState !is UiState.Loading) { dialogRequestId = null; viewModel.clearActionError() } },
            title = {
                Text(text = if (isObserve) "Observar Solicitud" else "Rechazar Solicitud")
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Ingresa el motivo u observaciones para '${request.businessName}':",
                        fontSize = 14.sp,
                        color = Color(0xFF424242),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = dialogObservationInput,
                        onValueChange = { dialogObservationInput = it },
                        placeholder = {
                            Text(
                                text = if (isObserve)
                                    "Ej. El RUC ingresado no coincide con la Razón Social."
                                else
                                    "Ej. El negocio no cumple los requisitos mínimos.",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = actionState !is UiState.Loading,
                    )
                    if (actionState is UiState.Error) {
                        Text((actionState as UiState.Error).message ?: "No se pudo completar la evaluación. Intenta nuevamente.",
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (actionState is UiState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val obs = dialogObservationInput.trim()
                        viewModel.evaluateRequest(
                            requestId = request.id,
                            status = targetStatus,
                            observations = obs,
                            onSuccess = {
                                dialogRequestId = null
                            },
                        )
                    },
                    enabled = dialogObservationInput.isNotBlank() && actionState !is UiState.Loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isObserve) Color(0xFFE65100) else Color(0xFFC62828),
                    ),
                ) {
                    Text(text = if (isObserve) "OBSERVAR" else "RECHAZAR")
                }
            },
            dismissButton = {
                TextButton(enabled = actionState !is UiState.Loading,
                    onClick = { dialogRequestId = null; viewModel.clearActionError() }) {
                    Text(text = "CANCELAR")
                }
            },
        )
    }
}

@Composable
private fun AdminBusinessCard(
    request: BusinessRequest,
    actionsEnabled: Boolean,
    onApprove: () -> Unit,
    onObserve: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(ChaskiDimens.SpacingLg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = request.businessName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF212121),
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(status = request.status)
            }

            Spacer(Modifier.height(ChaskiDimens.SpacingSm))

            Text(
                text = "RUC: ${request.ruc} · Categoría: ${request.category}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF616161),
            )
            Text(
                text = "Dirección: ${request.legalAddress}",
                fontSize = 13.sp,
                color = Color(0xFF757575),
            )
            Text(
                text = "Contacto: ${request.phone} | ${request.email}",
                fontSize = 13.sp,
                color = Color(0xFF757575),
            )

            if (!request.observations.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Observaciones: ${request.observations}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE65100),
                )
            }

            if (!request.reviewedBy.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Revisado por: ${request.reviewedBy}",
                    fontSize = 12.sp,
                    color = Color(0xFF9E9E9E),
                )
            }

            if (request.status.canBeEvaluated) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = ChaskiDimens.SpacingMd),
                    color = Color(0xFFEEEEEE),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2E7D32))
                            .clickable(enabled = actionsEnabled, onClick = onApprove)
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "APROBAR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFE65100))
                            .clickable(enabled = actionsEnabled, onClick = onObserve)
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "OBSERVAR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFC62828))
                            .clickable(enabled = actionsEnabled, onClick = onReject)
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "RECHAZAR",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: BusinessStatus) {
    val (bg, fg, label) = when (status) {
        BusinessStatus.PENDING_REVIEW -> Triple(ChaskiStatusPendingBg, ChaskiStatusPendingFg, "PENDIENTE")
        BusinessStatus.OBSERVED -> Triple(ChaskiStatusObservedBg, ChaskiStatusObservedFg, "OBSERVADA")
        BusinessStatus.APPROVED -> Triple(ChaskiStatusApprovedBg, ChaskiStatusApprovedFg, "APROBADA")
        BusinessStatus.REJECTED -> Triple(ChaskiStatusRejectedBg, ChaskiStatusRejectedFg, "RECHAZADA")
        BusinessStatus.SUSPENDED -> Triple(ChaskiStatusSuspendedBg, ChaskiStatusSuspendedFg, "SUSPENDIDO")
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
