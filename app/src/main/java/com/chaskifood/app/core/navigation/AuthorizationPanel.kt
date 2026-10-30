package com.chaskifood.app.core.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chaskifood.app.core.access.data.*
import com.chaskifood.app.ui.theme.ChaskiTheme

enum class AuthorizationPanel { VERIFYING, TEMPORARY_ERROR, OFFLINE, CONFLICT, RECONCILIATION, REVOKED, ACCOUNT_CHANGED, UNAVAILABLE }

@Composable
internal fun AuthorizationPanelContent(panel: AuthorizationPanel, onRetry: () -> Unit = {}, onSignOut: (() -> Unit)? = null) {
    val title = when (panel) {
        AuthorizationPanel.VERIFYING -> "Verificando permisos"
        AuthorizationPanel.TEMPORARY_ERROR -> "No pudimos comprobar tus permisos"
        AuthorizationPanel.OFFLINE -> "Sin conexión"
        AuthorizationPanel.CONFLICT -> "Conflicto de autorizaciones"
        AuthorizationPanel.RECONCILIATION -> "Reconciliación pendiente"
        AuthorizationPanel.REVOKED -> "Permisos no disponibles"
        AuthorizationPanel.ACCOUNT_CHANGED -> "Cambio de cuenta"
        AuthorizationPanel.UNAVAILABLE -> "Contexto no disponible"
    }
    val message = when (panel) {
        AuthorizationPanel.VERIFYING -> "Estamos comprobando el acceso de esta cuenta."
        AuthorizationPanel.TEMPORARY_ERROR -> "Ocurrió un error temporal. No se concede acceso sin comprobar los permisos."
        AuthorizationPanel.OFFLINE -> "No hay una conexión disponible. Reconecta y vuelve a comprobar."
        AuthorizationPanel.CONFLICT -> "Se requiere revisión autorizada. Volver a consultar no garantiza resolver el conflicto."
        AuthorizationPanel.RECONCILIATION -> "Tu acceso comercial requiere vinculación o revisión autorizada. Si te asignaron un local, verifica tu correo y vuelve a comprobar."
        AuthorizationPanel.REVOKED -> "No tienes autorización vigente para este destino. No se conservan acciones del acceso anterior."
        AuthorizationPanel.ACCOUNT_CHANGED -> "El contenido de la cuenta anterior ya no está disponible."
        AuthorizationPanel.UNAVAILABLE -> "No podemos comprobar el contexto de acceso. Las acciones comerciales permanecen deshabilitadas."
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        if (panel == AuthorizationPanel.VERIFYING) CircularProgressIndicator()
        else Icon(if (panel == AuthorizationPanel.OFFLINE) Icons.Default.WifiOff else Icons.Default.Lock, null, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (panel !in setOf(AuthorizationPanel.VERIFYING, AuthorizationPanel.ACCOUNT_CHANGED)) {
            OutlinedButton(onClick = onRetry) { Text("Volver a comprobar") }
        }
        if (onSignOut != null && panel.offersSignOut()) TextButton(onClick = onSignOut) { Text("Cerrar sesión") }
    }
}

/** Ningún panel restrictivo deja la cuenta atrapada: siempre puede cerrarse la sesión para cambiar de cuenta. */
internal fun AuthorizationPanel.offersSignOut() = this != AuthorizationPanel.VERIFYING && this != AuthorizationPanel.ACCOUNT_CHANGED

internal fun authorizationPanel(state: AccessContextState): AuthorizationPanel = when {
    state.phase == AccessContextPhase.LOADING -> AuthorizationPanel.VERIFYING
    state.phase == AccessContextPhase.CONFLICT -> AuthorizationPanel.CONFLICT
    state.problem == AccessCheckProblem.RECONCILIATION_REQUIRED -> AuthorizationPanel.RECONCILIATION
    state.phase == AccessContextPhase.SIGNED_OUT -> AuthorizationPanel.ACCOUNT_CHANGED
    state.phase == AccessContextPhase.RESOLVED -> AuthorizationPanel.REVOKED
    state.phase == AccessContextPhase.UNAVAILABLE -> AuthorizationPanel.UNAVAILABLE
    else -> AuthorizationPanel.TEMPORARY_ERROR
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.3f)
@Composable private fun AuthorizationConflictPreview() { ChaskiTheme { AuthorizationPanelContent(AuthorizationPanel.CONFLICT) } }
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable private fun AuthorizationLoadingPreview() { ChaskiTheme { AuthorizationPanelContent(AuthorizationPanel.VERIFYING) } }