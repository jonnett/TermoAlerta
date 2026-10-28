package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ajustes de la app guardados en el teléfono (SharedPreferences privadas). */
object AppSettings {
    const val MIN_LIMIT = -20f
    const val MAX_LIMIT = 100f

    private lateinit var prefs: SharedPreferences

    private val _limit = MutableStateFlow(30f)
    /** Temperatura máxima permitida (°C). Si una lectura la supera, se envía una alerta. */
    val limit: StateFlow<Float> = _limit.asStateFlow()

    private val _limitChosen = MutableStateFlow(false)
    /** True cuando el usuario ya eligió y guardó su temperatura límite. */
    val limitChosen: StateFlow<Boolean> = _limitChosen.asStateFlow()

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _intervalSeconds = MutableStateFlow(2)
    /** Cada cuántos segundos el sensor envía una lectura. */
    val intervalSeconds: StateFlow<Int> = _intervalSeconds.asStateFlow()

    private val _transport = MutableStateFlow(Transport.WIFI)
    val transport: StateFlow<Transport> = _transport.asStateFlow()

    var lastMonitorIp: String
        get() = prefs.getString(KEY_LAST_IP, "") ?: ""
        set(value) { prefs.edit().putString(KEY_LAST_IP, value).apply() }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
        _limit.value = prefs.getFloat(KEY_LIMIT, 30f)
        _limitChosen.value = prefs.getBoolean(KEY_LIMIT_CHOSEN, false)
        _soundEnabled.value = prefs.getBoolean(KEY_SOUND, true)
        _intervalSeconds.value = prefs.getInt(KEY_INTERVAL, 2)
        _transport.value = runCatching { Transport.valueOf(prefs.getString(KEY_TRANSPORT, null) ?: "") }
            .getOrDefault(Transport.WIFI)
    }

    /** Guarda la temperatura límite elegida por el usuario (precisión de 0,1 °C). */
    fun setLimit(value: Float) {
        val clean = Math.round(value.coerceIn(MIN_LIMIT, MAX_LIMIT) * 10f) / 10f
        _limit.value = clean
        _limitChosen.value = true
        prefs.edit().putFloat(KEY_LIMIT, clean).putBoolean(KEY_LIMIT_CHOSEN, true).apply()
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        prefs.edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun setIntervalSeconds(seconds: Int) {
        val clean = seconds.coerceIn(1, 10)
        _intervalSeconds.value = clean
        prefs.edit().putInt(KEY_INTERVAL, clean).apply()
    }

    fun setTransport(transport: Transport) {
        _transport.value = transport
        prefs.edit().putString(KEY_TRANSPORT, transport.name).apply()
    }

    private const val KEY_LIMIT = "limite_max"
    private const val KEY_LIMIT_CHOSEN = "limite_elegido"
    private const val KEY_SOUND = "sonido"
    private const val KEY_INTERVAL = "intervalo_s"
    private const val KEY_TRANSPORT = "transporte"
    private const val KEY_LAST_IP = "ultima_ip"
}
