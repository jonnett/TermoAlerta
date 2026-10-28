package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Pantallas de la app. */
enum class Screen { LOGIN, HOME, MONITOR, SENSOR, SETTINGS, HISTORY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TermoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TermoNavigation()
                }
            }
        }
    }
}

/**
 * Navegación simple con una pila de pantallas.
 * Al salir de Monitor o Sensor con "volver" se corta la conexión.
 */
@Composable
private fun TermoNavigation() {
    val start = if (AuthRepository.isLoggedIn) Screen.HOME else Screen.LOGIN
    var stackText by rememberSaveable { mutableStateOf(start.name) }
    val stack = stackText.split(',').map { Screen.valueOf(it) }
    var demoUser by rememberSaveable { mutableStateOf(false) }

    // Control de acceso: sin sesión iniciada solo se puede ver el login
    val current = if (!AuthRepository.isLoggedIn && !demoUser) Screen.LOGIN else stack.last()

    fun go(screen: Screen) { stackText = (stack + screen).joinToString(",") { it.name } }
    fun reset(screen: Screen) { stackText = screen.name }
    fun back() {
        if (current == Screen.MONITOR || current == Screen.SENSOR) ConnectionManager.stop()
        if (stack.size > 1) stackText = stack.dropLast(1).joinToString(",") { it.name }
    }

    BackHandler(enabled = stack.size > 1) { back() }

    when (current) {
        Screen.LOGIN -> LoginScreen(
            onLoggedIn = { demoUser = false; reset(Screen.HOME) },
            onDemoMode = { demoUser = true; reset(Screen.HOME) },
        )

        Screen.HOME -> HomeScreen(
            userLabel = AuthRepository.currentEmail ?: if (demoUser) "Modo de prueba (sin login)" else "",
            onMonitor = { go(Screen.MONITOR) },
            onSensor = { go(Screen.SENSOR) },
            onSettings = { go(Screen.SETTINGS) },
            onHistory = { go(Screen.HISTORY) },
            onLogout = {
                ConnectionManager.stop()
                AuthRepository.signOut()
                demoUser = false
                reset(Screen.LOGIN)
            },
        )

        Screen.MONITOR -> MonitorScreen(
            onBack = ::back,
            onSettings = { go(Screen.SETTINGS) },
            onHistory = { go(Screen.HISTORY) },
        )

        Screen.SENSOR -> SensorScreen(onBack = ::back)
        Screen.SETTINGS -> SettingsScreen(onBack = ::back)
        Screen.HISTORY -> HistoryScreen(onBack = ::back)
    }
}
