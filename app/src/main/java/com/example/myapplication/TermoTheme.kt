package com.example.myapplication

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta de TermoAlerta: azul petróleo (marca), verde/ámbar/rojo para el estado térmico.
private val Petrol = Color(0xFF0B5E7A)
private val PetrolLight = Color(0xFF8BCFE6)
private val Slate = Color(0xFF4A6572)

val TempNormal = Color(0xFF2E9E6A)
val TempWarning = Color(0xFFE59A1E)
val TempAlert = Color(0xFFD32F2F)

private val LightColors = lightColorScheme(
    primary = Petrol,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEAF5),
    onPrimaryContainer = Color(0xFF00313F),
    secondary = Slate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4EA),
    onSecondaryContainer = Color(0xFF0E1F27),
    tertiary = Color(0xFFB85C1E),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F7F9),
    onBackground = Color(0xFF151C20),
    surface = Color(0xFFF4F7F9),
    onSurface = Color(0xFF151C20),
    surfaceVariant = Color(0xFFDDE5EA),
    onSurfaceVariant = Color(0xFF40494E),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDF2F5),
    surfaceContainerHigh = Color(0xFFE6EDF1),
    outline = Color(0xFF71797E),
    outlineVariant = Color(0xFFC1C9CE),
)

private val DarkColors = darkColorScheme(
    primary = PetrolLight,
    onPrimary = Color(0xFF003545),
    primaryContainer = Color(0xFF004D63),
    onPrimaryContainer = Color(0xFFCDEAF5),
    secondary = Color(0xFFB4C9D3),
    onSecondary = Color(0xFF1F333C),
    secondaryContainer = Color(0xFF354A53),
    onSecondaryContainer = Color(0xFFD6E4EA),
    tertiary = Color(0xFFFFB68B),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDEE3E6),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDEE3E6),
    surfaceVariant = Color(0xFF40494E),
    onSurfaceVariant = Color(0xFFC1C9CE),
    surfaceContainerLow = Color(0xFF171D20),
    surfaceContainer = Color(0xFF1B2125),
    surfaceContainerHigh = Color(0xFF252C30),
    outline = Color(0xFF8B9398),
    outlineVariant = Color(0xFF40494E),
)

@Composable
fun TermoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

/** Color según qué tan cerca está la temperatura del límite. */
fun temperatureColor(celsius: Float?, limit: Float?): Color = when {
    celsius == null || limit == null -> Color.Gray
    celsius > limit -> TempAlert
    celsius > limit - 3f -> TempWarning
    else -> TempNormal
}
