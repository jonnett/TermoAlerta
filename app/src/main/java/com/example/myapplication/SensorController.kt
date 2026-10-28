package com.example.myapplication

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lógica del teléfono SENSOR: lee la temperatura (simulada por ahora) y la envía
 * al monitor cada cierto intervalo. También recibe el límite que fija el monitor.
 */
object SensorController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val simulated = SimulatedTemperatureSource()
    private val source: TemperatureSource get() = simulated

    private val _current = MutableStateFlow<Float?>(null)
    val current: StateFlow<Float?> = _current.asStateFlow()

    private val _monitorLimit = MutableStateFlow<Float?>(null)
    /** Límite recibido desde el monitor (null hasta conectarse). */
    val monitorLimit: StateFlow<Float?> = _monitorLimit.asStateFlow()

    private val _sentCount = MutableStateFlow(0)
    val sentCount: StateFlow<Int> = _sentCount.asStateFlow()

    private var loop: Job? = null

    fun init() {
        scope.launch {
            ConnectionManager.incoming.collect { message ->
                if (ConnectionManager.role == Role.SENSOR && message is Message.Limit) {
                    _monitorLimit.value = message.maxCelsius
                }
            }
        }
    }

    /** Empieza a medir (se llama al abrir la pantalla del sensor). */
    fun start() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                val value = source.read()
                _current.value = value
                if (ConnectionManager.role == Role.SENSOR && ConnectionManager.isConnected) {
                    ConnectionManager.send(Message.Temperature(value, System.currentTimeMillis(), source.id))
                    _sentCount.value += 1
                }
                delay(AppSettings.intervalSeconds.value * 1000L)
            }
        }
    }

    fun stop() {
        loop?.cancel()
        loop = null
        _monitorLimit.value = null
        _sentCount.value = 0
    }
}
