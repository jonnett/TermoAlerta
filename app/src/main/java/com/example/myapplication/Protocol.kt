package com.example.myapplication

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Mensajes que intercambian el SENSOR y el MONITOR.
 * Cada mensaje es un JSON de una línea que viaja cifrado (ver [SecureChannel]).
 *
 *   {"t":"hello","role":"sensor","name":"Pixel 7"}
 *   {"t":"temp","v":24.6,"ts":1730000000000,"src":"simulado"}
 *   {"t":"limit","v":30.0}
 */
sealed interface Message {
    fun toJson(): String

    data class Hello(val role: String, val name: String) : Message {
        override fun toJson(): String =
            JSONObject().put("t", "hello").put("role", role).put("name", name).toString()
    }

    data class Temperature(val celsius: Float, val timestamp: Long, val source: String) : Message {
        override fun toJson(): String = JSONObject()
            .put("t", "temp")
            .put("v", celsius.toDouble())
            .put("ts", timestamp)
            .put("src", source)
            .toString()
    }

    data class Limit(val maxCelsius: Float) : Message {
        override fun toJson(): String =
            JSONObject().put("t", "limit").put("v", maxCelsius.toDouble()).toString()
    }

    companion object {
        /** Rango físico aceptado; cualquier valor fuera se descarta (validación de entradas). */
        val VALID_RANGE = -50f..150f

        fun parse(json: String): Message? = try {
            val o = JSONObject(json)
            when (o.getString("t")) {
                "hello" -> Hello(
                    role = o.getString("role").take(20),
                    name = o.optString("name", "Dispositivo").take(40),
                )
                "temp" -> {
                    val v = o.getDouble("v").toFloat()
                    if (v in VALID_RANGE) {
                        Temperature(v, o.optLong("ts", System.currentTimeMillis()), o.optString("src", "?").take(20))
                    } else null
                }
                "limit" -> {
                    val v = o.getDouble("v").toFloat()
                    if (v in VALID_RANGE) Limit(v) else null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Cifrado de extremo a extremo entre los dos teléfonos (ISO/IEC 27400: confidencialidad
 * e integridad de los datos del dispositivo IoT).
 *
 * - La clave AES de 256 bits se deriva del PIN de vinculación con PBKDF2-HMAC-SHA256
 *   y una sal aleatoria que el monitor genera en cada conexión.
 * - Cada mensaje se cifra con AES-GCM y un IV aleatorio de 12 bytes; GCM además
 *   detecta cualquier mensaje alterado. Un PIN incorrecto hace fallar el descifrado.
 */
class SecureChannel private constructor(private val key: SecretKey) {

    private val random = SecureRandom()

    fun encrypt(plain: String): String {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    /** Lanza una excepción si el mensaje fue alterado o se cifró con otro PIN. */
    fun decrypt(line: String): String {
        val all = Base64.getDecoder().decode(line.trim())
        require(all.size > IV_BYTES + TAG_BITS / 8) { "Mensaje demasiado corto" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES))
        val plain = cipher.doFinal(all, IV_BYTES, all.size - IV_BYTES)
        return String(plain, Charsets.UTF_8)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 60_000

        fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

        fun fromPin(pin: String, salt: ByteArray): SecureChannel {
            val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
            try {
                val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).encoded
                return SecureChannel(SecretKeySpec(bytes, "AES"))
            } finally {
                spec.clearPassword()
            }
        }

        fun encodeSalt(salt: ByteArray): String = Base64.getEncoder().encodeToString(salt)
        fun decodeSalt(text: String): ByteArray = Base64.getDecoder().decode(text.trim())
    }
}
