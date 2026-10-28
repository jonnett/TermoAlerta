package com.example.myapplication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val limit by AppSettings.limit.collectAsState()
    val sound by AppSettings.soundEnabled.collectAsState()
    val interval by AppSettings.intervalSeconds.collectAsState()

    Scaffold(topBar = { TermoTopBar("Límite y ajustes", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "Temperatura máxima permitida") {
                LimitEditor()
                InfoText("Rango permitido: ${formatC(AppSettings.MIN_LIMIT)} a ${formatC(AppSettings.MAX_LIMIT)}. Si hay un sensor conectado, el nuevo límite se le envía al instante.")
            }

            SectionCard(title = "Alertas") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sonido y vibración", style = MaterialTheme.typography.bodyLarge)
                        InfoText(if (sound) "La alerta suena como alarma" else "La alerta llega en silencio")
                    }
                    Switch(checked = sound, onCheckedChange = AppSettings::setSoundEnabled)
                }
            }

            SectionCard(title = "Sensor") {
                Text("Enviar una lectura cada $interval s", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = interval.toFloat(),
                    onValueChange = { AppSettings.setIntervalSeconds(Math.round(it)) },
                    valueRange = 1f..10f,
                    steps = 8,
                )
            }
        }
    }
}
