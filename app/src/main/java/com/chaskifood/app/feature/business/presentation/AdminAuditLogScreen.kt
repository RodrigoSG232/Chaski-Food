package com.chaskifood.app.feature.business.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.core.common.ApiResult
import com.chaskifood.app.feature.auth.presentation.AuthAppBar
import com.chaskifood.app.feature.business.domain.AuditLog
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminAuditLogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminAuditLogViewModel = hiltViewModel(),
) {
    val auditResult by viewModel.auditLogs.collectAsState()

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    tint = Color(0xFF1565C0),
                )
                Spacer(Modifier.padding(start = 8.dp))
                Text(
                    text = "Historial de Auditoría",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D0D0D),
                )
            }

            Spacer(Modifier.height(ChaskiDimens.SpacingXs))

            Text(
                text = "Registro cronológico inmutable de todas las acciones administrativas tomadas en Chaski Food.",
                fontSize = 14.sp,
                color = Color(0xFF757575),
            )

            Spacer(Modifier.height(ChaskiDimens.SpacingLg))

            if (auditResult == null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Color(0xFF1565C0),
                )
            } else if (auditResult is ApiResult.Success) {
                val logs = (auditResult as ApiResult.Success).data

                if (logs.isEmpty()) {
                    Text(
                        text = "Aún no hay registros de auditoría almacenados.",
                        fontSize = 14.sp,
                        color = Color(0xFF757575),
                        modifier = Modifier.padding(top = ChaskiDimens.SpacingLg),
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(logs, key = { it.id }) { log ->
                            AuditLogCard(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditLogCard(log: AuditLog) {
    val dateFormat = rememberDateFormat()
    val dateString = try {
        dateFormat.format(Date(log.timestamp))
    } catch (e: Exception) {
        ""
    }

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
                ActionBadge(action = log.action)
                Text(
                    text = dateString,
                    fontSize = 12.sp,
                    color = Color(0xFF9E9E9E),
                )
            }

            Spacer(Modifier.height(ChaskiDimens.SpacingSm))

            Text(
                text = "Negocio: ${log.targetBusinessName} (RUC: ${log.targetRuc})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF212121),
            )

            if (!log.details.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = log.details,
                    fontSize = 13.sp,
                    color = Color(0xFF616161),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = ChaskiDimens.SpacingSm),
                color = Color(0xFFEEEEEE),
            )

            Text(
                text = "Realizado por: ${log.adminEmail}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1565C0),
            )
        }
    }
}

@Composable
private fun ActionBadge(action: String) {
    val (bg, fg, label) = when (action) {
        "APROBAR_NEGOCIO" -> Triple(Color(0xE8E8F5E9), Color(0xFF2E7D32), "APROBACIÓN")
        "OBSERVAR_NEGOCIO" -> Triple(Color(0xFFFFE0B2), Color(0xFFE65100), "OBSERVACIÓN")
        "RECHAZAR_NEGOCIO" -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), "RECHAZO")
        "SUSPENDER_NEGOCIO" -> Triple(Color(0xFFFFEBEE), Color(0xFFD32F2F), "SUSPENSIÓN")
        "REACTIVAR_NEGOCIO" -> Triple(Color(0xE8E8F5E9), Color(0xFF2E7D32), "REACTIVACIÓN")
        else -> Triple(Color(0xFFEEEEEE), Color(0xFF616161), action)
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

private fun rememberDateFormat(): SimpleDateFormat {
    return SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
}