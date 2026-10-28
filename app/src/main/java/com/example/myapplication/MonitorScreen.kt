package com.example.myapplication

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun MonitorScreen(onBack: () -> Unit, onSettings: () -> Unit, onHistory: () -> Unit) {
    val context = LocalContext.current
    val state by ConnectionManager.state.collectAsState()
    val transport by AppSettings.transport.collectAsState()
    val limit by AppSettings.limit.collectAsState()
    val limitChosen by AppSettings.limitChosen.collectAsState()
    val latest by MonitorController.latest.collectAsState()
    val recent by MonitorController.recent.collectAsState()
    val inAlert by MonitorController.inAlert.collectAsState()
    val alertCount by MonitorController.alertCount.collectAsState()

    val pairingCode by ConnectionManager.pairingCode.collectAsState()
    val isMonitorSession = ConnectionManager.role == Role.MONITOR
    val connected = state is ConnState.Connected && isMonitorSession
    val waiting = state is ConnState.Waiting && isMonitorSession

    // Permiso de notificaciones (Android 13+) para poder mostrar las alertas
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AlertNotifier.canNotify(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Reloj para saber hace cuánto llegó la última lectura
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    if (connected || waiting) KeepScreenOn()

    Scaffold(topBar = { TermoTopBar("Monitor", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                connected -> {
                    SectionCard {
                        ConnectionStatus(state)
                        OutlinedButton(onClick = { ConnectionManager.stop() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Desconectar")
                        }
                    }

                    if (inAlert) AlertBanner(latest?.celsius, limit)

                    val ageSeconds = latest?.let { (now - it.timestamp) / 1000 }
                    val caption = when {
                        ageSeconds == null -> "Esperando la primera lectura…"
                        ageSeconds > 15 -> "Sin datos hace $ageSeconds s"
                        else -> "Actualizado hace $ageSeconds s"
                    }
                    SectionCard {
                        TemperatureGauge(latest?.celsius, limit, Modifier.fillMaxWidth(), caption = caption)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("Estado", if (inAlert) "ALERTA" else if (latest == null) "--" else "Normal",
                                if (inAlert) TempAlert else TempNormal)
                            Stat("Alertas", alertCount.toString())
                            Stat("Lecturas", recent.size.toString())
                        }
                    }

                    SectionCard(title = "Últimas lecturas") {
                        TemperatureChart(recent.map { it.celsius }, limit)
                    }

                    SectionCard(title = "Temperatura límite") {
                        LimitEditor()
                        InfoText("El cambio se aplica al instante y se informa al sensor.")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Ajustes") }
                        OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("Historial") }
                    }
                }

                waiting -> {
                    val code = pairingCode
                    val detail = (state as? ConnState.Waiting)?.detail ?: ""
                    if (code != null) {
                        PairingCodeCard(code, transport, detail, now) { ConnectionManager.stop() }
                    } else {
                        WaitingCard(state) { ConnectionManager.stop() }
                    }
                    if (transport == Transport.WIFI) {
                        val ips = remember { ConnectionManager.localIpAddresses() }
                        if (ips.isNotEmpty()) {
                            InfoText("Si el sensor no encuentra este teléfono, puede conectarse con la IP ${ips.joinToString(" / ")}.")
                        }
                    }
                }

                else -> {
                    val failed = state as? ConnState.Failed
                    if (failed != null && ConnectionManager.role != Role.SENSOR) ErrorCard(failed.message)

                    SectionCard(title = "1. Elige la temperatura límite") {
                        LimitEditor()
                    }

                    SectionCard(title = "2. Conexión con el sensor") {
                        TransportSelector(transport, enabled = true, onSelect = AppSettings::setTransport)
                        InfoText("Al empezar, este teléfono mostrará un código de un solo uso para vincular el sensor.")
                        if (!limitChosen) {
                            InfoText("Primero guarda la temperatura límite (paso 1).", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    val canStart = limitChosen
                    val start = {
                        MonitorController.resetSession()
                        ConnectionManager.localName = monitorDisplayName()
                        ConnectionManager.startMonitor(context, transport)
                    }
                    when (transport) {
                        Transport.WIFI -> {
                            val ips = remember { ConnectionManager.localIpAddresses() }
                            SectionCard {
                                InfoText(
                                    if (ips.isEmpty()) "No se detecta una red WiFi. Conéctate a una red o activa el punto de acceso."
                                    else "El sensor debe estar en la misma red WiFi que este teléfono."
                                )
                                Button(onClick = start, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
                                    Text("Esperar al sensor")
                                }
                            }
                        }
                        Transport.BLUETOOTH -> BluetoothGate {
                            SectionCard {
                                InfoText("El sensor se conectará a este teléfono. Asegúrate de que ya estén emparejados.")
                                Button(onClick = start, enabled = canStart, modifier = Modifier.fillMaxWidth()) {
                                    Text("Esperar al sensor")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertBanner(celsius: Float?, limit: Float) {
    val transition = rememberInfiniteTransition(label = "alerta")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulso",
    )
    SectionCard(containerColor = TempAlert, modifier = Modifier.alpha(pulse)) {
        Text("¡TEMPERATURA SOBRE EL LÍMITE!", color = Color.White, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
        Text(
            "Lectura ${celsius?.let { formatC(it) } ?: "--"} · límite ${formatC(limit)}",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun Stat(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

private fun monitorDisplayName(): String {
    val email = AuthRepository.currentEmail
    return if (email != null) "${Build.MODEL} (${email.substringBefore('@')})" else Build.MODEL
}
