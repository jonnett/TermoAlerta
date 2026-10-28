package com.example.myapplication

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Lectura recibida en el monitor. */
data class LiveReading(val celsius: Float, val timestamp: Long, val source: String)

/**
 * Lógica del teléfono MONITOR: recibe las lecturas del sensor, las guarda en SQLite,
 * las compara con el límite configurado y lanza la alerta cuando se supera.
 */
object MonitorController {
    /** Margen para no alternar alerta/normal si la temperatura oscila justo en el límite. */
    private const val HYSTERESIS = 0.5f
    private const val CHART_POINTS = 60

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context
    private lateinit var database: ReadingsDatabase

    private val _latest = MutableStateFlow<LiveReading?>(null)
    val latest: StateFlow<LiveReading?> = _latest.asStateFlow()

    private val _recent = MutableStateFlow<List<LiveReading>>(emptyList())
    /** Últimas lecturas para el gráfico. */
    val recent: StateFlow<List<LiveReading>> = _recent.asStateFlow()

    private val _inAlert = MutableStateFlow(false)
    val inAlert: StateFlow<Boolean> = _inAlert.asStateFlow()

    private val _alertCount = MutableStateFlow(0)
    /** Alertas lanzadas en esta sesión. */
    val alertCount: StateFlow<Int> = _alertCount.asStateFlow()

    private var sensorName = "el sensor"

    fun init(context: Context, db: ReadingsDatabase) {
        appContext = context.applicationContext
        database = db

        scope.launch {
            ConnectionManager.incoming.collect { message ->
                if (ConnectionManager.role != Role.MONITOR) return@collect
                when (message) {
                    is Message.Hello -> {
                        sensorName = message.name
                        // Al conectarse un sensor, le informamos el límite vigente
                        ConnectionManager.send(Message.Limit(AppSettings.limit.value))
                    }
                    is Message.Temperature -> onReading(message)
                    is Message.Limit -> Unit // el monitor es quien define el límite
                }
            }
        }

        // Si el usuario cambia el límite, se reevalúa y se avisa al sensor
        scope.launch {
            AppSettings.limit.collect { limit ->
                if (ConnectionManager.role == Role.MONITOR) {
                    ConnectionManager.send(Message.Limit(limit))
                    _latest.value?.let { evaluate(it.celsius, limit) }
                }
            }
        }
    }

    private fun onReading(message: Message.Temperature) {
        val reading = LiveReading(message.celsius, System.currentTimeMillis(), message.source)
        _latest.value = reading
        _recent.value = (_recent.value + reading).takeLast(CHART_POINTS)

        val limit = AppSettings.limit.value
        val alert = evaluate(reading.celsius, limit)
        database.insert(reading.celsius, limit, alert, reading.source, reading.timestamp)
    }

    /** Devuelve true si la lectura está en estado de alerta. */
    private fun evaluate(celsius: Float, limit: Float): Boolean {
        val wasAlert = _inAlert.value
        val alert = if (wasAlert) celsius > limit - HYSTERESIS else celsius > limit
        if (alert && !wasAlert) {
            _alertCount.value += 1
            AlertNotifier.showAlert(appContext, celsius, limit, sensorName)
        } else if (!alert && wasAlert) {
            AlertNotifier.showBackToNormal(appContext, celsius, limit)
        }
        _inAlert.value = alert
        return alert
    }

    /** Limpia los datos en pantalla al iniciar una nueva sesión de monitoreo. */
    fun resetSession() {
        _latest.value = null
        _recent.value = emptyList()
        _inAlert.value = false
        _alertCount.value = 0
    }
}
