package com.example.myapplication

import android.content.Context
import android.util.Patterns
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Autenticación con Firebase Authentication (correo y contraseña).
 * Firebase guarda las contraseñas con hash en la nube y toda la comunicación va por HTTPS/TLS.
 */
object AuthRepository {

    private var configured = false

    fun init(context: Context) {
        configured = FirebaseApp.getApps(context).isNotEmpty()
    }

    /** False si falta app/google-services.json. */
    val isConfigured: Boolean get() = configured

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    val currentEmail: String?
        get() = if (configured) auth.currentUser?.email else null

    val isLoggedIn: Boolean get() = currentEmail != null

    suspend fun signIn(email: String, password: String): Result<Unit> = runAuth {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(email: String, password: String): Result<Unit> = runAuth {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runAuth {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() {
        if (configured) auth.signOut()
    }

    // ---------------------------------------------------------------- validaciones

    fun validateEmail(email: String): String? = when {
        email.isBlank() -> "Escribe tu correo"
        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "El correo no es válido"
        else -> null
    }

    /** Política de contraseña: mínimo 8 caracteres, con letras y números. */
    fun validateNewPassword(password: String): String? = when {
        password.length < 8 -> "Usa al menos 8 caracteres"
        password.none { it.isLetter() } || password.none { it.isDigit() } -> "Combina letras y números"
        else -> null
    }

    // ---------------------------------------------------------------- internos

    private suspend fun runAuth(block: suspend () -> Unit): Result<Unit> {
        if (!configured) {
            return Result.failure(IllegalStateException("Firebase no está configurado (falta google-services.json)"))
        }
        return try {
            block()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyMessage(e), e))
        }
    }

    private fun friendlyMessage(e: Exception): String = when (e) {
        is FirebaseAuthWeakPasswordException -> "La contraseña es muy débil"
        is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese correo"
        is FirebaseAuthInvalidUserException -> "No existe una cuenta con ese correo o está deshabilitada"
        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos"
        is FirebaseTooManyRequestsException -> "Demasiados intentos. Espera unos minutos"
        is FirebaseNetworkException -> "Sin conexión a internet"
        else -> e.message ?: "No se pudo completar la operación"
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                cont.resumeWithException(task.exception ?: Exception("Error desconocido"))
            }
        }
    }
}
