package com.example.myapplication

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SensorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by ConnectionManager.state.collectAsState()
    val transport by AppSettings.transport.collectAsState()
    val interval by AppSettings.intervalSeconds.collectAsState()
    val current by SensorController.current.collectAsState()
    val monitorLimit by SensorController.monitorLimit.collectAsState()
    val sentCount by SensorController.sentCount.collectAsState()
    val automatic by SensorController.simulated.automatic.collectAsState()
    val manualValue by SensorController.simulated.manualValue.collectAsState()

    var code by remember { mutableStateOf("") }
    var showManualIp by rememberSaveable { mutableStateOf(false) }
    var ip by rememberSaveable { mutableStateOf(AppSettings.lastMonitorIp) }
    var selectedAddress by rememberSaveable { mutableStateOf<String?>(null) }

    val isSensorSession = ConnectionManager.role == Role.SENSOR
    val connected = state is ConnState.Connected && isSensorSession
    val connecting = state is ConnState.Connecting && isSensorSession

    // El sensor mide mientras esta pantalla está abierta
    DisposableEffect(Unit) {
        SensorController.start()
        onDispose { SensorController.stop() }
    }
    if (connected) KeepScreenOn()

    Scaffold(topBar = { TermoTopBar("Sensor", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ------------------------------------------------ conexión
            when {
                connected -> SectionCard {
                    ConnectionStatus(state)
                    InfoText("Enviando una lectura cada $interval s · $sentCount enviadas")
                    OutlinedButton(onClick = { ConnectionManager.stop() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Desconectar")
                    }
                }

                connecting -> WaitingCard(state) { ConnectionManager.stop() }

                else -> {
                    val failed = state as? ConnState.Failed
                    if (failed != null && ConnectionManager.role != Role.MONITOR) ErrorCard(failed.message)

                    SectionCard(title = "Conectar con el monitor") {
                        TransportSelector(transport, enabled = true, onSelect = AppSettings::setTransport)
                        CodeField(code, enabled = true) { code = it }
                    }

                    when (transport) {
                        Transport.WIFI -> SectionCard {
                            InfoText("Este teléfono buscará solo al monitor en tu red WiFi. Ambos deben estar conectados a la misma red.")
                            Button(
                                onClick = {
                                    ConnectionManager.localName = Build.MODEL
                                    ConnectionManager.connectSensor(context, Transport.WIFI, null, code)
                                },
                                enabled = isCodeValid(code),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Buscar monitor y conectar") }

                            // Alternativa por si la red bloquea la búsqueda automática
                            TextButton(onClick = { showManualIp = !showManualIp }) {
                                Text(if (showManualIp) "Ocultar conexión por IP" else "¿No lo encuentra? Conectar con la IP")
                            }
                            if (showManualIp) {
                                OutlinedTextField(
                                    value = ip,
                                    onValueChange = { ip = it.filter { c -> c.isDigit() || c == '.' || c == ':' }.take(21) },
                                    label = { Text("IP del monitor") },
                                    placeholder = { Text("Ej: 192.168.1.20") },
                                    supportingText = { Text("Aparece bajo el código en el monitor") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedButton(
                                    onClick = {
                                        AppSettings.lastMonitorIp = ip
                                        ConnectionManager.localName = Build.MODEL
                                        ConnectionManager.connectSensor(context, Transport.WIFI, ip, code)
                                    },
                                    enabled = isCodeValid(code) && ip.count { it == '.' } == 3,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Conectar a esta IP") }
                            }
                        }

                        Transport.BLUETOOTH -> BluetoothGate {
                            var refresh by remember { mutableIntStateOf(0) }
                            val devices = remember(refresh) { ConnectionManager.pairedDevices(context) }
                            SectionCard(title = "Elige el teléfono monitor") {
                                if (devices.isEmpty()) {
                                    InfoText("No hay teléfonos emparejados. Empareja el monitor desde Ajustes > Bluetooth y vuelve aquí.")
                                }
                                devices.forEach { device ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = device.address == selectedAddress,
                                                onClick = { selectedAddress = device.address },
                                                role = SemanticsRole.RadioButton,
                                            )
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(selected = device.address == selectedAddress, onClick = null)
                                        Column(Modifier.padding(start = 12.dp)) {
                                            Text(device.name, style = MaterialTheme.typography.bodyLarge)
                                            Text(device.address, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                TextButton(onClick = { refresh++ }) { Text("Actualizar lista") }
                                Button(
                                    onClick = {
                                        ConnectionManager.localName = Build.MODEL
                                        ConnectionManager.connectSensor(context, Transport.BLUETOOTH, selectedAddress ?: "", code)
                                    },
                                    enabled = isCodeValid(code) && selectedAddress != null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Conectar") }
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ medición
            val effectiveLimit = monitorLimit
            SectionCard(title = "Temperatura medida") {
                TemperatureGauge(
                    current,
                    effectiveLimit,
                    Modifier.fillMaxWidth(),
                    caption = if (effectiveLimit == null) "El límite llega al conectarse al monitor" else "Límite fijado por el monitor",
                )
                if (effectiveLimit != null && current != null) {
                    val over = current!! > effectiveLimit
                    Text(
                        if (over) "Sobre el límite: el monitor está recibiendo la alerta" else "Dentro del límite",
                        color = if (over) TempAlert else TempNormal,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            SectionCard(title = "Simulación") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Modo automático", style = MaterialTheme.typography.bodyLarge)
                        InfoText(if (automatic) "La temperatura varía sola entre 18 y 38 °C" else "Tú fijas la temperatura")
                    }
                    Switch(checked = automatic, onCheckedChange = { SensorController.simulated.automatic.value = it })
                }
                if (!automatic) {
                    Text(formatC(manualValue), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Slider(
                        value = manualValue,
                        onValueChange = { SensorController.simulated.manualValue.value = (Math.round(it * 2f) / 2f) },
                        valueRange = 0f..60f,
                    )
                }
                InfoText("Más adelante esta lectura vendrá del Arduino UNO R4 en vez de la simulación.")
            }
        }
    }
}
