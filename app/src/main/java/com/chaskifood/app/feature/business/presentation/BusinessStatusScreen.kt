package com.chaskifood.app.feature.business.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.R
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.auth.presentation.AuthAppBar
import com.chaskifood.app.feature.auth.presentation.AuthSubmitButton
import com.chaskifood.app.feature.business.domain.BusinessStatus
import com.chaskifood.app.ui.theme.ChaskiAdminAccent
import com.chaskifood.app.ui.theme.ChaskiAdminCardBg
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
fun BusinessStatusScreen(
    onRegisterNew: () -> Unit,
    onEditAndResubmit: () -> Unit,
    onManageStores: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BusinessViewModel = hiltViewModel(),
) {
    val businessResult by viewModel.businessRequest.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        AuthAppBar(onBackClick = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ChaskiDimens.ScreenPadding),
        ) {
            Text(
                text = stringResource(R.string.business_status_title),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ChaskiTextPrimary,
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingXs))

            Text(
                text = stringResource(R.string.business_status_subtitle),
                fontSize = 14.sp,
                color = ChaskiTextMuted,
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingXl))

            if (businessResult == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = ChaskiStatusApprovedFg,
                )
            } else if (businessResult is ApiResult.Success) {
                val request = (businessResult as ApiResult.Success).data

                if (request == null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ChaskiAdminCardBg),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(ChaskiDimens.SpacingLg)) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = ChaskiAdminAccent,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.height(ChaskiDimens.SpacingSm))
                            Text(
                                text = stringResource(R.string.no_business_registered),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = ChaskiAdminAccent,
                            )
                            Spacer(Modifier.height(ChaskiDimens.SpacingXs))
                            Text(
                                text = stringResource(R.string.no_business_description),
                                fontSize = 14.sp,
                                color = ChaskiAdminAccent,
                            )
                            Spacer(Modifier.height(ChaskiDimens.SpacingLg))
                            AuthSubmitButton(
                                text = stringResource(R.string.submit_business_request),
                                onClick = onRegisterNew,
                            )
                        }
                    }
                } else {
                    // Muestra el estado del negocio
                    when (request.status) {
                        BusinessStatus.PENDING_REVIEW -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(ChaskiDimens.SpacingLg)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.HourglassTop,
                                            contentDescription = null,
                                            tint = Color(0xFFF57F17),
                                            modifier = Modifier.size(32.dp),
                                        )
                                        Text(
                                            text = "Estado: PENDING_REVIEW",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color(0xFFF57F17),
                                            modifier = Modifier.padding(start = ChaskiDimens.SpacingSm),
                                        )
                                    }
                                    Spacer(Modifier.height(ChaskiDimens.SpacingSm))
                                    Text(
                                        text = "Tu solicitud ha sido enviada con éxito y está siendo revisada por el Administrador Chaski.",
                                        fontSize = 14.sp,
                                        color = Color(0xFFE65100),
                                    )
                                }
                            }
                        }

                        BusinessStatus.OBSERVED -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(ChaskiDimens.SpacingLg)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFFE65100),
                                            modifier = Modifier.size(32.dp),
                                        )
                                        Text(
                                            text = "Estado: OBSERVED",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color(0xFFE65100),
                                            modifier = Modifier.padding(start = ChaskiDimens.SpacingSm),
                                        )
                                    }
                                    Spacer(Modifier.height(ChaskiDimens.SpacingMd))
                                    Text(
                                        text = "Observaciones pendientes de corrección:",
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFBF360C),
                                    )
                                    Spacer(Modifier.height(ChaskiDimens.SpacingXs))
                                    Text(
                                        text = request.observations ?: "Por favor revisa la información enviada.",
                                        fontSize = 14.sp,
                                        color = Color(0xFFBF360C),
                                    )
                                    Spacer(Modifier.height(ChaskiDimens.SpacingLg))
                                    AuthSubmitButton(
                                        text = "CORREGIR Y REENVIAR",
                                        onClick = onEditAndResubmit,
                                    )
                                }
                            }
                        }

                        BusinessStatus.APPROVED -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xE8E8F5E9)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(ChaskiDimens.SpacingLg)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(32.dp),
                                        )
                                        Text(
                                            text = "Estado: HABILITADO",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color(0xFF2E7D32),
                                            modifier = Modifier.padding(start = ChaskiDimens.SpacingSm),
                                        )
                                    }
                                    Spacer(Modifier.height(ChaskiDimens.SpacingSm))
                                    Text(
                                        text = "¡Tu negocio está habilitado para vender en Chaski Food!",
                                        fontSize = 14.sp,
                                        color = Color(0xFF1B5E20),
                                    )
                                    Spacer(Modifier.height(ChaskiDimens.SpacingLg))
                                    AuthSubmitButton(
                                        text = "GESTIONAR LOCALES Y RESPONSABLES",
                                        onClick = onManageStores,
                                    )
                                }
                            }
                        }

                        BusinessStatus.REJECTED -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(ChaskiDimens.SpacingLg)) {
                                    Text(
                                        text = "Estado: RECHAZADO",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = Color(0xFFC62828),
                                    )
                                    Spacer(Modifier.height(ChaskiDimens.SpacingSm))
                                    Text(
                                        text = request.observations ?: "La solicitud del negocio fue rechazada.",
                                        fontSize = 14.sp,
                                        color = Color(0xFFB71C1C),
                                    )
                                }
                            }
                        }

                        BusinessStatus.SUSPENDED -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(ChaskiDimens.SpacingLg)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Warning,
                                            contentDescription = null,
                                            tint = Color(0xFF4A148C),
                                            modifier = Modifier.size(32.dp),
                                        )
                                        Text(
                                            text = "Estado: NEGOCIO SUSPENDIDO",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color(0xFF4A148C),
                                            modifier = Modifier.padding(start = ChaskiDimens.SpacingSm),
                                        )
                                    }
                                    Spacer(Modifier.height(ChaskiDimens.SpacingMd))
                                    Text(
                                        text = "Tu negocio ha sido suspendido por el Administrador Chaski.",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF4A148C),
                                    )
                                    if (!request.suspensionReason.isNullOrBlank()) {
                                        Spacer(Modifier.height(ChaskiDimens.SpacingXs))
                                        Text(
                                            text = "Motivo: ${request.suspensionReason}",
                                            fontSize = 14.sp,
                                            color = Color(0xFF6A1B9A),
                                        )
                                    }
                                    Spacer(Modifier.height(ChaskiDimens.SpacingSm))
                                    Text(
                                        text = "⚠️ Tu negocio no puede recibir nuevos pedidos en este momento.",
                                        fontSize = 13.sp,
                                        color = Color(0xFFB71C1C),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(ChaskiDimens.SpacingLg))

                    // Muestra el resumen de datos enviados
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(ChaskiDimens.SpacingLg)) {
                            Text(
                                text = "Información del Negocio Enviada",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF212121),
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = ChaskiDimens.SpacingMd),
                                color = Color(0xFFEEEEEE),
                            )

                            DetailRow(label = "Razón Social:", value = request.businessName)
                            DetailRow(label = "RUC:", value = request.ruc)
                            DetailRow(label = "Dirección Legal:", value = request.legalAddress)
                            DetailRow(label = "Teléfono de Contacto:", value = request.phone)
                            DetailRow(label = "Correo Comercial:", value = request.email)
                            DetailRow(label = "Categoría:", value = request.category)
                        }
                    }
                }
            } else if (businessResult is ApiResult.Failure) {
                Text(
                    text = (businessResult as ApiResult.Failure).message ?: "Error al consultar estado.",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(ChaskiDimens.SpacingXxl))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = ChaskiDimens.SpacingMd)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF9E9E9E),
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF212121),
        )
    }
}