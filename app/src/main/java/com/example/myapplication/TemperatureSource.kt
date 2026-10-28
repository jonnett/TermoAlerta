package com.example.myapplication

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * De dónde obtiene el SENSOR la temperatura.
 * Hoy se usa [SimulatedTemperatureSource]. Cuando se sume el Arduino UNO R4,
 * basta con otra implementación de esta interfaz (o que el Arduino se conecte
 * directamente al monitor por WiFi usando el mismo protocolo).
 */
interface TemperatureSource {
    val id: String
    suspend fun read(): Float
}

/**
 * Temperatura simulada para pruebas y demostración.
 * - Automático: la temperatura deriva sola hacia objetivos al azar entre 18 y 38 °C,
 *   así que de vez en cuando cruza el límite y se ven las alertas.
 * - Manual: el valor lo fija el usuario con el deslizador.
 */
class SimulatedTemperatureSource : TemperatureSource {
    override val id = "simulado"

    val automatic = MutableStateFlow(true)
    val manualValue = MutableStateFlow(24f)

    private var value = 24f
    private var target = 24f
    private var ticks = 0

    override suspend fun read(): Float {
        if (!automatic.value) {
            value = manualValue.value
        } else {
            if (ticks++ % 12 == 0) target = Random.nextDouble(18.0, 38.0).toFloat()
            value += (target - value) * 0.18f + (Random.nextFloat() - 0.5f) * 0.4f
        }
        return (value * 10f).roundToInt() / 10f
    }
}
