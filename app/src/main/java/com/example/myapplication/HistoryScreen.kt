@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = remember { (context.applicationContext as TermoApp).database }
    val scope = rememberCoroutineScope()

    var onlyAlerts by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var readings by remember { mutableStateOf<List<StoredReading>>(emptyList()) }
    var stats by remember { mutableStateOf<ReadingStats?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(onlyAlerts, reload) {
        val (list, s) = withContext(Dispatchers.IO) { database.recent(300, onlyAlerts) to database.stats() }
        readings = list
        stats = s
    }

    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

    Scaffold(topBar = {
        androidx.compose.material3.TopAppBar(
            title = { Text("Historial", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    androidx.compose.material3.Icon(
                        androidx.compose.ui.res.painterResource(R.drawable.ic_back),
                        contentDescription = "Volver",
                    )
                }
            },
            actions = {
                TextButton(onClick = { confirmClear = true }, enabled = (stats?.count ?: 0) > 0) { Text("Borrar") }
            },
        )
    }) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val s = stats
                SectionCard(title = "Resumen (guardado en SQLite)") {
                    if (s == null || s.count == 0) {
                        InfoText("Aún no hay lecturas. Se guardan cuando este teléfono funciona como monitor.")
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("Lecturas", s.count.toString())
                            Stat("Mín", s.min?.let { formatC(it) } ?: "--")
                            Stat("Máx", s.max?.let { formatC(it) } ?: "--")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Stat("Promedio", s.average?.let { formatC(it) } ?: "--")
                            Stat("Alertas", s.alerts.toString(), if (s.alerts > 0) TempAlert else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = !onlyAlerts, onClick = { onlyAlerts = false }, label = { Text("Todas") })
                    FilterChip(selected = onlyAlerts, onClick = { onlyAlerts = true }, label = { Text("Solo alertas") })
                }
            }
            items(readings, key = { it.id }) { r ->
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(dateFormat.format(Date(r.timestamp)), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Límite ${formatC(r.limit)} · ${r.source}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (r.isAlert) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TempAlert)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("ALERTA", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            formatC(r.celsius),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (r.isAlert) TempAlert else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar historial?") },
            text = { Text("Se eliminarán todas las lecturas guardadas en este teléfono.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) { database.clear() }
                        reload++
                    }
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}
