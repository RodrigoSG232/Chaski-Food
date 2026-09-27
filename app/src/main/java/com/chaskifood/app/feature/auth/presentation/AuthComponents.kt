@file:Suppress("DEPRECATION")

package com.chaskifood.app.feature.auth.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaskifood.app.ui.components.ChaskiLogo
import com.chaskifood.app.ui.theme.ChaskiBackground
import com.chaskifood.app.ui.theme.ChaskiDimens
import com.chaskifood.app.ui.theme.ChaskiPrimary
import com.chaskifood.app.ui.theme.ChaskiSurface
import com.chaskifood.app.ui.theme.ChaskiSurfaceVariant
import com.chaskifood.app.ui.theme.ChaskiTextMuted
import com.chaskifood.app.ui.theme.ChaskiTextPrimary

private val AuthLabelColor = Color(0xFF424242)
private val AuthPlaceholderColor = Color(0xFF616161)
private val AuthBorderColor = Color(0xFFE0E0E0)
private val AuthTitleDark = Color(0xFF0D0D0D)
private val AuthIconColor = Color(0xFF9E9E9E)
private val GoogleBlue = Color(0xFF4285F4)
private val GoogleRed = Color(0xFFEA4335)
private val GoogleYellow = Color(0xFFFBBC05)
private val GoogleGreen = Color(0xFF34A853)
private val FacebookBlue = Color(0xFF395998)

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
                        color = com.chaskifood.app.ui.theme.ChaskiTextTertiary,
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
 * Campo de auth del diseño: etiqueta 16sp fw500 #424242 (tracking 0.5) + caja
 * rellena #EEEEEE radius 8 con icono opcional y placeholder #616161, 16sp.
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
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
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
 * Botón principal de auth: relleno rojo (o negro según [containerColor]),
 * radius 8, texto 14sp semibold tracking 0.84, sin chevrons.
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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
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
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ChaskiDimens.SpacingLg),
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
        border = BorderStroke(2.5.dp, AuthBorderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ChaskiSurface,
            contentColor = AuthLabelColor,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = ChaskiDimens.SpacingLg,
            vertical = 14.dp,
        ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.84.sp,
                color = AuthLabelColor,
            )
            Spacer(Modifier.width(ChaskiDimens.SpacingSm))
            logo()
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

/** Logo de Google de 4 colores (estilo aro). */
@Composable
fun GoogleLogo(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.size(19.dp),
    ) {
        val stroke = this.size.minDimension * 0.17f
        val gap = this.size.minDimension * 0.05f
        val radius = (this.size.minDimension - stroke - gap * 2) / 2f
        val c = center
        val topLeft = Offset(c.x - radius, c.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        fun arc(color: Color, start: Float, sweep: Float) {
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        arc(GoogleBlue, 120f, 60f)
        arc(GoogleRed, -10f, 70f)
        arc(GoogleYellow, 180f, 70f)
        arc(GoogleGreen, 70f, 50f)
    }
}

/** Insignia de Facebook: círculo #395998 con la "f" (logo del diseño). */
@Composable
fun FacebookLogo(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(19.dp)
            .clip(CircleShape)
            .background(FacebookBlue),
    ) {
        Text(
            text = "f",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
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