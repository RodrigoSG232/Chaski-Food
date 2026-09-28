@file:Suppress("DEPRECATION")

package com.chaskifood.app.feature.auth.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.R
import com.chaskifood.app.ui.components.ChaskiLogo
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiSurfaceVariant
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextPrimary
import com.chaskifood.app.ui.theme.ChaskiTextTertiary

private val AuthLabelColor = Color(0xFF424242)
private val AuthPlaceholderColor = Color(0xFF9E9E9E)
private val AuthBorderColor = Color(0xFFEEEEEE)
private val AuthTitleDark = Color(0xFF0D0D0D)
private val AuthIconColor = Color(0xFF9E9E9E)

/** Fondo base de las pantallas de auth: blanco para Sign in, #FBFBFB para el resto. */
@Composable
fun AuthBackground(
    content: @Composable () -> Unit,
    signIn: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (signIn) ChaskiSurface else ChaskiBackground),
    ) {
        content()
    }
}

/** App bar superior: solo flecha de regreso (izquierda, 24px, #212121) sobre fondo blanco. */
@Composable
fun AuthAppBar(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(ChaskiSurface)
            .height(80.dp),
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Regresar",
                tint = AuthTitleDark,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** App bar del Sign in: solo el logo de la marca, centrado (32px). */
@Composable
fun AuthLogoBar(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(ChaskiSurface)
            .height(80.dp),
    ) {
        Icon(
            imageVector = ChaskiLogo,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )
    }
}

/**
 * Banner de título del diseño: título 28/36 #212121 + columna con
 * texto 16/24 #616161 y un enlace rojo opcional.
 */
@Composable
fun AuthTitleBanner(
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    linkText: String? = null,
    onLinkClick: () -> Unit = {},
    titleWeight: FontWeight = FontWeight.Bold,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .background(ChaskiSurface)
            .padding(ChaskiDimens.SpacingLg),
    ) {
        Text(
            text = title,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = titleWeight,
            letterSpacing = 0.28.sp,
            color = ChaskiTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (text != null || linkText != null) {
            Spacer(Modifier.height(ChaskiDimens.SpacingLg))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (text != null) {
                    Text(
                        text = text,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = ChaskiTextTertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (linkText != null) {
                        Spacer(Modifier.height(ChaskiDimens.SpacingMd))
                    }
                }
                if (linkText != null) {
                    TextButton(onClick = onLinkClick) {
                        Text(
                            text = linkText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                            color = ChaskiPrimary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Campo de auth del diseño: etiqueta 16sp fw500 #424242 + caja
 * rellena #EEEEEE radius 8 con icono opcional y placeholder sutil #9E9E9E, 15sp.
 */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = AuthLabelColor,
        )
        Spacer(Modifier.height(ChaskiDimens.SpacingMd))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            placeholder = placeholder?.let { text ->
                {
                    Text(
                        text = text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = AuthPlaceholderColor,
                    )
                }
            },
            leadingIcon = leadingIcon?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = AuthIconColor,
                    )
                }
            },
            trailingIcon = {
                trailingIcon?.invoke()
            },
            singleLine = true,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ChaskiSurfaceVariant,
                unfocusedContainerColor = ChaskiSurfaceVariant,
                disabledContainerColor = ChaskiSurfaceVariant,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                cursorColor = ChaskiPrimary,
                focusedTextColor = ChaskiTextPrimary,
                unfocusedTextColor = ChaskiTextPrimary,
            ),
        )
    }
}

/**
 * Botón principal de auth: relleno rojo, radius 8, texto 14sp semibold.
 */
@Composable
fun AuthSubmitButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = ChaskiPrimary,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(
            horizontal = 48.dp,
            vertical = ChaskiDimens.SpacingLg,
        ),
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.84.sp,
            color = Color.White,
        )
    }
}

/** Fila con los botones de "o conéctate con..." Google / Facebook. */
@Composable
fun SocialAuthRow(
    onGoogle: () -> Unit,
    onFacebook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChaskiDimens.SpacingLg),
    ) {
        SocialAuthButton(
            text = "Google",
            logo = { GoogleLogo() },
            onClick = onGoogle,
            modifier = Modifier.weight(1f),
        )
        SocialAuthButton(
            text = "Facebook",
            logo = { FacebookLogo() },
            onClick = onFacebook,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SocialAuthButton(
    text: String,
    logo: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, AuthBorderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ChaskiSurface,
            contentColor = AuthLabelColor,
        ),
        contentPadding = PaddingValues(
            horizontal = ChaskiDimens.SpacingLg,
            vertical = 12.dp,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            logo()
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = AuthLabelColor,
            )
        }
    }
}

/** Enlace discreto (p. ej. "¿Olvidaste tu contraseña?"): 14sp fw500 #0D0D0D. */
@Composable
fun AuthTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            color = AuthTitleDark,
        )
    }
}

/** Logo de Google oficial. */
@Composable
fun GoogleLogo(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(id = R.drawable.ic_google_logo),
        contentDescription = "Google",
        modifier = modifier.size(20.dp),
        tint = Color.Unspecified,
    )
}

/** Logo de Facebook oficial. */
@Composable
fun FacebookLogo(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(id = R.drawable.ic_facebook_logo),
        contentDescription = "Facebook",
        modifier = modifier.size(20.dp),
        tint = Color.Unspecified,
    )
}

/** Footer de términos (Registro / Verificar teléfono): 14/22 #757575 centrado. */
@Composable
fun AuthTermsFooter(modifier: Modifier = Modifier) {
    Text(
        text = "Al registrarte, has aceptado nuestros Términos y Política de privacidad",
        fontSize = 14.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        color = ChaskiTextMuted,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 25.dp),
    )
}