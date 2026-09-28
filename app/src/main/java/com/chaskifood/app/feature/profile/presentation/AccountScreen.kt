package com.chaskifood.app.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens

@Composable
fun AccountScreen(
    onOpenProfileInfo: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenPaymentMethods: () -> Unit,
    onOpenLocations: () -> Unit,
    onOpenSocialAccounts: () -> Unit,
    onOpenReferral: () -> Unit,
    onOpenBusiness: () -> Unit = {},
    onOpenAdminReview: () -> Unit = {},
    onOpenAdminSupervision: () -> Unit = {},
    onOpenAdminAuditLog: () -> Unit = {},
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val currentUser by viewModel.currentUser.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ChaskiBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ChaskiDimens.ScreenPadding),
    ) {
        Spacer(Modifier.height(ChaskiDimens.SpacingLg))

        Text(
            text = "Cuenta",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0D0D0D),
        )

        Spacer(Modifier.height(ChaskiDimens.SpacingXs))

        Text(
            text = "Actualiza los datos de tu cuenta como notificaciones, pagos, perfil, etc.",
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF757575),
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(ChaskiDimens.SpacingXl))

        // SECCIÓN PERFIL
        SectionHeader(title = "PERFIL")

        AccountMenuItem(
            icon = Icons.Filled.Person,
            title = "Información del perfil",
            subtitle = currentUser?.displayName ?: "Cambia la información de tu cuenta",
            onClick = onOpenProfileInfo,
        )

        AccountMenuItem(
            icon = Icons.Filled.Store,
            title = "Registrar o Administrar Negocio",
            subtitle = "Solicita la habilitación comercial de tu negocio",
            onClick = onOpenBusiness,
        )

        if (currentUser?.isAdmin == true) {
            AccountMenuItem(
                icon = Icons.Filled.AdminPanelSettings,
                title = "Panel Admin Chaski: Evaluar Solicitudes",
                subtitle = "Revisa, aprueba, observa o rechaza solicitudes de ingreso",
                titleColor = Color(0xFF1565C0),
                onClick = onOpenAdminReview,
            )

            AccountMenuItem(
                icon = Icons.Filled.Storefront,
                title = "Panel Admin Chaski: Supervisar Negocios",
                subtitle = "Monitorea, suspende o reactiva la operación de negocios",
                titleColor = Color(0xFF1565C0),
                onClick = onOpenAdminSupervision,
            )

            AccountMenuItem(
                icon = Icons.Filled.History,
                title = "Panel Admin Chaski: Historial de Auditoría",
                subtitle = "Registro cronológico inmutable de todas las acciones",
                titleColor = Color(0xFF1565C0),
                onClick = onOpenAdminAuditLog,
            )
        }

        AccountMenuItem(
            icon = Icons.Filled.Lock,
            title = "Seguridad",
            subtitle = "Cambia los detalles de tu contraseña",
            onClick = onOpenSecurity,
        )

        AccountMenuItem(
            icon = Icons.Filled.CreditCard,
            title = "Métodos de pago",
            subtitle = "Agrega tus tarjetas de crédito y débito",
            onClick = onOpenPaymentMethods,
        )

        AccountMenuItem(
            icon = Icons.Filled.Place,
            title = "Ubicación",
            subtitle = "Gestiona tus direcciones de entrega",
            onClick = onOpenLocations,
        )

        AccountMenuItem(
            icon = Icons.Filled.PersonAdd,
            title = "Agregar cuentas sociales",
            subtitle = "Vincula Google, Facebook y más",
            onClick = onOpenSocialAccounts,
        )

        AccountMenuItem(
            icon = Icons.Filled.People,
            title = "Referir a tus amigos",
            subtitle = "Consigue descuentos compartiendo la app",
            onClick = onOpenReferral,
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = ChaskiDimens.SpacingMd),
            color = Color(0xFFEEEEEE),
        )

        // SECCIÓN NOTIFICACIONES
        SectionHeader(title = "NOTIFICACIONES")

        AccountMenuItem(
            icon = Icons.AutoMirrored.Filled.Message,
            title = "Notificaciones SMS",
            subtitle = "Mensajes de confirmación de pedido",
            onClick = {},
        )

        AccountMenuItem(
            icon = Icons.Filled.Notifications,
            title = "Notificaciones push",
            subtitle = "Alertas de estado de tus envíos",
            onClick = {},
        )

        AccountMenuItem(
            icon = Icons.Filled.Campaign,
            title = "Notificaciones promocionales",
            subtitle = "Ofertas especiales y cupones de descuento",
            onClick = {},
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = ChaskiDimens.SpacingMd),
            color = Color(0xFFEEEEEE),
        )

        // SECCIÓN MÁS
        SectionHeader(title = "MÁS")

        AccountMenuItem(
            icon = Icons.Filled.Star,
            title = "Califícanos",
            subtitle = "Danos tu opinión en Play Store",
            onClick = {},
        )

        AccountMenuItem(
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            title = "Preguntas frecuentes (FAQ)",
            subtitle = "Resuelve tus dudas frecuentes",
            onClick = {},
        )

        AccountMenuItem(
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            title = "Cerrar sesión",
            subtitle = "Cierra la sesión actual de tu cuenta",
            titleColor = Color(0xFFD32F2F),
            onClick = {
                viewModel.signOut(onSignedOut = onSignOut)
            },
        )

        Spacer(Modifier.height(ChaskiDimens.SpacingXxl))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = Color(0xFF9E9E9E),
        modifier = Modifier.padding(bottom = ChaskiDimens.SpacingSm, top = ChaskiDimens.SpacingSm),
    )
}

@Composable
private fun AccountMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = Color(0xFF212121),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = ChaskiDimens.SpacingMd),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (titleColor != Color(0xFF212121)) titleColor else Color(0xFF757575),
            modifier = Modifier.size(24.dp),
        )

        Spacer(Modifier.width(ChaskiDimens.SpacingLg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF9E9E9E),
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFBDBDBD),
            modifier = Modifier.size(20.dp),
        )
    }
}