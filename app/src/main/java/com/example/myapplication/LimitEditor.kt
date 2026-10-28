package com.example.myapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * Donde el usuario elige la temperatura límite.
 * Puede escribir el valor exacto (ej. 27,5) y guardarlo, o ajustarlo con los botones −/+.
 * Si una lectura supera este valor, el monitor activa la alerta.
 */
@Composable
fun LimitEditor(modifier: Modifier = Modifier) {
    val limit by AppSettings.limit.collectAsState()
    val chosen by AppSettings.limitChosen.collectAsState()
    val focus = LocalFocusManager.current

    var text by remember { mutableStateOf(if (chosen) formatNumber(limit) else "") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun save() {
        val value = text.trim().replace(',', '.').toFloatOrNull()
        when {
            value == null -> {
                isError = true
                message = "Escribe un número, por ejemplo 28 o 27,5"
            }
            value < AppSettings.MIN_LIMIT || value > AppSettings.MAX_LIMIT -> {
                isError = true
                message = "Debe estar entre ${formatC(AppSettings.MIN_LIMIT)} y ${formatC(AppSettings.MAX_LIMIT)}"
            }
            else -> {
                AppSettings.setLimit(value)
                text = formatNumber(AppSettings.limit.value)
                isError = false
                message = "Límite guardado. La alerta se activará sobre ${formatC(AppSettings.limit.value)}."
                focus.clearFocus()
            }
        }
    }

    fun nudge(delta: Float) {
        val base = text.trim().replace(',', '.').toFloatOrNull() ?: limit
        AppSettings.setLimit(base + delta)
        text = formatNumber(AppSettings.limit.value)
        isError = false
        message = "Límite guardado: ${formatC(AppSettings.limit.value)}"
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (chosen) formatC(limit) else "Sin definir",
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        InfoText(
            if (chosen) "Si la temperatura supera este valor, se activa la alerta."
            else "Elige la temperatura máxima permitida. Al superarla se activará la alerta."
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    text = new.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }.take(6)
                    isError = false
                    message = null
                },
                label = { Text("Temperatura límite") },
                suffix = { Text("°C") },
                singleLine = true,
                isError = isError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { save() }) { Text("Guardar") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { nudge(-1f) }, modifier = Modifier.weight(1f)) { Text("−1") }
            FilledTonalButton(onClick = { nudge(-0.5f) }, modifier = Modifier.weight(1f)) { Text("−0,5") }
            FilledTonalButton(onClick = { nudge(0.5f) }, modifier = Modifier.weight(1f)) { Text("+0,5") }
            FilledTonalButton(onClick = { nudge(1f) }, modifier = Modifier.weight(1f)) { Text("+1") }
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else TempNormal,
            )
        }
    }
}

private fun formatNumber(value: Float): String = String.format(Locale.getDefault(), "%.1f", value)
