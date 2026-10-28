package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    userLabel: String,
    onMonitor: () -> Unit,
    onSensor: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onLogout: () -> Unit,
) {
    val limit by AppSettings.limit.collectAsState()
    val limitChosen by AppSettings.limitChosen.collectAsState()
    val transport by AppSettings.transport.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TermoAlerta", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(userLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onLogout) { Text("Salir") }
        }

        SectionCard(title = "Tipo de conexión") {
            TransportSelector(selected = transport, enabled = true, onSelect = AppSettings::setTransport)
            InfoText(
                when (transport) {
                    Transport.WIFI -> "Ambos teléfonos deben estar en la misma red WiFi (o uno compartiendo internet al otro)."
                    Transport.BLUETOOTH -> "Primero empareja los dos teléfonos desde los ajustes de Bluetooth de Android."
                }
            )
        }

        Text("¿Qué hará este teléfono?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        RoleCard(
            title = "Monitor",
            description = if (limitChosen) {
                "Recibe la temperatura y activa la alerta si supera tu límite de ${formatC(limit)}."
            } else {
                "Recibe la temperatura y activa la alerta si supera el límite que tú elijas."
            },
            accent = MaterialTheme.colorScheme.primary,
            onClick = onMonitor,
        )
        RoleCard(
            title = "Sensor",
            description = "Mide la temperatura (simulada por ahora) y la envía al monitor cada pocos segundos.",
            accent = TempWarning,
            onClick = onSensor,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Límite y ajustes") }
            OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("Historial") }
        }
    }
}

@Composable
private fun RoleCard(title: String, description: String, accent: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .width(6.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = accent)
        }
    }
}
