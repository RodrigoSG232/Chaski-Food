package com.chaskifood.app.feature.business.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.core.common.UiState
import com.chaskifood.app.feature.auth.presentation.AuthAppBar
import com.chaskifood.app.feature.business.domain.BusinessRequest
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens

@Composable
fun AdminBusinessSupervisionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminBusinessSupervisionViewModel = hiltViewModel(),
) {
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val filteredResult by viewModel.filteredRequests.collectAsState()
    val actionState by viewModel.actionState.collectAsState()

    var activeSuspendRequest by remember { mutableStateOf<BusinessRequest?>(null) }
    var suspendReasonInput by remember { mutableStateOf("") }

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
                text = "Supervisión de Negocios",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D0D0D),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingXs))

            Text(
                text = "Controla quién puede operar en la plataforma. Suspende o reactiva negocios según corresponda.",
                fontSize = 14.sp,
                color = Color(0xFF757575),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingMd))

            // Filtros
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val filters = listOf(
                    BusinessStatus.APPROVED to "Habilitados / Activos",
                    BusinessStatus.SUSPENDED to "Suspendidos",
                    null to "Todos",
                )

                items(filters, key = { it.second }) { (status, label) ->
                    val isSelected = selectedFilter == status
                    Surface(
                        onClick = { viewModel.selectedFilter.value = status },
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

            if (actionState is UiState.Error) {
                Text(
                    text = (actionState as UiState.Error).message ?: "Error al procesar acción",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (filteredResult == null || actionState is UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Color(0xFF388E3C),
                )
            } else if (filteredResult is ApiResult.Success) {
                val requests = (filteredResult as ApiResult.Success).data

                if (requests.isEmpty()) {
                    Text(
                        text = "No se encontraron negocios con el filtro seleccionado.",
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
                            SupervisionBusinessCard(
                                request = request,
                                onSuspend = {
                                    suspendReasonInput = ""
                                    activeSuspendRequest = request
                                },
                                onReactivate = {
                                    viewModel.reactivateBusiness(
                                        request = request,
                                        onSuccess = {},
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de Suspensión
    activeSuspendRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { activeSuspendRequest = null },
            title = {
                Text(text = "Suspender Negocio")
            },
            text = {
                Column {
                    Text(
                        text = "Ingresa el motivo obligatorio para suspender la operación de '${request.businessName}':",
                        fontSize = 14.sp,
                        color = Color(0xFF424242),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = suspendReasonInput,
                        onValueChange = { suspendReasonInput = it },
                        placeholder = {
                            Text(text = "Ej. Incumplimiento de normas de higiene o incidencias recurrentes.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.suspendBusiness(
                            request = request,
                            reason = suspendReasonInput,
                            onSuccess = {
                                activeSuspendRequest = null
                            },
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                ) {
                    Text(text = "SUSPENDER")
                }
            },
            dismissButton = {
                TextButton(onClick = { activeSuspendRequest = null }) {
                    Text(text = "CANCELAR")
                }
            },
        )
    }
}

@Composable
private fun SupervisionBusinessCard(
    request: BusinessRequest,
    onSuspend: () -> Unit,
    onReactivate: () -> Unit,
) {
    val isSuspended = request.status == BusinessStatus.SUSPENDED

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
                SupervisionStatusBadge(status = request.status)
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

            if (!request.suspensionReason.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Motivo de Suspensión: ${request.suspensionReason}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = ChaskiDimens.SpacingMd),
                color = Color(0xFFEEEEEE),
            )

            if (isSuspended) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF2E7D32))
                        .clickable(onClick = onReactivate)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "REACTIVAR NEGOCIO",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFD32F2F))
                        .clickable(onClick = onSuspend)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "SUSPENDER NEGOCIO",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun SupervisionStatusBadge(status: BusinessStatus) {
    val (bg, fg, label) = when (status) {
        BusinessStatus.APPROVED -> Triple(Color(0xE8E8F5E9), Color(0xFF2E7D32), "HABILITADO")
        BusinessStatus.SUSPENDED -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "SUSPENDIDO")
        else -> Triple(Color(0xFFEEEEEE), Color(0xFF616161), status.name)
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