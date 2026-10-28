@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ------------------------------------------------------------------ estructura

@Composable
fun TermoTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Volver")
                }
            }
        },
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
fun InfoText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
}

@Composable
fun ErrorCard(message: String) {
    SectionCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Mantiene la pantalla encendida mientras se monitorea o se mide. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

// ------------------------------------------------------------------ conexión

@Composable
fun TransportSelector(selected: Transport, enabled: Boolean, onSelect: (Transport) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Transport.entries.forEach { t ->
            FilterChip(
                selected = t == selected,
                onClick = { onSelect(t) },
                enabled = enabled,
                label = { Text(t.label) },
            )
        }
    }
}

/** Campo donde el sensor escribe el código de un solo uso que muestra el monitor. */
@Composable
fun CodeField(code: String, enabled: Boolean, onCodeChange: (String) -> Unit) {
    OutlinedTextField(
        value = code,
        onValueChange = { new -> onCodeChange(new.filter { it.isDigit() }.take(6)) },
        label = { Text("Código del monitor (6 dígitos)") },
        supportingText = { Text("Aparece en la pantalla del monitor. Sirve una sola vez.") },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Tarjeta del monitor con el código de vinculación en grande y su tiempo restante. */
@Composable
fun PairingCodeCard(code: PairingCode, transport: Transport, detail: String, now: Long, onCancel: () -> Unit) {
    val secondsLeft = ((code.expiresAt - now) / 1000).coerceAtLeast(0)
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            "Código de vinculación",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            code.display,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            String.format(Locale.getDefault(), "Vence en %d:%02d · sirve una sola vez", secondsLeft / 60, secondsLeft % 60),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            when (transport) {
                Transport.WIFI -> "En el otro teléfono: Sensor → WiFi → escribe este código. Debe estar en la misma red WiFi."
                Transport.BLUETOOTH -> "En el otro teléfono: Sensor → Bluetooth → elige este teléfono y escribe este código."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}

/** Muestra el estado de la conexión con un punto de color. */
@Composable
fun ConnectionStatus(state: ConnState) {
    val (color, text) = when (state) {
        ConnState.Idle -> Color.Gray to "Sin conexión"
        is ConnState.Waiting -> TempWarning to state.detail
        is ConnState.Connecting -> TempWarning to state.detail
        is ConnState.Connected -> TempNormal to "Conectado con ${state.peerName} por ${state.transport.label}"
        is ConnState.Failed -> TempAlert to state.message
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun WaitingCard(state: ConnState, onCancel: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Text(
                when (state) {
                    is ConnState.Waiting -> state.detail
                    is ConnState.Connecting -> state.detail
                    else -> "…"
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
    }
}

/**
 * Comprueba que haya permiso de Bluetooth y que esté encendido antes de mostrar [content].
 */
@SuppressLint("MissingPermission")
@Composable
fun BluetoothGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val adapter = remember { ConnectionManager.bluetoothAdapter(context) }
    val hasPermission = remember(refresh) { ConnectionManager.hasBluetoothPermission(context) }
    val enabled = remember(refresh) { adapter?.isEnabled == true }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val enableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    when {
        adapter == null -> ErrorCard("Este teléfono no tiene Bluetooth. Usa la conexión WiFi.")
        !hasPermission -> SectionCard(title = "Permiso de Bluetooth") {
            InfoText("La app necesita permiso para conectarse a teléfonos cercanos.")
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }) { Text("Dar permiso") }
        }
        !enabled -> SectionCard(title = "Bluetooth apagado") {
            InfoText("Enciende el Bluetooth para continuar.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    runCatching { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                }) { Text("Encender Bluetooth") }
                androidx.compose.material3.TextButton(onClick = { refresh++ }) { Text("Ya lo encendí") }
            }
        }
        else -> content()
    }
}

// ------------------------------------------------------------------ temperatura

/** Indicador circular con la temperatura actual y la marca del límite. */
@Composable
fun TemperatureGauge(
    celsius: Float?,
    limit: Float?,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val color = temperatureColor(celsius, limit)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val limitMarkColor = MaterialTheme.colorScheme.onSurface
    val scaleMin = min(0f, min(celsius ?: 0f, limit ?: 0f) - 5f)
    val scaleMax = max(50f, max(celsius ?: 0f, limit ?: 0f) + 10f)

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().padding(12.dp)) {
            val stroke = size.minDimension * 0.08f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val start = 150f
            val sweep = 240f

            drawArc(track, start, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (celsius != null) {
                val fraction = ((celsius - scaleMin) / (scaleMax - scaleMin)).coerceIn(0f, 1f)
                drawArc(color, start, sweep * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            if (limit != null) {
                val f = ((limit - scaleMin) / (scaleMax - scaleMin)).coerceIn(0f, 1f)
                val angle = Math.toRadians((start + sweep * f).toDouble())
                val r = diameter / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val inner = r - stroke * 1.1f
                val outer = r + stroke * 1.1f
                drawLine(
                    limitMarkColor,
                    Offset(center.x + inner * cos(angle).toFloat(), center.y + inner * sin(angle).toFloat()),
                    Offset(center.x + outer * cos(angle).toFloat(), center.y + outer * sin(angle).toFloat()),
                    strokeWidth = stroke * 0.35f,
                    cap = StrokeCap.Round,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = celsius?.let { String.format(Locale.getDefault(), "%.1f°", it) } ?: "--",
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = if (celsius == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
            )
            Text(
                text = limit?.let { "Límite ${formatC(it)}" } ?: "Límite sin definir",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Gráfico de línea de las últimas lecturas con el límite como línea punteada. */
@Composable
fun TemperatureChart(values: List<Float>, limit: Float, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val limitColor = TempAlert
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val lo = (minOf(values.minOrNull() ?: limit, limit) - 2f)
    val hi = (maxOf(values.maxOrNull() ?: limit, limit) + 2f)

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatC(hi), style = MaterialTheme.typography.labelSmall, color = labelColor)
            Text("Límite ${formatC(limit)}", style = MaterialTheme.typography.labelSmall, color = limitColor)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(vertical = 6.dp)
        ) {
            fun y(v: Float) = size.height - (v - lo) / (hi - lo) * size.height

            for (i in 0..3) {
                val gy = size.height * i / 3f
                drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }
            drawLine(
                limitColor, Offset(0f, y(limit)), Offset(size.width, y(limit)),
                strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )
            if (values.size >= 2) {
                val step = size.width / (values.size - 1)
                val path = Path()
                values.forEachIndexed { i, v ->
                    if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * step, y(v))
                }
                drawPath(path, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                val last = values.last()
                drawCircle(if (last > limit) limitColor else lineColor, 9f, Offset(size.width, y(last)))
            }
        }
        Text(formatC(lo), style = MaterialTheme.typography.labelSmall, color = labelColor)
    }
}
