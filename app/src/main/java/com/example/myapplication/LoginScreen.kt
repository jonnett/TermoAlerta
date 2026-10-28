package com.example.myapplication

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onDemoMode: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var registerMode by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }   // la contraseña no se guarda en el estado persistente
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    fun submit() {
        error = null
        info = null
        AuthRepository.validateEmail(email)?.let { error = it; return }
        if (registerMode) {
            AuthRepository.validateNewPassword(password)?.let { error = it; return }
            if (password != confirm) { error = "Las contraseñas no coinciden"; return }
        } else if (password.isEmpty()) {
            error = "Escribe tu contraseña"; return
        }
        busy = true
        scope.launch {
            val result = if (registerMode) AuthRepository.register(email, password)
            else AuthRepository.signIn(email, password)
            busy = false
            result.onSuccess {
                password = ""
                confirm = ""
                onLoggedIn()
            }.onFailure { error = it.message }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_stat_thermo),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Text("TermoAlerta", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Monitoreo de temperatura con alertas entre dispositivos",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (!AuthRepository.isConfigured) {
            ErrorCard(
                "Firebase no está configurado. Descarga google-services.json desde la consola de " +
                    "Firebase, cópialo en la carpeta app/ del proyecto y vuelve a compilar."
            )
        }

        SectionCard(title = if (registerMode) "Crear cuenta" else "Iniciar sesión") {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.take(100) },
                label = { Text("Correo electrónico") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.take(64) },
                label = { Text("Contraseña") },
                supportingText = {
                    if (registerMode) Text("Mínimo 8 caracteres, con letras y números")
                },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (registerMode) ImeAction.Next else ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (registerMode) {
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.take(64) },
                    label = { Text("Repite la contraseña") },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }

            Button(
                onClick = { submit() },
                enabled = !busy && AuthRepository.isConfigured,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(if (registerMode) "Crear cuenta" else "Ingresar")
            }

            if (!registerMode) {
                TextButton(
                    onClick = {
                        error = null
                        info = null
                        AuthRepository.validateEmail(email)?.let { error = it; return@TextButton }
                        busy = true
                        scope.launch {
                            AuthRepository.sendPasswordReset(email)
                                .onSuccess { info = "Te enviamos un correo para restablecer la contraseña" }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    },
                    enabled = !busy && AuthRepository.isConfigured,
                ) { Text("¿Olvidaste tu contraseña?") }
            }
        }

        OutlinedButton(
            onClick = {
                registerMode = !registerMode
                error = null
                info = null
                confirm = ""
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (registerMode) "Ya tengo cuenta, iniciar sesión" else "No tengo cuenta, registrarme")
        }

        // Solo en compilaciones de prueba y si Firebase aún no está configurado
        if (!AuthRepository.isConfigured && debuggable) {
            TextButton(onClick = onDemoMode) {
                Text("Entrar sin login (solo pruebas mientras configuras Firebase)")
            }
        }
    }
}
